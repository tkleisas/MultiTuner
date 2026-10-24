# MultiTuner — Product Design and Roadmap

Status: living document. v0.8.1 Side B recording and cassette polish.

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
- Foreground audio shipped in 0.2.0; background playback via a media foreground
  service is deferred to 0.2.x.

## 4. Drums

- Shipped in 0.3.0: 8-track synthesized kit, 16-step grid, normal/accent steps,
  Rock/Funk/House/Break presets, swing, drum volume and a shared transport with
  the metronome.
- Built-in synthesized kit adapted from yawn's `DrumSynth`.
- Metronome and drums have independent start/stop states but share one transport
  and can run together.
- Sample kit playback adapted from yawn's `DrumRack` voice, pattern chaining,
  probability, sample import, per-track effects and MIDI sync come later.

## 5. Cassette 4-track recorder

### Physical model

This emulates a Yamaha MT-50-style **full-width 4-track cassette**, not a
standard 2×2 stereo cassette.

- One cassette = **4 mono tracks**, full tape width, exactly **10 minutes**
- One direction records all four tracks; there is no independent Side B audio
- Every track is a preallocated, zero-filled 10:00 WAV
- Side B is the same tape buffer-reversed and with track order swapped 1↔4, 2↔3
- Storage: 4 × 10 min mono at 48 kHz/16-bit ≈ **230 MB per cassette**
- Reverse recording is supported by flipping and arming the mirrored tracks

### Flip and routing

When flipped:

- Each full track file is sample-reversed and the track order is swapped
- Playback, reels and the tape counter remain forward, starting at 0:00
- Deck strips display 4, 3, 2, 1
- Stored files route: 1 → deck 4, 2 → deck 3, 3 → deck 2, 4 → deck 1
- Pan, volume, mute and solo follow the **deck track strips**
- Varispeed and tape emulation remain active
- Metronome and drums are unaffected
- Recording is enabled while flipped; takes are written in Side B time and become reversed on Side A
- Flipping back exposes the Side A timeline with Side B takes reversed

### Cassette UI

Shipped through 0.8.1:

- Cassette library with create, select and delete; rename/duplicate are planned
- User-selectable cassette shell colors
- Shell shading, label plate, side badge, screws, tape window, head and capstans
- Variable tape packs on the reels; both reels rotate in the transport direction
  and follow varispeed
- Tape counter, side/reverse indicator and total length
- Four track strips: arm, volume, constant-power pan, mute, solo and per-deck
  reverb/delay sends
- Hold `<<`/`>>` to wind continuously (with an immediate first step); playback is
  muted while winding
- `|<` and `>|` jump to tape start/end
- Record input gain (0–200%) plus REC IN and TAPE OUT level meters
- Varispeed control and cassette-bus tape-emulation panel on the deck
- Master reverb and delay returns with size/damping and time/feedback/tone
- Export button for WAV/OGG stereo mixdowns and Android share-sheet handoff

Planned polish: a flip animation, per-strip meters and waveforms, and a larger
deck-style transport.

### Session layout

```
<app files>/cassettes.json
<external app files>/cassettes/<id>/
  track-1.wav
  track-2.wav
  track-3.wav
  track-4.wav
```

`files/cassettes.json` stores the cassette library, selected cassette, shell
colors, per-track file paths and start offsets, and per-deck
pan/volume/mute/solo/FX-send metadata. Varispeed, flip, tape and master FX
settings live in app preferences.

### Recording and storage

Shipped:

- Stream PCM16 mono WAV at 48 kHz to app-specific storage; takes are never held
  in RAM
- Record the current transport position and overdub while existing tracks play
- Store recordings locally; they are not transmitted
- Stop and finalize the take when the app is backgrounded
- Exclude cassette audio and metadata from Android cloud backup and transfer
- Export a forward 1× stereo mix with deck settings, sends, master FX and tape
  to WAV or OGG Opus across the full 10:00 tape
- Hand exports to the Android share sheet through `FileProvider`
- Exclude metronome/drums from exports; MP3 is not offered because Android has
  no dependable system MP3 encoder

Planned:

- Free-space estimate before recording
- Foreground recording service for background recording
- Record-varispeed and export presets
- Per-track FX inserts and monitor-through-tape

## 6. Varispeed

- Range **0.5×–2.0×**, default 1.0×
- Playback-only through 0.7.0; recording runs at 1×
- Display ratio and semitones; ratio changes currently apply immediately
- Metronome and drums follow varispeed; effective BPM display is planned
- Windowed-sinc resampling with anti-aliasing for read rates above 1×
- Hold-wind steps are content-time based and respect varispeed; start/end jumps
  set the transport directly
- Record-varispeed with takes resampled into the session timeline is planned

## 7. Tape emulation

- Inserted on the cassette bus after the four-deck mix and varispeed, with bypass
- Ported from yawn's `TapeEmulation` (MIT): asymmetric saturation, wow, flutter,
  hiss, HF rolloff/tone, wet/dry, 2× oversampling
- Tape off by default; the panel exposes drive, tone, wow, flutter, hiss and mix
- Scale wow/flutter rate and tone with varispeed in a later pass
- Compensate for the effect's ~10 ms delay in monitoring and overdubs later
- Per-track tape and monitor-through-tape later

### Master reverb and delay

- Two stereo aux buses with per-deck REV/DLY sends and master returns
- Reverb: Freeverb-style comb/allpass network with size and damping
- Delay: 40–1200 ms with feedback and a tone filter
- Routing: deck mix → sends → reverb/delay returns → cassette tape stage → output
- Returns default to 0% (off); returns sit before the tape stage
- FX settings persist and are included in exports, but click/drums bypass them

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
| **0.1.0** | Shipped: data-driven tunings, Oud/Laouto/Lute presets, microtonal offsets, compact many-course UI, tests |
| **0.2.0** | Shipped: Oboe audio engine, shared transport, foreground metronome |
| **0.3.0** | Shipped: synthesized 8-track drum machine, 16-step sequencer, presets, swing, shared metronome/drum transport |
| **0.4.0** | Shipped: full-width 4-track cassette recorder, REW/FF, record gain and meters, flip/reverse, pan, playback varispeed 0.5×–2×, cassette-bus tape emulation |
| **0.7.0** | Shipped: per-deck reverb/delay sends, master FX returns, stereo WAV/OGG export and Android sharing |
| **0.7.1** | Shipped: realistic cassette shell, variable tape packs, hold-to-wind and tape start/end controls |
| **0.8.0** | Shipped: simultaneous MIC/CLICK/DRUMS/BUS recording, BUS sends, punch-in overwrite, 10-minute preallocated tape and full-buffer side flip |
| **0.8.1** | Shipped: Side B reverse recording, cassette rename, tuner title contrast and reel rotation fixes |
| **Next** | Record varispeed, per-track tape, foreground recording, export presets, library polish |

## 11. Decisions locked

- App name remains **MultiTuner**.
- Instruments: Oud, Laouto and Lute in the first expansion.
- Microtonal per-string offsets now; full temperaments later.
- Oboe for the shared audio engine.
- Drums use yawn's synthesized and sample-based drum work.
- Cassette: one full-width 4-track program, 10 minutes; flipping reverses every
  full track buffer and swaps deck routing. No independent Side B audio.
- Varispeed: 0.5×–2.0×, playback-only through 0.7.1.
- Tape: cassette bus first, off by default, master/per-track later.
- FX: two shared stereo aux buses, per-deck sends, returns before tape.
- Export: forward 1× WAV or OGG Opus, no MP3 encoder dependency.
- Metronome/drums follow varispeed and are excluded from exports.
- Four mono recorder tracks with pan and adjustable input gain.
