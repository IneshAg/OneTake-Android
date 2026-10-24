# OneTake · Android 🎬

> **The actor's self-tape audition studio.** Kotlin, Jetpack Compose, CameraX and ML Kit, for Android 8.0+ (API 26).

[![Android CI](https://github.com/IneshAg/OneTake-Android/actions/workflows/ci.yml/badge.svg)](https://github.com/IneshAg/OneTake-Android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

OneTake takes an actor from a casting notice to a finished self-tape: it reads the notice and the
sides, helps you learn the lines, films the slate and every scene with a prompter and a reader,
checks the tape, and packages it the way casting asked. There's no account and no server —
everything stays on the phone until you send it.

> 🍎 **iOS:** [OneTake-iOS](https://github.com/IneshAg/OneTake-iOS) (SwiftUI, AVFoundation). The two apps
> share the design and the flow; a few features differ (see below).

---

## ✨ Features

### Auditions
- **Add from anywhere** — paste the notice, pick a photo, take a photo of the pages, pick a PDF or text
  file, type it in, or **Share → OneTake** from Gmail, WhatsApp, a browser or Files. Text is read on the
  phone with ML Kit OCR.
- **Reads the notice** — role, project, due date, scene count and what casting asked for.
- **Splits the sides into scenes** at each slugline (`INT.` / `EXT.`) or `SCENE 2` header and marks your lines.
- **Checklist per audition** — headshot, slate, optional full length and one piece per scene.
- **Edit details** (project, role, due date, destination, casting's notes), **revised sides**, and
  **callbacks** that carry the reader over.
- **Deadline reminders** the morning it's due and two hours before (local notifications), and a
  **"Next audition" home-screen widget**.

### Learn the lines
- **Prepare** — full lines, first letters, or hide yours, with casting's notes on top.
- **Run lines** and drills, with your own recorded reader or a text-to-speech reader, and real
  "lines learned" progress.
- **Record the reader** in your own voice, per scene.

### Shoot
- CameraX at up to 1080p, front or back camera, with a countdown, a prompter and your reader playing
  under the take.
- Slate, optional full length, then each scene — with a scene picker and "Next: Scene N".
- Hands-free "Action" / "Cut" (only when said on their own).

### Takes, Tape check and sending
- Takes per scene, circle your send take, **trim**, compare, and private notes.
- **Tape check** measures the real tape: what's recorded and missing, orientation and length against
  what casting asked, and inside each scene's take the **sound** (silent, quiet, distorting) and the
  **framing** (out of shot, off-centre, too far or close) using on-device ML Kit face detection.
- **Package** joins the slate, full length and circled takes into one `.mp4` named
  `LAST_FIRST_ROLE_PROJECT.mp4`, and hands it to the Android share sheet (mail, messaging, Drive…).

### Not on Android (yet)
- iOS's **iCloud download link** for "Email or link" — Android sends the file through the share sheet.
- iOS's **Lock Screen countdown** (Live Activity).

---

## 🏛️ Project layout

```
app/src/main/kotlin/com/onetake/app/
├── AppStore.kt        App state (AndroidViewModel), persisted as JSON on every change
├── MainActivity.kt    Entry point; receives shares and widget taps
├── Navigation.kt      Routes and the Navigator used by every screen
├── OneTakeApp.kt      Nav graph, tab bar, sheets
├── data/              Persistence, media storage, ScriptParser, MediaExporter, TakeTrimmer,
│                      TapeChecker + TapeAnalysis, DeadlineReminders, SharedNotice
├── models/            Audition, Take, Scene, Slate…
├── ocr/               ML Kit text recognition (images and PDFs)
├── widget/            "Next audition" Glance widget
└── ui/                Screens by feature, the design system, camera and system bridges
```

---

## 🚀 Build and run

Requirements: Android Studio (Ladybug or newer) or JDK 17 with the Android SDK (API 35).

```bash
git clone https://github.com/IneshAg/OneTake-Android.git
cd OneTake-Android
./gradlew assembleDebug        # build a debug APK
./gradlew installDebug         # install on a connected device or emulator
./gradlew testDebugUnitTest    # unit tests
```

On an emulator, set the camera to **Webcam0** (Device Manager → Edit → Advanced) to record with your
computer's webcam.

### CI and releases
- **Android CI** (`.github/workflows/ci.yml`) runs on every push and pull request: unit tests, a debug
  APK (downloadable from the run), and the minified release build.
- **Release** (`.github/workflows/release.yml`) runs when you push a tag like `v0.2.0`; the version name
  and code come from the tag. Add the repository secrets `ONETAKE_KEYSTORE_BASE64`,
  `ONETAKE_KEYSTORE_PASSWORD`, `ONETAKE_KEY_ALIAS` and `ONETAKE_KEY_PASSWORD` to publish a signed APK and
  an AAB for Google Play; without them the release carries a debug-signed APK.

---

## 📄 License

MIT — see [LICENSE](LICENSE).
