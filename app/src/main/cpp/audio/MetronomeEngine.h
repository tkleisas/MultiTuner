#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>

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
    void setPlaying(bool playing);

    bool isPlaying() const;
    bool isReady() const;
    int currentBeat() const;
    float beatPhase() const;
    int sampleRate() const;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(
        oboe::AudioStream* audioStream,
        oboe::Result error) override;

private:
    oboe::ManagedStream mStream;
    Transport mTransport;
    Metronome mMetronome;

    std::atomic<bool> mReady{false};
    std::atomic<bool> mResetClick{false};
    int32_t mChannels = 2;
    int32_t mSampleRate = 48000;
};

} // namespace multituner::audio
