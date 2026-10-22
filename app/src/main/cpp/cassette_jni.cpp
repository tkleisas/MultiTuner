#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <string>
#include <vector>

#include "audio/CassettePlayer.h"

namespace {

constexpr int kTracks = multituner::audio::CassettePlayer::kTracks;

bool readFloats(JNIEnv* env, jfloatArray source, std::array<float, kTracks>& out) {
    if (source == nullptr || env->GetArrayLength(source) < kTracks) return false;
    env->GetFloatArrayRegion(source, 0, kTracks, out.data());
    return true;
}

bool readLongs(JNIEnv* env, jlongArray source, std::array<int64_t, kTracks>& out) {
    if (source == nullptr || env->GetArrayLength(source) < kTracks) return false;
    env->GetLongArrayRegion(source, 0, kTracks, out.data());
    return true;
}

bool readBooleans(JNIEnv* env, jbooleanArray source,
                  std::array<bool, kTracks>& out) {
    if (source == nullptr || env->GetArrayLength(source) < kTracks) return false;
    std::array<jboolean, kTracks> values{};
    env->GetBooleanArrayRegion(source, 0, kTracks, values.data());
    for (int i = 0; i < kTracks; ++i) out[i] = values[i] == JNI_TRUE;
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_CassetteNative_nativeRender(
    JNIEnv* env, jobject, jobjectArray paths, jlongArray starts,
    jfloatArray gains, jfloatArray pans, jbooleanArray muted,
    jbooleanArray soloed, jfloatArray reverbSends, jfloatArray delaySends,
    jlong totalFrames, jstring outputPath, jfloat reverbReturn,
    jfloat reverbSize, jfloat reverbDamping, jfloat delayReturn,
    jfloat delayTimeMs, jfloat delayFeedback, jfloat delayTone,
    jboolean tapeEnabled, jfloat tapeDrive, jfloat tapeTone, jfloat tapeWow,
    jfloat tapeFlutter, jfloat tapeHiss, jfloat tapeMix) {
    if (paths == nullptr || env->GetArrayLength(paths) < kTracks ||
        outputPath == nullptr || totalFrames <= 0) {
        return JNI_FALSE;
    }

    std::array<int64_t, kTracks> startSamples{};
    std::array<float, kTracks> gainValues{};
    std::array<float, kTracks> panValues{};
    std::array<bool, kTracks> mutedValues{};
    std::array<bool, kTracks> soloedValues{};
    std::array<float, kTracks> reverbSendValues{};
    std::array<float, kTracks> delaySendValues{};
    if (!readLongs(env, starts, startSamples) ||
        !readFloats(env, gains, gainValues) ||
        !readFloats(env, pans, panValues) ||
        !readBooleans(env, muted, mutedValues) ||
        !readBooleans(env, soloed, soloedValues) ||
        !readFloats(env, reverbSends, reverbSendValues) ||
        !readFloats(env, delaySends, delaySendValues)) {
        return JNI_FALSE;
    }

    std::array<std::string, kTracks> trackPaths;
    for (int i = 0; i < kTracks; ++i) {
        auto path = static_cast<jstring>(
            env->GetObjectArrayElement(paths, i));
        if (path == nullptr) continue;
        const char* chars = env->GetStringUTFChars(path, nullptr);
        if (chars != nullptr) {
            trackPaths[i] = chars;
            env->ReleaseStringUTFChars(path, chars);
        }
        env->DeleteLocalRef(path);
    }

    const char* outputChars = env->GetStringUTFChars(outputPath, nullptr);
    if (outputChars == nullptr) return JNI_FALSE;
    const std::string output = outputChars;
    env->ReleaseStringUTFChars(outputPath, outputChars);

    multituner::audio::CassettePlayer renderer;
    renderer.init(48000.0);
    renderer.setVolume(0.8f);
    renderer.setFlip(false);

    int loadedTracks = 0;
    for (int i = 0; i < kTracks; ++i) {
        renderer.setDeckGain(i, gainValues[i]);
        renderer.setDeckPan(i, panValues[i]);
        renderer.setDeckMute(i, mutedValues[i]);
        renderer.setDeckSolo(i, soloedValues[i]);
        renderer.setDeckReverbSend(i, reverbSendValues[i]);
        renderer.setDeckDelaySend(i, delaySendValues[i]);
        if (trackPaths[i].empty()) continue;
        if (renderer.loadTrack(i, trackPaths[i], startSamples[i])) {
            ++loadedTracks;
        }
    }
    if (loadedTracks == 0) return JNI_FALSE;

    renderer.setReverbReturn(reverbReturn);
    renderer.setReverbSize(reverbSize);
    renderer.setReverbDamping(reverbDamping);
    renderer.setDelayReturn(delayReturn);
    renderer.setDelayTimeMs(delayTimeMs);
    renderer.setDelayFeedback(delayFeedback);
    renderer.setDelayTone(delayTone);

    renderer.setTapeParameter(0, tapeDrive);
    renderer.setTapeParameter(1, tapeWow);
    renderer.setTapeParameter(2, tapeFlutter);
    renderer.setTapeParameter(3, tapeHiss);
    renderer.setTapeParameter(
        4, 200.0f * static_cast<float>(
            std::pow(100.0, static_cast<double>(tapeTone))));
    renderer.setTapeParameter(5, tapeMix);
    renderer.setTapeParameter(6, 1.0f);
    renderer.setTapeEnabled(tapeEnabled == JNI_TRUE);

    const bool result = renderer.renderWav(
        output, static_cast<int64_t>(totalFrames));
    return result ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
