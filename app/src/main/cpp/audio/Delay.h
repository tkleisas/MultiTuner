#pragma once

#include <vector>

namespace multituner::audio {

class Delay {
public:
    void init(double sampleRate);
    void reset();
    void setTimeMs(float timeMs);
    void setFeedback(float feedback);
    void setTone(float tone);
    void process(float* left, float* right, int numFrames);

private:
    float readInterpolated(const std::vector<float>& buffer, float delaySamples) const;

    std::vector<float> mBufferLeft;
    std::vector<float> mBufferRight;
    int mWriteIndex = 0;
    double mSampleRate = 48000.0;
    float mTargetDelaySamples = 0.0f;
    float mCurrentDelaySamples = 0.0f;
    float mFeedback = 0.35f;
    float mTone = 0.7f;
    float mToneCoefficient = 0.0f;
    float mToneGain = 1.0f;
    float mFilterLeft = 0.0f;
    float mFilterRight = 0.0f;
};

} // namespace multituner::audio
