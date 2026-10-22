#include "audio/DrumMachine.h"
#include "audio/DrumSynth.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using multituner::audio::DrumMachine;
using multituner::audio::DrumSynth;

namespace {
int failures = 0;

void check(bool condition, const char* message) {
    if (!condition) {
        std::printf("FAIL: %s\n", message);
        ++failures;
    }
}

double peak(const std::vector<float>& left, const std::vector<float>& right) {
    double result = 0.0;
    for (float sample : left) {
        result = std::max(result, static_cast<double>(std::fabs(sample)));
    }
    for (float sample : right) {
        result = std::max(result, static_cast<double>(std::fabs(sample)));
    }
    return result;
}

void testDrumSynth() {
    for (int slot = 0; slot < DrumSynth::kNumDrums; ++slot) {
        DrumSynth synth;
        synth.init(48000.0, 1024);
        synth.trigger(slot, 1.0f);
        std::vector<float> left(1024, 0.0f);
        std::vector<float> right(1024, 0.0f);
        synth.render(left.data(), right.data(), 1024);
        check(peak(left, right) > 0.0, "drum slot produces audio");
    }

    DrumSynth silent;
    silent.init(48000.0, 1024);
    std::vector<float> left(1024, 0.0f);
    std::vector<float> right(1024, 0.0f);
    silent.render(left.data(), right.data(), 1024);
    check(peak(left, right) == 0.0, "silent drum synth stays silent");
}

void testDrumMachine() {
    constexpr double bpm = 120.0;

    DrumMachine downbeat;
    downbeat.init(48000.0, 1024);
    downbeat.setStep(DrumSynth::Kick, 0, 1);
    std::vector<float> left(1024, 0.0f);
    std::vector<float> right(1024, 0.0f);
    downbeat.process(left.data(), right.data(), 1024, bpm, 0, true);
    check(peak(left, right) > 0.0, "kick step triggers at the downbeat");

    DrumMachine fourthStep;
    fourthStep.init(48000.0, 1024);
    fourthStep.setStep(DrumSynth::Kick, 4, 1);
    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    fourthStep.process(left.data(), right.data(), 1024, bpm, 24000, true);
    check(peak(left, right) > 0.0, "kick step triggers on step four");

    DrumMachine noTriggers;
    noTriggers.init(48000.0, 1024);
    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    noTriggers.process(left.data(), right.data(), 1024, bpm, 0, true);
    check(peak(left, right) == 0.0, "empty pattern is silent");

    DrumMachine disabled;
    disabled.init(48000.0, 1024);
    disabled.setStep(DrumSynth::Kick, 0, 1);
    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    disabled.process(left.data(), right.data(), 1024, bpm, 0, false);
    check(peak(left, right) == 0.0, "disabled drum machine does not trigger");

    DrumMachine unswung;
    unswung.init(48000.0, 1024);
    unswung.setStep(DrumSynth::Kick, 1, 1);
    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    unswung.process(left.data(), right.data(), 1024, bpm, 6000, true);
    check(peak(left, right) > 0.0, "step one triggers without swing");

    DrumMachine swung;
    swung.init(48000.0, 1024);
    swung.setStep(DrumSynth::Kick, 1, 1);
    swung.setSwing(0.5f);
    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    swung.process(left.data(), right.data(), 1024, bpm, 6000, true);
    check(peak(left, right) == 0.0, "swing delays the offbeat step");

    std::fill(left.begin(), left.end(), 0.0f);
    std::fill(right.begin(), right.end(), 0.0f);
    swung.process(left.data(), right.data(), 1024, bpm, 9000, true);
    check(peak(left, right) > 0.0, "swung step triggers at the delayed time");

    DrumMachine counter;
    counter.init(48000.0, 1024);
    check(counter.currentStep(bpm, 0) == 0, "current step at zero");
    check(counter.currentStep(bpm, 6000) == 1, "current step after one step");
    check(counter.currentStep(bpm, 24000) == 4, "current step after four steps");
    check(counter.currentStep(bpm, 96000) == 0, "current step wraps after one bar");
}
} // namespace

int main() {
    testDrumSynth();
    testDrumMachine();
    if (failures == 0) {
        std::printf("drum tests passed\n");
        return 0;
    }
    std::printf("drum failures=%d\n", failures);
    return 1;
}
