#include "audio/Reverb.h"

#include <algorithm>
#include <cmath>

namespace multituner::audio {

namespace {
constexpr int kCombTuning[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
constexpr int kAllpassTuning[4] = {556, 441, 341, 225};
constexpr int kStereoSpread = 23;
constexpr float kInputGain = 0.05f;
constexpr float kOutputGain = 0.9f;

int scaledLength(int tuning, double sampleRate) {
    return std::max(1, static_cast<int>(
        std::lround(static_cast<double>(tuning) * sampleRate / 44100.0)));
}
} // namespace

float Reverb::Comb::process(float input, float feedback, float damp1, float damp2) {
    const float output = buffer[static_cast<size_t>(index)];
    store = output * damp2 + store * damp1;
    buffer[static_cast<size_t>(index)] = input + store * feedback;
    index = (index + 1) % static_cast<int>(buffer.size());
    return output;
}

float Reverb::Allpass::process(float input) {
    const float buffered = buffer[static_cast<size_t>(index)];
    buffer[static_cast<size_t>(index)] = input + buffered * 0.5f;
    index = (index + 1) % static_cast<int>(buffer.size());
    return buffered - input;
}

void Reverb::init(double sampleRate) {
    mSampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    for (int i = 0; i < kCombCount; ++i) {
        const int leftLength = scaledLength(kCombTuning[i], mSampleRate);
        const int rightLength = leftLength + kStereoSpread;
        mCombLeft[i].buffer.assign(static_cast<size_t>(leftLength), 0.0f);
        mCombRight[i].buffer.assign(static_cast<size_t>(rightLength), 0.0f);
    }
    for (int i = 0; i < kAllpassCount; ++i) {
        const int leftLength = scaledLength(kAllpassTuning[i], mSampleRate);
        const int rightLength = leftLength + kStereoSpread;
        mAllpassLeft[i].buffer.assign(static_cast<size_t>(leftLength), 0.0f);
        mAllpassRight[i].buffer.assign(static_cast<size_t>(rightLength), 0.0f);
    }
    reset();
}

void Reverb::reset() {
    for (int i = 0; i < kCombCount; ++i) {
        std::fill(mCombLeft[i].buffer.begin(), mCombLeft[i].buffer.end(), 0.0f);
        std::fill(mCombRight[i].buffer.begin(), mCombRight[i].buffer.end(), 0.0f);
        mCombLeft[i].index = 0;
        mCombRight[i].index = 0;
        mCombLeft[i].store = 0.0f;
        mCombRight[i].store = 0.0f;
    }
    for (int i = 0; i < kAllpassCount; ++i) {
        std::fill(mAllpassLeft[i].buffer.begin(), mAllpassLeft[i].buffer.end(), 0.0f);
        std::fill(mAllpassRight[i].buffer.begin(), mAllpassRight[i].buffer.end(), 0.0f);
        mAllpassLeft[i].index = 0;
        mAllpassRight[i].index = 0;
    }
}

void Reverb::setSize(float size) {
    mSize = std::clamp(size, 0.0f, 1.0f);
}

void Reverb::setDamping(float damping) {
    mDamping = std::clamp(damping, 0.0f, 1.0f);
}

void Reverb::process(float* left, float* right, int numFrames) {
    if (numFrames <= 0) return;

    const float feedback = 0.70f + mSize * 0.28f;
    const float damp1 = mDamping * 0.4f;
    const float damp2 = 1.0f - damp1;

    for (int frame = 0; frame < numFrames; ++frame) {
        const float inputL = left[frame] * kInputGain;
        const float inputR = right[frame] * kInputGain;
        float wetL = 0.0f;
        float wetR = 0.0f;

        for (int i = 0; i < kCombCount; ++i) {
            wetL += mCombLeft[i].process(inputL, feedback, damp1, damp2);
            wetR += mCombRight[i].process(inputR, feedback, damp1, damp2);
        }
        for (int i = 0; i < kAllpassCount; ++i) {
            wetL = mAllpassLeft[i].process(wetL);
            wetR = mAllpassRight[i].process(wetR);
        }

        left[frame] = wetL * kOutputGain;
        right[frame] = wetR * kOutputGain;
    }
}

} // namespace multituner::audio
