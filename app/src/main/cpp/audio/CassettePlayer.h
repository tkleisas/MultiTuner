#pragma once

#include "audio/Delay.h"
#include "audio/Reverb.h"
#include "audio/TapeEmulation.h"

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace multituner::audio {

class CassettePlayer {
public:
    static constexpr int kTracks = 4;
    static constexpr double kTapeSeconds = 600.0;

    void init(double sampleRate);
    void reset();

    bool loadTrack(int index, const std::string& path, int64_t startSample);
    void clearTrack(int index);
    void clearTracks();

    void setDeckGain(int deck, float gain);
    void setDeckPan(int deck, float pan);
    void setDeckMute(int deck, bool muted);
    void setDeckSolo(int deck, bool soloed);
    void setDeckReverbSend(int deck, float send);
    void setDeckDelaySend(int deck, float send);
    void setDeckBusSend(int deck, float send);
    void setVolume(float volume);
    void setFlip(bool flipped);
    void setTapeEnabled(bool enabled);
    void setTapeParameter(int index, float value);
    void setReverbReturn(float value);
    void setReverbSize(float value);
    void setReverbDamping(float value);
    void setDelayReturn(float value);
    void setDelayTimeMs(float value);
    void setDelayFeedback(float value);
    void setDelayTone(float value);
    float peakLevel() const;
    const float* busLeft() const { return mBusLeft.data(); }
    const float* busRight() const { return mBusRight.data(); }

    void process(float* stereo, int numFrames, int64_t transportSamples,
                 float varispeed);

    bool renderWav(const std::string& path, int64_t totalFrames);

private:
    struct MappedTrack {
        void* mapping = nullptr;
        size_t mappingBytes = 0;
        const uint8_t* data = nullptr;
        size_t frames = 0;
        int sampleRate = 48000;
        int64_t startSample = 0;
        std::string path;

        ~MappedTrack();
    };

    struct DeckStrip {
        std::atomic<float> gain{1.0f};
        std::atomic<float> pan{0.0f};
        std::atomic<bool> muted{false};
        std::atomic<bool> soloed{false};
        std::atomic<float> reverbSend{0.0f};
        std::atomic<float> delaySend{0.0f};
        std::atomic<float> busSend{0.0f};
    };

    static bool parseWav(const uint8_t* bytes, size_t size, int& sampleRate,
                         const uint8_t*& data, size_t& dataBytes);

    std::shared_ptr<MappedTrack> mTracks[kTracks];
    DeckStrip mDeck[kTracks];
    std::atomic<bool> mFlip{false};
    std::atomic<float> mVolume{0.8f};
    std::atomic<bool> mTapeEnabled{false};
    std::atomic<bool> mTapeParamsDirty{false};
    std::atomic<float> mTapeParams[TapeEmulation::kParamCount] = {};
    std::atomic<float> mReverbReturn{0.0f};
    std::atomic<float> mReverbSize{0.5f};
    std::atomic<float> mReverbDamping{0.35f};
    std::atomic<float> mDelayReturn{0.0f};
    std::atomic<float> mDelayTimeMs{350.0f};
    std::atomic<float> mDelayFeedback{0.35f};
    std::atomic<float> mDelayTone{0.7f};
    std::atomic<float> mPeakLevel{0.0f};
    bool mWasTapeEnabled = false;

    TapeEmulation mTape;
    Reverb mReverb;
    Delay mDelay;
    std::vector<float> mReverbSendLeft;
    std::vector<float> mReverbSendRight;
    std::vector<float> mDelaySendLeft;
    std::vector<float> mDelaySendRight;
    std::vector<float> mBusLeft;
    std::vector<float> mBusRight;
    double mSampleRate = 48000.0;
    double mTapeLengthSamples = 600.0 * 48000.0;
};

} // namespace multituner::audio
