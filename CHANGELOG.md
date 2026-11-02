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
- **Search, Widget, Notification Deep-Linking & R8 Shrinking Hardening (`SearchScreen.kt`, `ScriptSearchEngine.kt`, `DeadlineReminders.kt`, `MainActivity.kt`, `NextAuditionWidget.kt`, `HeroStageCard.kt`, `RuntimeShaderCache.kt`, `proguard-rules.pro`):**
  - **Search Engine Scope & Highlight Spans**:
    - Fully wired `SearchScope.CUES` to isolate reader lines and partner cue dialogue.
    - Fully wired `SearchScope.CHARACTERS` to isolate character and audition role matches.
    - Added dynamic text span highlighting using `buildAnnotatedString` and `SpanStyle` to visually illuminate query matches in dialogue results.
    - Added `READER CUE` visual pill badges to clarify line ownership directly in search results.
    - Added unit test suite in `ScriptSearchEngineTest.kt` covering `SearchScope.CUES` and `SearchScope.CHARACTERS`.
  - **Notification Deep-Linking**:
    - Attached `"com.onetake.app.OPEN_AUDITION"` deep link intent to local deadline reminder notifications in `DeadlineReminders.kt`.
    - Handled notification intents in `MainActivity.kt` to immediately open the target audition on tap.
  - **Overdue Handling across Home Screen & Studio**:
    - Added `NextUp.isOverdue` and updated `NextAuditionWidget` to display overdue auditions with high-visibility urgent red tinting and `"overdue · was due <time>"` copy.
    - Upgraded `HeroStageCard` to show `"OVERDUE · ACTION REQUIRED"` in urgent red when an unsent audition passes its deadline.
    - Updated `AuditionsScreen`'s `DUE_THIS_WEEK` filter to check whole calendar day offsets (`0..7`), keeping same-day auditions accessible regardless of the current time.
  - **Compatibility & Proguard / R8 Shrinking**:
    - Guarded `RuntimeShader` with `NoOpRuntimeShader` on API < 33, preventing `NoClassDefFoundError` crashes on Android 12 and earlier.
    - Added Proguard keep rules for `com.onetake.app.models.**` enums (`valueOf`, `values`), Glance AppWidgets, and ML Kit Vision in `proguard-rules.pro` to ensure release APKs shrink safely without serialization failures.
- **Lighting Analysis & Voice Command Architecture Overhaul (`LiveTapeCheckEngine.kt`, `HeadshotFramingEngine.kt`, `Camera.kt`, `VoiceCommandEngine.kt`, `ShootFlowScreen.kt`, `ProfileScreens.kt`):**
  - **Live Light Check in Headshots, Slate, and Scenes**:
    - **Stride-Aware Y-Plane & Region Luminance**: Implemented `calculateRegionLuminance` in `LiveTapeCheckEngine` and `HeadshotFramingEngine` respecting buffer `rowStride` and `pixelStride`, eliminating corrupted readings from row padding bytes.
    - **Face Region & Backlighting Detection**: Light evaluation now samples the primary actor face bounding box (or central actor staging region) against whole-frame scene background luminance. Detects backlighting/silhouettes (`bgLuma > 195 && faceLuma < 75` -> "Backlit — face in shadow") and provides accurate guidance ("Too dark", "Too bright", "Light balanced" / "soft and even").
    - **Camera Multi-Stream Preview Fallback (`Camera.kt`)**: Added a continuous preview bitmap fallback in `CameraController.tick()` that delivers live frames to analyzers when hardware stream constraints prevent binding 3 simultaneous camera streams (`Preview + VideoCapture + ImageAnalysis`) on constrained front cameras. Guarantees lighting and framing checks never freeze in Slate, Scenes, or Headshots.
    - **Headshot Guidance Continuity (`ProfileScreens.kt`)**: Initialized guidance state on mount and updated chips continuously so the light chip never freezes on `"measuring…"`.
  - **Voice Commands in Slate and Scenes**:
    - **Audio HAL Release Arbitration (`ShootFlowScreen.kt`)**: Added an explicit 180ms delay after `audioMeter.stopAndJoin()` before calling `voiceCommander.start()` when transitioning out of the Check stage, eliminating `SpeechRecognizer.ERROR_AUDIO` race conditions during physical microphone hardware teardown.
    - **Noise-Resilient Keywords & Phrase Expansion (`VoiceCommandEngine.kt`)**: Added common director and shorthand commands:
      - Start: `"roll"`, `"let's roll"`, `"lets roll"`, `"we're rolling"`, `"go"`, `"let's go"`, `"lets go"`, `"okay go"`, `"start slate"`, `"slate now"`, `"slate please"`.
      - Cut: `"cut"`, `"stop"`, `"wrap"`, `"that's a wrap"`, `"that's wrap"`, `"it's a wrap"`, `"please stop"`, `"okay stop"`, `"stop now"`, `"stop it"`, `"done"`, `"finished"`, `"end take"`, `"all done"`.
      - Shorthand bare matching: Allowed `"roll"`, `"go"`, `"start"`, `"stop"` with natural leading cues (`"okay"`, `"please"`, `"let's"`).
    - **Responsive SpeechRecognizer Latency & Locale Fallback (`VoiceCommandEngine.kt`)**:
      - Reduced `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` to `50L` (removing the artificial 3-second delay on 1-word commands like "Action!" and "Cut!").
      - Tuned silence timeouts (`COMPLETE_SILENCE` = `1200L`, `POSSIBLY_COMPLETE` = `800L`) for instant response.
      - Forced `"en-US"` for on-device recognition to prevent crashes on non-US Android locales.
    - **Token Unmasking (`VoiceSessionTracker`)**: Fixed `resetForModeChange` to reset `ignoredPrefixTokenCount = 0` when transitioning stages with clean standby buffers, ensuring the first spoken word is never discarded.
    - **Viewfinder HUD & Status**: Wired live voice listening indicator into `ShootMiniStatusRow` ("Say 'Action'" / "Say 'Cut'") and added a real-time listening pill in the viewfinder controls.
  - **Unit Tests**:
    - Added tests in `LiveTapeCheckEngineTest.kt` for backlighting detection and row-stride region luminance.
    - Added tests in `VoiceCommandEngineTest.kt` for director shorthand commands ("Roll", "Let's go", "Done", "Please stop", "That's wrap").

---

### [Release: `v0.2.0`] — November 2026
- **Version Bump:** `0.1.0` -> `0.2.0` (`versionCode = 200`).
- **Take Trimming & Preview Playback Fixes (`TakesScreens.kt`, `TakeTrimmer.kt`, `TakesReviewTest.kt`):**
  - **Eliminated 0.1s Preview Playback Keyframe Seek Loop**:
    - **Root Cause**: Identified that `evaluateTrimLoopAction` was returning `SEEK_TO_START` whenever `currentPos < startMs`. Because Android `MediaPlayer` / `VideoView` seeks to keyframes (sync frames) which frequently sit prior to `startMs`, `currentPos` during forward playback between keyframe and `startMs` was repeatedly evaluated as `< startMs`. This re-triggered `seekTo(startMs)` every ~400ms as soon as the grace period expired, trapping playback in an infinite 0.1s stutter loop.
    - **Loop Evaluation Overhaul**: Removed `currentPos < startMs` from in-flight loop checks. Playback moves forward and is now permitted to play smoothly from keyframe through `startMs` to `endMs`. Only reaching or exceeding `endMs` (`currentPos >= endMs`) triggers loop reset back to `startMs`.
    - **Condition-Based Seek Tracking**: Replaced blind timer guessing with condition-based seek state tracking (`isSeeking` flag, `setOnSeekCompleteListener`, and boundary transition validation). While `isSeeking` is active, duplicate seeks are suppressed until the player has transitioned away from `endMs` (or a safety timeout elapses).
    - **Accurate Frame Seeking (`MediaPlayer.SEEK_CLOSEST`)**: Exposed the underlying `MediaPlayer` in `TkPlayerState` and upgraded seeking on API 26+ to use `mp.seekTo(targetMs, MediaPlayer.SEEK_CLOSEST)` with fallback to `VideoView.seekTo(int)`. This delivers exact frame accuracy rather than coarse multi-second keyframe jumps.
    - **Dual-Handle Range Slider Preview**: Fixed `RangeSlider` `onValueChange` to differentiate between start-handle and end-handle drags, seeking to `clamped.start` when the walk-in point is dragged and `clamped.endInclusive` when the walk-out point is dragged.
    - **EOF Loop Handling**: Added `onCompletion` listener on `TkPlayerCard` in `TkTrimSheet` to restart playback at `range.start` when the clip reaches EOF.
  - **Trim Range Clamping**: Added `clampTrimRange` enforcing a minimum 500ms duration gap on `RangeSlider` and nudge buttons, preventing the trim range from collapsing into zero or sub-second micro-loops.
  - **Single-File Trim Preview Looping**: Disabled `mp.isLooping` in `TkPlayerCard` for the trim sheet so the underlying `MediaPlayer` does not jump back to 0 on file end, allowing the custom trim boundary loop to handle looping cleanly.
  - **Interleaved Media Muxing**: Refactored `TakeTrimmer.kt` to extract and mux audio and video tracks simultaneously in chronological presentation order (`extractor.sampleTrackIndex`), eliminating out-of-order timestamps and non-interleaved MP4 chunk write corruption in `MediaMuxer`.
  - **Comprehensive Regression Tests**: Added tests in `TakesReviewTest.kt` testing that keyframes before `startMs` do not trigger seeks, `endMs` triggers loop seeks, `isSeeking` suppresses duplicate triggers, and stalled players recover after timeout.
- **Automated Release Pipeline (`.github/workflows/release.yml`, `scripts/release.sh`):**
  - Upgraded GitHub Actions `release.yml` with manual and automated dispatch (`workflow_dispatch` + push tags `v*`).
  - Automatically builds production-ready R8-shrunk APKs, Play Store AAB bundles, and computes cryptographic verification checksums (`SHA256SUMS.txt`).
  - Added standalone local automated release script `scripts/release.sh` supporting one-command test verification, multi-variant compilation, and git tagging.
  - Added `dist/` to `.gitignore` to keep release build outputs isolated from source control.
- **Audition Setup Workflow: Full-Screen Page Migration (`NewAuditionScreen.kt`, `AddAuditionSheet.kt`, `Navigation.kt`, `OneTakeApp.kt`, `AppStore.kt`):**
  - **Decoupled Capture Sheet from Multi-Step Form**: Converted `AddAuditionSheet` into a lightweight capture-only bottom sheet hosting only `AddCaptureStep` (Paste, Photo, Scan, File, Type).
  - **Dedicated Full-Screen Route (`Route.NewAudition`)**: Transitioned all post-capture workflow steps (`Reading`, `MissingDate`, `Review`, `EditAsks`, `CouldNotRead`) into a dedicated pushed `NavHost` page (`NewAuditionScreen.kt`).
  - **Gesture Dismissal & Data-Loss Protection**: Eliminated `ModalBottomSheet` vertical gesture drag-to-dismiss threshold during review and editing. Swiping or flicking downward now smoothly scrolls form fields without dismissing progress.
  - **Discard Confirmation Protection**: Implemented `BackHandler` and back navigation confirmation dialog (`AlertDialog`: "Discard audition? Any details you've entered will be lost.") preventing accidental back exits when user has entered content.
  - **Direct Incoming Share Routing**: Incoming Android shares (`ACTION_SEND`) now route directly to `Route.NewAudition` as a full page without showing intermediate capture sheets.
- **3-Tier Hybrid AI "Reading the Notice" Pipeline (`ModelDownloadManager.kt`, `HybridNoticeExtractor.kt`, `NoticeParser.kt`, `AddAuditionSheet.kt`, `NewAuditionScreen.kt`, `AppStore.kt`):**
  - **Tier 1 (System AICore)**: Integrates `com.google.mlkit:genai-prompt:1.0.0-beta5` querying `Generation.getClient().checkStatus()`. When on-device system AICore is available, zero download is required.
  - **Tier 2 (LiteRT-LM On-Device SLM Engine)**:
    - Integrates `com.google.ai.edge.litertlm:litertlm-android:latest.release`.
    - Resumable chunked OkHttp auto-downloader (`ModelDownloadManager.kt`) streaming models directly from Hugging Face `litert-community` into `noBackupFilesDir/models/` with HTTP 206 `Range` header support and throttled 250ms progress reporting.
    - Dynamic hardware model selection: selects **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`) when RAM >= 7.0 GB and available storage >= 3.2 GB; automatically selects **Qwen 3 0.6B** (`Qwen3-0.6B.litertlm`) for mid-range and storage-constrained hardware.
    - Background download support via WorkManager (`ModelDownloadWorker`).
    - GPU execution with automatic CPU fallback, thinking token budget bypass (`ThinkingConfig(enableThinking = false)`), and ColorOS 17 / Android 17 GPU eviction recovery.
  - **Tier 3 (Hardened Deterministic Baseline)**:
    - Hardened `NoticeParser.parse` executing in ~0.2ms addressing all Section 7 casting notice edge cases:
      - 7.1: Email subject prefix stripping (`Fwd:`, `Re:`, `[EXTERNAL]`), agency headers blacklist (`CAA`, `UTA`, `WME`, `ECO-CAST`, `AUDITION ALERT`), and quoted working title detection.
      - 7.2: Role extraction prioritizing character roles over actor names; multi-role breakdowns discovered and indexed into `allAvailableRoles`.
      - 7.3: Due date ranking giving precedence to self-tape/submission deadlines over shoot/callback dates; December-to-January year rollover handling; and `COB`, `EOD`, and military time (`1700 hrs`) support.
      - 7.4: Deliverable asks isolated strictly to the notice header partition prior to screenplay sides.
    - `parseSmart`: Runs deterministic 0.2ms baseline first, queries `HybridNoticeExtractor` under a 3-second timeout, normalizes OCR character typos, aligns screenplay sides anchors, and merges LLM metadata.
  - **UI & Lifecycle Integration**:
    - Asynchronous non-blocking background `warmUp()` on `Dispatchers.IO` in `AppStore.kt`.
    - Sleek non-blocking `ModelDownloadStatusPill` in `AddAuditionSheet` and `NewAuditionScreen` observing `ModelDownloadManager.status`.
    - Multi-role horizontal `FilterChip` switcher in `AddReviewStep` when `allAvailableRoles.size > 1` dynamically re-attributing scenes with `isMine = true`.
  - **Verification & Testing**:
    - Preserved `OcrManager.kt`, `OcrLayoutReconstructor.kt`, and `ScriptParser.kt` with **0 diff** and 100% public API compatibility.
    - Full test suite passing **271/271 unit tests (100% green)**.
    - Both `./gradlew assembleDebug` and `./gradlew assembleRelease` (through full R8 minification) compile cleanly.
