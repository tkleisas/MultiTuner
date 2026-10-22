#include <jni.h>

#include <algorithm>
#include <vector>

#include "audio/MetronomeEngine.h"

namespace {

multituner::audio::MetronomeEngine* fromHandle(jlong handle) {
    return reinterpret_cast<multituner::audio::MetronomeEngine*>(handle);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeCreate(
    JNIEnv*, jobject) {
    auto* engine = new multituner::audio::MetronomeEngine();
    if (!engine->open()) {
        delete engine;
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeDestroy(
    JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetBpm(
    JNIEnv*, jobject, jlong handle, jdouble bpm) {
    if (auto* engine = fromHandle(handle)) engine->setBpm(bpm);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetBeatsPerBar(
    JNIEnv*, jobject, jlong handle, jint beats) {
    if (auto* engine = fromHandle(handle)) engine->setBeatsPerBar(beats);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetSubdivision(
    JNIEnv*, jobject, jlong handle, jint subdivision) {
    if (auto* engine = fromHandle(handle)) engine->setSubdivision(subdivision);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetVolume(
    JNIEnv*, jobject, jlong handle, jfloat volume) {
    if (auto* engine = fromHandle(handle)) engine->setVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetAccentEnabled(
    JNIEnv*, jobject, jlong handle, jboolean enabled) {
    if (auto* engine = fromHandle(handle)) {
        engine->setAccentEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetMetronomePlaying(
    JNIEnv*, jobject, jlong handle, jboolean playing) {
    if (auto* engine = fromHandle(handle)) {
        engine->setMetronomePlaying(playing == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDrumsPlaying(
    JNIEnv*, jobject, jlong handle, jboolean playing) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDrumsPlaying(playing == JNI_TRUE);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsMetronomePlaying(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isMetronomePlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsDrumsPlaying(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isDrumsPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeCurrentDrumStep(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->currentDrumStep() : 0;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDrumStep(
    JNIEnv*, jobject, jlong handle, jint track, jint step, jint level) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDrumStep(track, step, level);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDrumPattern(
    JNIEnv* env, jobject, jlong handle, jbyteArray levels) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || levels == nullptr) return;
    const jsize count = std::min<jsize>(128, env->GetArrayLength(levels));
    std::vector<uint8_t> data(128, 0);
    env->GetByteArrayRegion(
        levels, 0, count, reinterpret_cast<jbyte*>(data.data()));
    engine->setDrumPattern(data.data());
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDrumSwing(
    JNIEnv*, jobject, jlong handle, jfloat swing) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDrumSwing(swing);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDrumVolume(
    JNIEnv*, jobject, jlong handle, jfloat volume) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDrumVolume(volume);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsPlaying(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsReady(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isReady() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeCurrentBeat(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->currentBeat() : 0;
}

JNIEXPORT jfloat JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeBeatPhase(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->beatPhase() : 0.0f;
}

JNIEXPORT jint JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSampleRate(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->sampleRate() : 0;
}

} // extern "C"
