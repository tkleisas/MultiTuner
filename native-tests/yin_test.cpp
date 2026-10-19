#include "yin.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using multituner::YinPitchDetector;

namespace {
constexpr int kSampleRate = 48000;
constexpr int kWindow = 4096;
constexpr int kHop = 2048;
constexpr int kBlock = 512;
constexpr double kPi = 3.14159265358979323846;

double cents(double a, double b) {
    return 1200.0 * std::log2(a / b);
}

struct Reading {
    double frequency = 0.0;
    double confidence = 0.0;
    int detections = 0;
};

Reading runTone(double frequency, bool plucked) {
    YinPitchDetector detector(kSampleRate, kWindow, kHop);
    std::vector<int16_t> block(kBlock);
    std::vector<double> estimates;
    const long total = static_cast<long>(kSampleRate * 1.2);
    const double w = 2.0 * kPi * frequency;

    for (long offset = 0; offset < total; offset += kBlock) {
        const int count = static_cast<int>(std::min<long>(kBlock, total - offset));
        for (int i = 0; i < count; ++i) {
            const double t = static_cast<double>(offset + i) / kSampleRate;
            double value = 0.0;
            if (plucked) {
                const double attack = std::min(1.0, t / 0.004);
                const double decay = std::exp(-1.6 * t);
                value = (0.58 * std::sin(w * t + 0.3) +
                         0.24 * std::sin(2.0 * w * t + 1.1) +
                         0.11 * std::sin(3.0 * w * t + 2.4) +
                         0.05 * std::sin(4.0 * w * t + 0.7)) *
                        0.5 * attack * decay;
            } else {
                value = (0.62 * std::sin(w * t) +
                         0.27 * std::sin(2.0 * w * t) +
                         0.11 * std::sin(3.0 * w * t)) *
                        0.35;
            }
            block[i] = static_cast<int16_t>(
                std::lround(std::clamp(value, -1.0, 1.0) * 32000.0));
        }

        detector.push(block.data(), count);
        if (detector.process() && detector.hasPitch()) {
            estimates.push_back(detector.frequency());
        }
    }

    Reading result;
    result.confidence = detector.confidence();
    result.detections = static_cast<int>(estimates.size());
    if (!estimates.empty()) {
        std::sort(estimates.begin(), estimates.end());
        result.frequency = estimates[estimates.size() / 2];
    }
    return result;
}
} // namespace

int main() {
    struct Case {
        const char* name;
        double frequency;
    };

    const Case cases[] = {
        {"Guitar E2", 82.4069},
        {"Guitar A2", 110.0},
        {"Guitar D3", 146.832},
        {"Guitar G3", 195.998},
        {"Guitar B3", 246.942},
        {"Guitar E4", 329.628},
        {"Bouzouki C3", 130.813},
        {"Bouzouki F3", 174.614},
        {"Bouzouki A3", 220.0},
        {"Bouzouki D4", 293.665},
        {"Saz A2", 110.0},
        {"Saz D3", 146.832},
        {"Saz G3", 195.998},
        {"Saz E3", 164.814},
        {"Bass B1", 61.735},
    };

    int failures = 0;

    for (const bool plucked : {false, true}) {
        std::printf("%s tones\n", plucked ? "Plucked" : "Harmonic");
        for (const auto& test : cases) {
            const Reading reading = runTone(test.frequency, plucked);
            const double error = reading.frequency > 0.0
                ? cents(reading.frequency, test.frequency)
                : 99999.0;
            const bool ok = std::abs(error) < 1.0;
            if (!ok) ++failures;
            std::printf(
                "  %-12s %8.3f Hz  error %+6.2f c  confidence %.2f  %s\n",
                test.name,
                reading.frequency,
                error,
                reading.confidence,
                ok ? "OK" : "FAIL");
        }
    }

    const double detunedTarget = 110.0;
    const double detuned = detunedTarget * std::pow(2.0, 20.0 / 1200.0);
    const Reading detunedReading = runTone(detuned, false);
    const double detunedError = cents(detunedReading.frequency, detunedTarget);
    const bool detunedOk = std::abs(detunedError - 20.0) < 1.0;
    if (!detunedOk) ++failures;
    std::printf("Detuned +20 c   measured %+6.2f c  %s\n",
                detunedError, detunedOk ? "OK" : "FAIL");

    const Reading silent = runTone(110.0, false);
    std::printf("Detections in the main run: %d\n", silent.detections);

    std::printf("failures=%d\n", failures);
    return failures == 0 ? 0 : 1;
}
