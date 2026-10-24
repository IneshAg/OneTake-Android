package com.onetake.app.ui.shoot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

// Camera flow: 15 check → 16 slate → 16b slate done → 16c full length → 17 scene → 17.5 take saved.
// Android port of iOS `ShootFlow`. Always dark; every control sits inside the system-bar insets.

private enum class ShootStage { Check, Slate, SlateDone, FullLength, Scene, Saved }

@Composable
fun ShootFlowScreen(auditionId: String) {
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
    var timerSeconds by remember { mutableIntStateOf(3) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var lineIndex by remember { mutableIntStateOf(0) }
    var takeNumber by remember { mutableIntStateOf((store.takes.maxOfOrNull { it.number } ?: 0) + 1) }
    var front by remember { mutableStateOf(true) }
    var exposureLocked by remember { mutableStateOf(false) }
    var lastTakeSeconds by remember { mutableIntStateOf(0) }
    var pendingTakeId by remember { mutableStateOf<String?>(null) }

    val recording = camera.state == CameraController.State.Recording
    val audition = store.audition(auditionId)
    val lines = audition?.scenes?.firstOrNull()?.lines.orEmpty()

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

    // The take's file lands asynchronously (CameraX Finalize) — attach it to the saved take.
    LaunchedEffect(camera.lastRecording) {
        val uri = camera.lastRecording ?: return@LaunchedEffect
        val id = pendingTakeId ?: return@LaunchedEffect
        store.takes = store.takes.map { if (it.id == id) it.copy(fileUri = uri.toString()) else it }
        pendingTakeId = null
    }

    // Reader: the actor's own recorded voice or TTS for the other character's lines.
    LaunchedEffect(lineIndex, recording, stage) {
        val line = lines.getOrNull(lineIndex)
        runCatching {
            if (stage == ShootStage.Scene && recording && line != null && !line.isMine) {
                reader.playOrSynthesize(lineIndex, line.text, tts)
            } else {
                reader.stop()
                tts.stop()
            }
        }
    }

    fun roll() {
        if (countdown != null || recording) return
        if (timerSeconds == 0) {
            countdown = null
            camera.startRecording()
            haptics.impact()
            return
        }
        scope.launch {
            for (n in timerSeconds downTo 1) {
                if (countdown == null && n < timerSeconds) return@launch // Cancelled
                countdown = n
                haptics.tick()
                delay(1000)
            }
            if (countdown != null) {
                countdown = null
                camera.startRecording()
                haptics.impact()
            }
        }
    }

    fun cut() {
        countdown = null
        val seconds = camera.elapsed.toInt()
        camera.stopRecording()
        haptics.success()
        when (stage) {
            ShootStage.Slate -> stage = ShootStage.SlateDone
            ShootStage.FullLength -> {
                store.markDone(PieceKind.FullLength, auditionId)
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
                )
                store.addTake(take)
                pendingTakeId = take.id
                stage = ShootStage.Saved
            }
            else -> Unit
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

    fun finish(goTo: Route) {
        store.markDone(PieceKind.Scene, auditionId)
        nav.closeCamera(then = goTo)
    }

    val time = camera.elapsed.toInt().let { "%02d:%02d:%02d".format(it / 3600, (it / 60) % 60, it % 60) }
    val pillText = when (stage) {
        ShootStage.Check -> "Camera check"
        ShootStage.Slate, ShootStage.SlateDone -> "Slate  $time"
        ShootStage.FullLength -> "Full length  $time"
        ShootStage.Scene, ShootStage.Saved -> "SC 1 · TK $takeNumber  $time"
    }

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
            }
            Spacer(Modifier.height(12.dp))

            // Middle: overlays + countdown. Takes the remaining space so controls below are never pushed off-screen.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (audition != null) {
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        when (stage) {
                            ShootStage.Check -> ShootCheckChips()
                            ShootStage.Slate -> ShootPromptCard(eyebrow = "Slate · say it to the lens · ≈ 5 s", text = store.slate.spoken)
                            ShootStage.FullLength -> ShootPromptCard(eyebrow = "After the slate", text = "Head to feet, hold still")
                            ShootStage.Scene -> {
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
                            detail = "0:05 · saved · opens every scene in ${audition.project}",
                            checks = listOf("Name", "Height", "City", "Lens"),
                            primary = "Next: full length" to {
                                store.markDone(PieceKind.Slate, auditionId)
                                stage = ShootStage.FullLength
                            },
                            secondary = "Redo" to { stage = ShootStage.Slate },
                            primaryFirst = true,
                        )
                        ShootStage.Saved -> ShootResultCard(
                            modifier = Modifier.align(Alignment.Center),
                            eyebrow = "Take saved",
                            title = "Take $takeNumber, in the can.",
                            detail = "${formatDuration(lastTakeSeconds)} · Reader under your voice · walk-in trimmed",
                            primary = "Watch take $takeNumber" to { finish(Route.Takes(auditionId)) },
                            secondary = "Go again" to {
                                takeNumber += 1
                                lineIndex = 0
                                stage = ShootStage.Scene
                            },
                            tertiary = "Done for now" to { finish(Route.AuditionDetail(auditionId)) },
                        )
                        else -> Unit
                    }
                }
                countdown?.let { n ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { countdown = null },
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
                    ShootChip(null, "1080p · 30")
                    ShootChip(null, "1/60")
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
                                    countdown != null -> countdown = null
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

private class ShootVoiceCommander(
    private val context: Context,
    private val onRecordTrigger: () -> Unit,
    private val onCutTrigger: () -> Unit,
) {
    val available: Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    var isListening by mutableStateOf(false)
        private set
    var lastHeard by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var isRecordingMode = false
    private val handler = Handler(Looper.getMainLooper())
    private val relisten = Runnable { if (wanted) listen() }

    private val recordRegex = Regex("""(?i)\b(record|recording|action|roll|rolling|start|slate)\b""")
    private val stopRegex = Regex("""(?i)\b(stop|cut|finish|finished|done|end)\b""")

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            isListening = false
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                wanted = false
                return
            }
            handler.removeCallbacks(relisten)
            if (wanted) {
                val delayMs = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 150L else 600L
                handler.postDelayed(relisten, delayMs)
            }
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            evaluate(results)
            handler.removeCallbacks(relisten)
            if (wanted) handler.postDelayed(relisten, 100)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            evaluate(partialResults)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun evaluate(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        for (text in matches) {
            lastHeard = text
            if (!isRecordingMode && recordRegex.containsMatchIn(text)) {
                onRecordTrigger()
                break
            } else if (isRecordingMode && stopRegex.containsMatchIn(text)) {
                onCutTrigger()
                break
            }
        }
    }

    fun setRecordingMode(recording: Boolean) {
        if (isRecordingMode != recording) {
            isRecordingMode = recording
            lastHeard = null
            if (wanted) {
                handler.removeCallbacks(relisten)
                runCatching { recognizer?.cancel() }
                handler.postDelayed(relisten, 100)
            }
        }
    }

    fun start() {
        if (!available) return
        wanted = true
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.cancel() }
        listen()
    }

    private fun listen() {
        if (!wanted) return
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            ?.also { it.setRecognitionListener(listener); recognizer = it }
            ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        runCatching {
            r.startListening(intent)
            isListening = true
        }.onFailure {
            isListening = false
            if (wanted) handler.postDelayed(relisten, 800)
        }
    }

    fun stop() {
        wanted = false
        isListening = false
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.stopListening() }
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
): ShootVoiceCommander {
    val context = LocalContext.current
    val currentOnRecord by rememberUpdatedState(onRecordTrigger)
    val currentOnCut by rememberUpdatedState(onCutTrigger)
    val commander = remember {
        ShootVoiceCommander(
            context = context.applicationContext,
            onRecordTrigger = { currentOnRecord() },
            onCutTrigger = { currentOnCut() },
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
private fun ShootCheckChips() {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("Framing", "Eyeline", "Light", "Sound").forEach { ShootChip(Icons.Filled.Check, it, big = true) }
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
    tertiary: Pair<String, () -> Unit>? = null,
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
        if (tertiary != null) {
            PlainButton(
                tertiary.first,
                onClick = tertiary.second,
                color = c.accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
