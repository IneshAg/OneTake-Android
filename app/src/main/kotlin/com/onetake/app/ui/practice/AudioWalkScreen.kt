package com.onetake.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.formatDuration
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.system.KeepScreenOn
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.rememberTtsEngine
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay

@Composable
fun AudioWalkScreen(
    id: String,
    sceneIndex: Int = 0,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val tts = rememberTtsEngine()
    val c = OT.colors

    val audition = store.audition(id)
    val practicePiece = if (audition == null) store.practicePiece(id) else null
    val scene = audition?.scenes?.getOrNull(sceneIndex)
        ?: audition?.scenes?.firstOrNull()
        ?: practicePiece?.scene

    if (scene == null || scene.lines.isEmpty()) {
        Column(
            Modifier.fillMaxSize().background(c.bgCanvas).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No script lines to play.", style = OT.type.headline, color = c.labelSecondary)
            Spacer(Modifier.height(16.dp))
            CircleIconButton(Icons.Filled.Close, "Back", onClick = { nav.back() })
        }
        return
    }

    KeepScreenOn()

    val lines = scene.lines
    var lineIndex by remember { mutableIntStateOf(0) }
    var loopCount by remember { mutableIntStateOf(1) }
    var isPlaying by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { tts.stop() }
    }

    // Playback loop
    LaunchedEffect(lineIndex, isPlaying) {
        if (!isPlaying) {
            tts.stop()
            return@LaunchedEffect
        }
        val currentLine = lines.getOrNull(lineIndex) ?: return@LaunchedEffect
        val words = currentLine.text.split(Regex("\\s+")).size
        val pacingMs = LineLearningMode.calculateSpokenPacingMillis(words)

        tts.speak(currentLine.text) {
            // Callback when line finishes
        }

        delay(pacingMs)

        if (isPlaying) {
            if (lineIndex + 1 < lines.size) {
                lineIndex += 1
            } else {
                // Loop around
                lineIndex = 0
                loopCount += 1
                haptics.tick()
            }
        }
    }

    val currentLine = lines.getOrNull(lineIndex) ?: lines.first()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = OT.dims.gutter),
    ) {
        // Top Action Bar
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Filled.Close, "Close", onClick = {
                tts.stop()
                nav.back()
            })
            Spacer(Modifier.weight(1f))
            // Loop count pill
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surfaceCard)
                    .border(0.5.dp, c.separator, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.GraphicEq, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text("Loop $loopCount", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Screen Header
        Text(
            "Hands-Free Audio Walk",
            style = OT.type.largeTitle,
            color = c.labelPrimary,
        )
        Text(
            "${scene.title} · ${lines.size} lines · about ${formatDuration(scene.estimatedSeconds)}",
            style = OT.type.callout,
            color = c.labelSecondary,
        )

        Spacer(Modifier.height(24.dp))

        // Monolithic Active Dialogue Card (Dark Titanium Squircle)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surfaceCard)
                .border(0.5.dp, c.separator, RoundedCornerShape(26.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val isMine = currentLine.isMine
            Text(
                text = if (isMine) "${currentLine.character} · YOU" else currentLine.character,
                style = OT.type.headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = if (isMine) c.accent else c.reader,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = currentLine.text,
                style = OT.type.largeTitle.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
                color = c.labelPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Line ${lineIndex + 1} of ${lines.size}",
                style = OT.type.footnote,
                color = c.labelTertiary,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Floating Titanium Control Capsule
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Rewind 1 line
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(c.surfaceCard)
                    .border(0.5.dp, c.separator, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tick()
                        lineIndex = (lineIndex - 1).coerceAtLeast(0)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.FastRewind, "Previous Line", tint = c.labelPrimary, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(20.dp))

            // Play / Pause Master
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable(role = Role.Button) {
                        haptics.impact()
                        isPlaying = !isPlaying
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = c.labelOnAccent,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.width(20.dp))

            // Forward 1 line
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(c.surfaceCard)
                    .border(0.5.dp, c.separator, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tick()
                        lineIndex = (lineIndex + 1) % lines.size
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.FastForward, "Next Line", tint = c.labelPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
