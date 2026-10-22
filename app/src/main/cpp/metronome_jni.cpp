#include <jni.h>

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
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetPlaying(
    JNIEnv*, jobject, jlong handle, jboolean playing) {
    if (auto* engine = fromHandle(handle)) {
        engine->setPlaying(playing == JNI_TRUE);
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
