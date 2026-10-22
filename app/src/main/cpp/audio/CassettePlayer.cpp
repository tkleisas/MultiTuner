#include "audio/CassettePlayer.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <limits>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace multituner::audio {

namespace {
uint32_t readU32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

uint16_t readU16(const uint8_t* p) {
    return static_cast<uint16_t>(p[0]) |
           static_cast<uint16_t>(p[1] << 8);
}

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

float panLeft(float pan) {
    return std::cos((pan + 1.0f) * 0.7853981f);
}

float panRight(float pan) {
    return std::sin((pan + 1.0f) * 0.7853981f);
}

constexpr int kSincRadius = 8;
constexpr int kMaxEffectBlock = 8192;
constexpr double kPi = 3.14159265358979323846;

float readWindowedSinc(const uint8_t* data, size_t frames, double position,
                       double cutoff) {
    if (position < 0.0 || position >= static_cast<double>(frames)) return 0.0f;

    const int64_t base = static_cast<int64_t>(std::floor(position));
    const double fraction = position - static_cast<double>(base);
    double sum = 0.0;
    double weightSum = 0.0;

    for (int tap = -kSincRadius; tap < kSincRadius; ++tap) {
        const int64_t index = base + tap;
        if (index < 0 || index >= static_cast<int64_t>(frames)) continue;

        const double distance =
            (static_cast<double>(tap) - fraction) * cutoff;
        const double sinc = std::fabs(distance) < 1e-9
            ? 1.0
            : std::sin(kPi * distance) / (kPi * distance);
        const double window = 0.5 + 0.5 * std::cos(
            kPi * (static_cast<double>(tap) - fraction) / (kSincRadius + 1));
        const double weight = sinc * window;

        const int16_t pcm = static_cast<int16_t>(
            data[index * 2] | (data[index * 2 + 1] << 8));
        sum += static_cast<double>(pcm) * weight;
        weightSum += weight;
    }

    if (std::fabs(weightSum) < 1e-9) return 0.0f;
    return static_cast<float>(sum / weightSum) / 32768.0f;
}
} // namespace

CassettePlayer::MappedTrack::~MappedTrack() {
    if (mapping != nullptr) {
        munmap(mapping, mappingBytes);
        mapping = nullptr;
    }
}

void CassettePlayer::init(double sampleRate) {
    mSampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    mTapeLengthSamples = kTapeSeconds * mSampleRate;
    mTape.init(mSampleRate, kMaxEffectBlock);
    mTape.setBypassed(true);
    for (int i = 0; i < TapeEmulation::kParamCount; ++i) {
        mTapeParams[i].store(mTape.getParameter(i), std::memory_order_release);
    }
    mTapeParamsDirty.store(false, std::memory_order_release);
    mReverb.init(mSampleRate);
    mDelay.init(mSampleRate);
    mReverbSendLeft.assign(kMaxEffectBlock, 0.0f);
    mReverbSendRight.assign(kMaxEffectBlock, 0.0f);
    mDelaySendLeft.assign(kMaxEffectBlock, 0.0f);
    mDelaySendRight.assign(kMaxEffectBlock, 0.0f);
    mBusLeft.assign(kMaxEffectBlock, 0.0f);
    mBusRight.assign(kMaxEffectBlock, 0.0f);
    reset();
}

void CassettePlayer::reset() {
    mTape.reset();
    mReverb.reset();
    mDelay.reset();
    mPeakLevel.store(0.0f, std::memory_order_release);
    mWasTapeEnabled = false;
}

float CassettePlayer::peakLevel() const {
    return mPeakLevel.load(std::memory_order_acquire);
}

bool CassettePlayer::parseWav(const uint8_t* bytes, size_t size, int& sampleRate,
                              const uint8_t*& data, size_t& dataBytes) {
    if (bytes == nullptr || size < 12) return false;
    if (std::memcmp(bytes, "RIFF", 4) != 0 ||
        std::memcmp(bytes + 8, "WAVE", 4) != 0) {
        return false;
    }

    sampleRate = 0;
    int channels = 0;
    int bits = 0;
    int format = 0;
    data = nullptr;
    dataBytes = 0;

    size_t offset = 12;
    while (offset + 8 <= size) {
        const uint8_t* chunk = bytes + offset;
        const uint32_t chunkSize = readU32(chunk + 4);
        const size_t chunkData = offset + 8;
        if (chunkData + chunkSize > size) return false;

        if (std::memcmp(chunk, "fmt ", 4) == 0 && chunkSize >= 16) {
            format = readU16(bytes + chunkData);
            channels = readU16(bytes + chunkData + 2);
            sampleRate = static_cast<int>(readU32(bytes + chunkData + 4));
            bits = readU16(bytes + chunkData + 14);
        } else if (std::memcmp(chunk, "data", 4) == 0) {
            data = bytes + chunkData;
            dataBytes = chunkSize;
        }

        offset = chunkData + chunkSize + (chunkSize & 1u);
    }

    return format == 1 && channels == 1 && bits == 16 &&
           sampleRate > 0 && data != nullptr &&
           dataBytes >= 2;
}

bool CassettePlayer::loadTrack(int index, const std::string& path,
                               int64_t startSample) {
    if (index < 0 || index >= kTracks) return false;

    const int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        clearTrack(index);
        return false;
    }

    struct stat st {};
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        clearTrack(index);
        return false;
    }

    const size_t size = static_cast<size_t>(st.st_size);
    void* mapping = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (mapping == MAP_FAILED) {
        clearTrack(index);
        return false;
    }

    const auto* bytes = static_cast<const uint8_t*>(mapping);
    int sampleRate = 0;
    const uint8_t* data = nullptr;
    size_t dataBytes = 0;
    if (!parseWav(bytes, size, sampleRate, data, dataBytes)) {
        munmap(mapping, size);
        clearTrack(index);
        return false;
    }

    auto track = std::make_shared<MappedTrack>();
    track->mapping = mapping;
    track->mappingBytes = size;
    track->data = data;
    track->frames = dataBytes / 2;
    track->sampleRate = sampleRate;
    track->startSample = std::max<int64_t>(0, startSample);
    track->path = path;

    std::atomic_store_explicit(
        &mTracks[index], std::move(track), std::memory_order_release);
    return true;
}

void CassettePlayer::clearTrack(int index) {
    if (index < 0 || index >= kTracks) return;
    std::shared_ptr<MappedTrack> empty;
    std::atomic_store_explicit(&mTracks[index], std::move(empty),
                               std::memory_order_release);
}

void CassettePlayer::clearTracks() {
    for (int i = 0; i < kTracks; ++i) clearTrack(i);
    mReverb.reset();
    mDelay.reset();
}

void CassettePlayer::setDeckGain(int deck, float gain) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].gain.store(std::clamp(gain, 0.0f, 2.0f),
                               std::memory_order_release);
    }
}

void CassettePlayer::setDeckPan(int deck, float pan) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].pan.store(std::clamp(pan, -1.0f, 1.0f),
                              std::memory_order_release);
    }
}

void CassettePlayer::setDeckMute(int deck, bool muted) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].muted.store(muted, std::memory_order_release);
    }
}

void CassettePlayer::setDeckSolo(int deck, bool soloed) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].soloed.store(soloed, std::memory_order_release);
    }
}

void CassettePlayer::setDeckReverbSend(int deck, float send) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].reverbSend.store(
            std::clamp(send, 0.0f, 1.0f), std::memory_order_release);
    }
}

void CassettePlayer::setDeckDelaySend(int deck, float send) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].delaySend.store(
            std::clamp(send, 0.0f, 1.0f), std::memory_order_release);
    }
}

void CassettePlayer::setDeckBusSend(int deck, float send) {
    if (deck >= 0 && deck < kTracks) {
        mDeck[deck].busSend.store(
            std::clamp(send, 0.0f, 1.0f), std::memory_order_release);
    }
}

void CassettePlayer::setVolume(float volume) {
    mVolume.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_release);
}

void CassettePlayer::setFlip(bool flipped) {
    mFlip.store(flipped, std::memory_order_release);
}

void CassettePlayer::setTapeEnabled(bool enabled) {
    mTapeEnabled.store(enabled, std::memory_order_release);
}

void CassettePlayer::setTapeParameter(int index, float value) {
    if (index < 0 || index >= TapeEmulation::kParamCount) return;
    mTapeParams[index].store(value, std::memory_order_release);
    mTapeParamsDirty.store(true, std::memory_order_release);
}

void CassettePlayer::setReverbReturn(float value) {
    mReverbReturn.store(std::clamp(value, 0.0f, 1.0f),
                        std::memory_order_release);
}

void CassettePlayer::setReverbSize(float value) {
    mReverbSize.store(std::clamp(value, 0.0f, 1.0f),
                      std::memory_order_release);
}

void CassettePlayer::setReverbDamping(float value) {
    mReverbDamping.store(std::clamp(value, 0.0f, 1.0f),
                         std::memory_order_release);
}

void CassettePlayer::setDelayReturn(float value) {
    mDelayReturn.store(std::clamp(value, 0.0f, 1.0f),
                       std::memory_order_release);
}

void CassettePlayer::setDelayTimeMs(float value) {
    mDelayTimeMs.store(std::clamp(value, 40.0f, 1200.0f),
                       std::memory_order_release);
}

void CassettePlayer::setDelayFeedback(float value) {
    mDelayFeedback.store(std::clamp(value, 0.0f, 0.9f),
                         std::memory_order_release);
}

void CassettePlayer::setDelayTone(float value) {
    mDelayTone.store(std::clamp(value, 0.0f, 1.0f),
                     std::memory_order_release);
}

void CassettePlayer::process(float* stereo, int numFrames,
                             int64_t transportSamples, float varispeed) {
    if (numFrames <= 0) return;

    std::shared_ptr<MappedTrack> tracks[kTracks];
    for (int i = 0; i < kTracks; ++i) {
        tracks[i] = std::atomic_load_explicit(&mTracks[i],
                                              std::memory_order_acquire);
    }

    bool anySolo = false;
    for (int i = 0; i < kTracks; ++i) {
        if (mDeck[i].soloed.load(std::memory_order_acquire)) {
            anySolo = true;
            break;
        }
    }

    const bool flipped = mFlip.load(std::memory_order_acquire);
    const float master = mVolume.load(std::memory_order_acquire);
    const bool tapeEnabled = mTapeEnabled.load(std::memory_order_acquire);
    const float reverbReturn = mReverbReturn.load(std::memory_order_acquire);
    const float delayReturn = mDelayReturn.load(std::memory_order_acquire);
    const bool effectBlockFits = numFrames <= kMaxEffectBlock;
    const bool reverbActive = effectBlockFits && reverbReturn > 0.0f;
    const bool delayActive = effectBlockFits && delayReturn > 0.0f;
    const float reverbSize = mReverbSize.load(std::memory_order_acquire);
    const float reverbDamping = mReverbDamping.load(std::memory_order_acquire);
    const float delayTimeMs = mDelayTimeMs.load(std::memory_order_acquire);
    const float delayFeedback = mDelayFeedback.load(std::memory_order_acquire);
    const float delayTone = mDelayTone.load(std::memory_order_acquire);
    const int effectFrames = std::min(numFrames, kMaxEffectBlock);

    if (reverbActive) {
        std::fill(mReverbSendLeft.begin(),
                  mReverbSendLeft.begin() + effectFrames, 0.0f);
        std::fill(mReverbSendRight.begin(),
                  mReverbSendRight.begin() + effectFrames, 0.0f);
        mReverb.setSize(reverbSize);
        mReverb.setDamping(reverbDamping);
    }
    if (delayActive) {
        std::fill(mDelaySendLeft.begin(),
                  mDelaySendLeft.begin() + effectFrames, 0.0f);
        std::fill(mDelaySendRight.begin(),
                  mDelaySendRight.begin() + effectFrames, 0.0f);
        mDelay.setTimeMs(delayTimeMs);
        mDelay.setFeedback(delayFeedback);
        mDelay.setTone(delayTone);
    }
    std::fill(mBusLeft.begin(), mBusLeft.begin() + effectFrames, 0.0f);
    std::fill(mBusRight.begin(), mBusRight.begin() + effectFrames, 0.0f);

    for (int deck = 0; deck < kTracks; ++deck) {
        if (mDeck[deck].muted.load(std::memory_order_acquire)) continue;
        if (anySolo && !mDeck[deck].soloed.load(std::memory_order_acquire)) {
            continue;
        }

        const int trackIndex = flipped ? (kTracks - 1 - deck) : deck;
        const auto& track = tracks[trackIndex];
        if (!track || track->frames < 2) continue;

        const float gain = mDeck[deck].gain.load(std::memory_order_acquire) * master;
        const float pan = mDeck[deck].pan.load(std::memory_order_acquire);
        const float gainL = gain * panLeft(pan);
        const float gainR = gain * panRight(pan);
        const float reverbSend = reverbActive
            ? mDeck[deck].reverbSend.load(std::memory_order_acquire)
            : 0.0f;
        const float delaySend = delayActive
            ? mDeck[deck].delaySend.load(std::memory_order_acquire)
            : 0.0f;
        const float busSend = mDeck[deck].busSend.load(std::memory_order_acquire);
        const float reverbGainL = gainL * reverbSend;
        const float reverbGainR = gainR * reverbSend;
        const float delayGainL = gainL * delaySend;
        const float delayGainR = gainR * delaySend;
        const float busGainL = gainL * busSend;
        const float busGainR = gainR * busSend;

        const double rate =
            static_cast<double>(track->sampleRate) / mSampleRate;
        const double readStep = static_cast<double>(varispeed) * rate;
        const double cutoff = readStep > 1.0 ? 1.0 / readStep : 1.0;

        for (int i = 0; i < numFrames; ++i) {
            double sourcePos = static_cast<double>(transportSamples + i) *
                               static_cast<double>(varispeed);
            if (flipped) {
                sourcePos = mTapeLengthSamples - 1.0 - sourcePos;
            }
            if (sourcePos < 0.0 || sourcePos >= mTapeLengthSamples) continue;

            const double filePos =
                (sourcePos - static_cast<double>(track->startSample)) * rate;
            const float sample = readWindowedSinc(
                track->data, track->frames, filePos, cutoff);

            stereo[i * 2] += sample * gainL;
            stereo[i * 2 + 1] += sample * gainR;
            if (reverbSend > 0.0f) {
                mReverbSendLeft[static_cast<size_t>(i)] += sample * reverbGainL;
                mReverbSendRight[static_cast<size_t>(i)] += sample * reverbGainR;
            }
            if (delaySend > 0.0f) {
                mDelaySendLeft[static_cast<size_t>(i)] += sample * delayGainL;
                mDelaySendRight[static_cast<size_t>(i)] += sample * delayGainR;
            }
            if (busSend > 0.0f) {
                mBusLeft[static_cast<size_t>(i)] += sample * busGainL;
                mBusRight[static_cast<size_t>(i)] += sample * busGainR;
            }
        }
    }

    if (reverbActive) {
        mReverb.process(mReverbSendLeft.data(), mReverbSendRight.data(),
                        effectFrames);
    }
    if (delayActive) {
        mDelay.process(mDelaySendLeft.data(), mDelaySendRight.data(),
                       effectFrames);
    }
    if (reverbActive || delayActive) {
        for (int i = 0; i < effectFrames; ++i) {
            if (reverbActive) {
                stereo[i * 2] +=
                    mReverbSendLeft[static_cast<size_t>(i)] * reverbReturn;
                stereo[i * 2 + 1] +=
                    mReverbSendRight[static_cast<size_t>(i)] * reverbReturn;
            }
            if (delayActive) {
                stereo[i * 2] +=
                    mDelaySendLeft[static_cast<size_t>(i)] * delayReturn;
                stereo[i * 2 + 1] +=
                    mDelaySendRight[static_cast<size_t>(i)] * delayReturn;
            }
        }
    }

    if (tapeEnabled) {
        if (mTapeParamsDirty.exchange(false, std::memory_order_acq_rel)) {
            for (int i = 0; i < TapeEmulation::kParamCount; ++i) {
                mTape.setParameter(
                    i, mTapeParams[i].load(std::memory_order_acquire));
            }
        }
        if (!mWasTapeEnabled) {
            mTape.reset();
            mWasTapeEnabled = true;
        }
        mTape.setBypassed(false);
        mTape.process(stereo, numFrames, 2);
    } else {
        mWasTapeEnabled = false;
    }

    float peak = 0.0f;
    for (int i = 0; i < numFrames * 2; ++i) {
        peak = std::max(peak, std::fabs(stereo[i]));
    }
    mPeakLevel.store(std::min(peak, 2.0f), std::memory_order_release);
}

bool CassettePlayer::renderWav(const std::string& path, int64_t totalFrames) {
    if (totalFrames <= 0) return false;
    const uint64_t dataBytes64 = static_cast<uint64_t>(totalFrames) * 4u;
    if (dataBytes64 > std::numeric_limits<uint32_t>::max()) return false;
    const uint32_t dataBytes = static_cast<uint32_t>(dataBytes64);
    const int sampleRate = static_cast<int>(mSampleRate);

    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) return false;

    out.write("RIFF", 4);
    writeU32(out, 36u + dataBytes);
    out.write("WAVE", 4);
    out.write("fmt ", 4);
    writeU32(out, 16);
    writeU16(out, 1);
    writeU16(out, 2);
    writeU32(out, static_cast<uint32_t>(sampleRate));
    writeU32(out, static_cast<uint32_t>(sampleRate) * 4u);
    writeU16(out, 4);
    writeU16(out, 16);
    out.write("data", 4);
    writeU32(out, dataBytes);
    if (!out) return false;

    reset();
    constexpr int kBlockFrames = 4096;
    std::vector<float> block(static_cast<size_t>(kBlockFrames) * 2, 0.0f);
    std::vector<int16_t> pcm(static_cast<size_t>(kBlockFrames) * 2, 0);

    for (int64_t position = 0; position < totalFrames;
         position += kBlockFrames) {
        const int frames = static_cast<int>(
            std::min<int64_t>(kBlockFrames, totalFrames - position));
        std::fill(block.begin(), block.end(), 0.0f);
        process(block.data(), frames, position, 1.0f);
        for (int i = 0; i < frames * 2; ++i) {
            const float clamped = std::clamp(block[static_cast<size_t>(i)],
                                             -1.0f, 1.0f);
            pcm[static_cast<size_t>(i)] = static_cast<int16_t>(
                std::lround(static_cast<double>(clamped) * 32767.0));
        }
        out.write(reinterpret_cast<const char*>(pcm.data()),
                  static_cast<std::streamsize>(frames) * 4);
        if (!out) return false;
    }

    return out.good();
}

} // namespace multituner::audio
