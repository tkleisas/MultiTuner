#include "audio/MetronomeEngine.h"

#include <algorithm>
#include <cmath>

#include <android/log.h>

namespace multituner::audio {

namespace {
constexpr const char* kLogTag = "MultiTunerMetronome";
constexpr double kRequestedSampleRate = 48000.0;
}

MetronomeEngine::~MetronomeEngine() {
    shutdown();
}

bool MetronomeEngine::open() {
    if (mReady.load(std::memory_order_acquire)) return true;
    if (mStream) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(static_cast<int32_t>(kRequestedSampleRate))
        ->setCallback(this);

    const oboe::Result result = builder.openManagedStream(mStream);
    if (result != oboe::Result::OK || !mStream) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "openManagedStream failed: %s",
                            oboe::convertToText(result));
        mStream.reset();
        return false;
    }

    mChannels = mStream->getChannelCount();
    mSampleRate = mStream->getSampleRate();
    mTransport.setSampleRate(static_cast<double>(mSampleRate));
    mTransport.reset();
    mMetronome.init(static_cast<double>(mSampleRate), 512);
    mMetronome.setEnabled(true);
    mMetronome.setBeatsPerBar(mTransport.beatsPerBar());
    mReady.store(true, std::memory_order_release);
    return true;
}

void MetronomeEngine::shutdown() {
    mReady.store(false, std::memory_order_release);
    mTransport.stop();
    if (mStream) {
        mStream->requestStop();
        mStream.reset();
    }
}

void MetronomeEngine::setBpm(double bpm) {
    mTransport.setBPM(std::clamp(bpm, 20.0, 400.0));
}

void MetronomeEngine::setBeatsPerBar(int beats) {
    const int clamped = std::clamp(beats, 1, 16);
    mTransport.setTimeSignature(clamped, 4);
    mMetronome.setBeatsPerBar(clamped);
}

void MetronomeEngine::setSubdivision(int subdivision) {
    mMetronome.setSubdivision(subdivision);
}

void MetronomeEngine::setVolume(float volume) {
    mMetronome.setVolume(volume);
}

void MetronomeEngine::setAccentEnabled(bool enabled) {
    mMetronome.setAccentEnabled(enabled);
}

void MetronomeEngine::setPlaying(bool playing) {
    if (!mReady.load(std::memory_order_acquire) || !mStream) return;

    if (playing) {
        if (mTransport.isPlaying()) return;
        mTransport.setPositionInSamples(0);
        mResetClick.store(true, std::memory_order_release);
        mTransport.play();
        const oboe::Result result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            mTransport.stop();
            mReady.store(false, std::memory_order_release);
            __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                "requestStart failed: %s",
                                oboe::convertToText(result));
        }
    } else {
        mTransport.stop();
        mStream->requestStop();
    }
}

bool MetronomeEngine::isPlaying() const {
    return mTransport.isPlaying();
}

bool MetronomeEngine::isReady() const {
    return mReady.load(std::memory_order_acquire);
}

int MetronomeEngine::currentBeat() const {
    if (!mTransport.isPlaying()) return 0;
    const int beats = std::max(1, mTransport.beatsPerBar());
    int beat = static_cast<int>(std::floor(mTransport.positionInBeats()));
    beat %= beats;
    if (beat < 0) beat += beats;
    return beat;
}

float MetronomeEngine::beatPhase() const {
    if (!mTransport.isPlaying()) return 0.0f;
    const double beats = mTransport.positionInBeats();
    const double fraction = beats - std::floor(beats);
    return static_cast<float>(fraction);
}

int MetronomeEngine::sampleRate() const {
    return mSampleRate;
}

oboe::DataCallbackResult MetronomeEngine::onAudioReady(
    oboe::AudioStream* /*audioStream*/, void* audioData, int32_t numFrames) {
    auto* output = static_cast<float*>(audioData);
    const int sampleCount = numFrames * mChannels;
    std::fill(output, output + sampleCount, 0.0f);

    if (mResetClick.exchange(false, std::memory_order_acq_rel)) {
        mMetronome.reset();
    }

    if (mTransport.isPlaying()) {
        const int64_t position = mTransport.positionInSamples();
        mMetronome.process(
            output,
            numFrames,
            mChannels,
            mTransport.bpm(),
            position,
            true);
        mTransport.advance(numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

void MetronomeEngine::onErrorAfterClose(
    oboe::AudioStream* /*audioStream*/, oboe::Result error) {
    mReady.store(false, std::memory_order_release);
    mTransport.stop();
    __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                        "audio stream closed with error: %s",
                        oboe::convertToText(error));
}

} // namespace multituner::audio
