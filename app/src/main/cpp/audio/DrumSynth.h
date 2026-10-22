#pragma once

// Adapted from the YAWN DAW DrumSynth (MIT License).
// Copyright (c) 2026 Tasos Kleisas.

#include "audio/Oversampling.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace multituner::audio {

class DrumSynth {
public:
    static constexpr int kNumDrums = 8;

    enum DrumSlot {
        Kick = 0,
        Snare,
        Clap,
        Tom1,
        ClosedHH,
        OpenHH,
        Tom2,
        Tambourine,
    };

    enum Param {
        pKickTune = 0, pKickAttack, pKickDecay,
        pKickSine, pKickWhite, pKickPink, pKickDrive,
        pSnareTune, pSnareAttack, pSnareDecay, pSnareDrive,
        pClapTune, pClapAttack, pClapDecay, pClapDrive,
        pTom1Tune, pTom1Attack, pTom1Decay, pTom1Drive,
        pCHHTune, pCHHAttack, pCHHDecay, pCHHDrive,
        pOHHTune, pOHHAttack, pOHHDecay, pOHHDrive,
        pTom2Tune, pTom2Attack, pTom2Decay, pTom2Drive,
        pTambTune, pTambAttack, pTambDecay, pTambDrive,
        pOversample,
        kParamCount,
    };

    void init(double sampleRate, int maxBlockSize);
    void reset();
    void trigger(int slot, float velocity);
    void render(float* outL, float* outR, int numFrames);

private:
    struct DrumVoice {
        bool active = false;
        bool justTriggered = false;
        float velocity = 0.0f;
        float ampLevel = 0.0f;
        int stage = 0;
        int ageSamples = 0;
        double phase0 = 0.0;
        double phases[6] = {};
        float bpZ1 = 0.0f;
        float bpZ2 = 0.0f;
    };

    void noteOn(int slot, float velocity);
    void renderActiveVoices(float* outL, float* outR, int n);

    void renderKick(DrumVoice& v, float* outL, float* outR, int n);
    void renderSnare(DrumVoice& v, float* outL, float* outR, int n);
    void renderClap(DrumVoice& v, float* outL, float* outR, int n);
    void renderTom(DrumVoice& v, float* outL, float* outR, int n, int slot);
    void renderHiHat(DrumVoice& v, float* outL, float* outR, int n,
                     bool open, int slot);
    void renderTambourine(DrumVoice& v, float* outL, float* outR, int n);

    float pinkNoise();
    float randFloat() {
        m_rngState ^= m_rngState << 13;
        m_rngState ^= m_rngState >> 17;
        m_rngState ^= m_rngState << 5;
        return static_cast<float>(m_rngState & 0x7FFFFF) / 8388607.0f;
    }

    static float panL(float pan) {
        return std::cos((pan + 1.0f) * 0.7853981f);
    }
    static float panR(float pan) {
        return std::sin((pan + 1.0f) * 0.7853981f);
    }
    static float drive(float x, float amount) {
        const float pre = 1.0f + amount * 5.0f;
        return std::tanh(x * pre) / std::tanh(pre);
    }
    static float tuneToHz(float t, float lo, float hi) {
        return lo * std::pow(hi / lo, std::clamp(t, 0.0f, 1.0f));
    }

    double m_sampleRate = 48000.0;
    int m_maxBlockSize = 512;
    DrumVoice m_voices[kNumDrums] = {};
    float m_params[kParamCount] = {};
    dsp::Downsampler2x m_decL;
    dsp::Downsampler2x m_decR;
    std::vector<float> m_scratchL;
    std::vector<float> m_scratchR;
    std::vector<float> m_osScratchL;
    std::vector<float> m_osScratchR;

    uint32_t m_rngState = 0xC0FFEEu;
    float m_pinkRows[5] = {};
    float m_pinkSum = 0.0f;
    uint32_t m_pinkCounter = 0;
};

} // namespace multituner::audio
