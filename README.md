# MultiTuner

[![Android CI](https://github.com/tkleisas/MultiTuner/actions/workflows/android.yml/badge.svg)](https://github.com/tkleisas/MultiTuner/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/tkleisas/MultiTuner)](https://github.com/tkleisas/MultiTuner/releases)

MultiTuner is a fast, offline Android tuner for stringed instruments. It is built
around a native C++ YIN pitch detector and provides real-time note, cents, needle,
strobe and per-course feedback.

The app ships with presets for guitar, Greek bouzouki (3- and 4-course), saz and
tambouras. It targets Android 10 and newer and is designed for both phones and
tablets.

## Screenshots

<p align="center">
  <img src="docs/screenshots/main-guitar.png" width="180" alt="Guitar Standard E" />
  <img src="docs/screenshots/in-tune.png" width="180" alt="Bouzouki D4 in tune" />
  <img src="docs/screenshots/tambouras.png" width="180" alt="Tambouras D-G-C" />
  <img src="docs/screenshots/bouzouki.png" width="180" alt="Bouzouki C-F-A-D" />
</p>

<p align="center">
  <img src="docs/screenshots/tuning-picker.png" width="230" alt="Tuning picker" />
  <img src="docs/screenshots/tablet.png" width="500" alt="Tablet layout" />
</p>

From left to right: guitar Standard E, bouzouki D4 in tune, tambouras D-G-C and
bouzouki C-F-A-D. Bottom: the tuning picker and the tablet layout.

## Features

- **Native pitch detection** - C++ YIN detector running through NDK/JNI, with a
  4096-sample analysis window, DC blocking, silence gating and parabolic
  interpolation.
- **String-aware feedback** - the detected pitch is mapped to the nearest string
  or course in the selected tuning. The matching course is highlighted with its
  live cents offset.
- **Auto and manual modes** - detect the nearest course automatically, or tap a
  course chip to lock the tuner to that course.
- **Multiple visualisations** - large note name, cents readout, confidence,
  needle gauge, animated strobe band, input level meter and an `IN TUNE` state.
- **Reference pitch** - adjustable A4 reference from 415 Hz to 466 Hz.
- **Instrument presets** - guitar, bouzouki, saz and tambouras.
- **Profile persistence** - the selected tuning, course lock and reference pitch
  are restored on the next launch.
- **Phone and tablet layouts** - portrait, landscape and wide-screen layouts.
- **Offline and private** - no internet permission; audio is processed on-device
  and is never recorded or uploaded.
- **Debug test tone** - debug builds include a `TONE` button for testing the
  complete audio path without an instrument. It is not present in release builds.

## Supported tunings

| Instrument | Presets |
|---|---|
| Guitar | Standard E, Drop D, Eb Standard, D Standard, Drop C, C Standard, DADGAD, Open G, Open D, Open E, Baritone B, 7-String Standard |
| Bouzouki | Tetrachordo C-F-A-D, Tetrachordo D-G-B-E, Trichordo D-A-D, Trichordo D-G-D |
| Saz | Bozuk (Kara) low A, Bozuk (Kara) high A, Bağlama low A, Bağlama high A |
| Tambouras | **D-G-C (default)**, D-A-D, D-A |

Course notes are shown low to high unless the tuning is intentionally
re-entrant. The bouzouki C-F-A-D preset includes the octave pairs on the C and F
courses.

Saz tuning names and octaves vary by instrument size and region. Both low-A and
high-A variants are included for Bozuk and Bağlama; use the one that matches your
instrument.

New presets can be added in
[`TuningLibrary.kt`](app/src/main/java/com/tkleisas/multituner/model/TuningLibrary.kt).

## How it works

1. `AudioRecord` captures mono audio at 48 kHz (44.1 kHz fallback), preferring an
   unprocessed input source when the device supports it.
2. Samples are passed to a native ring buffer. Every 2048 new samples a
   4096-sample window is analysed with YIN.
3. The detector returns frequency, confidence and RMS level. A one-pole DC
   blocker and a silence gate suppress rumble and empty input.
4. Kotlin maps the frequency to the closest string in the active tuning,
   calculates cents, smooths the display and applies an in-tune hold.
5. Jetpack Compose renders the note, needle, strobe, level meter and course
   chips.

Detection is monophonic: play one string at a time. Polyphonic "strum all
strings" detection is not implemented.

## Requirements

- Android 10 (API 29) or newer
- JDK 17 or newer (CI uses Temurin 17)
- Android SDK 35
- Android NDK `27.2.12479018`
- CMake `3.22.1`
- Gradle wrapper `8.11.1` (downloaded automatically)

The app builds for `arm64-v8a`, `armeabi-v7a` and `x86_64`.

## Build

### Android Studio

1. Open the project in Android Studio.
2. Let Android Studio create `local.properties` with `sdk.dir` pointing to your
   Android SDK, or create it manually:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
3. Install SDK 35, NDK `27.2.12479018` and CMake `3.22.1` when prompted.
4. Sync Gradle and run the `app` configuration.

### Command line

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME="/path/to/jdk-17"

yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" \
  "ndk;27.2.12479018" "cmake;3.22.1"

./gradlew assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Run lint and the native detector tests:

```bash
./gradlew lintDebug
./native-tests/run.sh
```

## Release signing

Release builds are signed when either `keystore.properties` (local development)
or the `RELEASE_*` environment variables (CI) are present.

### Local keystore

Create a release keystore outside the repository:

```bash
mkdir -p ~/.android/keystores
keytool -genkeypair -v -storetype PKCS12 \
  -keystore ~/.android/keystores/multituner-release.p12 \
  -alias multituner \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the project root:

```properties
storeFile=/home/you/.android/keystores/multituner-release.p12
storePassword=your-store-password
keyAlias=multituner
keyPassword=your-key-password
```

`keystore.properties` and all `*.p12`, `*.jks` and `*.keystore` files are
gitignored. Never commit them.

Build and verify the signed APK:

```bash
./gradlew assembleRelease

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify \
  --print-certs app/build/outputs/apk/release/app-release.apk
```

Output:

```
app/build/outputs/apk/release/app-release.apk
```

> **Back up the keystore and its passwords.** If you distribute updates outside
> Google Play, losing the signing key means you cannot publish an update for the
> same application ID. With Play App Signing, the upload key can be reset, but
> the original app-signing key still matters.

### GitHub Actions signing

The workflow expects these repository secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias, e.g. `multituner` |
| `RELEASE_KEY_PASSWORD` | Key password |

Set them with the GitHub CLI:

```bash
base64 -w0 ~/.android/keystores/multituner-release.p12 > keystore.b64
gh secret set RELEASE_KEYSTORE_BASE64 < keystore.b64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEY_PASSWORD
rm keystore.b64
```

On macOS, create the base64 value with:

```bash
base64 -i ~/.android/keystores/multituner-release.p12 | tr -d '\n' > keystore.b64
```

The workflow writes the decoded keystore to a temporary runner directory and
passes the path and credentials to Gradle through `RELEASE_KEYSTORE_PATH`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
Nothing is written back to the repository.

## CI/CD

The workflow is defined in
[`.github/workflows/android.yml`](.github/workflows/android.yml).

- Pushes to `master`/`main` and pull requests run the native detector tests,
  Android lint and `assembleDebug`, then upload the debug APK as a workflow
  artifact.
- Pushes of tags matching `v*` additionally decode the release keystore, build a
  signed release APK, verify it with `apksigner` and publish it as a GitHub
  release asset named `MultiTuner-vX.Y.Z.apk`.
- The workflow uses Ubuntu 24.04, Temurin JDK 17, Android SDK 35, NDK
  `27.2.12479018` and CMake `3.22.1`.

### Creating a release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit the change.
3. Create and push an annotated tag:

   ```bash
   git tag -a v0.0.4 -m "MultiTuner v0.0.4"
   git push origin v0.0.4
   ```

4. Watch the `Android CI` run in GitHub Actions. The signed APK appears on the
   corresponding GitHub release.

## Project structure

```
.
├── app/
│   ├── src/main/java/com/tkleisas/multituner/
│   │   ├── MainActivity.kt
│   │   ├── TunerViewModel.kt
│   │   ├── audio/          # AudioRecord capture, JNI bridge, test tone
│   │   ├── model/          # Tuning model and presets
│   │   └── ui/             # Compose screen, gauges, theme
│   ├── src/main/cpp/       # YIN detector and JNI entry point
│   └── build.gradle.kts
├── docs/screenshots/
├── native-tests/           # Host-side DSP tests
├── .github/workflows/
└── LICENSE
```

## Adding a tuning

Add a `Tuning` entry to
[`TuningLibrary.kt`](app/src/main/java/com/tkleisas/multituner/model/TuningLibrary.kt).
Each course contains one or more `TuningString(midi, centsOffset)` entries.
`centsOffset` is available for future microtonal or per-string offsets.

For octave-paired courses, provide both MIDI notes. For example, the bouzouki
C course is:

```kotlin
course("C", 48, 60)
```

## Debug test tone

Debug builds show a `TONE` button in the status line. It plays the currently
locked course, or the nearest detected course, through the speaker so the full
microphone-to-detector path can be tested without an instrument. The button and
tone generator are excluded from release builds.

## Privacy

MultiTuner does not request the `INTERNET` permission. Microphone audio is
processed on-device in real time and is not stored, recorded or transmitted.

## Roadmap

- Custom tuning editor with saved user tunings
- Microtonal/makam per-string offsets and alternate temperaments
- More instruments and regional presets
- Optional polyphonic string detection
- Signed App Bundle for Play Store distribution

## Contributing

Issues and pull requests are welcome. Before opening a pull request, run:

```bash
./native-tests/run.sh
./gradlew lintDebug assembleDebug
```

Do not commit keystores, passwords or other signing material.

## License

MultiTuner is released under the MIT License. See [LICENSE](LICENSE).

The native YIN detector is adapted from the tuner effect in the YAWN DAW
(MIT License, Copyright (c) 2026 Tasos Kleisas), with a corrected parabolic
interpolation stage and additional silence gating and fallback handling.

## Acknowledgments

- YAWN DAW for the original YIN tuner reference
- AndroidX, Jetpack Compose and the Android NDK toolchain
- The Greek and Turkish musicians whose instruments shaped the preset list
