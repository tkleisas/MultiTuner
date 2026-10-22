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

extern "C" {

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetCassettePlaying(
    JNIEnv*, jobject, jlong handle, jboolean playing) {
    if (auto* engine = fromHandle(handle)) {
        engine->setCassettePlaying(playing == JNI_TRUE);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsCassettePlaying(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isCassettePlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetVarispeed(
    JNIEnv*, jobject, jlong handle, jfloat varispeed) {
    if (auto* engine = fromHandle(handle)) engine->setVarispeed(varispeed);
}

JNIEXPORT jfloat JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeGetVarispeed(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->varispeed() : 1.0f;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetFlip(
    JNIEnv*, jobject, jlong handle, jboolean flipped) {
    if (auto* engine = fromHandle(handle)) engine->setFlip(flipped == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsFlipped(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isFlipped() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeGetPositionSamples(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? static_cast<jlong>(engine->positionSamples()) : 0;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetPositionSamples(
    JNIEnv*, jobject, jlong handle, jlong position) {
    if (auto* engine = fromHandle(handle)) {
        engine->setPositionSamples(static_cast<int64_t>(position));
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeLoadCassetteTrack(
    JNIEnv* env, jobject, jlong handle, jint index, jstring path,
    jlong startSample) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool ok = engine->loadCassetteTrack(
        index, std::string(chars), static_cast<int64_t>(startSample));
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeClearCassetteTracks(
    JNIEnv*, jobject, jlong handle) {
    if (auto* engine = fromHandle(handle)) engine->clearCassetteTracks();
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckGain(
    JNIEnv*, jobject, jlong handle, jint deck, jfloat gain) {
    if (auto* engine = fromHandle(handle)) engine->setDeckGain(deck, gain);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckPan(
    JNIEnv*, jobject, jlong handle, jint deck, jfloat pan) {
    if (auto* engine = fromHandle(handle)) engine->setDeckPan(deck, pan);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckMute(
    JNIEnv*, jobject, jlong handle, jint deck, jboolean muted) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDeckMute(deck, muted == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckSolo(
    JNIEnv*, jobject, jlong handle, jint deck, jboolean soloed) {
    if (auto* engine = fromHandle(handle)) {
        engine->setDeckSolo(deck, soloed == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckReverbSend(
    JNIEnv*, jobject, jlong handle, jint deck, jfloat send) {
    if (auto* engine = fromHandle(handle)) engine->setDeckReverbSend(deck, send);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckDelaySend(
    JNIEnv*, jobject, jlong handle, jint deck, jfloat send) {
    if (auto* engine = fromHandle(handle)) engine->setDeckDelaySend(deck, send);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDeckBusSend(
    JNIEnv*, jobject, jlong handle, jint deck, jfloat send) {
    if (auto* engine = fromHandle(handle)) engine->setDeckBusSend(deck, send);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetCassetteVolume(
    JNIEnv*, jobject, jlong handle, jfloat volume) {
    if (auto* engine = fromHandle(handle)) engine->setCassetteVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetTapeEnabled(
    JNIEnv*, jobject, jlong handle, jboolean enabled) {
    if (auto* engine = fromHandle(handle)) {
        engine->setTapeEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetTapeParameter(
    JNIEnv*, jobject, jlong handle, jint index, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setTapeParameter(index, value);
}


JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeStartInternalRecording(
    JNIEnv* env, jobject, jlong handle, jint source, jstring path) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool started = engine->startInternalRecording(
        source, std::string(chars));
    env->ReleaseStringUTFChars(path, chars);
    return started ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeStopInternalRecording(
    JNIEnv*, jobject, jlong handle, jint source) {
    if (auto* engine = fromHandle(handle)) engine->stopInternalRecording(source);
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeIsInternalRecording(
    JNIEnv*, jobject, jlong handle, jint source) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->isInternalRecording(source)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeInternalPeakLevel(
    JNIEnv*, jobject, jlong handle, jint source) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->internalPeakLevel(source) : 0.0f;
}

JNIEXPORT jboolean JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeInternalRecordingFailed(
    JNIEnv*, jobject, jlong handle, jint source) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->internalRecordingFailed(source)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetReverbReturn(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setReverbReturn(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetReverbSize(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setReverbSize(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetReverbDamping(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setReverbDamping(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDelayReturn(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setDelayReturn(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDelayTimeMs(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setDelayTimeMs(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDelayFeedback(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setDelayFeedback(value);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeSetDelayTone(
    JNIEnv*, jobject, jlong handle, jfloat value) {
    if (auto* engine = fromHandle(handle)) engine->setDelayTone(value);
}

JNIEXPORT jfloat JNICALL
Java_com_tkleisas_multituner_audio_MetronomeNative_nativeCassettePeakLevel(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr ? engine->cassettePeakLevel() : 0.0f;
}

} // extern "C"
