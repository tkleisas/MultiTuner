#pragma once

#include <vector>

namespace multituner::audio {

class Reverb {
public:
    void init(double sampleRate);
    void reset();
    void setSize(float size);
    void setDamping(float damping);
    void process(float* left, float* right, int numFrames);

private:
    static constexpr int kCombCount = 8;
    static constexpr int kAllpassCount = 4;

    struct Comb {
        std::vector<float> buffer;
        int index = 0;
        float store = 0.0f;

        float process(float input, float feedback, float damp1, float damp2);
    };

    struct Allpass {
        std::vector<float> buffer;
        int index = 0;

        float process(float input);
    };

    Comb mCombLeft[kCombCount];
    Comb mCombRight[kCombCount];
    Allpass mAllpassLeft[kAllpassCount];
    Allpass mAllpassRight[kAllpassCount];

    double mSampleRate = 48000.0;
    float mSize = 0.5f;
    float mDamping = 0.35f;
};

} // namespace multituner::audio
