#include <jni.h>

#include <memory>
#include <vector>

#include "yin.h"

namespace {

struct NativeTuner {
    std::unique_ptr<multituner::YinPitchDetector> detector;
    std::vector<float> scratch;
};

NativeTuner* fromHandle(jlong handle) {
    return reinterpret_cast<NativeTuner*>(handle);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tkleisas_multituner_audio_TunerNative_nativeCreate(
    JNIEnv*, jobject, jint sampleRate, jint windowSize, jint hopSize) {
    auto* tuner = new NativeTuner();
    tuner->detector = std::make_unique<multituner::YinPitchDetector>(
        sampleRate, windowSize, hopSize);
    tuner->scratch.resize(static_cast<size_t>(windowSize));
    return reinterpret_cast<jlong>(tuner);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_TunerNative_nativeDestroy(
    JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT void JNICALL
Java_com_tkleisas_multituner_audio_TunerNative_nativeReset(
    JNIEnv*, jobject, jlong handle) {
    if (auto* tuner = fromHandle(handle)) {
        tuner->detector->reset();
    }
}

JNIEXPORT jint JNICALL
Java_com_tkleisas_multituner_audio_TunerNative_nativeProcess(
    JNIEnv* env, jobject, jlong handle, jshortArray samples, jint count,
    jfloatArray out) {
    auto* tuner = fromHandle(handle);
    if (tuner == nullptr || samples == nullptr || out == nullptr || count <= 0) {
        return 0;
    }

    const jsize available = env->GetArrayLength(samples);
    const jsize n = count < available ? count : available;

    jshort* data = env->GetShortArrayElements(samples, nullptr);
    if (data == nullptr) return 0;
    tuner->detector->push(data, static_cast<int>(n));
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);

    const bool produced = tuner->detector->process();
    const jfloat result[3] = {
        tuner->detector->frequency(),
        tuner->detector->confidence(),
        tuner->detector->rms(),
    };
    env->SetFloatArrayRegion(out, 0, 3, result);

    if (!produced) return 0;
    return tuner->detector->hasPitch() ? 2 : 1;
}

} // extern "C"
