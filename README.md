# Squeve Redail

Android auto-redial app. Add numbers, choose how calls are ordered, and it dials with one call at a time.

## What it does

- **Targets:** type numbers, pick from contacts or the call log, paste from the clipboard, import a CSV/text file, or share numbers to the app from any other app. Invalid numbers and duplicates are skipped; an optional country code fixes local numbers.
- **Order:** one by one, round robin or random. Per-number overrides for calls, hang-up, interval, speaker and mute.
- **SIM:** default, SIM 1 or SIM 2, with fall-back or alternating strategies.
- **Timing:** hang-up limit with a KEEP CALL control, fixed or random interval, quiet hours, daily cap, battery pause.
- **Answers:** call-log based detection with a threshold, "skip once answered", retry only the unanswered.
- **During a run:** progress ring, floating card or bubble, live event log, notification with a countdown. It waits by itself during your other calls, in airplane mode or without signal.
- **After a run:** results summary, history with CSV export, finish alert, resume after an interruption.
- **Plan ahead:** scheduled starts (survive reboots), saved lists, dry run.
- **Shortcuts:** long-press app icon, Quick Settings tile and a home screen widget.
- **Look:** accent colours, pure black, UI size, optional app lock. Settings backup and restore.

## Build the APK on GitHub

1. Push this project to `main`.
2. Open the **Actions** tab. *Build APK* runs on every push.
3. Every successful build on `main` is published as a GitHub Release (`build-<number>`); the app updates from it (SYSTEM > Updates).

## Notes

- Signed with the key in `app/squeve-debug.store` so updates install over older builds.
- Not Play Store compliant (call + call-log permissions). Distribute via GitHub releases.
- Android gives no "answered" signal. Answers are inferred from call-log duration.
- Bundled fonts: Poppins and Liberation Mono (SIL OFL), see `app/src/main/assets/fonts/FONTS.txt`.
