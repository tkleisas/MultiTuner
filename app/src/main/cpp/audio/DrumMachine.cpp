#include "audio/DrumMachine.h"

namespace multituner::audio {

void DrumMachine::init(double sampleRate, int maxBlockSize) {
    m_sampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    m_synth.init(m_sampleRate, maxBlockSize);
}

void DrumMachine::reset() {
    m_synth.reset();
}

void DrumMachine::setStep(int track, int step, int level) {
    if (track < 0 || track >= kTracks || step < 0 || step >= kSteps) return;
    const uint32_t mask = 3u << (step * 2);
    const uint32_t value = static_cast<uint32_t>(std::clamp(level, 0, 2)) << (step * 2);
    uint32_t old = m_pattern[track].load(std::memory_order_acquire);
    uint32_t updated = 0;
    do {
        updated = (old & ~mask) | value;
    } while (!m_pattern[track].compare_exchange_weak(
        old, updated, std::memory_order_release, std::memory_order_acquire));
}

void DrumMachine::setPattern(const uint8_t* levels) {
    if (levels == nullptr) return;
    for (int track = 0; track < kTracks; ++track) {
        for (int step = 0; step < kSteps; ++step) {
            setStep(track, step, levels[track * kSteps + step]);
        }
    }
}

int DrumMachine::stepLevel(int track, int step) const {
    if (track < 0 || track >= kTracks || step < 0 || step >= kSteps) return 0;
    const uint32_t bits = m_pattern[track].load(std::memory_order_acquire);
    return static_cast<int>((bits >> (step * 2)) & 3u);
}

void DrumMachine::setSwing(float swing) {
    m_swing.store(std::clamp(swing, 0.0f, 0.6f), std::memory_order_release);
}

void DrumMachine::setVolume(float volume) {
    m_volume.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_release);
}

int DrumMachine::currentStep(double bpm, int64_t positionInSamples) const {
    if (bpm <= 0.0) return 0;
    const double stepSamples = m_sampleRate * 15.0 / bpm;
    if (stepSamples <= 0.0) return 0;
    int step = static_cast<int>(std::floor(
        static_cast<double>(positionInSamples) / stepSamples));
    step %= kSteps;
    if (step < 0) step += kSteps;
    return step;
}

void DrumMachine::process(float* outL, float* outR, int numFrames, double bpm,
                          int64_t positionInSamples, bool triggerEnabled) {
    if (numFrames <= 0) return;

    if (!triggerEnabled) {
        m_synth.render(outL, outR, numFrames);
        return;
    }

    if (bpm <= 0.0) return;

    const double stepSamples = m_sampleRate * 15.0 / bpm;
    if (stepSamples <= 0.0) return;

    const float swing = m_swing.load(std::memory_order_acquire);
    const float volume = m_volume.load(std::memory_order_acquire);
    const double oddDelay = stepSamples * swing;
    const int64_t bufferEnd = positionInSamples + numFrames;

    int firstStep = static_cast<int>(
        std::floor(static_cast<double>(positionInSamples) / stepSamples)) - 1;
    if (firstStep < 0) firstStep = 0;

    int cursor = 0;
    for (int step = firstStep; ; ++step) {
        double eventTime = static_cast<double>(step) * stepSamples;
        if ((step & 1) != 0) eventTime += oddDelay;
        const int64_t eventSample = static_cast<int64_t>(eventTime + 0.5);
        if (eventSample >= bufferEnd) break;
        if (eventSample < positionInSamples) continue;

        const int frame = static_cast<int>(eventSample - positionInSamples);
        if (frame > cursor) {
            m_synth.render(outL + cursor, outR + cursor, frame - cursor);
            cursor = frame;
        }

        int patternStep = step % kSteps;
        if (patternStep < 0) patternStep += kSteps;
        for (int track = 0; track < kTracks; ++track) {
            const int level = stepLevel(track, patternStep);
            if (level <= 0) continue;
            const float velocity = (level == 2 ? 1.0f : 0.7f) * volume;
            m_synth.trigger(track, velocity);
        }
    }

    if (cursor < numFrames) {
        m_synth.render(outL + cursor, outR + cursor, numFrames - cursor);
    }
}

} // namespace multituner::audio
