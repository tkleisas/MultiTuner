#include "yin.h"

#include <algorithm>
#include <cmath>

namespace multituner {

namespace {
constexpr float kSilenceRms = 0.0035f;
constexpr float kYinThreshold = 0.15f;
constexpr float kFallbackThreshold = 0.35f;
constexpr float kMinFrequency = 25.0f;
constexpr float kMaxFrequency = 2000.0f;
constexpr float kDcBlockerPole = 0.995f;
} // namespace

YinPitchDetector::YinPitchDetector(int sampleRate, int windowSize, int hopSize)
    : m_sampleRate(sampleRate),
      m_windowSize(windowSize),
      m_hopSize(hopSize),
      m_halfSize(windowSize / 2) {
    if (m_sampleRate <= 0) m_sampleRate = 48000;
    if (m_windowSize < 1024) m_windowSize = 1024;
    if (m_windowSize % 2 != 0) m_windowSize += 1;
    m_halfSize = m_windowSize / 2;
    if (m_hopSize <= 0) m_hopSize = m_windowSize / 2;
    if (m_hopSize > m_windowSize) m_hopSize = m_windowSize;

    m_ring.assign(m_windowSize, 0.0f);
    m_linear.assign(m_windowSize, 0.0f);
    m_diff.assign(m_halfSize, 0.0f);
    m_cmnd.assign(m_halfSize, 0.0f);
    reset();
}

void YinPitchDetector::reset() {
    std::fill(m_ring.begin(), m_ring.end(), 0.0f);
    std::fill(m_linear.begin(), m_linear.end(), 0.0f);
    std::fill(m_diff.begin(), m_diff.end(), 0.0f);
    std::fill(m_cmnd.begin(), m_cmnd.end(), 0.0f);
    m_writePos = 0;
    m_filled = 0;
    m_sinceHop = 0;
    m_dcPrevIn = 0.0f;
    m_dcPrevOut = 0.0f;
    m_frequency = 0.0f;
    m_confidence = 0.0f;
    m_rms = 0.0f;
    m_havePitch = false;
}

void YinPitchDetector::push(const int16_t* samples, int count) {
    for (int i = 0; i < count; ++i) {
        const float x = static_cast<float>(samples[i]) * (1.0f / 32768.0f);
        const float filtered = x - m_dcPrevIn + kDcBlockerPole * m_dcPrevOut;
        m_dcPrevIn = x;
        m_dcPrevOut = filtered;

        m_ring[m_writePos] = filtered;
        m_writePos = (m_writePos + 1) % m_windowSize;

        if (m_filled < m_windowSize) ++m_filled;
        ++m_sinceHop;
    }
}

void YinPitchDetector::push(const float* samples, int count) {
    for (int i = 0; i < count; ++i) {
        const float x = samples[i];
        const float filtered = x - m_dcPrevIn + kDcBlockerPole * m_dcPrevOut;
        m_dcPrevIn = x;
        m_dcPrevOut = filtered;

        m_ring[m_writePos] = filtered;
        m_writePos = (m_writePos + 1) % m_windowSize;

        if (m_filled < m_windowSize) ++m_filled;
        ++m_sinceHop;
    }
}

bool YinPitchDetector::process() {
    if (m_filled < m_windowSize || m_sinceHop < m_hopSize) return false;
    m_sinceHop = 0;
    detect();
    return true;
}

void YinPitchDetector::detect() {
    const int n = m_windowSize;
    const int halfN = m_halfSize;

    for (int i = 0; i < n; ++i) {
        m_linear[i] = m_ring[(m_writePos + i) % n];
    }

    float sumSquares = 0.0f;
    for (int i = 0; i < n; ++i) {
        sumSquares += m_linear[i] * m_linear[i];
    }
    m_rms = std::sqrt(sumSquares / static_cast<float>(n));

    if (m_rms < kSilenceRms) {
        m_frequency = 0.0f;
        m_confidence = 0.0f;
        m_havePitch = false;
        return;
    }

    for (int tau = 0; tau < halfN; ++tau) {
        float sum = 0.0f;
        for (int j = 0; j < halfN; ++j) {
            const float d = m_linear[j] - m_linear[j + tau];
            sum += d * d;
        }
        m_diff[tau] = sum;
    }

    m_cmnd[0] = 1.0f;
    float runningSum = 0.0f;
    for (int tau = 1; tau < halfN; ++tau) {
        runningSum += m_diff[tau];
        if (runningSum <= 1e-12f) {
            m_cmnd[tau] = 1.0f;
        } else {
            m_cmnd[tau] = m_diff[tau] * static_cast<float>(tau) / runningSum;
        }
    }

    int tauEstimate = -1;
    for (int tau = 2; tau < halfN; ++tau) {
        if (m_cmnd[tau] < kYinThreshold) {
            while (tau + 1 < halfN && m_cmnd[tau + 1] < m_cmnd[tau]) ++tau;
            tauEstimate = tau;
            break;
        }
    }

    if (tauEstimate < 0) {
        int bestTau = -1;
        float bestValue = 1.0f;
        for (int tau = 2; tau < halfN; ++tau) {
            if (m_cmnd[tau] < bestValue) {
                bestValue = m_cmnd[tau];
                bestTau = tau;
            }
        }
        if (bestTau > 0 && bestValue < kFallbackThreshold) {
            tauEstimate = bestTau;
        }
    }

    if (tauEstimate < 0) {
        m_frequency = 0.0f;
        m_confidence = 0.0f;
        m_havePitch = false;
        return;
    }

    float betterTau = static_cast<float>(tauEstimate);
    if (tauEstimate > 0 && tauEstimate < halfN - 1) {
        const float s0 = m_cmnd[tauEstimate - 1];
        const float s1 = m_cmnd[tauEstimate];
        const float s2 = m_cmnd[tauEstimate + 1];
        const float denom = 2.0f * (s0 + s2 - 2.0f * s1);
        if (std::fabs(denom) > 1e-8f) {
            betterTau += (s0 - s2) / denom;
        }
    }

    if (betterTau <= 1.0f) {
        m_frequency = 0.0f;
        m_confidence = 0.0f;
        m_havePitch = false;
        return;
    }

    const float frequency = static_cast<float>(m_sampleRate) / betterTau;
    if (frequency < kMinFrequency || frequency > kMaxFrequency) {
        m_frequency = 0.0f;
        m_confidence = 0.0f;
        m_havePitch = false;
        return;
    }

    m_frequency = frequency;
    m_confidence = std::clamp(1.0f - m_cmnd[tauEstimate], 0.0f, 1.0f);
    m_havePitch = m_confidence > 0.25f;
}

} // namespace multituner
