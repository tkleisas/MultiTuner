#pragma once

// Adapted from the YAWN DAW metronome (MIT License).
// Copyright (c) 2026 Tasos Kleisas.

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace multituner::audio {

class Metronome {
public:
    void init(double sampleRate, int maxBlockSize);
    void reset();

    void setEnabled(bool enabled) { m_enabled.store(enabled, std::memory_order_release); }
    bool enabled() const { return m_enabled.load(std::memory_order_acquire); }

    void setVolume(float volume) {
        m_volume.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_release);
    }
    float volume() const { return m_volume.load(std::memory_order_acquire); }

    void setBeatsPerBar(int beats) {
        m_beatsPerBar.store(std::max(1, beats), std::memory_order_release);
    }
    int beatsPerBar() const { return m_beatsPerBar.load(std::memory_order_acquire); }

    void setSubdivision(int subdivision) {
        m_subdivision.store(std::clamp(subdivision, 1, 4), std::memory_order_release);
    }
    int subdivision() const { return m_subdivision.load(std::memory_order_acquire); }

    void setAccentEnabled(bool enabled) {
        m_accentEnabled.store(enabled, std::memory_order_release);
    }
    bool accentEnabled() const {
        return m_accentEnabled.load(std::memory_order_acquire);
    }

    void process(float* output, int numFrames, int numChannels,
                 double bpm, int64_t positionInSamples, bool playing);

    const std::vector<float>& accentClick() const { return m_accentClick; }
    const std::vector<float>& normalClick() const { return m_normalClick; }

private:
    void generateClicks();
    void mixClickRange(float* output, int numChannels, int fromFrame, int toFrame);

    double m_sampleRate = 48000.0;
    std::atomic<bool> m_enabled{true};
    std::atomic<float> m_volume{0.7f};
    std::atomic<int> m_beatsPerBar{4};
    std::atomic<int> m_subdivision{1};
    std::atomic<bool> m_accentEnabled{true};

    std::vector<float> m_accentClick;
    std::vector<float> m_normalClick;

    int m_clickPos = -1;
    bool m_clickAccent = false;
};

} // namespace multituner::audio
