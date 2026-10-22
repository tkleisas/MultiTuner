#include "audio/Delay.h"
#include "audio/Reverb.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using multituner::audio::Delay;
using multituner::audio::Reverb;

namespace {
int failures = 0;

void check(bool condition, const char* message) {
    if (!condition) {
        std::printf("FAIL: %s\n", message);
        ++failures;
    }
}

double peak(const std::vector<float>& buffer, int start = 0) {
    double result = 0.0;
    for (int i = start; i < static_cast<int>(buffer.size()); ++i) {
        result = std::max(result, static_cast<double>(std::fabs(buffer[i])));
    }
    return result;
}

void testDelay() {
    Delay delay;
    delay.init(48000.0);
    delay.setTimeMs(100.0f);
    delay.setFeedback(0.5f);
    delay.setTone(1.0f);
    delay.reset();

    std::vector<float> left(12000, 0.0f);
    std::vector<float> right(12000, 0.0f);
    left[0] = 1.0f;
    right[0] = 1.0f;
    delay.process(left.data(), right.data(), static_cast<int>(left.size()));

    check(peak(left, 0) > 0.0, "delay produces an echo");
    check(peak(left, 5400) > 0.0, "delay echo repeats with feedback");
}

void testReverb() {
    Reverb reverb;
    reverb.init(48000.0);
    reverb.setSize(0.6f);
    reverb.setDamping(0.4f);
    reverb.reset();

    std::vector<float> left(8192, 0.0f);
    std::vector<float> right(8192, 0.0f);
    left[0] = 1.0f;
    right[0] = 1.0f;
    reverb.process(left.data(), right.data(), static_cast<int>(left.size()));

    check(peak(left, 1000) > 0.0, "reverb produces a tail");
    check(peak(right, 1000) > 0.0, "reverb produces a stereo tail");
}
} // namespace

int main() {
    testDelay();
    testReverb();
    if (failures == 0) {
        std::printf("effects tests passed\n");
        return 0;
    }
    std::printf("effects failures=%d\n", failures);
    return 1;
}
