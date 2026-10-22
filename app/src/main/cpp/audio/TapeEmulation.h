#pragma once

// Adapted from the YAWN DAW TapeEmulation effect (MIT License).
// Copyright (c) 2026 Tasos Kleisas.

#include "audio/Biquad.h"
#include "audio/Oversampling.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace multituner::audio {

class TapeEmulation {
public:
    enum Param {
        kSaturation = 0,
        kWow,
        kFlutter,
        kHiss,
        kTone,
        kWetDry,
        kOversample,
        kParamCount,
    };

    void init(double sampleRate, int maxBlockSize);
    void reset();
    void process(float* buffer, int numFrames, int numChannels);

    void setBypassed(bool bypassed) { m_bypassed = bypassed; }
    bool bypassed() const { return m_bypassed; }

    void setParameter(int index, float value) {
        if (index < 0 || index >= kParamCount) return;
        m_params[index] = value;
        if (index == kTone) updateToneFilter();
    }

    float getParameter(int index) const {
        return (index >= 0 && index < kParamCount) ? m_params[index] : 0.0f;
    }

private:
    static float tapeSaturate(float x) {
        if (x >= 0.0f) return std::tanh(x);
        return std::tanh(x * 0.8f) * 1.25f;
    }

    float readInterp(const std::vector<float>& buffer, float delaySamples) const {
        float readPos = static_cast<float>(m_writePos) - delaySamples;
        if (readPos < 0.0f) readPos += m_delaySize;
        int index0 = static_cast<int>(readPos) % m_delaySize;
        int index1 = (index0 + 1) % m_delaySize;
        float fraction = readPos - std::floor(readPos);
        return buffer[index0] * (1.0f - fraction) + buffer[index1] * fraction;
    }

    float nextNoise() {
        m_hissState ^= m_hissState << 13;
        m_hissState ^= m_hissState >> 17;
        m_hissState ^= m_hissState << 5;
        return static_cast<float>(static_cast<int32_t>(m_hissState)) / 2147483648.0f;
    }

    void updateToneFilter() {
        Biquad::computeStereo(m_toneL, m_toneR, Biquad::Type::LowPass,
                              m_sampleRate, m_params[kTone], 0.0, 0.707);
        Biquad::computeStereo(m_hissFilterL, m_hissFilterR, Biquad::Type::BandPass,
                              m_sampleRate, 3000.0, 0.0, 0.8);
    }

    std::vector<float> m_delayL;
    std::vector<float> m_delayR;
    int m_delaySize = 0;
    int m_writePos = 0;
    double m_wowPhase = 0.0;
    double m_flutterPhase = 0.0;
    Biquad m_toneL;
    Biquad m_toneR;
    Biquad m_hissFilterL;
    Biquad m_hissFilterR;
    uint32_t m_hissState = 12345;
    dsp::Oversampler2x m_osL;
    dsp::Oversampler2x m_osR;

    double m_sampleRate = 48000.0;
    int m_maxBlockSize = 512;
    bool m_bypassed = true;
    float m_params[kParamCount] = {0.3f, 0.15f, 0.1f, 0.05f, 8000.0f, 1.0f, 0.0f};
};

} // namespace multituner::audio
