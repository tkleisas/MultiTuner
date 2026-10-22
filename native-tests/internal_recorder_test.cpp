#include "audio/InternalRecorder.h"

#include <cmath>
#include <cstdio>
#include <fstream>
#include <string>
#include <vector>

#include <unistd.h>

using multituner::audio::InternalRecorder;

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

std::string tempPath() {
    char buffer[256];
    std::snprintf(buffer, sizeof(buffer),
                  "/tmp/multituner_internal_recorder_%d.wav",
                  static_cast<int>(getpid()));
    return buffer;
}
} // namespace

int main() {
    const std::string path = tempPath();
    std::remove(path.c_str());

    InternalRecorder recorder;
    check(recorder.start(path, 48000.0), "internal recorder starts");
    check(recorder.isRecording(), "internal recorder reports recording");

    std::vector<float> left(4800, 0.5f);
    std::vector<float> right(4800, 0.5f);
    for (int block = 0; block < 12; ++block) {
        recorder.push(left.data(), right.data(), static_cast<int>(left.size()));
    }
    approx(recorder.peakLevel(), 0.5, 1e-6, "internal recorder tracks peak");
    recorder.stop();

    check(!recorder.isRecording(), "internal recorder stops");
    check(!recorder.failed(), "internal recorder did not fail");

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    check(file.is_open(), "internal recorder output exists");
    if (file.is_open()) {
        const auto size = file.tellg();
        check(static_cast<long long>(size) == 44 + 4800 * 12 * 2,
              "internal recorder writes mono PCM16");
        file.seekg(0);
        char header[12] = {};
        file.read(header, sizeof(header));
        check(std::string(header, 4) == "RIFF", "internal recorder writes RIFF");
        check(std::string(header + 8, 4) == "WAVE", "internal recorder writes WAVE");
    }
    std::remove(path.c_str());

    if (failures == 0) {
        std::printf("internal recorder tests passed\n");
        return 0;
    }
    std::printf("internal recorder failures=%d\n", failures);
    return 1;
}
