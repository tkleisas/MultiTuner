#include "audio/Delay.h"

#include <algorithm>
#include <cmath>

namespace multituner::audio {

namespace {
constexpr float kMinDelayMs = 40.0f;
constexpr float kMaxDelayMs = 1200.0f;
constexpr float kMaxDelaySeconds = 2.0f;
} // namespace

void Delay::init(double sampleRate) {
    mSampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    const size_t size = static_cast<size_t>(
        std::lround(kMaxDelaySeconds * mSampleRate)) + 2;
    mBufferLeft.assign(size, 0.0f);
    mBufferRight.assign(size, 0.0f);
    setTimeMs(350.0f);
    mCurrentDelaySamples = mTargetDelaySamples;
    reset();
}

void Delay::reset() {
    std::fill(mBufferLeft.begin(), mBufferLeft.end(), 0.0f);
    std::fill(mBufferRight.begin(), mBufferRight.end(), 0.0f);
    mWriteIndex = 0;
    mCurrentDelaySamples = mTargetDelaySamples;
    mFilterLeft = 0.0f;
    mFilterRight = 0.0f;
}

void Delay::setTimeMs(float timeMs) {
    const float clamped = std::clamp(timeMs, kMinDelayMs, kMaxDelayMs);
    mTargetDelaySamples = static_cast<float>(
        static_cast<double>(clamped) * mSampleRate / 1000.0);
    if (mCurrentDelaySamples <= 0.0f) mCurrentDelaySamples = mTargetDelaySamples;
}

void Delay::setFeedback(float feedback) {
    mFeedback = std::clamp(feedback, 0.0f, 0.9f);
}

void Delay::setTone(float tone) {
    mTone = std::clamp(tone, 0.0f, 1.0f);
    const double cutoff = 500.0 * std::pow(2.0, static_cast<double>(mTone) * 4.6);
    mToneCoefficient = static_cast<float>(
        std::exp(-2.0 * 3.14159265358979323846 * cutoff / mSampleRate));
    mToneGain = 1.0f - mToneCoefficient;
}

float Delay::readInterpolated(const std::vector<float>& buffer,
                              float delaySamples) const {
    const int size = static_cast<int>(buffer.size());
    float readPosition = static_cast<float>(mWriteIndex) - delaySamples;
    while (readPosition < 0.0f) readPosition += static_cast<float>(size);
    while (readPosition >= static_cast<float>(size)) {
        readPosition -= static_cast<float>(size);
    }
    const int index0 = static_cast<int>(readPosition);
    const int index1 = (index0 + 1) % size;
    const float fraction = readPosition - static_cast<float>(index0);
    return buffer[static_cast<size_t>(index0)] * (1.0f - fraction) +
           buffer[static_cast<size_t>(index1)] * fraction;
}

void Delay::process(float* left, float* right, int numFrames) {
    if (numFrames <= 0) return;

    const float maxDelay = static_cast<float>(mBufferLeft.size() - 2);
    for (int frame = 0; frame < numFrames; ++frame) {
        mCurrentDelaySamples +=
            (mTargetDelaySamples - mCurrentDelaySamples) * 0.0015f;
        const float delaySamples = std::clamp(
            mCurrentDelaySamples, 1.0f, maxDelay);

        const float delayedLeft = readInterpolated(mBufferLeft, delaySamples);
        const float delayedRight = readInterpolated(mBufferRight, delaySamples);

        mFilterLeft = delayedLeft * mToneGain + mFilterLeft * mToneCoefficient;
        mFilterRight = delayedRight * mToneGain + mFilterRight * mToneCoefficient;

        mBufferLeft[static_cast<size_t>(mWriteIndex)] =
            left[frame] + mFilterLeft * mFeedback;
        mBufferRight[static_cast<size_t>(mWriteIndex)] =
            right[frame] + mFilterRight * mFeedback;
        mWriteIndex = (mWriteIndex + 1) % static_cast<int>(mBufferLeft.size());

        left[frame] = delayedLeft;
        right[frame] = delayedRight;
    }
}

} // namespace multituner::audio
