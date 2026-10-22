#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <vector>

#include "audio/DrumMachine.h"
#include "audio/Metronome.h"
#include "audio/Transport.h"

namespace multituner::audio {

class MetronomeEngine : public oboe::AudioStreamCallback {
public:
    MetronomeEngine() = default;
    ~MetronomeEngine() override;

    bool open();
    void shutdown();

    void setBpm(double bpm);
    void setBeatsPerBar(int beats);
    void setSubdivision(int subdivision);
    void setVolume(float volume);
    void setAccentEnabled(bool enabled);

    void setMetronomePlaying(bool playing);
    void setDrumsPlaying(bool playing);

    bool isMetronomePlaying() const;
    bool isDrumsPlaying() const;
    bool isPlaying() const;
    bool isReady() const;

    int currentBeat() const;
    float beatPhase() const;
    int currentDrumStep() const;
    int sampleRate() const;

    void setDrumStep(int track, int step, int level);
    void setDrumPattern(const uint8_t* levels);
    void setDrumSwing(float swing);
    void setDrumVolume(float volume);

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(
        oboe::AudioStream* audioStream,
        oboe::Result error) override;

private:
    void syncTransport();

    oboe::ManagedStream mStream;
    Transport mTransport;
    Metronome mMetronome;
    DrumMachine mDrums;

    std::atomic<bool> mReady{false};
    std::atomic<bool> mResetClick{false};
    std::atomic<bool> mMetronomePlaying{false};
    std::atomic<bool> mDrumsPlaying{false};

    int32_t mChannels = 2;
    int32_t mSampleRate = 48000;
    std::vector<float> mDrumL;
    std::vector<float> mDrumR;
};

} // namespace multituner::audio
