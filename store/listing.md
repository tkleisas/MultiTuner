# Google Play Store listing

## App identity

- **App name:** MultiTuner
- **Package name:** `com.tkleisas.multituner`
- **Default language:** English (United States)
- **App or game:** App
- **Free or paid:** Free
- **Category:** Music & Audio
- **Tags:** Music, Tuner, Guitar
- **Contact email:** tasos.kleisas@gmail.com
- **Website:** https://github.com/tkleisas/MultiTuner
- **Privacy policy:** https://tkleisas.github.io/MultiTuner/privacy-policy.html

## Short description

```
Offline tuner for guitar, bouzouki, saz and tambouras.
```

## Full description

```
MultiTuner is a fast, offline tuner for stringed instruments. It shows a large note readout, a cents needle, an animated strobe and live feedback for every string or course in the selected tuning.

Built for guitar, Greek bouzouki, saz and tambouras:

Guitar: Standard E, Drop D, Eb Standard, D Standard, Drop C, C Standard, DADGAD, Open G, Open D, Open E, Baritone B and 7-string Standard.
Bouzouki: 4-course C-F-A-D and D-G-B-E; 3-course D-A-D and D-G-D.
Saz: Bozuk (Kara) and Bağlama in low-A and high-A variants.
Tambouras: D-G-C (default), D-A-D and D-A.

HOW IT WORKS
Play one string at a time. MultiTuner detects the pitch with a native YIN detector, finds the nearest string or course, and shows how many cents you are away from the target. Tap a course chip to lock the tuner to that course when you want to focus on a single string. The needle, strobe and colour feedback make it easy to see when you are in tune.

FEATURES
- Native C++ YIN pitch detection
- Automatic string/course detection and manual course lock
- Large note, cents, confidence, needle and strobe feedback
- Adjustable A4 reference from 415 to 466 Hz
- Phone and tablet layouts
- Works offline
- No ads, no account and no internet permission
- Audio is processed on-device and is never recorded or uploaded

For best results, play close to the microphone in a quiet room and tune one string at a time.

MultiTuner is suitable for practice, instrument setup and live tuning.
```

## Graphics

| Asset | File |
|---|---|
| App icon (512 x 512) | `store/icon-512.png` |
| Feature graphic (1024 x 500) | `store/feature-graphic-1024x500.png` |
| Phone screenshot 1 | `store/screenshots/01-guitar-standard-e.png` |
| Phone screenshot 2 | `store/screenshots/02-bouzouki-in-tune.png` |
| Phone screenshot 3 | `store/screenshots/03-tambouras-dgc.png` |
| Phone screenshot 4 | `store/screenshots/04-bouzouki-cfad.png` |
| Phone screenshot 5 | `store/screenshots/05-tuning-picker.png` |
| Tablet screenshot | `store/screenshots/06-tablet-layout.png` |

Upload the tablet screenshot to both the 7-inch and 10-inch tablet sections if
Play Console asks for both.

## App content declarations

- **Privacy policy:** https://tkleisas.github.io/MultiTuner/privacy-policy.html
- **Ads:** No
- **In-app purchases:** No
- **App access:** All functionality is available without a login. The microphone
  permission is required for tuning.
- **Target audience:** 13+ (not designed for children).
- **News app:** No
- **COVID-19 contact tracing:** No
- **Government app:** No
- **Financial features:** No
- **Health features:** No
- **Target API level:** 36 (Android 16)

### Content rating questionnaire

Suggested answers:

- Category: Utility, Productivity, Communication, or Other
- Violence, scary content, sexual content, language, controlled substances,
  gambling and user-generated content: No
- Ads: No
- Purchases: No

This should produce an Everyone / PEGI 3 / ESRB Everyone rating. Confirm the
final result in Play Console.

### Data safety

- **Does the app collect or share any required user data?** No.
- **Microphone:** Used for core app functionality only. Audio is processed
  on-device in real time and is not stored, recorded or transmitted.
- **Analytics / advertising / tracking SDKs:** None.
- **Internet permission:** Not requested.
- **Account creation:** None.
- **Data deletion:** No accounts or stored user data exist.

## Reviewer instructions

1. Open the app and grant microphone permission.
2. Select a tuning from the header.
3. Play a single string near the microphone.
4. Tap a course chip to lock a string; tap it again to return to auto mode.
5. Adjust the A4 reference with the A4 button and pause/resume with the Audio button.

No test account or login is required.

## Release notes for v0.0.5

```
Initial Play Store release.

- Native YIN pitch detection for guitar, bouzouki, saz and tambouras
- Tambouras D-G-C default tuning
- Auto string detection and manual course lock
- Needle, strobe and per-course visual feedback
- A4 reference from 415 to 466 Hz
- Phone and tablet layouts
- 16 KB page size support for Android 15+ devices
- Offline, no ads, no data collection
```

## Signing notes

- Play App Signing is required for new apps.
- Recommended setup: let Google generate and manage the app signing key, and use
  the existing keystore as the upload key.
- Export the upload certificate for Play Console:

  ```bash
  keytool -export -rfc \
    -keystore ~/.android/keystores/multituner-release.p12 \
    -alias multituner \
    -file ~/.android/keystores/multituner-upload-cert.pem
  ```

- Upload the resulting `.pem` certificate in Play Console under
  **Release > Setup > App signing**.
- The Play Store build is re-signed by Google. GitHub release APKs are signed
  with the upload key, so a sideloaded APK cannot be upgraded in place by the
  Play Store version; uninstall the sideloaded app before installing from Play.

## Testing and production access

If this is a **personal** developer account created after November 13, 2023,
Google requires a closed test with at least **12 testers opted in for 14
consecutive days** before you can apply for production access. Keep the testers
opted in continuously and collect feedback for the production access
application.

An **organization** account does not have this 12-tester requirement.

## First release checklist

- [ ] Create the app in Play Console with package `com.tkleisas.multituner`
- [ ] Complete the store listing and upload the icon, feature graphic and screenshots
- [ ] Set the privacy policy URL
- [ ] Complete Ads, App access, Content rating, Target audience and Data safety
- [ ] Confirm target API 36 is reported for the AAB
- [ ] Upload `MultiTuner-v0.0.5.aab` to Internal testing first
- [ ] Test the Play-signed build on a physical device
- [ ] Run the pre-launch report
- [ ] Complete closed testing if required
- [ ] Apply for production access
- [ ] Roll out to production
