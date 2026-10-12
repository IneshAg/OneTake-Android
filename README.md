# OneTake — Android

Android companion app for **OneTake**, the actor's self-tape audition studio. Built with **Kotlin**, **Jetpack Compose**, and **CameraX**, matching 100% of the feature set and the Velvet design system from the iOS version.

---

## Features
- **Hero Audition Card & Schedule** — Shows your most urgent upcoming self-tape, hours left, readiness dial, and today's practice schedule.
- **Audition Library** — Filter auditions by Due, Ready, and Sent. Add new auditions with pieces checklists.
- **Pieces Checklist** — Track headshot, slate, full-length, and script scenes for each casting request.
- **CameraX Shoot Mode** — Live video capture with exposure lock, prompter for slate details, front/back switching, and recording HUD.
- **Takes Review & Circled Takes** — Review takes, long-press/tap star to circle your favorite take, review notes to self, and scrubber bar.
- **Automated Tape Check** — Pre-flight checks (framing, eye-line alerts, reader audio volume warnings).
- **Package & Send** — Export according to destination specifications (Actors Access, Casting Networks, Spotlight, Email).
- **Practice & Rehearsal** — Rehearse sides with lines tracking.
- **Actor Slate & Settings** — Configure spoken slate (Name, Height, Based In, Representation), noise reduction, and reader volume.

---

## How to Open and Run

### Option 1: Android Studio (Recommended)
1. Open **Android Studio**
2. Click **Open** and select `/Users/user76/Downloads/Audition App /OneTake-Android`
3. Wait for Gradle to sync dependencies
4. Select an Android Emulator (API 26+) or your physical Android device
5. Click **Run ▶** (or press `Shift + F10`)

### Option 2: Command Line (Gradle)
```bash
cd ~/Downloads/"Audition App "/OneTake-Android

# Build debug APK
./gradlew assembleDebug

# Install and run on connected device/emulator
./gradlew installDebug
```

---

## Testing on Android Emulator
- The app supports CameraX preview.
- In Android Studio Virtual Device Manager, you can configure the Emulator's Camera:
  - Go to **Virtual Device Manager** → Edit Virtual Device → **Advanced Settings** → Set Front Camera to **Webcam0** (or Emulated) to test video recording with your Mac's webcam!
