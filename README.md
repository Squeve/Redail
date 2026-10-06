# Squeve Redail

Android auto-redial app. Add several numbers, set calls per number, call duration and interval, and it dials them strictly in order: number 1 x N calls, then number 2 x N calls, and so on until done.

While redialing, a draggable floating card shows live progress with Pause/Resume and Stop. It needs "Display over other apps"; the app asks once on first START.

## Build the APK on GitHub

1. Push this project to `main`.
2. Open the **Actions** tab. *Build APK* runs on every push (or hit **Run workflow**).
3. Download **SqueveRedail-apk** from the run's Artifacts.
4. Every successful build on `main` is also published as a GitHub Release (`build-<number>`). The app's **Settings > Get latest version** downloads that release and installs it.

## Install

Enable "install unknown apps", install the APK, tap **Grant permissions**, add numbers, press **START**.
Disable battery optimization for the app, especially on Xiaomi / Oppo / Samsung.

## Notes

- Debug-signed APK for sideloading, signed with the key in `app/squeve-debug.store` so updates install over older builds.
- Not Play Store compliant (call + call-log permissions). Distribute via GitHub releases.
- Android gives no "answered" signal. "Skip if answered" uses call-log duration and is off by default.
