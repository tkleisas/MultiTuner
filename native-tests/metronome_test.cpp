#include "audio/Metronome.h"
#include "audio/Transport.h"

#include <cmath>
#include <cstdio>
#include <vector>

using multituner::audio::Metronome;
using multituner::audio::Transport;

namespace {
int failures = 0;

void check(bool condition, const char* message) {
    if (!condition) {
        std::printf("FAIL: %s\n", message);
        ++failures;
    }
}

void approx(double actual, double expected, double tolerance, const char* message) {
    if (std::fabs(actual - expected) > tolerance) {
        std::printf("FAIL: %s (actual %.9f expected %.9f)\n",
                    message, actual, expected);
        ++failures;
    }
}

double peak(const std::vector<float>& buffer) {
    double result = 0.0;
    for (float sample : buffer) {
        result = std::max(result, static_cast<double>(std::fabs(sample)));
    }
    return result;
}

void testTransport() {
    Transport transport;
    check(!transport.isPlaying(), "transport defaults to stopped");
    approx(transport.bpm(), 120.0, 1e-9, "transport default BPM");
    approx(transport.sampleRate(), 48000.0, 1e-9, "transport default sample rate");
    check(transport.positionInSamples() == 0, "transport default position");

    transport.play();
    check(transport.isPlaying(), "transport play");
    transport.advance(256);
    transport.advance(512);
    check(transport.positionInSamples() == 768, "transport advance");
    transport.stop();
    transport.advance(256);
    check(transport.positionInSamples() == 768, "transport stopped advance");

    transport.setSampleRate(48000.0);
    transport.setBPM(120.0);
    approx(transport.samplesPerBeat(), 24000.0, 1e-9, "samples per beat");
    transport.setPositionInSamples(24000);
    approx(transport.positionInBeats(), 1.0, 1e-9, "position in beats");

    transport.setTimeSignature(3, 4);
    check(transport.beatsPerBar() == 3, "beats per bar");
    approx(transport.samplesPerBar(), 72000.0, 1e-9, "samples per bar");

    transport.reset();
    check(!transport.isPlaying(), "transport reset stopped");
    check(transport.positionInSamples() == 0, "transport reset position");
    approx(transport.bpm(), 120.0, 1e-9, "transport reset BPM");
}

void configureMetronome(Metronome& metronome, double sampleRate = 48000.0) {
    metronome.init(sampleRate, 1024);
    metronome.setEnabled(true);
    metronome.setVolume(1.0f);
    metronome.setBeatsPerBar(4);
    metronome.setSubdivision(1);
    metronome.setAccentEnabled(true);
}

void testMetronome() {
    constexpr double sr = 48000.0;
    constexpr double bpm = 120.0;
    constexpr int frames = 2048;

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        check(!metronome.accentClick().empty(), "accent click generated");
        check(!metronome.normalClick().empty(), "normal click generated");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        metronome.setEnabled(false);
        std::vector<float> buffer(frames * 2, 0.0f);
        metronome.process(buffer.data(), frames, 2, bpm, 0, true);
        approx(peak(buffer), 0.0, 1e-9, "disabled metronome is silent");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        std::vector<float> buffer(frames * 2, 0.0f);
        metronome.process(buffer.data(), frames, 2, bpm, 0, false);
        approx(peak(buffer), 0.0, 1e-9, "stopped metronome is silent");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        std::vector<float> buffer(frames * 2, 0.0f);
        metronome.process(buffer.data(), frames, 2, bpm, 0, true);
        check(peak(buffer) > 0.0, "downbeat click is audible");
    }

    {
        Metronome full;
        Metronome half;
        configureMetronome(full, sr);
        configureMetronome(half, sr);
        half.setVolume(0.5f);
        std::vector<float> fullBuffer(frames * 2, 0.0f);
        std::vector<float> halfBuffer(frames * 2, 0.0f);
        full.process(fullBuffer.data(), frames, 2, bpm, 0, true);
        half.process(halfBuffer.data(), frames, 2, bpm, 0, true);
        approx(peak(halfBuffer) / peak(fullBuffer), 0.5, 0.01,
               "volume scales the click");
    }

    {
        Metronome accent;
        Metronome normal;
        configureMetronome(accent, sr);
        configureMetronome(normal, sr);
        std::vector<float> accentBuffer(frames * 2, 0.0f);
        std::vector<float> normalBuffer(frames * 2, 0.0f);
        accent.process(accentBuffer.data(), frames, 2, bpm, 0, true);
        normal.process(normalBuffer.data(), frames, 2, bpm, 24000, true);
        check(peak(accentBuffer) > peak(normalBuffer),
              "downbeat click is accented");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        metronome.setSubdivision(2);
        std::vector<float> buffer(frames * 2, 0.0f);
        metronome.process(buffer.data(), frames, 2, bpm, 12000, true);
        check(peak(buffer) > 0.0, "subdivision click is audible");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        metronome.setSubdivision(2);
        std::vector<float> buffer(frames * 2, 0.0f);
        metronome.process(buffer.data(), frames, 2, bpm, 6000, true);
        approx(peak(buffer), 0.0, 1e-9, "no click between subdivisions");
    }

    {
        Metronome metronome;
        configureMetronome(metronome, sr);
        std::vector<float> first(10 * 2, 0.0f);
        std::vector<float> second(1190 * 2, 0.0f);
        metronome.process(first.data(), 10, 2, bpm, 0, true);
        metronome.process(second.data(), 1190, 2, bpm, 10, true);
        check(peak(first) > 0.0, "click starts in first buffer");
        check(peak(second) > 0.0, "click continues across buffers");
    }

    {
        Metronome accented;
        Metronome unaccented;
        configureMetronome(accented, sr);
        configureMetronome(unaccented, sr);
        unaccented.setAccentEnabled(false);
        std::vector<float> accentedBuffer(frames * 2, 0.0f);
        std::vector<float> unaccentedBuffer(frames * 2, 0.0f);
        accented.process(accentedBuffer.data(), frames, 2, bpm, 0, true);
        unaccented.process(unaccentedBuffer.data(), frames, 2, bpm, 0, true);
        check(peak(accentedBuffer) > peak(unaccentedBuffer),
              "accent toggle changes the downbeat");
    }
}
} // namespace

int main() {
    testTransport();
    testMetronome();
    if (failures == 0) {
        std::printf("metronome tests passed\n");
        return 0;
    }
    std::printf("metronome failures=%d\n", failures);
    return 1;
}
