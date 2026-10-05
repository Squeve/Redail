# Squeve Redail

Android auto-redial app. Queue several numbers, set attempts / gap / ring timeout per number, and it dials each one automatically, then moves to the next.

## Build the APK on GitHub

1. Create a new GitHub repo and push this project to `main`.
2. Open the **Actions** tab. The *Build APK* workflow runs on every push (or hit **Run workflow**).
3. When it finishes, open the run and download **SqueveRedail-apk** from *Artifacts*.
4. To publish a downloadable release: `git tag v0.1.0 && git push origin v0.1.0`. The APK is attached to the release automatically.

## Install

Enable "install unknown apps", install the APK, open the app, tap **Grant permissions**, then **Start**.
Disable battery optimization for the app, especially on Xiaomi / Oppo / Samsung.

## Notes

- Debug-signed APK: fine for sideloading. Add a release keystore later for signed builds.
- Not Play Store compliant (call + call-log permissions). Distribute via GitHub releases.
- Android gives no "answered" signal; "connected" is detected via call-log duration > 0.
- Keep attempts and gaps reasonable. Repeated automated calls can breach harassment or telemarketing rules.
