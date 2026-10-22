#include "audio/InternalRecorder.h"

#include <algorithm>
#include <chrono>
#include <cmath>

namespace multituner::audio {

namespace {
void writeU16(std::ostream& out, uint16_t value) {
    const char bytes[2] = {
        static_cast<char>(value & 0xFF),
        static_cast<char>((value >> 8) & 0xFF),
    };
    out.write(bytes, 2);
}

void writeU32(std::ostream& out, uint32_t value) {
    const char bytes[4] = {
        static_cast<char>(value & 0xFF),
        static_cast<char>((value >> 8) & 0xFF),
        static_cast<char>((value >> 16) & 0xFF),
        static_cast<char>((value >> 24) & 0xFF),
    };
    out.write(bytes, 4);
}
} // namespace

InternalRecorder::~InternalRecorder() {
    stop();
}

bool InternalRecorder::start(const std::string& path, double sampleRate) {
    stop();

    mSampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    mFile.open(path, std::ios::binary | std::ios::trunc);
    if (!mFile) return false;

    mFramesWritten = 0;
    mWriteIndex.store(0, std::memory_order_release);
    mReadIndex.store(0, std::memory_order_release);
    mFailed.store(false, std::memory_order_release);
    mPeakLevel.store(0.0f, std::memory_order_release);
    writeWavHeader(0);
    if (!mFile) {
        mFile.close();
        return false;
    }

    mRunning.store(true, std::memory_order_release);
    mThread = std::thread(&InternalRecorder::writerLoop, this);
    return true;
}

void InternalRecorder::stop() {
    mRunning.store(false, std::memory_order_release);
    if (mThread.joinable()) mThread.join();
    if (mFile.is_open()) mFile.close();
}

bool InternalRecorder::isRecording() const {
    return mRunning.load(std::memory_order_acquire);
}

bool InternalRecorder::failed() const {
    return mFailed.load(std::memory_order_acquire);
}

float InternalRecorder::peakLevel() const {
    return mPeakLevel.load(std::memory_order_acquire);
}

void InternalRecorder::push(const float* left, const float* right, int numFrames) {
    if (!mRunning.load(std::memory_order_acquire) || numFrames <= 0) return;

    const uint64_t writeIndex = mWriteIndex.load(std::memory_order_relaxed);
    const uint64_t readIndex = mReadIndex.load(std::memory_order_acquire);
    const uint64_t freeFrames = kCapacity - (writeIndex - readIndex);
    if (freeFrames < static_cast<uint64_t>(numFrames)) {
        mFailed.store(true, std::memory_order_release);
        return;
    }

    float peak = 0.0f;
    for (int i = 0; i < numFrames; ++i) {
        const float mono = (left[i] + right[i]) * 0.5f;
        peak = std::max(peak, std::fabs(mono));
        const float clamped = std::clamp(mono, -1.0f, 1.0f);
        const auto value = static_cast<int16_t>(
            std::lround(static_cast<double>(clamped) * 32767.0));
        mRing[static_cast<size_t>((writeIndex + i) & kMask)] = value;
    }
    mWriteIndex.store(writeIndex + static_cast<uint64_t>(numFrames),
                      std::memory_order_release);
    mPeakLevel.store(std::min(peak, 2.0f), std::memory_order_release);
}


void InternalRecorder::pushInterleaved(const float* stereo, int numFrames) {
    if (!mRunning.load(std::memory_order_acquire) || numFrames <= 0) return;

    const uint64_t writeIndex = mWriteIndex.load(std::memory_order_relaxed);
    const uint64_t readIndex = mReadIndex.load(std::memory_order_acquire);
    const uint64_t freeFrames = kCapacity - (writeIndex - readIndex);
    if (freeFrames < static_cast<uint64_t>(numFrames)) {
        mFailed.store(true, std::memory_order_release);
        return;
    }

    float peak = 0.0f;
    for (int i = 0; i < numFrames; ++i) {
        const float mono = (stereo[i * 2] + stereo[i * 2 + 1]) * 0.5f;
        peak = std::max(peak, std::fabs(mono));
        const float clamped = std::clamp(mono, -1.0f, 1.0f);
        const auto value = static_cast<int16_t>(
            std::lround(static_cast<double>(clamped) * 32767.0));
        mRing[static_cast<size_t>((writeIndex + i) & kMask)] = value;
    }
    mWriteIndex.store(writeIndex + static_cast<uint64_t>(numFrames),
                      std::memory_order_release);
    mPeakLevel.store(std::min(peak, 2.0f), std::memory_order_release);
}

void InternalRecorder::writerLoop() {
    std::array<int16_t, 4096> chunk{};

    while (mRunning.load(std::memory_order_acquire) ||
           mReadIndex.load(std::memory_order_relaxed) <
               mWriteIndex.load(std::memory_order_acquire)) {
        const uint64_t writeIndex = mWriteIndex.load(std::memory_order_acquire);
        const uint64_t readIndex = mReadIndex.load(std::memory_order_relaxed);
        if (readIndex >= writeIndex) {
            std::this_thread::sleep_for(std::chrono::milliseconds(3));
            continue;
        }

        const size_t available = static_cast<size_t>(
            std::min<uint64_t>(writeIndex - readIndex, chunk.size()));
        for (size_t i = 0; i < available; ++i) {
            chunk[i] = mRing[static_cast<size_t>((readIndex + i) & kMask)];
        }
        mFile.write(reinterpret_cast<const char*>(chunk.data()),
                    static_cast<std::streamsize>(available * 2));
        if (!mFile) {
            mFailed.store(true, std::memory_order_release);
            mRunning.store(false, std::memory_order_release);
            break;
        }
        mFramesWritten += available;
        mReadIndex.store(readIndex + static_cast<uint64_t>(available),
                         std::memory_order_release);
    }

    writeWavHeader(static_cast<uint32_t>(mFramesWritten * 2));
    mFile.close();
    mRunning.store(false, std::memory_order_release);
}

void InternalRecorder::writeWavHeader(uint32_t dataBytes) {
    const int sampleRate = static_cast<int>(mSampleRate);
    mFile.seekp(0);
    mFile.write("RIFF", 4);
    writeU32(mFile, 36u + dataBytes);
    mFile.write("WAVE", 4);
    mFile.write("fmt ", 4);
    writeU32(mFile, 16);
    writeU16(mFile, 1);
    writeU16(mFile, 1);
    writeU32(mFile, static_cast<uint32_t>(sampleRate));
    writeU32(mFile, static_cast<uint32_t>(sampleRate) * 2u);
    writeU16(mFile, 2);
    writeU16(mFile, 16);
    mFile.write("data", 4);
    writeU32(mFile, dataBytes);
    mFile.seekp(0, std::ios::end);
}

} // namespace multituner::audio
