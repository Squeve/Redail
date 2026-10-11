# Squeve Redail

Android auto-redial app. Add numbers, choose how calls are ordered, set calls per number, call duration and interval, pick the SIM, and it dials with one call at a time.

- **Order:** one by one, round robin, or random
- **SIM:** phone default, SIM 1 or SIM 2
- **Floating control:** expanded card (progress, hang-up countdown with KEEP CALL, pause, stop) or a small snapping bubble
- **Fonts:** optional custom fonts, see `app/src/main/assets/fonts/README.txt`
- **Audio:** start on speaker, mute microphone (best effort)
- **Updates:** SYSTEM tab > Get latest version

## Build the APK on GitHub

1. Push this project to `main`.
2. Open the **Actions** tab. *Build APK* runs on every push (or hit **Run workflow**).
3. Every successful build on `main` is published as a GitHub Release (`build-<number>`); the app updates from it.

## Install

Enable "install unknown apps", install the APK, open the SYSTEM tab and clear the READINESS list, then press START.

## Notes

- Signed with the key in `app/squeve-debug.store` so updates install over older builds.
- Not Play Store compliant (call + call-log permissions). Distribute via GitHub releases.
- Android gives no "answered" signal. "Skip if answered" uses call-log duration and is off by default.
