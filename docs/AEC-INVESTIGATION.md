# Acoustic Echo Cancellation Investigation

Status: research notes, no production AEC yet.

## Goal

Record a live instrument on the phone while backing audio plays through
external speakers, without capturing the backing audio in the microphone
signal.

The intended tape workflow stays destructive: the microphone take is the source
of truth unless bleed cancellation is explicitly enabled.

## Why phase inversion is not enough

The microphone hears the backing audio after:

- digital-to-analog conversion
- speaker response
- room reflections and reverberation
- microphone response
- a variable acoustic delay
- clock drift between playback and capture

A simple polarity flip only cancels a time-aligned, gain-matched, otherwise
identical copy. The acoustic copy is none of those things. Real cancellation
requires an adaptive echo canceller with a far-end reference.

## Test setup

For the initial investigation, use the laptop as the playback source and the
phone only as the recorder:

- Laptop speakers play a known reference WAV.
- The phone runs MultiTuner with all app playback and guides off.
- One empty cassette MIC track records the room, starting at 0:00.
- The laptop reference WAV should be 48 kHz mono; resample it if necessary.
- The reference WAV is the far-end signal for offline evaluation.

This avoids building a native reference tap before we know which cancellation
method is worth integrating. The phone is not playing any audio in this setup.

### Suggested captures

Record three short takes, around 15-30 seconds each, without changing the
phone/laptop positions or volume:

1. **Far-end only** — laptop backing only, no instrument.
2. **Near-end only** — instrument only, laptop silent.
3. **Double talk** — laptop backing plus live instrument.

Start the laptop reference with a short click or impulse so the two recordings
can be aligned. Start the phone recording just before the laptop playback.

Pull the recorded cassette take:

```bash
adb pull /sdcard/Android/data/com.tkleisas.multituner/files/cassettes/<cassette-id>/track-<n>.wav
```

The phone recording is a full 10:00 mono 48 kHz WAV. Trim it to the captured
section before analysis.

## Candidate review

| Candidate | License | Rate | Runtime | Notes |
|---|---|---:|---|---|
| `a2heng/lightweight-aec-48k` | MIT | 48 kHz | ONNX | 4.95 MB streaming model, 480-sample frames, far-end reference, speech-trained |
| `breizhn/DTLN-aec` | MIT | 16 kHz | TFLite | 1.8M/3.9M/10.4M parameter speech models, 16 kHz only |
| `localai-org/LocalVQE` | Apache-2.0 | 16 kHz | GGML/C++ | 203K parameter echo-only variant, 16 kHz only |
| `fjiang9/NKF-AEC` | unclear | 16 kHz | Python | Neural Kalman filter, requires delay compensation |
| WebRTC AEC3 | BSD | 48 kHz | C++ | Mature adaptive AEC, voice-focused, no maintained Android artifact |
| SpeexDSP AEC | BSD | configurable | C | Small/legacy, no robust delay estimation |
| Custom partitioned-block adaptive filter | ours | 48 kHz | C++ | Reference-aware, no training data required, more DSP work |

Links:

- <https://github.com/a2heng/lightweight-aec-48k>
- <https://github.com/breizhn/DTLN-aec>
- <https://github.com/localai-org/LocalVQE>
- <https://github.com/fjiang9/NKF-AEC>
- <https://github.com/microsoft/AEC-Challenge>

## ONNX experiment

The 48 kHz ONNX model was run locally with ONNX Runtime 1.30.0. The model
expects mono 48 kHz STFT frames and a far-end reference plus streaming cache
tensors.

Synthetic test: a plucked chord bed as the far-end, a separate plucked melody
as the near-end instrument, and a simulated room echo with an 80 ms direct
delay plus early reflections.

| Case | Input RMS | Output RMS | Result |
|---|---:|---:|---|
| Far-end only | 0.0527 | 0.00069 | 37.6 dB echo reduction |
| Near-end only | 0.0814 | 0.00223 | 31.2 dB of the instrument removed |
| Double talk | 0.0944 | 0.00246 | 31.7 dB removed, near-end correlation 0.025 |

The model can cancel the reference, but it does not preserve non-speech
instrument content. It is trained on speech/AEC data, not music. It should not
be integrated as-is for MultiTuner.

## First real capture

A first far-end-only capture was made using the laptop reference and the phone
MIC track:

- 18 s reference, laptop speakers, phone not playing audio
- aligned by cross-correlation at about **2.11 s**
- reference/microphone correlation about **-0.29**, with the recorded echo well
  below the room noise floor at this placement/volume
- the 48 kHz ONNX model did not materially reduce the microphone RMS or residual
  correlation on this low-echo capture

This is not yet a useful cancellation benchmark. The next capture should use a
higher reference level or place the phone closer to the laptop so the echo is
clearly above the room noise. The capture files are intentionally not stored in
the repository.

## Decision

- Do not use the speech-trained ONNX model in production.
- Do not add a phone-side monitor reference tap yet.
- Use the laptop reference + phone microphone setup to collect real music data
  first.
- Evaluate a classical adaptive canceller next, with neural residual suppression
  only if real captures show it is needed.

## Next steps

1. Capture the three reference/microphone cases with laptop playback.
2. Align capture and reference by cross-correlation or GCC-PHAT.
3. Measure:
   - echo return loss enhancement on far-end-only material
   - near-end preservation on instrument-only material
   - residual echo and instrument damage during double talk
4. Prototype WebRTC AEC3 and a reference-aware linear adaptive filter offline.
5. Integrate only if the result preserves instruments and survives speaker
   volume, distance and room changes.
6. Keep headphones as the reliable workflow until then.

## Phone recording notes

- Use an empty existing cassette MIC track; no special app build is required.
- Recording is destructive; do not reuse a track you want to keep.
- Keep all MultiTuner playback, click and drums off.
- Use the phone speaker for neither monitoring nor playback.
- Use the raw microphone source where available; the recorder prefers
  `UNPROCESSED` and falls back to `MIC`.
- Do not move the phone or laptop between the three captures.
- Keep the laptop at a realistic listening volume; do not clip the phone input.
