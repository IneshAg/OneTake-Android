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
- **Live Tape Check Engine (`ShootStage.Check`, `Slate`, `Scene`):**
  - Replaced static placeholder chips (`ShootCheckChips`) with a real-time reactive engine (`LiveTapeCheckEngine`):
    - **Framing & Centering:** Computes real-time horizontal face centering (tolerance 38%..62%) and distance ratio (13%..56% medium close-up). Generates dynamic cues: "Framing centered", "Shift left", "Shift right", "Move closer", "Step back", "Step into frame".
    - **Eyeline Detection:** Enforces self-tape rule of thirds; detects whether eyes/face sit in the upper third (22%..52% vertical ratio), guiding the actor to "Eyeline level", "Raise camera", or "Tilt down".
    - **Lighting Analysis:** Samples camera Y-plane luminance in real-time, detecting "Light balanced", "Too dark" (< 48 luma), or "Too bright" (> 216 luma).
    - **Live Microphone Metering:** Added `AudioMeter` running off-main-thread PCM sampling for live RMS decibel monitoring; detects "Room quiet", "Clear level", "Audio peaking" (> -8 dBFS), and "Mic needed". Connected `onRmsChanged` directly from `ShootVoiceCommander` for continuous volume reporting.
  - CameraX Multi-Use-Case Analyzer Support: Updated `Camera.kt` `bind()` pipeline to bind `ImageAnalysis` alongside video capture when not recording, preventing previous dead-analyzer limitations.
  - Live HUD Status in Slate & Scene: Added `ShootMiniStatusRow` and `ShootMiniStatusBadge` during slate and scene setup/pre-roll, plus a live audio recording pulse during takes.
  - Added full unit test suite `LiveTapeCheckEngineTest.kt` verifying all thresholds and edge cases.
- **Total Elimination of Redundant Device / Storage Phrasing:**
  - Audited and cleansed all remaining instances of "on this phone", "stays on this phone", "nothing leaves this phone", and "read on the phone" across comments, manifest, and UI copy (`AndroidManifest.xml`, `AppStore.kt`, `Persistence.kt`, `SharedNotice.kt`, `TapeAnalysis.kt`, `TapeChecker.kt`, `AddAuditionSheet.kt`, `AuditionDetailScreen.kt`, `OnboardingFlow.kt`, `ProfileScreens.kt`, `ReaderScreens.kt`, `ShootScreens.kt`). Replaced with natural, production-grade terminology.
- **Deadline Time Picker & Quick Presets in Audition Setup:**
  - Added full interactive time editing and selection to the "Here's what they want" review step (`AddReviewStep` in `AddAuditionSheet.kt`):
    - Replaced the single date box with side-by-side **Date** and **Time** cards featuring calendar and schedule icons, clear labels, and tap-to-edit affordances.
    - Added Material 3 `AddTimePickerDialog` utilizing `rememberTimePickerState`, `TimePicker`, and `AlertDialog` matching the theme.
    - Added one-tap industry standard deadline time preset chips (`10:00 AM`, `12:00 PM`, `5:00 PM`, `6:00 PM`, `9:00 PM`, `11:59 PM`, `Custom…`), highlighting the selected time and updating the audition draft while preserving the chosen date.
    - Extended time selection to `AddMissingDateStep` (when notice did not state a due date) and `AddEditAsksStep` ("What they want" edit sheet), ensuring actors can customize audition deadline times anywhere in the flow.
    - Added `LocalTime.otTime` extension helper in `Models.kt` formatted as standard 12-hour AM/PM time.
    - Added unit tests in `AddAuditionNoticeParsingTest.kt` for `LocalTime.otTime` format verification and date/time component preservation.
- **Context-Aware Audition Review & Type-In Support:**
  - Fully nuked generic and false "found in the email" copy from audition creation.
  - Introduced `AddAuditionSource` enum (`Scan`, `PdfOrFile`, `Photo`, `Paste`, `Share`, `Type`) wired across all ingestion launchers.
  - Made title, subtitle, and asks headers dynamically context-aware based on the user's input method:
    - **Manual Typing (`Type`):** Title dynamically switches to `"Audition details"`, subtitle to `"Set up your role, deadline, and deliverables, then save."`, and section label from `"They asked for"` to `"What to prepare"`.
    - **Camera Scan (`Scan`):** Subtitle reflects scan origin (e.g. `"$items found from your scan. Tap any line to edit, then save."`).
    - **PDF / File (`PdfOrFile`):** Subtitle reflects document origin (e.g. `"$items found in the PDF. Tap any line to edit, then save."`).
    - **Photo (`Photo`):** Subtitle reflects image origin (e.g. `"$items found in the image. Tap any line to edit, then save."`).
    - **Pasted / Shared (`Paste` / `Share`):** Subtitle accurately references the notice (e.g. `"$items found in the notice. Tap any line to edit, then save."`).
  - Updated `AddEditAsksStep` ("What they want" / "What to prepare") and `AddMissingDateStep` ("One thing missing") to dynamically adapt titles, explanations, and button labels between typing ("Add a deliverable", "What to prepare") and scanning ("Add something they asked for", "They asked for").
  - Fixed sides detail label in review to only claim "Your lines found" if real sides were parsed, gracefully showing "Sides to record" or "Sides" for manually typed auditions.
  - Added unit test suite `testContextAwareReviewTitlesAndSubtitles` in `AddAuditionNoticeParsingTest.kt`.
- **Robust Hands-Free Voice Commands & Crowded-Environment Engine Overhaul:**
  - Resolved root cause of voice command unreliability ("dodgy" behavior in quiet rooms and complete failure in crowded rooms):
    - **Eliminated Destructive Reconnect Loop:** Previously, routine silence pauses and background no-matches (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`) destroyed the `SpeechRecognizer` instance, unbinding from system IPC and locking the mic out for up to a second every 2 seconds. Replaced with hot-loop non-destructive cancellation and instant 30ms re-listening.
    - **Active Partial Results (`onPartialResults`):** Enabled instantaneous command matching on partial transcript buffers, triggering the exact millisecond the user finishes saying "Action" or "Cut" without waiting for dead silence.
    - **Tolerant & Noise-Resilient Regexes:** Upgraded `RECORD_REGEX` and `STOP_REGEX` to support conversational director prefixes ("okay action", "alright cut", "let's record", "and cut") and trailing command suffixes, while adding trailing-tail evaluation for noisy rooms where ambient chatter precedes the command.
    - **Monologue Take Protection:** Maintained strict protection against accidental mid-take cuts during lines of dialogue, requiring cut commands to be standalone short commands, repetition ("cut cut"), or located at the tail/boundary of utterances.
    - **On-Device / Offline Recognition Preference:** Configured `SpeechRecognizer.createOnDeviceSpeechRecognizer` on Android 13+ (API 33+) with `EXTRA_PREFER_OFFLINE`, `EXTRA_MAX_RESULTS, 5`, and tight silence latency parameters for near-zero latency.
    - **Synchronized Across Flows:** Hardened `HeadshotVoiceCommander` ("say now", "now", "shoot", "cheese", "smile", "click", "snap") and `PrepSpeechTrigger` against the same destructive reconnect failure mode.
    - **Expanded Unit Test Suite:** Added `testMatchesCommandWithCrowdedRoomNoise` and `testHeadshotVoiceCommanderRegexes` in `ShootControlTest.kt`.
- **Comprehensive Editing Overhaul End-to-End (Takes & Video, Auditions, Scripts & Scenes, Practice Repertoire, Headshots):**
  - **Takes & Video Clip Editor (`TkTrimSheet`, `TakesScreens.kt`, `TakeTrimmer.kt`, `AppStore.kt`):**
    - Embedded live looping preview player inside `TkTrimSheet` constrained to `[range.start..range.endInclusive]`.
    - Added tenths-of-a-second clock timestamps (`0:01.4`) and `-0.1s`/`+0.1s` fine nudge buttons for both Start and End cut points.
    - Added "Save as new take" vs "Replace take" options via `store.saveTrimmedTake`.
    - Added take deletion (`store.deleteTake`) with confirmation dialog.
    - Hardened `TakeTrimmer.kt` with `MediaMetadataRetriever` rotation fallback and strictly monotonic B-frame timestamp adjustment.
    - Interactive 2-player `TkCompareView` with chip pickers to compare any two takes side by side.
    - Handled empty takes states with direct "Record take" action.
  - **Audition Detail & Editing (`AuditionDetailScreen.kt`, `AppStore.kt`):**
    - Added "Recorded takes" card directly in `AuditionDetailScreen` routing to `Route.Takes(a.id)`.
    - Upgraded `DetailEditSheet` with `projectType` selector, `roleSize` selector, minute-accurate due date and time, deliverables `asks` syncing with `pieces` (including `PieceKind.FullLength` and `PieceKind.Slate`), and audition status toggle ("Mark as Sent" / "Reopen").
  - **Persistent Script, Scene & Line Editing (`PrepareScreens.kt`, `AppStore.kt`):**
    - Replaced in-memory edits with persistent `AppStore` mutations: `updateScriptLine`, `addScriptLine`, `deleteScriptLine`, `updatePracticeLine`, `addPracticeLine`, `deletePracticeLine`.
    - Upgraded `PrepEditDialog` to allow editing line text, changing character, toggling `isMine` ("You" vs "Reader"), adding a line below, or deleting a line.
    - Added "Takes" capsule button in `PrepareScreen` for instant take inspection.
  - **Practice Repertoire Management (`PracticeScreens.kt`, `AppStore.kt`):**
    - Added `PracticePieceActionSheet` on tapping any repertoire piece, giving actors one-tap access to Run Lines, Prepare Script, Hands-Free Audio Walk, Record Practice Take, View Practice Takes, Edit Piece Details, or Delete Piece.
  - **Headshots Editing & 4:5 Crop Sheet (`ProfileScreens.kt`, `HeadshotFramingEngine.kt`, `AppStore.kt`):**
    - Upgraded all 3 headshot slots (Front, Left profile, Right profile) with `HeadshotOptionsSheet` supporting Take with Camera, Import from Photos (via Photo Picker), Adjust 4:5 Crop & Rotation, and Delete Headshot.
    - Added interactive `HeadshotCropSheet` with 4:5 portrait preview, pan gestures, zoom slider, 90° rotation, and saving via `HeadshotFramingEngine.processAndCropHeadshot`.
  - Added unit test suite `AppStoreEditingTest.kt` verifying all editing mutations and state guarantees.
- **Industry-Standard `.zip` Package Export & Delivery Architecture (`MediaExporter.kt`, `PackageScreen.kt`, `Platform.kt`, `TapeChecker.kt`):**
  - **`MediaExporter.exportZipPackage(...)`**:
    - Exports audition deliverables into a cleanly structured, production-ready `.zip` archive named according to casting requirements: `<LAST>_<FIRST>_<ROLE>_<PROJECT>.zip`.
    - Automatically builds and embeds `MANIFEST.txt` containing actor details (Name, Role, Project, Agency/Representation, Height, Location, Destination, Date), plus an itemized file manifest with uncompressed byte sizes.
    - Packages cleanly named individual media files: `..._SLATE.mp4`, `..._FULL_LENGTH.mp4`, `..._SCENE_1.mp4`, `..._SCENE_2.mp4`, and 4:5 headshots (`..._HEADSHOT_FRONT.jpg`, `..._HEADSHOT_LEFT.jpg`, `..._HEADSHOT_RIGHT.jpg`).
    - Stitches and embeds optional assembled full tape (`..._FULL_TAPE.mp4`) when 2+ video pieces are present.
    - Safely cleans up temporary intermediate concatenation files in `finally` blocks.
  - **`MediaExporter.inspectPackageManifest(...)`**:
    - Computes package deliverables, calculating entry names, media kinds, byte sizes, and availability prior to compression.
  - **`PackageScreen.kt` Overhaul**:
    - Replaced single-video export flow with dual delivery workflow:
      - **"Send .zip package"**: Automatically compiles `.zip` archive and launches Android system share sheet with MIME type `application/zip` and `ClipData` permissions.
      - **"Save .zip to device"**: Prompts file destination via `ActivityResultContracts.CreateDocument("application/zip")` and streams package bytes directly to local or cloud storage.
      - **"Preview full tape"**: Generates/plays combined `.mp4` deliverable without having to extract files.
    - Upgraded `PkgDeliverableDetailsSheet` with full itemized deliverable manifest, file sizes, and destination specifications.
    - Fixed UI freeze/ANR in `PkgAudioSheet`: Moved `TapeAnalysis.sound` decoding off the main composition thread to `Dispatchers.IO` via `produceState`.
    - Fixed false-green "Audio track present · clear" bug when `sound == null` in `PkgAudioSheet`; now accurately reports "No audio track detected" or "Silent track".
  - **`TapeChecker.kt` & `Platform.kt`**:
    - Supported `slateAtStart` ordering parameter in `TapeChecker.run`.
    - Added "Both profiles" deliverable check when casting asks for left & right profiles.
    - Updated `Platform.shareFile` and `Platform.viewFile` with full `application/zip` MIME support and `ClipData` content URI grant permissions for third-party apps (Drive, Gmail, WhatsApp).
  - **Unit Test Suite**:
    - Added `MediaExporterZipTest.kt` verifying `ZipFile` entries, `MANIFEST.txt` contents, file naming conventions, and non-zero media payloads.
    - Updated `PackageScreenTest.kt` with `deliverableZipFileName` assertions.
