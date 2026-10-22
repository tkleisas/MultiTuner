#include "audio/DrumSynth.h"

#include <cstring>

namespace multituner::audio {

static constexpr float kDrumPan[8] = {
    0.0f,   // Kick
    -0.05f, // Snare
    -0.40f, // Clap (left to balance hi-hat right)
    -0.25f, // Tom1
    +0.30f, // ClosedHH
    +0.30f, // OpenHH
    +0.25f, // Tom2
    +0.45f, // Tambourine
};

// Hi-hat metallic frequencies (TR-808-ish ratios). Multiplied by
// the per-voice tune ratio so the Tune knob shifts the whole
// metallic spectrum coherently rather than detuning individual
// partials. Scaled inside renderHiHat to per-drum tune range.
static constexpr float kHiHatRatios[6] = {
    1.0f, 1.342f, 1.658f, 1.974f, 2.456f, 2.835f
};

// Tambourine "jingle" frequencies — pairs of high tuned partials
// give the metallic shake character. Same ratio-times-base scheme.
static constexpr float kTambRatios[4] = {
    1.0f, 1.193f, 1.486f, 1.738f
};


namespace {
constexpr float kDefaults[DrumSynth::kParamCount] = {
    0.30f, 1.0f, 350.0f, 0.90f, 0.25f, 0.20f, 0.20f,
    0.50f, 1.0f, 180.0f, 0.30f,
    0.50f, 1.0f, 220.0f, 0.10f,
    0.30f, 1.0f, 500.0f, 0.10f,
    0.50f, 1.0f, 60.0f, 0.10f,
    0.50f, 1.0f, 350.0f, 0.10f,
    0.55f, 1.0f, 400.0f, 0.10f,
    0.60f, 1.0f, 150.0f, 0.10f,
    1.0f,
};
} // namespace

void DrumSynth::init(double sampleRate, int maxBlockSize) {
    m_sampleRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    m_maxBlockSize = maxBlockSize > 0 ? maxBlockSize : 512;
    std::copy(kDefaults, kDefaults + DrumSynth::kParamCount, m_params);
    m_scratchL.assign(static_cast<size_t>(m_maxBlockSize), 0.0f);
    m_scratchR.assign(static_cast<size_t>(m_maxBlockSize), 0.0f);
    m_osScratchL.assign(static_cast<size_t>(m_maxBlockSize) * 2, 0.0f);
    m_osScratchR.assign(static_cast<size_t>(m_maxBlockSize) * 2, 0.0f);
    reset();
}

void DrumSynth::reset() {
    for (auto& voice : m_voices) voice = DrumVoice{};
    m_rngState = 0xC0FFEEu;
    std::memset(m_pinkRows, 0, sizeof(m_pinkRows));
    m_pinkSum = 0.0f;
    m_pinkCounter = 0;
    m_decL.reset();
    m_decR.reset();
}

void DrumSynth::trigger(int slot, float velocity) {
    noteOn(slot, velocity);
}

void DrumSynth::noteOn(int slot, float velocity) {
    if (slot < 0 || slot >= kNumDrums) return;
    auto& voice = m_voices[slot];
    voice.active = true;
    voice.justTriggered = true;
    voice.velocity = std::clamp(velocity, 0.0f, 1.0f);
    voice.ampLevel = 0.0f;
    voice.stage = 0;
    voice.ageSamples = 0;
    voice.phase0 = 0.0;
    for (auto& phase : voice.phases) phase = 0.0;
    voice.bpZ1 = 0.0f;
    voice.bpZ2 = 0.0f;
    if (slot == ClosedHH) m_voices[OpenHH].active = false;
}

float DrumSynth::pinkNoise() {
    // Voss-McCartney pink noise approximation. Cheap, ~3 dB/oct
    // tilt — close enough to pink for the kick-thump tail role
    // we use it for. Each row updates at 1/2^row sample period.
    int idx = ++m_pinkCounter;
    int row = 0;
    while ((idx & 1) == 0 && row < 5) { idx >>= 1; ++row; }
    if (row < 5) {
        m_pinkSum -= m_pinkRows[row];
        m_pinkRows[row] = randFloat() - 0.5f;
        m_pinkSum += m_pinkRows[row];
    }
    return m_pinkSum * 0.5f;
}

void DrumSynth::renderKick(DrumVoice& v, float* outL, float* outR, int n) {
    const float baseHz   = tuneToHz(m_params[pKickTune], 30.0f, 120.0f);
    const float atkMs    = m_params[pKickAttack];
    const float decMs    = m_params[pKickDecay];
    const float sineAmt  = m_params[pKickSine];
    const float whiteAmt = m_params[pKickWhite];
    const float pinkAmt  = m_params[pKickPink];
    const float drv      = m_params[pKickDrive];

    const float sr       = static_cast<float>(m_sampleRate);
    const float atkInc   = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef  = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));
    // Pitch sweep: drops from baseHz × ~3 down to baseHz over ~30 ms.
    // Gives the kick its "thump" — the click-to-tone transition is
    // basically the whole kick character. Kept independent of the
    // amp decay so even very long kicks have a tight pitch attack.
    const float pitchTau = std::max(0.001f, 0.030f * sr);
    const float pitchCoef = std::exp(-1.0f / pitchTau);

    const float pL = panL(kDrumPan[Kick]);
    const float pR = panR(kDrumPan[Kick]);

    for (int i = 0; i < n; ++i) {
        // Envelope
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        // Pitch sweep — pitchEnv is reused via phase0's own role; we
        // store the sweep multiplier in v.phases[0] so we don't need
        // an extra DrumVoice field. White-noise click decays much
        // faster than the body — the first ~5 ms gives the kick its
        // initial transient (phases[1]).
        if (v.justTriggered) {
            v.phases[0] = 1.0f;
            v.phases[1] = 1.0f;
            v.justTriggered = false;
        } else {
            v.phases[0] *= pitchCoef;
            v.phases[1] *= 0.9985f;
        }
        const float pitchMul = 1.0f + 2.0f * static_cast<float>(v.phases[0]);
        const double freq = baseHz * pitchMul;
        v.phase0 += freq / m_sampleRate;
        if (v.phase0 >= 1.0) v.phase0 -= 1.0;

        const float sine = std::sin(static_cast<float>(v.phase0) * 6.2831853f);
        const float white = (randFloat() - 0.5f) * 2.0f
                           * static_cast<float>(v.phases[1]);
        const float pink  = pinkNoise() * 2.0f;

        float out = sineAmt  * sine
                  + whiteAmt * white
                  + pinkAmt  * pink * v.ampLevel;   // pink rides amp env
        out *= v.ampLevel * v.velocity;
        out = drive(out, drv);

        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::renderSnare(DrumVoice& v, float* outL, float* outR, int n) {
    const float baseHz = tuneToHz(m_params[pSnareTune], 120.0f, 350.0f);
    const float atkMs  = m_params[pSnareAttack];
    const float decMs  = m_params[pSnareDecay];
    const float drv    = m_params[pSnareDrive];

    const float sr       = static_cast<float>(m_sampleRate);
    const float atkInc   = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef  = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));

    // Bandpass biquad for the noise tail, centred around baseHz × 4
    // (the snare wires resonate ~3–8× the body fundamental). Direct-
    // Form-II Transposed — z1/z2 stored on the voice so the filter
    // memory survives across blocks.
    const float bpHz = std::min(baseHz * 4.0f, sr * 0.45f);
    const float w0   = 2.0f * 3.14159265f * bpHz / sr;
    const float sinW = std::sin(w0), cosW = std::cos(w0);
    const float alpha = sinW / (2.0f * 1.5f);   // Q ≈ 1.5
    const float a0 = 1.0f + alpha;
    const float b0 = (sinW * 0.5f) / a0;
    // BPF: b1 = 0, b2 = -b0
    const float a1 = (-2.0f * cosW) / a0;
    const float a2 = (1.0f - alpha) / a0;

    const float pL = panL(kDrumPan[Snare]);
    const float pR = panR(kDrumPan[Snare]);

    for (int i = 0; i < n; ++i) {
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        // Body: triangle at baseHz with fast pitch decay (~25 ms)
        if (v.justTriggered) {
            v.phases[0] = 1.0f;
            v.justTriggered = false;
        } else {
            v.phases[0] *= 0.9982f;
        }
        const double freq = baseHz * (1.0f + static_cast<float>(v.phases[0]) * 0.5f);
        v.phase0 += freq / m_sampleRate;
        if (v.phase0 >= 1.0) v.phase0 -= 1.0;
        const float t = static_cast<float>(v.phase0);
        const float body = (t < 0.5f ? 4.0f * t - 1.0f : 3.0f - 4.0f * t);

        // Noise through bandpass — DF-II Transposed:
        //   y[n] = b0*x[n] + z1[n-1]
        //   z1   = b1*x[n] - a1*y + z2[n-1]   (b1=0 here)
        //   z2   = b2*x[n] - a2*y             (b2 = -b0)
        const float x = (randFloat() - 0.5f) * 2.0f;
        const float y = b0 * x + v.bpZ1;
        v.bpZ1 = -a1 * y + v.bpZ2;
        v.bpZ2 = -b0 * x - a2 * y;

        float out = 0.35f * body * static_cast<float>(v.phases[0]) + 0.65f * y;
        out *= v.ampLevel * v.velocity;
        out = drive(out, drv);

        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::renderClap(DrumVoice& v, float* outL, float* outR, int n) {
    const float baseHz = tuneToHz(m_params[pClapTune], 800.0f, 2400.0f);
    const float atkMs  = m_params[pClapAttack];
    const float decMs  = m_params[pClapDecay];
    const float drv    = m_params[pClapDrive];

    const float sr      = static_cast<float>(m_sampleRate);
    const float atkInc  = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));

    // Three quick bursts ~10 ms apart, then a longer noise tail.
    // burstSamples lists trigger-relative sample positions where
    // the burst-envelope re-arms back to ~1.0; between bursts the
    // burst envelope decays at ~3 ms time-constant. The result is
    // a textured "kha-kha-kha-shhh" classic clap shape.
    const float burstTau = std::max(0.001f, 0.003f * sr);
    const float burstCoef = std::exp(-1.0f / burstTau);
    const int burstSamples[3] = {
        static_cast<int>(0.010f * sr),
        static_cast<int>(0.020f * sr),
        static_cast<int>(0.030f * sr),
    };

    const float pL = panL(kDrumPan[Clap]);
    const float pR = panR(kDrumPan[Clap]);

    for (int i = 0; i < n; ++i) {
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        // Burst envelope — phases[0] is the burst level. The clap's
        // signature "kha-kha-kha-shhh" texture comes from re-arming
        // this envelope to 1.0 at three preset sample offsets after
        // trigger, then letting it decay between bursts. After the
        // last burst, the noise-tail floor of 0.55 keeps the body
        // audible until the amp envelope fades out.
        if (v.justTriggered) {
            v.phases[0] = 1.0f;     // first burst is at age=0
            v.justTriggered = false;
        }
        for (int b = 0; b < 3; ++b)
            if (v.ageSamples == burstSamples[b]) v.phases[0] = 1.0f;
        v.phases[0] *= burstCoef;
        const float burstAmp = std::max(static_cast<float>(v.phases[0]), 0.55f);

        // Noise centred via simple two-pole BP approximation — same
        // simplified DF-I as the snare, accuracy not critical.
        const float n_ = (randFloat() - 0.5f) * 2.0f;
        v.bpZ1 = 0.92f * v.bpZ1 + 0.08f * n_;     // soft lowpass
        const float shaped = (n_ - v.bpZ1) * 0.7f;  // highpass component

        // Centre frequency tilt — multiply by sine of base wraparound
        // to shift the noise spectrum up; modest effect.
        v.phase0 += baseHz / m_sampleRate;
        if (v.phase0 >= 1.0) v.phase0 -= 1.0;
        const float tilt = std::sin(static_cast<float>(v.phase0) * 6.2831853f) * 0.3f + 0.7f;

        float out = shaped * tilt * burstAmp * v.ampLevel * v.velocity;
        out = drive(out, drv);
        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::renderTom(DrumVoice& v, float* outL, float* outR, int n,
                           int slot) {
    const int pBase = (slot == Tom1) ? pTom1Tune : pTom2Tune;
    // Tom1 sits low, Tom2 high — the tune ranges differ accordingly.
    const float lo = (slot == Tom1) ? 60.0f  : 110.0f;
    const float hi = (slot == Tom1) ? 220.0f : 380.0f;
    const float baseHz = tuneToHz(m_params[pBase + 0], lo, hi);
    const float atkMs  = m_params[pBase + 1];
    const float decMs  = m_params[pBase + 2];
    const float drv    = m_params[pBase + 3];

    const float sr      = static_cast<float>(m_sampleRate);
    const float atkInc  = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));
    const float pitchCoef = std::exp(-1.0f / std::max(0.001f, 0.060f * sr));

    const float pL = panL(kDrumPan[slot]);
    const float pR = panR(kDrumPan[slot]);

    for (int i = 0; i < n; ++i) {
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        if (v.justTriggered) {
            v.phases[0] = 1.0f;
            v.justTriggered = false;
        } else {
            v.phases[0] *= pitchCoef;
        }
        const double freq = baseHz * (1.0f + static_cast<float>(v.phases[0]) * 0.4f);
        v.phase0 += freq / m_sampleRate;
        if (v.phase0 >= 1.0) v.phase0 -= 1.0;

        const float sine = std::sin(static_cast<float>(v.phase0) * 6.2831853f);
        // Subtle noise body so it doesn't sound like a pure sine.
        const float thump = (randFloat() - 0.5f) * 0.10f;
        float out = (sine + thump) * v.ampLevel * v.velocity;
        out = drive(out, drv);
        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::renderHiHat(DrumVoice& v, float* outL, float* outR, int n,
                             bool open, int slot) {
    const int pBase = open ? pOHHTune : pCHHTune;
    const float baseHz = tuneToHz(m_params[pBase + 0], 6000.0f, 14000.0f);
    const float atkMs  = m_params[pBase + 1];
    const float decMs  = m_params[pBase + 2];
    const float drv    = m_params[pBase + 3];

    const float sr      = static_cast<float>(m_sampleRate);
    const float atkInc  = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));

    const float pL = panL(kDrumPan[slot]);
    const float pR = panR(kDrumPan[slot]);

    // Pre-compute per-partial increments. baseHz is the lowest
    // partial; the others are scaled up by kHiHatRatios. Keeps the
    // metallic spectrum coherent under the Tune knob.
    double inc[6];
    for (int p = 0; p < 6; ++p)
        inc[p] = baseHz * kHiHatRatios[p] / m_sampleRate;

    // Hi-hat doesn't use phases[0] as an envelope (oscillator phases
    // start at 0 and don't need first-sample initialisation), but we
    // still clear justTriggered for consistency — tests assert it's
    // false once any render has run.
    v.justTriggered = false;

    for (int i = 0; i < n; ++i) {
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        // Sum 6 squares — produces an inharmonic metallic spectrum.
        // Square-wave sums create the classic 808 hi-hat colour.
        float sum = 0.0f;
        for (int p = 0; p < 6; ++p) {
            v.phases[p] += inc[p];
            if (v.phases[p] >= 1.0) v.phases[p] -= 1.0;
            sum += (v.phases[p] < 0.5) ? 1.0f : -1.0f;
        }
        sum *= 0.16f;   // 1/6 with some headroom

        // High-pass via leaky-integrator subtraction (cheap one-pole HP).
        // bpZ1 holds running average; output is signal minus average.
        v.bpZ1 = 0.985f * v.bpZ1 + 0.015f * sum;
        float hp = sum - v.bpZ1;

        // Add a touch of bandpassed noise to break up the periodicity.
        const float n_ = (randFloat() - 0.5f) * 0.3f;
        float out = (hp + n_) * v.ampLevel * v.velocity;
        out = drive(out, drv);
        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::renderTambourine(DrumVoice& v, float* outL, float* outR, int n) {
    const float baseHz = tuneToHz(m_params[pTambTune], 4000.0f, 10000.0f);
    const float atkMs  = m_params[pTambAttack];
    const float decMs  = m_params[pTambDecay];
    const float drv    = m_params[pTambDrive];

    const float sr      = static_cast<float>(m_sampleRate);
    const float atkInc  = 1.0f / std::max(1.0f, atkMs * 0.001f * sr);
    const float decCoef = std::exp(-1.0f / std::max(0.001f, decMs * 0.001f * sr));

    const float pL = panL(kDrumPan[Tambourine]);
    const float pR = panR(kDrumPan[Tambourine]);

    double inc[4];
    for (int p = 0; p < 4; ++p)
        inc[p] = baseHz * kTambRatios[p] / m_sampleRate;

    // Tambourine, like the hi-hat, only uses phases[] as oscillator
    // phases starting at 0 — clear the justTriggered flag for
    // consistency with the rest of the renderers.
    v.justTriggered = false;

    for (int i = 0; i < n; ++i) {
        if (v.stage == 0) {
            v.ampLevel += atkInc;
            if (v.ampLevel >= 1.0f) { v.ampLevel = 1.0f; v.stage = 1; }
        } else {
            v.ampLevel *= decCoef;
            if (v.ampLevel < 0.0001f) { v.active = false; return; }
        }
        // 4 sines (cleaner than squares for the jingle character).
        float sum = 0.0f;
        for (int p = 0; p < 4; ++p) {
            v.phases[p] += inc[p];
            if (v.phases[p] >= 1.0) v.phases[p] -= 1.0;
            sum += std::sin(static_cast<float>(v.phases[p]) * 6.2831853f);
        }
        sum *= 0.20f;

        // Add bright noise — the tambourine's beads.
        const float n_ = (randFloat() - 0.5f) * 1.2f;
        float out = (sum * 0.4f + n_ * 0.6f) * v.ampLevel * v.velocity;
        out = drive(out, drv);
        outL[i] += out * pL;
        outR[i] += out * pR;
        ++v.ageSamples;
    }
}

void DrumSynth::render(float* outL, float* outR, int numFrames) {
    if (numFrames <= 0 || numFrames > m_maxBlockSize) return;

    if (m_params[pOversample] > 0.5f) {
        const int osN = numFrames * 2;
        std::memset(m_osScratchL.data(), 0,
                    static_cast<size_t>(osN) * sizeof(float));
        std::memset(m_osScratchR.data(), 0,
                    static_cast<size_t>(osN) * sizeof(float));

        const double baseSr = m_sampleRate;
        m_sampleRate = baseSr * 2.0;
        renderActiveVoices(m_osScratchL.data(), m_osScratchR.data(), osN);
        m_sampleRate = baseSr;

        for (int i = 0; i < numFrames; ++i) {
            outL[i] += m_decL.process(m_osScratchL[2 * i], m_osScratchL[2 * i + 1]);
            outR[i] += m_decR.process(m_osScratchR[2 * i], m_osScratchR[2 * i + 1]);
        }
        return;
    }

    std::memset(m_scratchL.data(), 0,
                static_cast<size_t>(numFrames) * sizeof(float));
    std::memset(m_scratchR.data(), 0,
                static_cast<size_t>(numFrames) * sizeof(float));
    renderActiveVoices(m_scratchL.data(), m_scratchR.data(), numFrames);
    for (int i = 0; i < numFrames; ++i) {
        outL[i] += m_scratchL[i];
        outR[i] += m_scratchR[i];
    }
}

void DrumSynth::renderActiveVoices(float* outL, float* outR, int n) {
    for (int slot = 0; slot < kNumDrums; ++slot) {
        auto& v = m_voices[slot];
        if (!v.active) continue;
        switch (slot) {
            case Kick:       renderKick(v, outL, outR, n); break;
            case Snare:      renderSnare(v, outL, outR, n); break;
            case Clap:       renderClap(v, outL, outR, n); break;
            case Tom1:
            case Tom2:       renderTom(v, outL, outR, n, slot); break;
            case ClosedHH:   renderHiHat(v, outL, outR, n, false, slot); break;
            case OpenHH:     renderHiHat(v, outL, outR, n, true,  slot); break;
            case Tambourine: renderTambourine(v, outL, outR, n); break;
        }
    }
}

} // namespace multituner::audio
