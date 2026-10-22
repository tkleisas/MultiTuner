#include "audio/MetronomeEngine.h"

#include <algorithm>
#include <cmath>

#include <android/log.h>

namespace multituner::audio {

namespace {
constexpr const char* kLogTag = "MultiTunerPlayback";
constexpr double kRequestedSampleRate = 48000.0;
constexpr int kMaxDrumBlock = 8192;
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
    mDrums.init(static_cast<double>(mSampleRate), kMaxDrumBlock);
    mDrumL.assign(static_cast<size_t>(kMaxDrumBlock), 0.0f);
    mDrumR.assign(static_cast<size_t>(kMaxDrumBlock), 0.0f);
    mMetronomeBuffer.assign(static_cast<size_t>(kMaxDrumBlock) * 2, 0.0f);
    mCassette.init(static_cast<double>(mSampleRate));
    mCassetteBuffer.assign(static_cast<size_t>(kMaxDrumBlock) * 2, 0.0f);
    mReady.store(true, std::memory_order_release);
    return true;
}

void MetronomeEngine::shutdown() {
    mReady.store(false, std::memory_order_release);
    mMetronomePlaying.store(false, std::memory_order_release);
    mDrumsPlaying.store(false, std::memory_order_release);
    mCassettePlaying.store(false, std::memory_order_release);
    mClickRecorder.stop();
    mDrumRecorder.stop();
    mBusRecorder.stop();
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

void MetronomeEngine::setMetronomePlaying(bool playing) {
    if (!mReady.load(std::memory_order_acquire) || !mStream) return;
    const bool wasPlaying = mMetronomePlaying.exchange(
        playing, std::memory_order_acq_rel);
    if (playing && !wasPlaying) {
        mResetClick.store(true, std::memory_order_release);
    }
    syncTransport();
}

void MetronomeEngine::setDrumsPlaying(bool playing) {
    if (!mReady.load(std::memory_order_acquire) || !mStream) return;
    mDrumsPlaying.store(playing, std::memory_order_release);
    syncTransport();
}

void MetronomeEngine::setCassettePlaying(bool playing) {
    if (!mReady.load(std::memory_order_acquire) || !mStream) return;
    mCassettePlaying.store(playing, std::memory_order_release);
    syncTransport();
}

void MetronomeEngine::syncTransport() {
    const bool anyPlaying = mMetronomePlaying.load(std::memory_order_acquire) ||
                            mDrumsPlaying.load(std::memory_order_acquire) ||
                            mCassettePlaying.load(std::memory_order_acquire);

    if (anyPlaying) {
        if (!mTransport.isPlaying()) {
            mTransport.setPositionInSamples(0);
            mTransport.play();
            const oboe::Result result = mStream->requestStart();
            if (result != oboe::Result::OK) {
                mMetronomePlaying.store(false, std::memory_order_release);
                mDrumsPlaying.store(false, std::memory_order_release);
                mCassettePlaying.store(false, std::memory_order_release);
                mTransport.stop();
                mReady.store(false, std::memory_order_release);
                __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                    "requestStart failed: %s",
                                    oboe::convertToText(result));
            }
        }
    } else if (mTransport.isPlaying()) {
        mTransport.stop();
        mStream->requestStop();
    }
}

bool MetronomeEngine::isMetronomePlaying() const {
    return mMetronomePlaying.load(std::memory_order_acquire);
}

bool MetronomeEngine::isDrumsPlaying() const {
    return mDrumsPlaying.load(std::memory_order_acquire);
}

bool MetronomeEngine::isCassettePlaying() const {
    return mCassettePlaying.load(std::memory_order_acquire);
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
    double effectiveBpm = mTransport.bpm();
    if (mCassettePlaying.load(std::memory_order_acquire)) {
        effectiveBpm *= mVarispeed.load(std::memory_order_acquire);
    }
    const double beatsPosition =
        (static_cast<double>(mTransport.positionInSamples()) /
         static_cast<double>(mSampleRate)) * (effectiveBpm / 60.0);
    int beat = static_cast<int>(std::floor(beatsPosition));
    beat %= beats;
    if (beat < 0) beat += beats;
    return beat;
}

float MetronomeEngine::beatPhase() const {
    if (!mTransport.isPlaying()) return 0.0f;
    double effectiveBpm = mTransport.bpm();
    if (mCassettePlaying.load(std::memory_order_acquire)) {
        effectiveBpm *= mVarispeed.load(std::memory_order_acquire);
    }
    const double beatsPosition =
        (static_cast<double>(mTransport.positionInSamples()) /
         static_cast<double>(mSampleRate)) * (effectiveBpm / 60.0);
    const double fraction = beatsPosition - std::floor(beatsPosition);
    return static_cast<float>(fraction);
}

int MetronomeEngine::currentDrumStep() const {
    if (!mTransport.isPlaying()) return 0;
    double effectiveBpm = mTransport.bpm();
    if (mCassettePlaying.load(std::memory_order_acquire)) {
        effectiveBpm *= mVarispeed.load(std::memory_order_acquire);
    }
    return mDrums.currentStep(effectiveBpm, mTransport.positionInSamples());
}

int MetronomeEngine::sampleRate() const {
    return mSampleRate;
}

int64_t MetronomeEngine::positionSamples() const {
    return mTransport.positionInSamples();
}

void MetronomeEngine::setPositionSamples(int64_t position) {
    mTransport.setPositionInSamples(std::max<int64_t>(0, position));
}

void MetronomeEngine::setVarispeed(float varispeed) {
    mVarispeed.store(std::clamp(varispeed, 0.5f, 2.0f),
                     std::memory_order_release);
}

float MetronomeEngine::varispeed() const {
    return mVarispeed.load(std::memory_order_acquire);
}

void MetronomeEngine::setFlip(bool flipped) {
    mFlip.store(flipped, std::memory_order_release);
    mCassette.setFlip(flipped);
}

bool MetronomeEngine::isFlipped() const {
    return mFlip.load(std::memory_order_acquire);
}

bool MetronomeEngine::loadCassetteTrack(int index, const std::string& path,
                                        int64_t startSample) {
    return mCassette.loadTrack(index, path, startSample);
}

void MetronomeEngine::clearCassetteTracks() {
    mCassette.clearTracks();
}

void MetronomeEngine::setDeckGain(int deck, float gain) {
    mCassette.setDeckGain(deck, gain);
}

void MetronomeEngine::setDeckPan(int deck, float pan) {
    mCassette.setDeckPan(deck, pan);
}

void MetronomeEngine::setDeckMute(int deck, bool muted) {
    mCassette.setDeckMute(deck, muted);
}

void MetronomeEngine::setDeckSolo(int deck, bool soloed) {
    mCassette.setDeckSolo(deck, soloed);
}

void MetronomeEngine::setDeckReverbSend(int deck, float send) {
    mCassette.setDeckReverbSend(deck, send);
}

void MetronomeEngine::setDeckDelaySend(int deck, float send) {
    mCassette.setDeckDelaySend(deck, send);
}

void MetronomeEngine::setDeckBusSend(int deck, float send) {
    mCassette.setDeckBusSend(deck, send);
}

void MetronomeEngine::setCassetteVolume(float volume) {
    mCassette.setVolume(volume);
}

void MetronomeEngine::setTapeEnabled(bool enabled) {
    mCassette.setTapeEnabled(enabled);
}

void MetronomeEngine::setTapeParameter(int index, float value) {
    mCassette.setTapeParameter(index, value);
}

void MetronomeEngine::setReverbReturn(float value) {
    mCassette.setReverbReturn(value);
}

void MetronomeEngine::setReverbSize(float value) {
    mCassette.setReverbSize(value);
}

void MetronomeEngine::setReverbDamping(float value) {
    mCassette.setReverbDamping(value);
}

void MetronomeEngine::setDelayReturn(float value) {
    mCassette.setDelayReturn(value);
}

void MetronomeEngine::setDelayTimeMs(float value) {
    mCassette.setDelayTimeMs(value);
}

void MetronomeEngine::setDelayFeedback(float value) {
    mCassette.setDelayFeedback(value);
}

void MetronomeEngine::setDelayTone(float value) {
    mCassette.setDelayTone(value);
}

float MetronomeEngine::cassettePeakLevel() const {
    return mCassette.peakLevel();
}

void MetronomeEngine::setDrumStep(int track, int step, int level) {
    mDrums.setStep(track, step, level);
}

void MetronomeEngine::setDrumPattern(const uint8_t* levels) {
    mDrums.setPattern(levels);
}

void MetronomeEngine::setDrumSwing(float swing) {
    mDrums.setSwing(swing);
}

void MetronomeEngine::setDrumVolume(float volume) {
    mDrums.setVolume(volume);
}

bool MetronomeEngine::startInternalRecording(int source,
                                             const std::string& path) {
    if (!mReady.load(std::memory_order_acquire)) return false;
    InternalRecorder* recorder = nullptr;
    switch (source) {
        case 1: recorder = &mClickRecorder; break;
        case 2: recorder = &mDrumRecorder; break;
        case 3: recorder = &mBusRecorder; break;
        default: return false;
    }
    return recorder->start(path, static_cast<double>(mSampleRate));
}

void MetronomeEngine::stopInternalRecording(int source) {
    switch (source) {
        case 1: mClickRecorder.stop(); break;
        case 2: mDrumRecorder.stop(); break;
        case 3: mBusRecorder.stop(); break;
        default: break;
    }
}

bool MetronomeEngine::isInternalRecording(int source) const {
    switch (source) {
        case 1: return mClickRecorder.isRecording();
        case 2: return mDrumRecorder.isRecording();
        case 3: return mBusRecorder.isRecording();
        default: return false;
    }
}

float MetronomeEngine::internalPeakLevel(int source) const {
    switch (source) {
        case 1: return mClickRecorder.peakLevel();
        case 2: return mDrumRecorder.peakLevel();
        case 3: return mBusRecorder.peakLevel();
        default: return 0.0f;
    }
}

bool MetronomeEngine::internalRecordingFailed(int source) const {
    switch (source) {
        case 1: return mClickRecorder.failed();
        case 2: return mDrumRecorder.failed();
        case 3: return mBusRecorder.failed();
        default: return false;
    }
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
        const bool cassettePlaying =
            mCassettePlaying.load(std::memory_order_acquire);
        const bool flipped = mFlip.load(std::memory_order_acquire);
        const float varispeed = mVarispeed.load(std::memory_order_acquire);
        const double effectiveBpm = mTransport.bpm() *
            (cassettePlaying ? static_cast<double>(varispeed) : 1.0);

        if (cassettePlaying && numFrames <= kMaxDrumBlock) {
            std::fill(mCassetteBuffer.begin(),
                      mCassetteBuffer.begin() + numFrames * 2, 0.0f);
            mCassette.process(
                mCassetteBuffer.data(),
                numFrames,
                position,
                varispeed);
            for (int i = 0; i < numFrames; ++i) {
                output[i * mChannels] += mCassetteBuffer[i * 2];
                if (mChannels > 1) {
                    output[i * mChannels + 1] += mCassetteBuffer[i * 2 + 1];
                }
            }
            if (mBusRecorder.isRecording()) {
                mBusRecorder.push(mCassette.busLeft(), mCassette.busRight(),
                                  numFrames);
            }
        }

        if (!(cassettePlaying && flipped)) {
            const bool metronomePlaying =
                mMetronomePlaying.load(std::memory_order_acquire);
            const bool clickRecording = mClickRecorder.isRecording();
            if ((metronomePlaying || clickRecording) &&
                numFrames <= kMaxDrumBlock) {
                std::fill(mMetronomeBuffer.begin(),
                          mMetronomeBuffer.begin() + numFrames * 2, 0.0f);
                if (metronomePlaying) {
                    mMetronome.process(
                        mMetronomeBuffer.data(),
                        numFrames,
                        2,
                        effectiveBpm,
                        position,
                        true);
                    for (int i = 0; i < numFrames; ++i) {
                        output[i * mChannels] += mMetronomeBuffer[i * 2];
                        if (mChannels > 1) {
                            output[i * mChannels + 1] +=
                                mMetronomeBuffer[i * 2 + 1];
                        }
                    }
                }
                if (clickRecording) {
                    mClickRecorder.pushInterleaved(
                        mMetronomeBuffer.data(), numFrames);
                }
            }

            const bool drumsPlaying =
                mDrumsPlaying.load(std::memory_order_acquire);
            const bool drumRecording = mDrumRecorder.isRecording();
            if ((drumsPlaying || drumRecording) &&
                numFrames <= kMaxDrumBlock) {
                std::fill(mDrumL.begin(), mDrumL.begin() + numFrames, 0.0f);
                std::fill(mDrumR.begin(), mDrumR.begin() + numFrames, 0.0f);
                mDrums.process(
                    mDrumL.data(),
                    mDrumR.data(),
                    numFrames,
                    effectiveBpm,
                    position,
                    drumsPlaying);
                if (drumsPlaying) {
                    for (int i = 0; i < numFrames; ++i) {
                        output[i * mChannels] += mDrumL[i];
                        if (mChannels > 1) {
                            output[i * mChannels + 1] += mDrumR[i];
                        }
                    }
                }
                if (drumRecording) {
                    mDrumRecorder.push(mDrumL.data(), mDrumR.data(),
                                       numFrames);
                }
            }
        }

        mTransport.advance(numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

void MetronomeEngine::onErrorAfterClose(
    oboe::AudioStream* /*audioStream*/, oboe::Result error) {
    mReady.store(false, std::memory_order_release);
    mMetronomePlaying.store(false, std::memory_order_release);
    mDrumsPlaying.store(false, std::memory_order_release);
    mCassettePlaying.store(false, std::memory_order_release);
    mClickRecorder.stop();
    mDrumRecorder.stop();
    mBusRecorder.stop();
    mTransport.stop();
    __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                        "audio stream closed with error: %s",
                        oboe::convertToText(error));
}

} // namespace multituner::audio
