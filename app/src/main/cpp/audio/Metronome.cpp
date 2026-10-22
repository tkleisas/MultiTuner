#include "audio/Metronome.h"

namespace multituner::audio {

void Metronome::init(double sampleRate, int maxBlockSize) {
    (void)maxBlockSize;
    m_sampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    generateClicks();
    reset();
}

void Metronome::reset() {
    m_clickPos = -1;
    m_clickAccent = false;
}

void Metronome::process(float* output, int numFrames, int numChannels,
                        double bpm, int64_t positionInSamples, bool playing) {
    if (!m_enabled.load(std::memory_order_acquire)) {
        if (m_clickPos >= 0) mixClickRange(output, numChannels, 0, numFrames);
        return;
    }

    if (!playing || bpm <= 0.0) {
        if (m_clickPos >= 0) mixClickRange(output, numChannels, 0, numFrames);
        return;
    }

    const double samplesPerBeat = m_sampleRate * 60.0 / bpm;
    const int subdivision = m_subdivision.load(std::memory_order_acquire);
    const double samplesPerStep = samplesPerBeat / static_cast<double>(subdivision);
    if (samplesPerStep <= 0.0) return;

    const int beatsPerBar = m_beatsPerBar.load(std::memory_order_acquire);
    const bool accentEnabled = m_accentEnabled.load(std::memory_order_acquire);
    const int64_t bufferEnd = positionInSamples + numFrames;

    struct StepEvent {
        int frame;
        bool accent;
    };

    StepEvent events[64];
    int numEvents = 0;

    const double startStepExact =
        static_cast<double>(positionInSamples) / samplesPerStep;
    int firstStep = static_cast<int>(std::ceil(startStepExact - 1e-9));
    if (firstStep < 0) firstStep = 0;

    for (int step = firstStep; numEvents < 64; ++step) {
        const int64_t stepSample =
            static_cast<int64_t>(static_cast<double>(step) * samplesPerStep + 0.5);
        if (stepSample >= bufferEnd) break;
        if (stepSample < positionInSamples) continue;

        const bool isBeat = (step % subdivision) == 0;
        const int beatIndex = step / subdivision;
        const bool accent = accentEnabled && isBeat &&
                            (beatIndex % beatsPerBar == 0);

        events[numEvents++] = {
            static_cast<int>(stepSample - positionInSamples),
            accent,
        };
    }

    int currentFrame = 0;
    for (int eventIndex = 0; eventIndex < numEvents; ++eventIndex) {
        const int eventFrame = events[eventIndex].frame;

        if (m_clickPos >= 0 && eventFrame > currentFrame) {
            mixClickRange(output, numChannels, currentFrame, eventFrame);
        }

        m_clickPos = 0;
        m_clickAccent = events[eventIndex].accent;
        currentFrame = eventFrame;
    }

    if (m_clickPos >= 0) {
        mixClickRange(output, numChannels, currentFrame, numFrames);
    }
}

void Metronome::generateClicks() {
    const int accentLength = static_cast<int>(m_sampleRate * 0.025);
    m_accentClick.resize(accentLength);
    for (int i = 0; i < accentLength; ++i) {
        const double t = static_cast<double>(i) / m_sampleRate;
        const double envelope = std::exp(-t * 80.0);
        m_accentClick[i] =
            static_cast<float>(std::sin(2.0 * M_PI * 1500.0 * t) * envelope * 0.8);
    }

    const int normalLength = static_cast<int>(m_sampleRate * 0.020);
    m_normalClick.resize(normalLength);
    for (int i = 0; i < normalLength; ++i) {
        const double t = static_cast<double>(i) / m_sampleRate;
        const double envelope = std::exp(-t * 100.0);
        m_normalClick[i] =
            static_cast<float>(std::sin(2.0 * M_PI * 800.0 * t) * envelope * 0.6);
    }
}

void Metronome::mixClickRange(float* output, int numChannels, int fromFrame,
                              int toFrame) {
    const auto& click = m_clickAccent ? m_accentClick : m_normalClick;
    const int clickLength = static_cast<int>(click.size());
    const float volume = m_volume.load(std::memory_order_acquire);

    for (int frame = fromFrame; frame < toFrame && m_clickPos >= 0; ++frame) {
        if (m_clickPos < clickLength) {
            const float sample = click[m_clickPos] * volume;
            for (int channel = 0; channel < numChannels; ++channel) {
                output[frame * numChannels + channel] += sample;
            }
            ++m_clickPos;
        } else {
            m_clickPos = -1;
        }
    }
}

} // namespace multituner::audio
