#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <fstream>
#include <string>
#include <thread>

namespace multituner::audio {

class InternalRecorder {
public:
    ~InternalRecorder();

    bool start(const std::string& path, double sampleRate);
    void stop();

    bool isRecording() const;
    bool failed() const;
    float peakLevel() const;

    void push(const float* left, const float* right, int numFrames);
    void pushInterleaved(const float* stereo, int numFrames);

private:
    static constexpr size_t kCapacity = 262144;
    static constexpr size_t kMask = kCapacity - 1;

    void writerLoop();
    void writeWavHeader(uint32_t dataBytes);

    std::array<int16_t, kCapacity> mRing{};
    std::atomic<uint64_t> mWriteIndex{0};
    std::atomic<uint64_t> mReadIndex{0};
    std::atomic<bool> mRunning{false};
    std::atomic<bool> mFailed{false};
    std::atomic<float> mPeakLevel{0.0f};

    std::thread mThread;
    std::ofstream mFile;
    double mSampleRate = 48000.0;
    uint64_t mFramesWritten = 0;
};

} // namespace multituner::audio
