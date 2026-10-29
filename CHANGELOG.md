# OneTake Android — Changelog & Agent Context Ledger

This document maintains a continuous, detailed record of architectural decisions, feature additions, bug remediations, and critical rules for all contributing AI agents and developers.

---

## ⚠️ Non-Negotiable Project Policies

Every agent working on this codebase **MUST** strictly adhere to the following rules:

1. **Zero-Hardcoded Script Policy**:
   - **NEVER** introduce hardcoded script text, fake dialogue lines, seeded repertoire scenes, or placeholder characters (e.g., `"The Audition"`, `"Where were you on the night..."`, `"Audition scene lines."`, `"SAM · YOU"`, `"NINA"`).
   - All script lines must originate exclusively from genuine user data: pasted text, camera OCR scans, imported sides PDFs, or parsed casting notices.
   - When no scripts exist, screens must show clean empty states or route directly to setup flows (e.g. `Route.PracticeSetup`).
2. **Verification Before Completion**:
   - Never assert that tests or builds succeed without executing the actual commands and inspecting outputs. Always verify with:
     ```bash
     ./gradlew testDebugUnitTest --rerun-tasks
     ./gradlew assembleDebug
     ```

---

## 🌿 Active Git Branches

- **`main`** (`origin/main` @ `13135c1`):
  - Primary production branch with merged PR #2 (persistence engine, real media, tape check, home-screen widget, take trimming, deadline notifications, and honest UI).
- **`feature/practice-drills-voice-headshots`** (`origin/feature/practice-drills-voice-headshots` @ `0044616`):
  - Studio practice suite, zero-hardcoded script binding, headshot 4:5 auto-framing engine with face detection, hands-free director voice commands with live HUD, and dynamic practice take persistence.

---

## 📜 Chronological Change History

### [Branch: `feature/practice-drills-voice-headshots`] — October 2026

#### 1. Zero-Hardcoded Script Audit & Quick Drill Architecture
- **Commit:** `0044616`
- **Files Modified / Deleted:**
  - Deleted: `app/src/main/kotlin/com/onetake/app/data/DefaultRepertoire.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/AppStore.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/practice/PracticeScreens.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/prepare/PrepareScreens.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/practice/AudioWalkScreen.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/auditions/AddAuditionSheet.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/auditions/AuditionDetailScreen.kt`
  - Modified: `app/src/test/kotlin/com/onetake/app/ui/practice/PracticeDrillSuiteTest.kt`
- **What Changed:**
  - Completely purged seeded sample scripts and fake fallback dialogue.
  - Bound Quick Drills (`Partner`, `First-Letter`, `Cold Read`, `Audio Walk`) dynamically to the actor's real active practice piece or audition scene.
  - When no scripts exist, drill taps navigate to `Route.PracticeSetup`, allowing actors to paste, scan, or import real sides.
  - Replaced fake example placeholder text with generic prompt copy.

#### 2. Dynamic Practice Take Flow & Persistence
- **Commit:** `90a1ac6`
- **Files Modified:**
  - `app/src/main/kotlin/com/onetake/app/ui/practice/PracticeScreens.kt`
  - `app/src/test/kotlin/com/onetake/app/ui/practice/PracticeDrillSuiteTest.kt`
- **What Changed:**
  - Dynamically bound `PracticeTakeScreen()` to the actor's real character, dialogue lines, and partner cues.
  - Dynamically computes take number based on saved practice takes (`store.takes.count { it.sceneId == currentPieceId } + 1`).
  - Persists real practice takes to `store.takes` with real elapsed duration and timestamps.
  - Displays "Free Rehearsal" when filming unscripted takes without fake dialogue.

#### 3. Director Voice Commands & Live Viewfinder HUD
- **Commit:** `a09b7a6`
- **Files Modified:**
  - `app/src/main/kotlin/com/onetake/app/ui/shoot/ShootFlowScreen.kt`
  - `app/src/test/kotlin/com/onetake/app/ui/shoot/ShootControlTest.kt`
- **What Changed:**
  - Refined `STOP_REGEX` to match only true director cut commands (`cut`, `and cut`, `scene cut`, `stop recording`, `cut take`, `that's a wrap`), eliminating false cuts triggered on dialogue words like `"done"` or `"end"`.
  - Refined `RECORD_REGEX` for director rolling triggers (`action`, `roll camera`, `rolling`, `roll`, `start`, `start recording`, `record`).
  - Added hands-free stage voice navigation in `Check` ("slate", "shoot") and `Saved` ("circle take", "go again", "done").
  - Added floating `VoiceCommandHud` pill showing real-time mic status and animated recognized command flashes.

#### 4. Face-Centered 4:5 Headshot Auto-Framing & Hands-Free Capture
- **Commit:** `d1a37cb`
- **Files Created / Modified:**
  - Created: `app/src/main/kotlin/com/onetake/app/ui/camera/HeadshotFramingEngine.kt`
  - Created: `app/src/test/kotlin/com/onetake/app/ui/profile/HeadshotFramingEngineTest.kt`
  - Modified: `app/src/main/kotlin/com/onetake/app/ui/profile/ProfileScreens.kt`
- **What Changed:**
  - Implemented `HeadshotFramingEngine`: reads Exif rotation upright, detects face coordinates with Android native `FaceDetector` (with upper-third fallback matching the oval guide), and crops to standard 4:5 portrait ratio.
  - Added hands-free `HeadshotVoiceCommander` recognizing `"now"`, `"take"`, `"shoot"`, `"capture"`, `"cheese"`, `"ready"`.
  - Added mic listening badge next to the shutter button.

#### 5. Interactive Line Learning Drills
- **Commit:** `0c3c1b3`
- **Files Modified:**
  - `app/src/main/kotlin/com/onetake/app/ui/practice/LineLearningMode.kt`
  - `app/src/main/kotlin/com/onetake/app/ui/prepare/PrepareScreens.kt`
  - `app/src/test/kotlin/com/onetake/app/ui/practice/LineLearningModeTest.kt`
- **What Changed:**
  - Added `LineLearningMode.formatMaskedWithReveals` with tap-to-peek word shielding in `PrepLineMode.MaskAndReveal`.
  - Added live animated audio gap countdown with `LinearProgressIndicator` and remaining seconds display in `PrepLineMode.AudioGap`.

#### 6. App Logo & CameraX Stability
- **Commits:** `63c3827`, `fef1bae`, `69186e5`
- **What Changed:**
  - Replaced old logo with movie clapper supporting Material 3 dynamic color adaptive iconography.
  - Added fallback `QualitySelector` to prevent CameraX bind failure crashes across different Android devices.
  - Replaced hold-to-stop with tap-to-stop recording and introduced a pre-roll countdown timer.

---

### [Branch: `main`] — October 2026 (PR #2 Integration)

- **Merged PR #2 (`13135c1`)**:
  - **Persistence & Architecture:** Added off-main-thread storage calculation, JSON persistence, and proper cleanup on data deletion.
  - **Tape Check Engine:** Implemented ML Kit face detection inside recorded takes to measure sound levels (silence, clipping) and framing (off-center, distance).
  - **Home Screen Widget:** Added "Next audition" Android app widget displaying soonest due auditions.
  - **Sides & Notice Sharing:** Integrated Android `Share → OneTake` for receiving scripts and casting notices directly from Gmail, WhatsApp, and browser.
  - **Audition Management:** Added take trimming, multi-scene reader recording, callback creation, and local deadline reminder alarms.

---

### [Branch: `main`] — October 2026 (Live Dynamic Headshot Framing, Hands-Free Voice Capture, and Core Cleanups)

- **Headshot Dynamic Framing & Hands-Free Capture:**
  - Implemented real-time CameraX `ImageAnalysis` with ML Kit `FaceDetection` and Y-plane luma processing in `HeadshotFramingEngine`.
  - Replaced misleading static "Hold still — you're centred" default with honest, live dynamic guidance ("Align your face in the oval", "Move closer", "Step back", "Raise camera", "Lower camera", "Move left", "Move right", "Face camera directly", "Keep head level", "Hold still — you’re centred").
  - Added robust CameraX surface combination fallback for legacy/limited hardware devices, ensuring `ImageAnalysis` runs reliably during framing and swaps `ImageCapture` during high-res shutter snaps.
  - Implemented face-centered 4:5 aspect ratio cropping with golden ratio headroom.
  - Fixed hands-free "Say Now" speech recognition: bound to Activity context, added live permission change reactivity, decoupled capture closures via `rememberUpdatedState`, and added clean recognizer teardown/recreation on speech engine errors.
- **Zero-Hardcoded Script Integrity:**
  - Purged hardcoded audition dialogue and fake placeholder scenes across models and screens (`Models.kt`, `AuditionDetailScreen.kt`, `PracticeScreens.kt`).
- **Interactive Line Learning:**
  - Added tap-to-peek word unmasking in `MaskAndReveal` and visual audio gap countdown progress in `AudioGap`.
- **Director Voice Triggers:**
  - Refined director speech triggers in `ShootFlowScreen` and hardened error recovery across all speech recognizers.
- **Copy Cleanup (Privacy & Local Storage Disclaimers):**
  - Purged repetitive, nagging "stays on this phone", "nothing leaves this phone", and "on this phone" disclaimers across Practice, Profile, Auditions, Shoot, Takes, and Package flows. Replaced with concise, professional UI labels.
- **Ready to Send (`PackageScreen`) Interactive Package Overhaul:**
  - Made every tape strip block and legend item interactive in `PkgTapeStrip`: tapping Headshot, Slate, Full length, or Scene segments opens direct preview sheets.
  - Built comprehensive interactive bottom sheets:
    - `PkgHeadshotsSheet`: Previews all 3 headshots with 4:5 aspect ratio, angle labels, and retake action.
    - `PkgClipSheet`: Embedded video playback with `TkPlayerCard`, slate spoken text, and re-record actions for Slate and Full Length.
    - `PkgSceneTakeSheet`: Displays circled take preview with playback and direct "Choose different take" shortcut.
    - `PkgAudioSheet`: Verifies audio presence, peak dBFS levels, and distortion warnings across all deliverable clips.
    - `PkgDeliverableDetailsSheet`: Shows deliverable file naming, destination casting specifications, video/audio encoding parameters, and duration.
  - Fixed dead chevron bug in `PkgRow`: Chevrons now only render on clickable rows.
  - Dynamic Tape Check Row: Evaluates real tape status using `TapeChecker.run` (`c.success` checkmark when passed, `c.warning` when issues need review).
  - Pre-Export Assembly Preview: Tapping "Preview the whole tape" when unexported offers instant composition and playback via system player.
