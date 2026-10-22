#pragma once

#include "audio/DrumSynth.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>

namespace multituner::audio {

class DrumMachine {
public:
    static constexpr int kTracks = DrumSynth::kNumDrums;
    static constexpr int kSteps = 16;

    void init(double sampleRate, int maxBlockSize);
    void reset();

    void setStep(int track, int step, int level);
    void setPattern(const uint8_t* levels);
    int stepLevel(int track, int step) const;

    void setSwing(float swing);
    void setVolume(float volume);

    int currentStep(double bpm, int64_t positionInSamples) const;

    void process(float* outL, float* outR, int numFrames, double bpm,
                 int64_t positionInSamples, bool triggerEnabled);

private:
    std::atomic<uint32_t> m_pattern[kTracks] = {};
    std::atomic<float> m_swing{0.0f};
    std::atomic<float> m_volume{0.8f};
    DrumSynth m_synth;
    double m_sampleRate = 48000.0;
};

} // namespace multituner::audio
