#pragma once

#include <cstdint>
#include <vector>

namespace multituner {

// Adapted from the Tuner effect in the YAWN DAW.
// Copyright (c) 2026 Tasos Kleisas, MIT License.
class YinPitchDetector {
public:
    YinPitchDetector(int sampleRate, int windowSize, int hopSize);

    void reset();

    void push(const int16_t* samples, int count);
    void push(const float* samples, int count);

    bool process();

    float frequency() const { return m_frequency; }
    float confidence() const { return m_confidence; }
    float rms() const { return m_rms; }
    bool hasPitch() const { return m_havePitch; }

    int sampleRate() const { return m_sampleRate; }
    int windowSize() const { return m_windowSize; }
    int hopSize() const { return m_hopSize; }

private:
    void detect();

    int m_sampleRate;
    int m_windowSize;
    int m_hopSize;
    int m_halfSize;

    std::vector<float> m_ring;
    std::vector<float> m_linear;
    std::vector<float> m_diff;
    std::vector<float> m_cmnd;

    int m_writePos = 0;
    int m_filled = 0;
    int m_sinceHop = 0;

    float m_dcPrevIn = 0.0f;
    float m_dcPrevOut = 0.0f;

    float m_frequency = 0.0f;
    float m_confidence = 0.0f;
    float m_rms = 0.0f;
    bool m_havePitch = false;
};

} // namespace multituner
