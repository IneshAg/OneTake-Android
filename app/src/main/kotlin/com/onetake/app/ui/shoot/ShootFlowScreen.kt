package com.onetake.app.ui.shoot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.onetake.app.data.MediaStore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.PieceKind
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import com.onetake.app.ui.camera.CameraBackground
import com.onetake.app.ui.camera.CameraController
import com.onetake.app.ui.camera.LiveTapeCheckEngine
import com.onetake.app.ui.camera.RecordStartErrorDialog
import com.onetake.app.ui.camera.rememberCameraController
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.PlainButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.system.KeepScreenOn
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.rememberReaderPlayer
import com.onetake.app.ui.system.rememberTtsEngine
import com.onetake.app.ui.theme.DarkOT
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

// Camera flow: 15 check → 16 slate → 16b slate done → 16c full length → 17 scene → 17.5 take saved.
// Android port of iOS `ShootFlow`. Always dark; every control sits inside the system-bar insets.

private enum class ShootStage { Check, Slate, SlateDone, FullLength, Scene, Saved }

@Composable
fun ShootFlowScreen(auditionId: String, initialScene: Int? = null) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val camera = rememberCameraController()
    val reader = rememberReaderPlayer()
    val tts = rememberTtsEngine()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var stage by remember { mutableStateOf(ShootStage.Check) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var timerSeconds by remember { mutableIntStateOf(3) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var lineIndex by remember { mutableIntStateOf(0) }
    // The scene being shot: the one asked for, else the next one still to shoot. Takes, reader lines
    // and the checklist tick all follow it.
    var sceneIndex by remember { mutableIntStateOf(initialScene ?: store.sceneToShoot(auditionId)) }
    var takeNumber by remember { mutableIntStateOf(store.nextTakeNumber(auditionId, sceneIndex)) }
    var front by remember { mutableStateOf(true) }
    var exposureLocked by remember { mutableStateOf(false) }
    var lastTakeSeconds by remember { mutableIntStateOf(0) }
    var lastSlateSeconds by remember { mutableIntStateOf(0) }
    var liveTapeCheck by remember { mutableStateOf(LiveTapeCheckEngine.LiveTapeCheck()) }
    var audioLevelDb by remember { mutableFloatStateOf(-50f) }
    var checkTipMessage by remember { mutableStateOf<String?>(null) }

    val recording = camera.state == CameraController.State.Recording
    val audition = store.audition(auditionId)
    val scene = audition?.scenes?.getOrNull(sceneIndex)
    val lines = scene?.lines.orEmpty()

    KeepScreenOn()
    ShootLightSystemBars()

    var micPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micPermissionGranted = it
    }

    LaunchedEffect(Unit) {
        if (!micPermissionGranted) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val audioMeter = remember {
        LiveTapeCheckEngine.AudioMeter(
            context = context.applicationContext,
            scope = scope,
            onLevelUpdate = { db -> audioLevelDb = db },
        )
    }

    LaunchedEffect(stage, micPermissionGranted) {
        if (stage == ShootStage.Check && micPermissionGranted) {
            audioMeter.start()
        } else {
            audioMeter.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { audioMeter.stop() }
    }

    val liveAnalyzer = remember(front) {
        LiveTapeCheckEngine.Analyzer(isFrontCamera = front) { check ->
            liveTapeCheck = check
        }
    }

    LaunchedEffect(front) {
        liveAnalyzer.isFrontCamera = front
    }

    LaunchedEffect(audioLevelDb, micPermissionGranted) {
        liveAnalyzer.currentAudioDb = audioLevelDb
        liveAnalyzer.micPermissionGranted = micPermissionGranted
    }

    LaunchedEffect(stage, camera.hasPermission) {
        if (camera.hasPermission && (stage == ShootStage.Check || stage == ShootStage.Slate || stage == ShootStage.Scene)) {
            camera.setAnalyzer(liveAnalyzer)
        } else {
            camera.clearAnalyzer()
        }
    }

    DisposableEffect(Unit) {
        onDispose { camera.clearAnalyzer() }
    }

    // The file lands asynchronously (CameraX Finalize), possibly after this screen has closed, so
    // saving runs from the camera's own callback against app-scoped objects only. A piece is
    // ticked off only once its recording is actually on disk.
    val appContext = context.applicationContext
    fun couldNotSave() {
        Toast.makeText(appContext, "Couldn't save that recording.", Toast.LENGTH_SHORT).show()
    }
    fun saveSlate(uri: Uri?) {
        val saved = MediaStore.persist(appContext, uri, "slate.mp4", "audition-$auditionId") ?: return couldNotSave()
        store.setSlateVideo(auditionId, saved.toString())
        store.markDone(PieceKind.Slate, auditionId)
    }
    fun saveFullLength(uri: Uri?) {
        val saved = MediaStore.persist(appContext, uri, "fulllength.mp4", "audition-$auditionId") ?: return couldNotSave()
        store.setFullLengthVideo(auditionId, saved.toString())
        store.markDone(PieceKind.FullLength, auditionId)
    }
    fun saveTake(uri: Uri?, take: Take) {
        // The take's own scene, not the screen's current one: the file can land after the actor has
        // already moved on to the next scene.
        val shotScene = take.sceneIndex ?: 0
        val saved = MediaStore.persist(
            appContext, uri, "take-${take.number}-${take.id.take(8)}.mp4", "audition-$auditionId/scene-$shotScene",
        ) ?: return couldNotSave()
        store.addTake(take.copy(fileUri = saved.toString()))
        // Tick off the scene that was actually shot, not whichever scene piece is first undone.
        val a = store.audition(auditionId)
        val pieces = a?.pieces.orEmpty()
        val title = a?.scenes?.getOrNull(shotScene)?.title
            ?.takeIf { t -> pieces.any { it.kind == PieceKind.Scene && it.title == t } }
        store.markDone(PieceKind.Scene, auditionId, title)
    }

    // Reader: the actor's own recorded voice or TTS for the other character's lines, at the
    // Profile → Reader volume level so casting hears the actor first.
    LaunchedEffect(lineIndex, recording, stage) {
        reader.volume = store.readerVolumeLevel
        tts.volume = store.readerVolumeLevel
        val line = lines.getOrNull(lineIndex)
        runCatching {
            if (stage == ShootStage.Scene && recording && scene != null && line != null && !line.isMine) {
                // Reader files are numbered by reader line within the scene, not by overall line.
                val readerIndex = lines.take(lineIndex).count { !it.isMine }
                reader.playOrSynthesize(scene.id, readerIndex, line.text, tts)
            } else {
                reader.stop()
                tts.stop()
            }
        }
    }

    /** Cancels a running countdown. Cancelling the job (not just clearing the number) means a
     *  quick cancel + re-roll can't leave two countdowns running and starting two recordings. */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }

    fun roll() {
        if (countdown != null || recording) return
        if (timerSeconds == 0) {
            if (camera.startRecording()) haptics.impact() else haptics.warning()
            return
        }
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (n in timerSeconds downTo 1) {
                countdown = n
                haptics.tick()
                delay(1000)
            }
            countdown = null
            countdownJob = null
            if (camera.startRecording()) haptics.impact() else haptics.warning()
        }
    }

    fun cut() {
        cancelCountdown()
        val seconds = camera.elapsed.toInt()
        haptics.success()
        when (stage) {
            ShootStage.Slate -> {
                camera.stopRecording { saveSlate(it) }
                lastSlateSeconds = seconds
                stage = ShootStage.SlateDone
            }
            ShootStage.FullLength -> {
                camera.stopRecording { saveFullLength(it) }
                stage = ShootStage.Scene
            }
            ShootStage.Scene -> {
                lastTakeSeconds = seconds
                val take = Take(
                    number = takeNumber,
                    durationSeconds = seconds,
                    recordedAt = LocalDateTime.now(),
                    isCircled = false,
                    notes = emptyList(),
                    auditionId = auditionId,
                    sceneIndex = sceneIndex,
                )
                camera.stopRecording { saveTake(it, take) }
                stage = ShootStage.Saved
            }
            else -> camera.stopRecording()
        }
    }

    val voiceCommander = rememberShootVoiceCommander(
        onRecordTrigger = {
            if (voiceEnabled && !recording && countdown == null && (stage == ShootStage.Slate || stage == ShootStage.FullLength || stage == ShootStage.Scene)) {
                roll()
            }
        },
        onCutTrigger = {
            if (voiceEnabled && recording) {
                cut()
            }
        },
        onRmsChanged = { rms ->
            audioLevelDb = rms
        },
    )

    LaunchedEffect(voiceEnabled, micPermissionGranted, stage) {
        if (voiceEnabled && micPermissionGranted && (stage == ShootStage.Slate || stage == ShootStage.FullLength || stage == ShootStage.Scene)) {
            voiceCommander.start()
        } else {
            voiceCommander.stop()
        }
    }

    LaunchedEffect(recording) {
        voiceCommander.setRecordingMode(recording)
    }

    /** Scenes with a take already (saved, or the one just shot whose file is still landing). */
    val shotScenes = store.takes.filter { it.auditionId == auditionId }.mapNotNull { it.sceneIndex }.toSet() +
        (if (stage == ShootStage.Saved) setOf(sceneIndex) else emptySet())

    fun pickScene(i: Int) {
        if (recording || countdown != null) return
        reader.stop()
        tts.stop()
        sceneIndex = i
        lineIndex = 0
        takeNumber = store.nextTakeNumber(auditionId, i)
        haptics.tick()
    }

    /** The next scene after this one (wrapping round) with no take yet. */
    fun nextUnshotScene(): Int? {
        val count = audition?.scenes?.size ?: 0
        return (1 until count).map { (sceneIndex + it) % count }.firstOrNull { it !in shotScenes }
    }

    fun moveToScene(i: Int) {
        sceneIndex = i
        lineIndex = 0
        takeNumber = store.nextTakeNumber(auditionId, i)
        stage = ShootStage.Scene
    }

    // The scene piece is ticked off by saveTake once the take is really on disk — not here.
    fun finish(goTo: Route) {
        nav.closeCamera(then = goTo)
    }

    val time = camera.elapsed.toInt().let { "%02d:%02d:%02d".format(it / 3600, (it / 60) % 60, it % 60) }
    val pillText = when (stage) {
        ShootStage.Check -> "Camera check"
        ShootStage.Slate, ShootStage.SlateDone -> "Slate  $time"
        ShootStage.FullLength -> "Full length  $time"
        ShootStage.Scene, ShootStage.Saved -> "SC ${sceneIndex + 1} · TK $takeNumber  $time"
    }

    RecordStartErrorDialog(camera)
    Box(Modifier.fillMaxSize().background(OT.camera.bg)) {
        CameraBackground(camera, Modifier.fillMaxSize(), front = front)
        // Soft bottom scrim so chips and controls stay legible on bright frames.
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.25f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                )
            )
        )
        if (recording) {
            Box(Modifier.fillMaxSize().border(6.dp, OT.camera.recRed, RoundedCornerShape(40.dp)))
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircleIconButton(Icons.Filled.Close, "Close", onClick = { nav.closeCamera() }, onDark = true)
                Spacer(Modifier.weight(1f))

                // Voice trigger toggle & indicator
                if (stage == ShootStage.Slate || stage == ShootStage.FullLength || stage == ShootStage.Scene) {
                    Row(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(OT.camera.glass)
                            .clickable { voiceEnabled = !voiceEnabled }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (voiceEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = if (voiceEnabled) "Voice commands on" else "Voice commands off",
                            tint = if (voiceEnabled && voiceCommander.isListening) OT.colors.accent else if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (voiceEnabled) "Voice" else "Muted",
                            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                        )
                    }
                }

                // Countdown Timer selector
                if (!recording && (stage == ShootStage.Slate || stage == ShootStage.FullLength || stage == ShootStage.Scene)) {
                    Box {
                        Row(
                            Modifier
                                .heightIn(min = 32.dp)
                                .clip(CircleShape)
                                .background(OT.camera.glass)
                                .clickable { if (countdown == null) showTimerMenu = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                contentDescription = "Timer",
                                tint = if (timerSeconds > 0) OT.colors.accent else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                if (timerSeconds == 0) "None" else "${timerSeconds}s",
                                style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = showTimerMenu,
                            onDismissRequest = { showTimerMenu = false },
                            modifier = Modifier
                                .background(Color(0xF01A1A1E))
                                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(16.dp)),
                        ) {
                            val timerOptions = listOf(
                                0 to "None (instant)",
                                3 to "3s (default)",
                                5 to "5s",
                                10 to "10s",
                                15 to "15s",
                            )
                            timerOptions.forEach { (sec, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                label,
                                                style = OT.type.body,
                                                color = if (timerSeconds == sec) OT.colors.accent else Color.White,
                                            )
                                            if (timerSeconds == sec) {
                                                Spacer(Modifier.width(12.dp))
                                                Icon(Icons.Filled.Check, null, tint = OT.colors.accent, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        timerSeconds = sec
                                        showTimerMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Stage & Duration Pill
                Row(
                    Modifier.heightIn(min = 32.dp).clip(CircleShape).background(OT.camera.glass).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (recording) Box(Modifier.size(8.dp).clip(CircleShape).background(OT.camera.recRed))
                    Text(
                        pillText,
                        style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = if (recording) OT.camera.recRed else Color.White,
                        maxLines = 1,
                    )
                }

                if (recording) {
                    Row(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(OT.camera.glass)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = "Sound recording",
                            tint = if (liveTapeCheck.sound == LiveTapeCheckEngine.SoundStatus.Clipping) OT.colors.warning else OT.colors.success,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Middle: overlays + countdown. Takes the remaining space so controls below are never pushed off-screen.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (audition != null) {
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        when (stage) {
                            ShootStage.Check -> {
                                ShootCheckChips(
                                    check = liveTapeCheck,
                                    onSelectDetail = { detail ->
                                        checkTipMessage = when {
                                            "step into" in detail.lowercase() || "shift" in detail.lowercase() || "closer" in detail.lowercase() || "back" in detail.lowercase() || "framing" in detail.lowercase() ->
                                                "Framing: Sit or stand centered in frame with head and shoulders visible for a medium close-up."
                                            "eye" in detail.lowercase() || "camera" in detail.lowercase() ->
                                                "Eyeline: Place camera lens at eye level so your gaze looks straight ahead."
                                            "dark" in detail.lowercase() || "bright" in detail.lowercase() || "light" in detail.lowercase() ->
                                                "Lighting: Ensure soft, even front lighting on your face without bright backlights."
                                            else ->
                                                "Sound: Speak at normal performance level. Check microphone is unblocked."
                                        }
                                    },
                                )
                                if (checkTipMessage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .border(0.5.dp, OT.colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable { checkTipMessage = null }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Outlined.Info, null, tint = OT.colors.accent, modifier = Modifier.size(16.dp))
                                        Text(checkTipMessage!!, style = OT.type.footnote, color = Color.White, modifier = Modifier.weight(1f))
                                        Icon(Icons.Filled.Close, "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                ShootScenePicker(audition.scenes, sceneIndex, shotScenes, onPick = ::pickScene)
                            }
                            ShootStage.Slate -> {
                                if (!recording && countdown == null) {
                                    ShootMiniStatusRow(liveTapeCheck)
                                    Spacer(Modifier.height(6.dp))
                                }
                                ShootPromptCard(eyebrow = "Slate · say it to the lens · ≈ 5 s", text = store.slate.spoken)
                            }
                            ShootStage.FullLength -> {
                                if (!recording && countdown == null) {
                                    ShootMiniStatusRow(liveTapeCheck)
                                    Spacer(Modifier.height(6.dp))
                                }
                                ShootPromptCard(eyebrow = "After the slate", text = "Head to feet, hold still")
                            }
                            ShootStage.Scene -> {
                                if (!recording && countdown == null) {
                                    ShootMiniStatusRow(liveTapeCheck)
                                    Spacer(Modifier.height(6.dp))
                                    ShootScenePicker(audition.scenes, sceneIndex, shotScenes, onPick = ::pickScene)
                                    Spacer(Modifier.height(10.dp))
                                }
                                val line = lines.getOrNull(lineIndex)
                                if (line != null) {
                                    ShootScenePrompt(
                                        eyebrow = if (lineIndex == 0 && !recording) "Top of the scene" else null,
                                        character = if (line.isMine) "${line.character} · you" else line.character,
                                        text = line.text,
                                        mine = line.isMine,
                                        next = lines.getOrNull(lineIndex + 1)?.let { it.character to it.text },
                                        onTap = { lineIndex = minOf(lineIndex + 1, lines.lastIndex) },
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                    when (stage) {
                        ShootStage.SlateDone -> ShootResultCard(
                            modifier = Modifier.align(Alignment.Center),
                            eyebrow = null,
                            title = "Slate’s done.",
                            detail = "${formatDuration(lastSlateSeconds)} · opens every scene in ${audition.project}",
                            primary = "Next: full length" to { stage = ShootStage.FullLength },
                            secondary = "Redo" to { stage = ShootStage.Slate },
                            primaryFirst = true,
                        )
                        ShootStage.Saved -> {
                            val goAgain = "Go again" to {
                                takeNumber += 1
                                lineIndex = 0
                                stage = ShootStage.Scene
                            }
                            val next = nextUnshotScene()
                            if (next != null) {
                                ShootResultCard(
                                    modifier = Modifier.align(Alignment.Center),
                                    eyebrow = "Take saved",
                                    title = "Take $takeNumber, in the can.",
                                    detail = "${formatDuration(lastTakeSeconds)} · ${audition.scenes[next].title} is still to shoot",
                                    primary = "Next: ${audition.scenes[next].title}" to { moveToScene(next) },
                                    secondary = goAgain,
                                    extras = listOf(
                                        "Watch take $takeNumber" to { finish(Route.Takes(auditionId)) },
                                        "Done for now" to { finish(Route.AuditionDetail(auditionId)) },
                                    ),
                                )
                            } else {
                                ShootResultCard(
                                    modifier = Modifier.align(Alignment.Center),
                                    eyebrow = "Take saved",
                                    title = "Take $takeNumber, in the can.",
                                    detail = "${formatDuration(lastTakeSeconds)} · saved",
                                    primary = "Watch take $takeNumber" to { finish(Route.Takes(auditionId)) },
                                    secondary = goAgain,
                                    extras = listOf("Done for now" to { finish(Route.AuditionDetail(auditionId)) }),
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                countdown?.let { n ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { cancelCountdown() },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = n,
                            transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 1.3f)) togetherWith fadeOut(tween(200)) },
                            label = "countdown",
                        ) { value ->
                            Text("$value", fontSize = 160.sp, lineHeight = 170.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Hint above the HUD (Figma 16.2p / 17p)
            val hint: Pair<ImageVector, String>? = when {
                countdown != null -> Icons.Outlined.Timer to "Tap screen to cancel countdown"
                recording && voiceEnabled -> Icons.Filled.Mic to "Say \"Cut\" · or tap stop button"
                !recording && voiceEnabled && (stage == ShootStage.Slate || stage == ShootStage.Scene) ->
                    Icons.Filled.Mic to "Say \"Record\" or \"Action\" from your mark"
                stage == ShootStage.Slate && recording -> Icons.Filled.GraphicEq to "Say it, then pause"
                stage == ShootStage.Scene && !recording ->
                    Icons.Filled.Visibility to "Lines sit right under the lens, so your eyeline stays up"
                else -> null
            }
            if (hint != null) {
                ShootChip(hint.first, hint.second, Modifier.align(Alignment.CenterHorizontally), big = true)
                Spacer(Modifier.height(8.dp))
            }

            // HUD
            if (stage != ShootStage.Saved && stage != ShootStage.SlateDone) {
                val minutes = remember { (context.filesDir.usableSpace / (130L * 1024 * 1024)).coerceAtLeast(0) }
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (exposureLocked) ShootChip(Icons.Filled.Lock, "Exposure locked", tint = DarkOT.warning)
                    ShootChip(Icons.Filled.Storage, "$minutes min")
                }
            }
            Spacer(Modifier.height(12.dp))

            // Bottom controls — fixed height so nothing jumps between stages.
            Row(
                Modifier.fillMaxWidth().height(104.dp).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (stage) {
                        ShootStage.Check -> PrimaryButton(
                            "Go to your mark",
                            onClick = {
                                stage = ShootStage.Slate
                                exposureLocked = true
                                haptics.impact()
                            },
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                        ShootStage.SlateDone, ShootStage.Saved -> Unit
                        else -> {
                            ShootRecordButton(isRecording = recording) {
                                when {
                                    countdown != null -> cancelCountdown()
                                    recording -> cut()
                                    else -> roll()
                                }
                            }
                        }
                    }
                }
                CircleIconButton(
                    Icons.Filled.Cameraswitch,
                    "Switch camera",
                    onClick = { if (!recording && countdown == null) front = !front },
                    onDark = true,
                )
            }
        }
    }
}

/** Camera screens are always dark: force light status/nav bar icons while the flow is up. */
@Composable
private fun ShootLightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findShootActivity()?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = ctl?.isAppearanceLightStatusBars
        val prevNav = ctl?.isAppearanceLightNavigationBars
        ctl?.isAppearanceLightStatusBars = false
        ctl?.isAppearanceLightNavigationBars = false
        onDispose {
            if (ctl != null && prevStatus != null) ctl.isAppearanceLightStatusBars = prevStatus
            if (ctl != null && prevNav != null) ctl.isAppearanceLightNavigationBars = prevNav
        }
    }
}

private tailrec fun Context.findShootActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findShootActivity()
    else -> null
}

// MARK: - Voice Commander (Hands-free STT for "Record" / "Action" & "Cut" / "Stop")

internal class ShootVoiceCommander(
    private val context: Context,
    private val onRecordTrigger: () -> Unit,
    private val onCutTrigger: () -> Unit,
    private val onRmsChanged: ((Float) -> Unit)? = null,
) {
    val available: Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    var isListening by mutableStateOf(false)
        private set
    var lastHeard by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var isRecordingMode = false
    private var consecutiveFatalErrors = 0
    private var lastTriggerTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val relisten = Runnable { if (wanted) listen() }

    companion object {
        val RECORD_REGEX = Regex(
            """(?i)^\s*(?:(?:and|okay|ok|alright|ready|go(?:\s+for)?|let's|lets|we're|hey)\s+)?(?:record|action|roll\s*camera|camera\s*roll|rolling|camera\s*speed|speed|start(?:\s+recording)?)(?:\s+(?:now|please|camera|mark))?[\s.!]*$"""
        )
        val STOP_REGEX = Regex(
            """(?i)^\s*(?:(?:and|okay|ok|alright|yeah|scene|please)\s+)?(?:cut(?:\s+(?:cut|it|now|please|take|there|that))?|stop(?:\s+recording|\s+take)?|that'?s\s+a\s+wrap|wrap(?:\s+it)?)(?:\s+(?:now|please|mark|take|cut))?[\s.!]*$"""
        )

        fun matchesCommand(text: String, isRecording: Boolean): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            val regex = if (isRecording) STOP_REGEX else RECORD_REGEX

            if (regex.matches(trimmed) || regex.containsMatchIn(trimmed)) return true

            // Check tail words for noise-prefixed utterances in noisy/crowded rooms
            val words = trimmed.split(Regex("""\s+"""))
            if (words.size > 1) {
                val tail1 = words.takeLast(1).joinToString(" ")
                val tail2 = words.takeLast(2).joinToString(" ")
                val tail3 = words.takeLast(3).joinToString(" ")
                if (regex.matches(tail1) || regex.matches(tail2) || regex.matches(tail3)) return true
            }
            return false
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            consecutiveFatalErrors = 0
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            isListening = false
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                wanted = false
                return
            }
            handler.removeCallbacks(relisten)
            if (!wanted) return

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence/speech boundary — keep recognizer alive and restart immediately
                    consecutiveFatalErrors = 0
                    runCatching { recognizer?.cancel() }
                    handler.postDelayed(relisten, 30L)
                }
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    consecutiveFatalErrors = 0
                    runCatching { recognizer?.cancel() }
                    handler.postDelayed(relisten, 80L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    runCatching { recognizer?.cancel() }
                    handler.postDelayed(relisten, 150L)
                }
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER -> {
                    consecutiveFatalErrors++
                    runCatching {
                        recognizer?.cancel()
                        recognizer?.destroy()
                    }
                    recognizer = null
                    val backoff = (150L * consecutiveFatalErrors).coerceAtMost(1000L)
                    handler.postDelayed(relisten, backoff)
                }
                else -> {
                    runCatching { recognizer?.cancel() }
                    handler.postDelayed(relisten, 100L)
                }
            }
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            evaluate(results)
            handler.removeCallbacks(relisten)
            if (wanted) handler.postDelayed(relisten, 30L)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            evaluate(partialResults)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun evaluate(bundle: Bundle?): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < 1500L) return false

        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (matches.isEmpty()) return false
        lastHeard = matches.firstOrNull()

        for (text in matches) {
            if (matchesCommand(text, isRecordingMode)) {
                lastTriggerTime = now
                runCatching { recognizer?.cancel() }
                if (!isRecordingMode) {
                    onRecordTrigger()
                } else {
                    onCutTrigger()
                }
                return true
            }
        }
        return false
    }

    fun setRecordingMode(recording: Boolean) {
        if (isRecordingMode != recording) {
            isRecordingMode = recording
            lastHeard = null
            if (wanted) {
                handler.removeCallbacks(relisten)
                runCatching { recognizer?.cancel() }
                handler.postDelayed(relisten, 40L)
            }
        }
    }

    fun start() {
        if (!available) return
        wanted = true
        consecutiveFatalErrors = 0
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.cancel() }
        listen()
    }

    private fun listen() {
        if (!wanted) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        val r = recognizer ?: run {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            rec?.also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        } ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100L)
        }
        runCatching {
            r.startListening(intent)
            isListening = true
        }.onFailure {
            isListening = false
            if (wanted) handler.postDelayed(relisten, 250L)
        }
    }

    fun stop() {
        wanted = false
        isListening = false
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        wanted = false
        isListening = false
        handler.removeCallbacks(relisten)
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }
}

@Composable
private fun rememberShootVoiceCommander(
    onRecordTrigger: () -> Unit,
    onCutTrigger: () -> Unit,
    onRmsChanged: (Float) -> Unit = {},
): ShootVoiceCommander {
    val context = LocalContext.current
    // The commander outlives recompositions, so route through the latest lambdas — otherwise it
    // keeps the first composition's closures, where `recording` was captured as false forever.
    val latestRecord by rememberUpdatedState(onRecordTrigger)
    val latestCut by rememberUpdatedState(onCutTrigger)
    val latestRms by rememberUpdatedState(onRmsChanged)
    val commander = remember {
        ShootVoiceCommander(
            context = context.applicationContext,
            onRecordTrigger = { latestRecord() },
            onCutTrigger = { latestCut() },
            onRmsChanged = { latestRms(it) },
        )
    }
    DisposableEffect(commander) {
        onDispose { commander.destroy() }
    }
    return commander
}

// MARK: - Record button (v2/Record Button · Ready / Tap to stop)

/** Tap to start recording. Tap again immediately stops recording. */
@Composable
private fun ShootRecordButton(isRecording: Boolean, onAction: () -> Unit) {
    val action by rememberUpdatedState(onAction)
    val spec = spring<Dp>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    val inner by animateDpAsState(if (isRecording) 32.dp else 64.dp, spec, label = "recSize")
    val corner by animateDpAsState(if (isRecording) 8.dp else 32.dp, spec, label = "recCorner")
    val red = OT.camera.recRed
    val label = if (isRecording) "Stop recording" else "Record"

    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .clickable(onClick = action)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(78.dp)) {
            val stroke = 4.dp.toPx()
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White, 0f, 360f, false, tl, sz, style = Stroke(stroke))
        }
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(red))
    }
}

// MARK: - Pieces

@Composable
private fun ShootChip(
    icon: ImageVector?,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    big: Boolean = false,
) {
    Row(
        modifier
            .heightIn(min = if (big) 32.dp else 26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = if (big) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(if (big) 16.dp else 12.dp))
        Text(
            text,
            style = (if (big) OT.type.footnote else OT.type.caption).copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShootCheckChips(
    check: LiveTapeCheckEngine.LiveTapeCheck,
    onSelectDetail: (String) -> Unit = {},
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShootLiveChip(
            icon = if (check.isFramingPassed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            text = check.framingLabel,
            tint = if (check.isFramingPassed) OT.colors.success else OT.colors.warning,
            onClick = { onSelectDetail(check.framingLabel) },
        )
        ShootLiveChip(
            icon = if (check.isEyelinePassed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            text = check.eyelineLabel,
            tint = if (check.isEyelinePassed) OT.colors.success else OT.colors.warning,
            onClick = { onSelectDetail(check.eyelineLabel) },
        )
        ShootLiveChip(
            icon = if (check.isLightPassed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            text = check.lightLabel,
            tint = if (check.isLightPassed) OT.colors.success else OT.colors.warning,
            onClick = { onSelectDetail(check.lightLabel) },
        )
        ShootLiveChip(
            icon = if (check.isSoundPassed) Icons.Filled.GraphicEq else Icons.Filled.Warning,
            text = check.soundLabel,
            tint = if (check.isSoundPassed) OT.colors.success else OT.colors.warning,
            onClick = { onSelectDetail(check.soundLabel) },
        )
    }
}

@Composable
private fun ShootLiveChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(0.5.dp, tint.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShootMiniStatusRow(check: LiveTapeCheckEngine.LiveTapeCheck) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShootMiniStatusBadge(
            icon = if (check.isFramingPassed) Icons.Filled.Check else Icons.Filled.Warning,
            label = if (check.isFramingPassed) "Framing OK" else check.framingLabel,
            passed = check.isFramingPassed,
        )
        ShootMiniStatusBadge(
            icon = if (check.isLightPassed) Icons.Filled.Check else Icons.Filled.Warning,
            label = if (check.isLightPassed) "Light OK" else check.lightLabel,
            passed = check.isLightPassed,
        )
        ShootMiniStatusBadge(
            icon = if (check.isSoundPassed) Icons.Filled.GraphicEq else Icons.Filled.Warning,
            label = if (check.isSoundPassed) "Mic OK" else check.soundLabel,
            passed = check.isSoundPassed,
        )
    }
}

@Composable
private fun ShootMiniStatusBadge(
    icon: ImageVector,
    label: String,
    passed: Boolean,
) {
    val tint = if (passed) OT.colors.success else OT.colors.warning
    Row(
        Modifier
            .heightIn(min = 24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(0.5.dp, tint.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(11.dp))
        Text(
            label,
            style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShootPromptCard(eyebrow: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(eyebrow.uppercase(), style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = OT.camera.label2)
        Text(text, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 17 — current line big under the lens, the next line dimmed. Tap to advance. */
@Composable
private fun ShootScenePrompt(
    eyebrow: String?,
    character: String,
    text: String,
    mine: Boolean,
    next: Pair<String, String>?,
    onTap: () -> Unit,
) {
    val readerColor = DarkOT.reader
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClickLabel = "Next line", role = Role.Button, onClick = onTap)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val eyebrowStyle = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
        if (eyebrow != null) Text(eyebrow.uppercase(), style = eyebrowStyle, color = OT.camera.label2)
        Text(character.uppercase(), style = eyebrowStyle, color = if (mine) OT.camera.label2 else readerColor)
        Text(
            text,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = if (mine) Color.White else Color.White.copy(alpha = 0.7f),
        )
        if (next != null) {
            Spacer(Modifier.height(4.dp))
            Text(next.first.uppercase(), style = eyebrowStyle, color = Color.White.copy(alpha = 0.4f))
            Text(next.second, style = OT.type.footnote, color = Color.White.copy(alpha = 0.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShootResultCard(
    modifier: Modifier,
    eyebrow: String?,
    title: String,
    detail: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    extras: List<Pair<String, () -> Unit>> = emptyList(),
    checks: List<String> = emptyList(),
    primaryFirst: Boolean = false,
) {
    val c = OT.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceCard)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = OT.type.eyebrow, color = c.success)
        }
        Text(title, style = OT.type.title.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.labelPrimary)
        Text(detail, style = OT.type.footnote, color = c.labelTertiary)
        if (checks.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                checks.forEach { item ->
                    Row(
                        Modifier.clip(CircleShape).background(c.accentSoft).padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(12.dp))
                        Text(item, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (primaryFirst) {
                PrimaryButton(primary.first, onClick = primary.second, modifier = Modifier.weight(1.5f))
                SecondaryButton(secondary.first, onClick = secondary.second, modifier = Modifier.weight(1f))
            } else {
                SecondaryButton(secondary.first, onClick = secondary.second, modifier = Modifier.weight(1f))
                PrimaryButton(primary.first, onClick = primary.second, modifier = Modifier.weight(1f))
            }
        }
        extras.forEach { (label, action) ->
            PlainButton(
                label,
                onClick = action,
                color = c.accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Scene chips — only when the sides have more than one scene. A tick marks scenes with a take. */
@Composable
private fun ShootScenePicker(
    scenes: List<com.onetake.app.models.ScriptScene>,
    selected: Int,
    shot: Set<Int>,
    onPick: (Int) -> Unit,
) {
    if (scenes.size < 2) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        scenes.forEachIndexed { i, scene ->
            val on = i == selected
            Row(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (on) DarkOT.accent else Color.Black.copy(alpha = 0.55f))
                    .clickable(role = Role.Tab, onClickLabel = "Shoot ${scene.title}") { onPick(i) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (i in shot) Icon(Icons.Filled.Check, null, tint = if (on) DarkOT.labelOnAccent else Color.White, modifier = Modifier.size(14.dp))
                Text(
                    scene.title,
                    style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) DarkOT.labelOnAccent else Color.White,
                )
            }
        }
    }
}
