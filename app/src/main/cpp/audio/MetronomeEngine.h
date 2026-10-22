#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

#include "audio/CassettePlayer.h"
#include "audio/DrumMachine.h"
#include "audio/InternalRecorder.h"
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
    void setCassettePlaying(bool playing);

    bool isMetronomePlaying() const;
    bool isDrumsPlaying() const;
    bool isCassettePlaying() const;
    bool isPlaying() const;
    bool isReady() const;

    int currentBeat() const;
    float beatPhase() const;
    int currentDrumStep() const;
    int sampleRate() const;
    int64_t positionSamples() const;
    void setPositionSamples(int64_t position);

    void setVarispeed(float varispeed);
    float varispeed() const;
    void setFlip(bool flipped);
    bool isFlipped() const;

    bool loadCassetteTrack(int index, const std::string& path,
                           int64_t startSample);
    void clearCassetteTracks();
    void setDeckGain(int deck, float gain);
    void setDeckPan(int deck, float pan);
    void setDeckMute(int deck, bool muted);
    void setDeckSolo(int deck, bool soloed);
    void setDeckReverbSend(int deck, float send);
    void setDeckDelaySend(int deck, float send);
    void setDeckBusSend(int deck, float send);
    void setCassetteVolume(float volume);
    void setTapeEnabled(bool enabled);
    void setTapeParameter(int index, float value);
    void setReverbReturn(float value);
    void setReverbSize(float value);
    void setReverbDamping(float value);
    void setDelayReturn(float value);
    void setDelayTimeMs(float value);
    void setDelayFeedback(float value);
    void setDelayTone(float value);
    float cassettePeakLevel() const;

    bool startInternalRecording(int source, const std::string& path);
    void stopInternalRecording(int source);
    bool isInternalRecording(int source) const;
    float internalPeakLevel(int source) const;
    bool internalRecordingFailed(int source) const;

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
    CassettePlayer mCassette;

    std::atomic<bool> mReady{false};
    std::atomic<bool> mResetClick{false};
    std::atomic<bool> mMetronomePlaying{false};
    std::atomic<bool> mDrumsPlaying{false};
    std::atomic<bool> mCassettePlaying{false};
    std::atomic<float> mVarispeed{1.0f};
    std::atomic<bool> mFlip{false};

    int32_t mChannels = 2;
    int32_t mSampleRate = 48000;
    std::vector<float> mDrumL;
    std::vector<float> mDrumR;
    std::vector<float> mMetronomeBuffer;
    std::vector<float> mCassetteBuffer;
    InternalRecorder mClickRecorder;
    InternalRecorder mDrumRecorder;
    InternalRecorder mBusRecorder;
};

} // namespace multituner::audio
