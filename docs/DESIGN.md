# MultiTuner — Product Design and Roadmap

Status: living document. v0.1.0 instrument expansion implemented.

## Vision

MultiTuner is an offline Android toolkit for string players and small recording
setups. The tuner remains the anchor, but the app grows into four modules:

**Tuner · Metronome · Drums · Cassette (4-track recorder)**

The app keeps the name **MultiTuner**. The product is deliberately a little
offbeat: a serious tuner with a cassette-based recorder and tape character.

## Product structure

- Bottom navigation on phones, navigation rail on tablets.
- Tuner is the default screen.
- One shared audio engine and transport behind the modules.
- Audio is processed on-device; no internet permission for tuner/recorder.
- The existing TDD suite and native DSP tests must stay green.

## 1. Tuner expansion

### Data-driven catalog

Tunings move from hardcoded Kotlin to:

```
app/src/main/resources/tunings.json
```

The JSON schema supports:

- instrument family
- tuning id, name and description
- preferred sharp/flat spelling
- courses with a label and one or more strings
- per-string MIDI note, optional cents offset, optional note label

The loader validates unique IDs, non-empty courses/strings, valid MIDI notes and
known families. Tests parse the real catalog and invalid fixture data.

### First instrument expansion

- **Oud**: Arabic (older D-G-A-D-G-C), modern Arabic C-F-A-D-G-C and
  F-A-D-G-C-F, Turkish Bolahenk C♯-F♯-B-E-A-D
- **Laouto**: mainland Greek C-G-D-A with octave-pair courses; Cretan
  G-D-A-E with octave-pair courses
- **Lute**: Renaissance 6-, 8- and 10-course tenor lute in G; octave pairs where
  historically used
- Existing: guitar, bouzouki, saz, tambouras

Microtonal support is implemented through per-string cents offsets and optional
labels. Verified makam/maqam presets can be added without code changes.

### UI

- Compact horizontally scrollable course strip for more than six courses.
- Existing wrapping chip layout for six or fewer.
- Course chips display octave pairs and optional microtonal labels.
- Family picker wraps across multiple rows.

## 2. Shared audio engine

- Native Oboe engine, full-duplex where supported, with separate input/output
  streams and timestamp-based alignment as a fallback.
- Shared transport: BPM, time signature, count-in, position, recording state.
- Mixer: four recorder tracks, metronome and drum bus; constant-power pan,
  volume, mute, solo and metering.
- Audio thread is allocation-free and lock-free; UI commands use a command queue.
- 16 KB page-size alignment and R8 full-mode optimization remain release
  requirements.

## 3. Metronome

- Port yawn's `Transport` and `Metronome` (MIT).
- BPM, tap tempo, time signature, subdivisions, accents and count-in.
- Sample-accurate scheduling from the audio clock.
- Standalone screen and reused transport inside the cassette recorder.
- Background playback via a media foreground service.

## 4. Drums

- Phase 1: rhythm box with 4–8 tracks, 16-step grid, velocity, swing, tempo,
  preset patterns and built-in kits.
- Built-in synthesized kit adapted from yawn's `DrumSynth`.
- Sample kit playback adapted from yawn's `DrumRack` voice.
- Factory patterns adapted from yawn's GM pattern library.
- Later: pattern chaining, probability, sample import, per-track effects and
  MIDI sync.

## 5. Cassette 4-track recorder

### Physical model

This emulates a Yamaha MT-50-style **full-width 4-track cassette**, not a
standard 2×2 stereo cassette.

- One cassette = **4 mono tracks**, full tape width, up to **15 minutes**
- One direction records all four tracks; there is no independent Side B audio
- The physical cassette has two sides, but flipping runs the same tape backwards
- Flipping reverses track order: 1↔4 and 2↔3
- Tape position maps as `t → 15:00 − t`
- Storage: 4 × 15 min mono at 48 kHz/16-bit ≈ **346 MB per cassette**
- Recording only in the forward direction in v1; reverse recording later

### Flip and routing

When flipped:

- All four tracks play in reverse
- Deck strips display 4, 3, 2, 1
- Stored files route: 1 → deck 4, 2 → deck 3, 3 → deck 2, 4 → deck 1
- Pan, volume, mute and solo follow the **deck track strips**, matching a
  physically flipped tape
- Reels turn backwards and the tape counter runs down
- Varispeed remains active
- Tape emulation remains active
- Metronome and drums mute
- Recording is disabled while flipped in v1
- Flipping back restores original routing and strip order

### Cassette UI

- Cassette library/shelf with create, rename, duplicate and delete
- User-selectable cassette shell colors
- Animated supply/take-up reels; reel speed and direction follow transport and
  varispeed; supply radius shrinks and take-up radius grows
- Flip animation that rotates the shell and swaps reel roles
- Tape counter, side/reverse indicator, elapsed and remaining time
- Four track strips: arm, record, volume, pan, mute, solo, meter and waveform
- Varispeed control and master tape-emulation panel on the deck

### Session layout

```
cassettes/<id>/
  cassette.json
  track-1.wav
  track-2.wav
  track-3.wav
  track-4.wav
```

`cassette.json` stores name, shell color, duration limit, varispeed, tape
settings and per-track pan/volume/mute metadata.

### Recording and storage

- Stream PCM16 WAV to app-specific storage; do not hold 15-minute takes in RAM
- Check free space before recording and show a storage estimate
- Exclude cassette audio from Android backup
- Foreground recording service for background recording
- Mixdown/export to stereo WAV; share via FileProvider
- Recordings never leave the device unless the user exports them

## 6. Varispeed

- Range **0.5×–2.0×**, default 1.0×
- Playback-only in 0.4.0; recording runs at 1×
- Smooth ratio changes; no clicks
- Display ratio, percentage and semitones/octaves
- Metronome and drums follow varispeed; show nominal and effective BPM
- High-quality variable-rate windowed-sinc resampler with anti-aliasing
- Record-varispeed with takes resampled into the session timeline in 0.5.0

## 7. Tape emulation

- Master-bus insert after mix and varispeed, with bypass
- Ported from yawn's `TapeEmulation` (MIT): asymmetric saturation, wow, flutter,
  hiss, HF rolloff/tone, wet/dry, 2× oversampling
- Scale wow/flutter rate and tone with varispeed
- Tape off by default, with a subtle preset available
- Compensate for the effect's ~10 ms delay in monitoring and overdubs
- Per-track tape and monitor-through-tape in 0.5.0

## 8. Reuse map

| Source | Reuse |
|---|---|
| `../volna` | Oboe stream setup, callback structure, CMake integration |
| `../yawn/src/audio/Transport.*` | Play/stop, BPM, time signature, count-in, loop |
| `../yawn/src/audio/Metronome.*` | Sample-accurate click scheduling and accents |
| `../yawn/src/instruments/DrumSynth.*` | Synthesized 8-piece drum kit |
| `../yawn/src/instruments/DrumRack.*` | Sample pad voice, choke groups, envelopes |
| `../yawn/src/presets/DrumPatterns.*` | GM drum patterns and genre loops |
| `../yawn/src/audio/Mixer.*` | Pan, mute/solo, metering and signal-flow concepts |
| `../yawn/src/audio/AudioEngine.*` | Count-in, arm, latency measurement and record-state concepts |
| `../yawn/src/effects/TapeEmulation.*` | Master tape emulation |
| `../yawn/src/effects/Resampler.h` | Not used for varispeed; it is a glitch effect |
| `../yawn/tests/test_Transport.cpp`, `test_Metronome.cpp`, `test_Mixer.cpp` | Port into native test suite |

## 9. Testing and TDD

- Pure Kotlin tests for tuning catalog parsing, microtonal targets, string
  matching, display logic, session models and transport calculations.
- Native offline render tests for click/drum scheduling, varispeed, tape,
  pan/mixing, WAV I/O and overdub alignment.
- Port relevant yawn tests for transport, metronome and mixer.
- Compose UI tests for navigation, tuner courses, metronome and recorder.
- Write the failing test first for every new behavior; `./scripts/test.sh` must
  pass before commits.

## 10. Roadmap

| Version | Scope |
|---|---|
| **0.1.0** | Data-driven tunings, Oud/Laouto/Lute presets, microtonal offsets, compact many-course UI, tests |
| **0.2.0** | Oboe audio engine, shared transport, metronome |
| **0.3.0** | Rhythm box and step-sequencer drum machine |
| **0.4.0** | Full-width 4-track cassette recorder, flip/reverse, pan, playback varispeed 0.5×–2×, master tape emulation |
| **0.5.0** | Record varispeed, per-track tape, monitor-through-tape, export/share, integration polish |

## 11. Decisions locked

- App name remains **MultiTuner**.
- Instruments: Oud, Laouto and Lute in the first expansion.
- Microtonal per-string offsets now; full temperaments later.
- Oboe for the shared audio engine.
- Drums use yawn's synthesized and sample-based drum work.
- Cassette: one full-width 4-track program, 15 minutes; flipping plays the same
  tape backwards with physical deck-strip routing. No independent Side B audio.
- Varispeed: 0.5×–2.0×, playback-only in 0.4.0.
- Tape: master bus first, off by default, per-track later.
- Metronome/drums follow varispeed.
- Four mono recorder tracks with pan.
