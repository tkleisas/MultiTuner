#include "audio/CassettePlayer.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <string>
#include <vector>

#include <unistd.h>

using multituner::audio::CassettePlayer;

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

void putU16(std::vector<uint8_t>& bytes, uint16_t value) {
    bytes.push_back(static_cast<uint8_t>(value & 0xFF));
    bytes.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
}

void putU32(std::vector<uint8_t>& bytes, uint32_t value) {
    putU16(bytes, static_cast<uint16_t>(value & 0xFFFF));
    putU16(bytes, static_cast<uint16_t>((value >> 16) & 0xFFFF));
}

std::string tempPath(const char* name) {
    char buffer[256];
    std::snprintf(buffer, sizeof(buffer), "/tmp/multituner_cassette_%d_%s.wav",
                  static_cast<int>(getpid()), name);
    return buffer;
}

std::string writeWav(const std::string& path, const std::vector<int16_t>& samples,
                     int sampleRate = 48000, int channels = 1) {
    const uint32_t dataBytes =
        static_cast<uint32_t>(samples.size() * sizeof(int16_t));
    std::vector<uint8_t> bytes;
    bytes.reserve(44 + dataBytes);

    bytes.insert(bytes.end(), {'R', 'I', 'F', 'F'});
    putU32(bytes, 36 + dataBytes);
    bytes.insert(bytes.end(), {'W', 'A', 'V', 'E'});
    bytes.insert(bytes.end(), {'f', 'm', 't', ' '});
    putU32(bytes, 16);
    putU16(bytes, 1);
    putU16(bytes, static_cast<uint16_t>(channels));
    putU32(bytes, static_cast<uint32_t>(sampleRate));
    putU32(bytes, static_cast<uint32_t>(sampleRate * channels * 2));
    putU16(bytes, static_cast<uint16_t>(channels * 2));
    putU16(bytes, 16);
    bytes.insert(bytes.end(), {'d', 'a', 't', 'a'});
    putU32(bytes, dataBytes);
    for (int16_t sample : samples) {
        putU16(bytes, static_cast<uint16_t>(sample));
    }

    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    file.write(reinterpret_cast<const char*>(bytes.data()),
               static_cast<std::streamsize>(bytes.size()));
    return path;
}

std::string writeText(const std::string& path, const std::string& text) {
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    file << text;
    return path;
}

std::vector<float> render(CassettePlayer& player, int frames,
                          int64_t transportSamples, float varispeed = 1.0f) {
    std::vector<float> buffer(static_cast<size_t>(frames) * 2, 0.0f);
    player.process(buffer.data(), frames, transportSamples, varispeed);
    return buffer;
}

void testLoading() {
    const auto constantPath = writeWav(
        tempPath("constant"), std::vector<int16_t>(1000, 16384));
    const auto stereoPath = writeWav(
        tempPath("stereo"), std::vector<int16_t>(1000, 16384), 48000, 2);
    const auto invalidPath = writeText(tempPath("invalid"), "not a wav file");

    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, constantPath, 0), "valid mono WAV loads");
    check(!player.loadTrack(0, invalidPath, 0), "invalid WAV is rejected");
    check(!player.loadTrack(0, stereoPath, 0), "stereo WAV is rejected");
    check(!player.loadTrack(0, "/tmp/does-not-exist-multituner.wav", 0),
          "missing file is rejected");

    const auto silent = render(player, 64, 0);
    approx(peak(silent), 0.0, 1e-9, "rejected track is cleared");

    std::remove(constantPath.c_str());
    std::remove(stereoPath.c_str());
    std::remove(invalidPath.c_str());
}

void testPlaybackAndMixer() {
    const std::vector<int16_t> constant(1000, 16384);
    const auto path = writeWav(tempPath("mix"), constant);

    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, path, 0), "mixer track loads");
    player.setVolume(0.8f);
    player.setDeckGain(0, 0.5f);

    auto buffer = render(player, 64, 0);
    approx(buffer[0], 0.5 * 0.5 * 0.8 * std::cos(3.14159265f / 4.0f),
           1e-4, "center pan uses constant power");
    approx(buffer[1], buffer[0], 1e-6, "center pan is symmetric");
    approx(player.peakLevel(), buffer[0], 1e-5, "peak level follows the output");

    player.setDeckPan(0, -1.0f);
    buffer = render(player, 64, 0);
    approx(buffer[0], 0.5 * 0.5 * 0.8, 1e-4, "hard left routes to left");
    approx(buffer[1], 0.0, 1e-6, "hard left silences right");

    player.setDeckMute(0, true);
    buffer = render(player, 64, 0);
    approx(peak(buffer), 0.0, 1e-9, "muted deck is silent");
    player.setDeckMute(0, false);

    player.setDeckSolo(1, true);
    buffer = render(player, 64, 0);
    approx(peak(buffer), 0.0, 1e-9, "solo on an empty deck silences others");
    player.setDeckSolo(1, false);

    player.setDeckGain(0, 1.0f);
    player.setDeckPan(0, 0.0f);
    player.setVolume(1.0f);
    buffer = render(player, 64, 0);
    approx(buffer[0], 0.5 * std::cos(3.14159265f / 4.0f),
           1e-4, "full gain and volume");
    approx(buffer[1], buffer[0], 1e-6, "full gain remains symmetric");

    std::remove(path.c_str());
}

void testStartOffset() {
    const auto path = writeWav(
        tempPath("offset"), std::vector<int16_t>(1000, 16384));
    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, path, 1000), "offset track loads");
    player.setVolume(1.0f);
    player.setDeckGain(0, 1.0f);
    player.setDeckPan(0, -1.0f);

    auto buffer = render(player, 64, 0);
    approx(peak(buffer), 0.0, 1e-9, "audio before the recorded position is silent");

    buffer = render(player, 64, 1000);
    approx(buffer[0], 0.5, 1e-3, "audio starts at the recorded position");

    std::remove(path.c_str());
}

void testFlipAndVarispeed() {
    std::vector<int16_t> ramp(1000);
    for (size_t i = 0; i < ramp.size(); ++i) {
        ramp[i] = static_cast<int16_t>(i * 20);
    }
    const auto path = writeWav(tempPath("flip"), ramp);

    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(3, path, 0), "flip track loads");
    player.setVolume(1.0f);
    player.setDeckGain(0, 1.0f);
    player.setDeckPan(0, -1.0f);
    player.setDeckSolo(0, true);

    const int64_t tapeEnd = static_cast<int64_t>(
        CassettePlayer::kTapeSeconds * 48000.0);
    player.setFlip(true);

    auto buffer = render(player, 1, tapeEnd - 1 - 100);
    approx(buffer[0], (100.0 * 20.0) / 32768.0, 1e-3,
           "flip routes stored track four to deck one");

    buffer = render(player, 1, tapeEnd - 1 - 99);
    approx(buffer[0], (99.0 * 20.0) / 32768.0, 1e-3,
           "flip plays the tape backwards");

    player.setFlip(false);
    buffer = render(player, 1, 100);
    approx(peak(buffer), 0.0, 1e-9,
           "unflipped deck does not play the stored track four");

    player.clearTracks();
    check(player.loadTrack(0, path, 0), "varispeed track loads");
    buffer = render(player, 1, 100, 2.0f);
    approx(buffer[0], (200.0 * 20.0) / 32768.0, 0.02,
           "varispeed maps playback position");

    buffer = render(player, 1, 100, 0.5f);
    approx(buffer[0], (50.0 * 20.0) / 32768.0, 0.02,
           "half speed maps playback position");

    std::remove(path.c_str());
}

void testEffectSends() {
    const auto path = writeWav(
        tempPath("sends"), std::vector<int16_t>(1000, 16384));

    CassettePlayer dry;
    dry.init(48000.0);
    check(dry.loadTrack(0, path, 0), "send test track loads");
    dry.setVolume(1.0f);
    dry.setDeckGain(0, 1.0f);
    dry.setDeckPan(0, -1.0f);
    dry.setDeckSolo(0, true);
    const auto dryBuffer = render(dry, 8192, 0);

    CassettePlayer reverb;
    reverb.init(48000.0);
    check(reverb.loadTrack(0, path, 0), "reverb send track loads");
    reverb.setVolume(1.0f);
    reverb.setDeckGain(0, 1.0f);
    reverb.setDeckPan(0, -1.0f);
    reverb.setDeckSolo(0, true);
    reverb.setDeckReverbSend(0, 1.0f);
    reverb.setReverbReturn(1.0f);
    reverb.setReverbSize(0.6f);
    reverb.setReverbDamping(0.4f);
    const auto reverbBuffer = render(reverb, 8192, 0);
    check(std::fabs(reverbBuffer[4000]) > 1e-4,
          "reverb send returns into the cassette mix");

    CassettePlayer delay;
    delay.init(48000.0);
    check(delay.loadTrack(0, path, 0), "delay send track loads");
    delay.setVolume(1.0f);
    delay.setDeckGain(0, 1.0f);
    delay.setDeckPan(0, -1.0f);
    delay.setDeckSolo(0, true);
    delay.setDeckDelaySend(0, 1.0f);
    delay.setDelayReturn(1.0f);
    delay.setDelayTimeMs(100.0f);
    delay.setDelayFeedback(0.4f);
    delay.setDelayTone(1.0f);
    delay.reset();
    const auto delayBuffer = render(delay, 8192, 0);
    check(std::fabs(delayBuffer[10000]) > 1e-4, "delay send returns an echo");

    std::remove(path.c_str());
}


void testRenderWav() {
    const auto inputPath = writeWav(
        tempPath("renderin"), std::vector<int16_t>(1000, 16384));
    const auto outputPath = tempPath("renderout");

    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, inputPath, 0), "render input loads");
    player.setVolume(1.0f);
    player.setDeckGain(0, 1.0f);
    player.setDeckPan(0, -1.0f);
    check(player.renderWav(outputPath, 2000), "cassette mixdown renders");

    std::ifstream file(outputPath, std::ios::binary | std::ios::ate);
    const auto size = file.tellg();
    check(size == 44 + 2000 * 4, "mixdown has a stereo PCM16 payload");

    std::remove(inputPath.c_str());
    std::remove(outputPath.c_str());
}


void testBusSend() {
    const auto path = writeWav(
        tempPath("bus"), std::vector<int16_t>(1000, 16384));

    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, path, 0), "bus track loads");
    player.setVolume(1.0f);
    player.setDeckGain(0, 1.0f);
    player.setDeckPan(0, -1.0f);
    player.setDeckSolo(0, true);
    player.setDeckBusSend(0, 1.0f);

    std::vector<float> buffer(64 * 2, 0.0f);
    player.process(buffer.data(), 64, 0, 1.0f);
    check(player.busLeft()[0] > 0.49f, "deck bus send feeds the bus");
    check(player.busRight()[0] == 0.0f, "deck bus respects pan");

    player.setDeckMute(0, true);
    std::fill(buffer.begin(), buffer.end(), 0.0f);
    player.process(buffer.data(), 64, 0, 1.0f);
    check(player.busLeft()[0] == 0.0f, "muted deck does not feed the bus");

    std::remove(path.c_str());
}

void testTapeEmulation() {
    const auto path = writeWav(
        tempPath("tape"), std::vector<int16_t>(4096, 16384));
    CassettePlayer player;
    player.init(48000.0);
    check(player.loadTrack(0, path, 0), "tape track loads");
    player.setVolume(1.0f);
    player.setDeckGain(0, 1.0f);

    const auto dry = render(player, 2048, 0);
    player.setTapeEnabled(true);
    const auto wet = render(player, 2048, 0);
    check(peak(wet) > 0.0, "tape emulation produces audio");
    check(std::fabs(wet[1500] - dry[1500]) > 1e-5,
          "tape emulation changes the dry signal");

    player.setTapeParameter(3, 1.0f);
    player.setTapeEnabled(false);
    const auto bypassed = render(player, 2048, 0);
    check(std::fabs(bypassed[1500] - dry[1500]) < 1e-6,
          "bypassing tape restores the dry signal");

    std::remove(path.c_str());
}
} // namespace

int main() {
    testLoading();
    testPlaybackAndMixer();
    testStartOffset();
    testFlipAndVarispeed();
    testEffectSends();
    testBusSend();
    testTapeEmulation();
    testRenderWav();

    if (failures == 0) {
        std::printf("cassette tests passed\n");
        return 0;
    }
    std::printf("cassette failures=%d\n", failures);
    return 1;
}
