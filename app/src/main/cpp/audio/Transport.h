#pragma once

// Adapted from the YAWN DAW transport (MIT License).
// Copyright (c) 2026 Tasos Kleisas.

#include <atomic>
#include <cstdint>

namespace multituner::audio {

class Transport {
public:
    static constexpr double kDefaultBPM = 120.0;
    static constexpr double kDefaultSampleRate = 48000.0;
    static constexpr int kDefaultNumerator = 4;
    static constexpr int kDefaultDenominator = 4;

    void setSampleRate(double sampleRate) {
        m_sampleRate.store(sampleRate, std::memory_order_release);
    }

    double sampleRate() const {
        return m_sampleRate.load(std::memory_order_acquire);
    }

    void play() { m_playing.store(true, std::memory_order_release); }
    void stop() { m_playing.store(false, std::memory_order_release); }
    bool isPlaying() const {
        return m_playing.load(std::memory_order_acquire);
    }

    void setBPM(double bpm) { m_bpm.store(bpm, std::memory_order_release); }
    double bpm() const { return m_bpm.load(std::memory_order_acquire); }

    void setTimeSignature(int numerator, int denominator) {
        m_numerator.store(numerator > 0 ? numerator : 1, std::memory_order_release);
        m_denominator.store(denominator > 0 ? denominator : 4, std::memory_order_release);
    }

    int numerator() const { return m_numerator.load(std::memory_order_acquire); }
    int denominator() const { return m_denominator.load(std::memory_order_acquire); }
    int beatsPerBar() const { return numerator(); }

    void setPositionInSamples(int64_t position) {
        m_positionInSamples.store(position, std::memory_order_release);
    }

    int64_t positionInSamples() const {
        return m_positionInSamples.load(std::memory_order_acquire);
    }

    void advance(int numFrames) {
        if (m_playing.load(std::memory_order_acquire)) {
            m_positionInSamples.fetch_add(numFrames, std::memory_order_relaxed);
        }
    }

    double positionInBeats() const {
        const double sr = sampleRate();
        const double currentBpm = bpm();
        if (sr <= 0.0 || currentBpm <= 0.0) return 0.0;
        return (static_cast<double>(positionInSamples()) / sr) * (currentBpm / 60.0);
    }

    double samplesPerBeat() const {
        const double currentBpm = bpm();
        if (currentBpm <= 0.0) return 0.0;
        return sampleRate() * 60.0 / currentBpm;
    }

    double samplesPerBar() const {
        return samplesPerBeat() * static_cast<double>(beatsPerBar());
    }

    void reset() {
        m_playing.store(false, std::memory_order_release);
        m_positionInSamples.store(0, std::memory_order_release);
        m_bpm.store(kDefaultBPM, std::memory_order_release);
        m_numerator.store(kDefaultNumerator, std::memory_order_release);
        m_denominator.store(kDefaultDenominator, std::memory_order_release);
    }

private:
    std::atomic<double> m_sampleRate{kDefaultSampleRate};
    std::atomic<bool> m_playing{false};
    std::atomic<double> m_bpm{kDefaultBPM};
    std::atomic<int64_t> m_positionInSamples{0};
    std::atomic<int> m_numerator{kDefaultNumerator};
    std::atomic<int> m_denominator{kDefaultDenominator};
};

} // namespace multituner::audio
