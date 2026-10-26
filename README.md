# Voice Reminder VIP PRO

تطبيق أندرويد عربي أولًا للتذكيرات الصوتية، يعمل محليًا ودون إنترنت: منبهات دقيقة، رسائل متسلسلة، قوالب، تصنيفات، تسجيلات صوتية، وسجل تنبيهات.

An Arabic-first Android voice reminder app built with Kotlin and Jetpack Compose. Offline-first: no INTERNET permission, no accounts, no ads, no analytics.

## Download

The APK is built by GitHub Actions (no local build is required):

1. Open **Actions → Build APK → latest successful run**.
2. Download the **voice-reminder-vip-pro-apk** artifact (`debug` and `release` APK + `SHA256SUMS.txt`).
3. Install the debug APK directly, or sign the release APK before distribution.

> The release APK is unsigned unless the optional `VR_KEYSTORE_*` repository secrets are configured. Prefer the debug APK for personal testing.

## Features

- **Offline TTS alarms**: exact alarms (when allowed) with full-screen ringing UI, repeat speech, pre-tone, vibration, and snooze.
- **Voice recordings**: record with the microphone and attach a clip to an alarm; falls back to TTS when no clip is set.
- **Sequential reminders**: create morning/school/work routines where each step is an independent alarm in a group.
- **Templates**: ready-made sequences (wake up, school, work, daily tasks, sleep) that can be created from any start time.
- **Categories**: colored, icon-based categories with filters on the home screen.
- **Offline smart parser**: rule-based Arabic / English / Indonesian parsing of phrases like «ذكرني كل يوم الساعة 7 صباحًا». No network and no AI model.
- **History**: fired, dismissed, snoozed, missed, and cancelled events stored locally.
- **Privacy**: no INTERNET permission; all data stays in the app's private storage.
- **Arabic RTL UI** with light/dark themes and optional 24-hour time.

## Build & verify

CI runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

The workflow uses JDK 17, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compileSdk 34, minSdk 26, targetSdk 34.

Optional signed release secrets:

| Secret | Purpose |
| --- | --- |
| `VR_KEYSTORE_BASE64` | Base64 of the release keystore |
| `VR_KEYSTORE_PASSWORD` | Keystore password |
| `VR_KEY_ALIAS` | Key alias |
| `VR_KEY_PASSWORD` | Key password |

If the keystore secret is absent, CI still produces an unsigned release APK.

## Permissions

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | Show alarm notifications (Android 13+) |
| `SCHEDULE_EXACT_ALARM` | Fire alarms at the exact minute (Android 12+) |
| `USE_FULL_SCREEN_INTENT` | Show the ringing screen over the lock screen (Android 14+) |
| `RECORD_AUDIO` | Record custom voice clips |
| `RECEIVE_BOOT_COMPLETED` | Restore schedules after reboot |
| `WAKE_LOCK` / `VIBRATE` | Keep the alarm active and vibrate |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reliable ringing playback |
| `DISABLE_KEYGUARD` | Show the alarm screen on the lock screen |

## Honest limitations

- Exact alarms, full-screen intents, and notification permission are controlled by the user/OS. The app shows a readiness banner and links to system settings.
- Offline speech depends on the installed TTS engine and downloaded language data. Availability and offline capability are detected from the engine, not guaranteed by the app.
- Some manufacturers aggressively kill background apps. Enabling autostart / disabling battery optimization improves reliability but cannot be guaranteed by any app.
- The parser is heuristic. Parsed suggestions are always shown for review in the editor before saving.
- Instrumented tests are compiled in CI but not executed (no emulator step). Only JVM unit tests are run.

## Project layout

```
app/src/main/java/com/voicereminder/vip/
  core/        models, scheduling, TTS, recorder, parser, permissions, time utilities
  data/        Room database, repositories, DataStore settings, seed data
  feature/     alarm receiver/service/activity and boot rescheduling
  ui/          Compose theme, shared components, screens, ViewModels
```

## License

Private project. All rights reserved unless a license is added by the owner.
