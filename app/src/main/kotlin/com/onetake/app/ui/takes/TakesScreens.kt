package com.onetake.app.ui.takes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.Take
import com.onetake.app.models.TapeFinding
import com.onetake.app.models.formatDuration
import androidx.compose.ui.viewinterop.AndroidView
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PlainButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.ReadinessDial
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.OT
import android.net.Uri
import androidx.compose.ui.unit.Dp
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - 18 Your takes (+ 18.1 Compare, 18.2 One more?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakesScreen(auditionId: String) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors

    var selected by remember { mutableStateOf<String?>(null) }
    var showOneMore by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var isPlaying by remember(selected) { mutableStateOf(false) }

    // Scoped to this audition — otherwise every audition's takes show up mixed together.
    val takes = store.takes.filter { it.auditionId == auditionId }
    val circled = takes.firstOrNull { it.isCircled }
    val current = takes.firstOrNull { it.id == (selected ?: circled?.id) } ?: takes.lastOrNull()
    val sceneTitle = store.audition(auditionId)?.scenes?.firstOrNull()?.title ?: "Scene 1"

    if (comparing) {
        BackHandler { comparing = false }
        TkCompareView(
            takes = takes,
            current = current,
            onClose = { comparing = false },
            onCircle = { store.circle(it); haptics.success() },
            onSend = { comparing = false; nav.push(Route.TapeCheck(auditionId)) },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        TkHeader(title = "Your takes", subtitle = sceneTitle, onBack = { nav.back() }) {
            TkToolCapsule {
                TkToolButton("Trim", onClick = { /* Trim not yet available — same as iOS */ }) {
                    Icon(Icons.Filled.ContentCut, null, tint = c.labelPrimary, modifier = Modifier.size(22.dp))
                }
                TkToolButton("Compare", onClick = { comparing = true }) { TkCompareGlyph(c.labelPrimary) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TkPlayerCard(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                fileUri = current?.fileUri,
                isPlaying = isPlaying,
                onTogglePlay = { isPlaying = !isPlaying },
            )
            TkPlaybackBar()

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                takes.forEach { take ->
                    TkTakeThumbnail(
                        take = take,
                        isSelected = take.id == current?.id,
                        onTap = { selected = take.id; haptics.tick() },
                        onLongPress = { store.circle(take); haptics.success() },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Take ${circled?.number ?: 0} is circled — that's your send.",
                    style = OT.type.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (current != null && !current.isCircled) {
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { store.circle(current); haptics.success() }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Circle take ${current.number}", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                }
            }

            OTDivider()

            // Notes to self (disclosure)
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { notesOpen = !notesOpen },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = c.labelSecondary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Notes to self", style = OT.type.body, color = c.labelPrimary)
                        Text(
                            "Never sent · " + (current?.notes?.joinToString(" · ") ?: ""),
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        if (notesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (notesOpen) "Hide notes" else "Show notes",
                        tint = c.labelSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (notesOpen) {
                    Column(Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (current?.notes ?: emptyList()).forEach { note ->
                            Text("• $note", style = OT.type.callout, color = c.labelPrimary)
                        }
                    }
                }
            }
        }

        // Pinned actions
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.bgCanvas)
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton("Go again", onClick = { showOneMore = true }, modifier = Modifier.width(130.dp))
            PrimaryButton(
                "Send take ${circled?.number ?: 0}",
                onClick = { nav.push(Route.TapeCheck(auditionId)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showOneMore) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val dismiss: (() -> Unit) -> Unit = { then ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                showOneMore = false
                then()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showOneMore = false },
            sheetState = sheetState,
            containerColor = c.surfaceCard,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OT.dims.gutter)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("One more?", style = OT.type.headline.copy(fontSize = OT.type.title.fontSize * 0.75f, lineHeight = OT.type.title.lineHeight * 0.8f), color = c.labelPrimary)
                Text(
                    "Takes 1 to ${takes.size} stay right here. Three solid takes is plenty — trust the one you circled.",
                    style = OT.type.callout,
                    color = c.labelSecondary,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton("Record take ${takes.size + 1}", onClick = {
                    dismiss { nav.openCamera(CameraFlow.Shoot(auditionId)) }
                })
                SecondaryButton("Not now", onClick = { dismiss {} })
            }
        }
    }
}

// MARK: - Header (back · title/subtitle · trailing)

@Composable
private fun TkHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onDark: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val fg = if (onDark) Color.White else OT.colors.labelPrimary
    val fg2 = if (onDark) OT.camera.label2 else OT.colors.labelTertiary
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack, onDark = onDark)
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = OT.type.headline, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = OT.type.caption, color = fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** White capsule holding the header tools (scissors · compare), like the Figma glass pill. */
@Composable
private fun TkToolCapsule(content: @Composable RowScope.() -> Unit) {
    val c = OT.colors
    Row(
        Modifier
            .padding(end = 4.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(CircleShape)
            .background(if (c.isDark) c.surfaceRaised else c.surfaceCard)
            .border(0.5.dp, c.separator, CircleShape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun TkToolButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** "rectangle.split.2x1" glyph drawn by hand (no exact Material equivalent). */
@Composable
private fun TkCompareGlyph(color: Color) {
    Box(Modifier.size(22.dp, 18.dp).border(1.8.dp, color, RoundedCornerShape(4.dp))) {
        Box(Modifier.align(Alignment.Center).width(1.8.dp).fillMaxHeight().background(color))
    }
}

// MARK: - Player card (VideoView when take.fileUri is present, headshot placeholder otherwise)

@Composable
private fun TkPlayerCard(
    modifier: Modifier = Modifier,
    fileUri: String? = null,
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
) {
    val cardShape = RoundedCornerShape(26.dp)
    val hasVideo = !fileUri.isNullOrBlank()

    Box(
        modifier
            .clip(cardShape)
            .background(Color.Black)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasVideo) {
            AndroidView(
                factory = { context ->
                    android.widget.VideoView(context).apply {
                        runCatching {
                            setVideoPath(fileUri)
                            setOnPreparedListener { mp -> mp.isLooping = true }
                        }
                    }
                },
                update = { videoView ->
                    runCatching {
                        if (isPlaying) {
                            if (!videoView.isPlaying) videoView.start()
                        } else {
                            if (videoView.isPlaying) videoView.pause()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF16161A)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Movie, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(56.dp))
            }
        }

        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(role = Role.Button, onClick = onTogglePlay)
                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// MARK: - v2/Playback Bar (scrubber with "your lines" markers)

@Composable
private fun TkPlaybackBar(
    onDark: Boolean = false,
    startLabel: String = "0:00",
    endLabel: String = "1:30",
    centerLabel: String? = "your lines · tap to jump",
) {
    val c = OT.colors
    var position by remember { mutableFloatStateOf(0.35f) }
    val fill = if (onDark) Color.White else c.labelPrimary
    val track = if (onDark) Color.White.copy(alpha = 0.22f) else c.fillQuiet
    val marker = if (onDark) Color.White.copy(alpha = 0.7f) else c.accent
    val markers = listOf(0.1f, 0.42f, 0.78f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = "Playback position ${(position * 100).toInt()} percent" }
                .pointerInput(Unit) {
                    detectTapGestures { o -> position = (o.x / size.width).coerceIn(0f, 1f) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        position = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        ) {
            val h = 4.dp.toPx()
            val y = size.height / 2 - h / 2
            drawRoundRect(track, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(fill, Offset(0f, y), Size(size.width * position, h), CornerRadius(h / 2))
            val mw = 2.5.dp.toPx()
            val mh = 12.dp.toPx()
            markers.forEach { m ->
                drawRoundRect(marker, Offset(size.width * m - mw / 2, size.height / 2 - mh / 2), Size(mw, mh), CornerRadius(mw / 2))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val lbl = if (onDark) OT.camera.label2 else c.labelTertiary
            Text(startLabel, style = OT.type.caption, color = lbl)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (centerLabel != null) {
                    Text(centerLabel, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (onDark) OT.camera.label2 else c.accent)
                }
            }
            Text(endLabel, style = OT.type.caption, color = lbl)
        }
    }
}

/** A real video-frame grab (mirrors iOS's `VideoThumbnail`/`AVAssetImageGenerator`) instead of a
 *  generic movie-reel icon for every take — falls back to the icon while loading or if there's no
 *  file yet (e.g. a take recorded on a device with no camera hardware). */
@Composable
private fun TkVideoThumbnail(fileUri: String?, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, fileUri) {
        value = if (fileUri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(fileUri))
                    retriever.frameAtTime
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.Movie, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(iconSize))
        }
    }
}

// MARK: - v2/Take Thumbnail · Default / Selected / Chosen

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TkTakeThumbnail(take: Take, isSelected: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val c = OT.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Watch take ${take.number}",
                onLongClickLabel = "Circle this take",
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .border(2.5.dp, if (isSelected) c.accent else Color.Transparent, RoundedCornerShape(15.dp))
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .size(104.dp, 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222228)),
                contentAlignment = Alignment.Center,
            ) {
                TkVideoThumbnail(fileUri = take.fileUri, modifier = Modifier.matchParentSize())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Take ${take.number}", style = OT.type.headline, color = c.labelPrimary)
            if (take.isCircled) Icon(Icons.Filled.Star, "Circled", tint = c.accent, modifier = Modifier.size(14.dp))
        }
        Text(
            "${formatDuration(take.durationSeconds)} · ${tkAgo(take.recordedAt)}",
            style = OT.type.caption,
            color = c.labelTertiary,
        )
    }
}

private fun tkAgo(t: LocalDateTime): String {
    val m = Duration.between(t, LocalDateTime.now()).toMinutes()
    return when {
        m < 1 -> "now"
        m < 60 -> "$m min"
        m < 60 * 24 -> "${m / 60} h"
        else -> "${m / (60 * 24)} d"
    }
}

// MARK: - 18.1 Compare (always dark, like the design)

@Composable
private fun TkCompareView(
    takes: List<Take>,
    current: Take?,
    onClose: () -> Unit,
    onCircle: (Take) -> Unit,
    onSend: () -> Unit,
) {
    val cam = OT.camera
    val c = OT.colors
    val circled = takes.firstOrNull { it.isCircled }
    val left = circled ?: current ?: takes.firstOrNull()
    val right = (if (current != null && current.id != left?.id) current else takes.lastOrNull { it.id != left?.id }) ?: left
    var listen by remember { mutableIntStateOf(0) }
    val pair = listOfNotNull(left, right)
    val compareSubtitle = if (left != null && right != null && left.id != right.id) "Take ${left.number} vs Take ${right.number}" else "Compare takes"

    Column(Modifier.fillMaxSize().background(cam.bg)) {
        TkHeader(title = "Compare", subtitle = compareSubtitle, onBack = onClose, onDark = true)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, t ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF222228))
                                .border(2.5.dp, if (i == listen) c.accent else Color.Transparent, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TkVideoThumbnail(fileUri = t.fileUri, modifier = Modifier.matchParentSize(), iconSize = 48.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Take ${t.number}", style = OT.type.headline.copy(fontSize = OT.type.headline.fontSize * 1.1f), color = cam.label)
                            if (t.isCircled) Icon(Icons.Filled.Star, "Circled", tint = c.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "${formatDuration(t.durationSeconds)} · ${tkAgo(t.recordedAt).let { if (it == "now") "just now" else "$it ago" }}",
                            style = OT.type.footnote,
                            color = cam.label2,
                        )
                    }
                }
            }

            TkPlaybackBar(onDark = true, startLabel = "00:00", endLabel = "01:30", centerLabel = null)
            Text("Both play in sync · your lines · tap to jump", style = OT.type.caption, color = cam.label2)

            SectionLabel("Listen to", color = cam.label2)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(3.dp),
            ) {
                pair.forEachIndexed { i, t ->
                    val on = i == listen
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color.White else Color.Transparent)
                            .clickable(role = Role.Tab) { listen = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Take ${t.number}",
                            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = if (on) Color.Black else Color.White,
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Take ${circled?.number ?: 0} is circled. That's your send.",
                    style = OT.type.footnote,
                    color = cam.label2,
                    modifier = Modifier.weight(1f),
                )
                val other = pair.firstOrNull { !it.isCircled }
                if (other != null) {
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onCircle(other) }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Circle take ${other.number}", style = OT.type.footnote, color = Color(0xFFB79BD0))
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, cam.stroke, CircleShape)
                    .clickable(role = Role.Button) { /* Trim not yet available — same as iOS */ },
                contentAlignment = Alignment.Center,
            ) { Text("Trim", style = OT.type.headline, color = Color.White) }
            PrimaryButton(
                "Send take ${circled?.number ?: 0}",
                onClick = onSend,
                modifier = Modifier.weight(1f),
                tint = Color(0xFF5B3E73),
                contentColor = Color.White,
            )
        }
    }
}

// MARK: - 19b Tape check (+ 19b.L2 finding sheet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapeCheckScreen(auditionId: String) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val c = OT.colors
    val findings = remember(store.takes, auditionId) {
        store.takes.filter { it.auditionId == auditionId }.flatMap { take ->
            take.notes.map { note ->
                TapeFinding(
                    category = "Take ${take.number}",
                    title = note,
                    where = "Take ${take.number} · ${formatDuration(take.durationSeconds)}",
                    advice = "Review Take ${take.number} playback before sending."
                )
            }
        }
    }
    var finding by remember { mutableStateOf<TapeFinding?>(null) }
    var passedOpen by remember { mutableStateOf(false) }
    val project = store.audition(auditionId)?.project

    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        TkHeader(title = "Tape check", subtitle = project, onBack = { nav.back() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            val audition = store.audition(auditionId)
            val requiredScenes = audition?.scenes?.size ?: 1
            val auditionTakes = store.takes.filter { it.auditionId == auditionId }
            val circledTakesCount = audition?.scenes?.indices?.count { i -> auditionTakes.any { it.sceneIndex == i && it.isCircled } }
                ?: auditionTakes.count { it.isCircled }
            val hasHeadshot = if (store.headshotsTaken > 0) 1 else 0
            val hasSlate = if (store.slate.name.isNotBlank()) 1 else 0
            val totalChecks = maxOf(1, 2 + requiredScenes)
            val doneChecks = minOf(totalChecks, hasHeadshot + hasSlate + circledTakesCount)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReadinessDial(done = doneChecks, total = totalChecks, size = 84.dp, lineWidth = 8.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ready to send", style = OT.type.title, color = c.labelPrimary)
                    Text(
                        "${findings.size} things are worth a look. Nothing stops you sending.",
                        style = OT.type.callout,
                        color = c.labelSecondary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Worth a look")
            Spacer(Modifier.height(4.dp))

            findings.forEach { f ->
                TkFindingRow(
                    category = f.category,
                    categoryColor = c.warning,
                    title = f.title,
                    detail = f.where,
                    onClick = { finding = f },
                    leading = {
                        Box(
                            Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222228)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Movie, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                    },
                )
            }

            // Optional clean-up (Figma 19b) — maps to the "Clean up room noise" setting.
            TkFindingRow(
                category = "Optional",
                categoryColor = c.accent,
                title = "Clean up room hum",
                detail = if (store.cleanNoise) "On · your voice untouched · original kept" else "Your voice untouched · original kept",
                onClick = { store.cleanNoise = !store.cleanNoise },
                leading = {
                    Box(
                        Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
                },
                trailing = {
                    if (store.cleanNoise) Icon(Icons.Filled.Check, "On", tint = c.accent, modifier = Modifier.size(20.dp))
                    else Spacer(Modifier.size(20.dp))
                },
            )

            // N checks passed (disclosure)
            Column(Modifier.padding(top = 8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { passedOpen = !passedOpen },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("11 checks passed", style = OT.type.body, color = c.labelPrimary)
                        if (!passedOpen) {
                            Text(TK_PASSED, style = OT.type.footnote, color = c.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(
                        if (passedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (passedOpen) "Hide checks" else "Show checks",
                        tint = c.labelSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (passedOpen) {
                    Text(TK_PASSED, style = OT.type.footnote, color = c.labelSecondary, modifier = Modifier.padding(start = 32.dp, top = 2.dp))
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bgCanvas)
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton("Continue to package", onClick = { nav.push(Route.Package(auditionId)) })
            PlainButton("Watch the ${findings.size} moments", onClick = { finding = findings.firstOrNull() })
        }
    }

    val f = finding
    if (f != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { finding = null }
        }
        ModalBottomSheet(
            onDismissRequest = { finding = null },
            sheetState = sheetState,
            containerColor = c.surfaceCard,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OT.dims.gutter)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val parts = f.where.split(" · ")
                val shortWhere = parts.take(2).joinToString(" · ")
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(c.warning))
                            Text(
                                "${f.category} · $shortWhere",
                                style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = c.warning,
                            )
                        }
                        Text(f.title, style = OT.type.title.copy(fontSize = OT.type.title.fontSize * 0.8f, lineHeight = OT.type.title.lineHeight * 0.85f), color = c.labelPrimary)
                    }
                    CircleIconButton(Icons.Filled.Close, "Close", onClick = close, size = 40.dp)
                }
                TkPlayerCard(Modifier.fillMaxWidth().height(180.dp))
                Text(f.advice, style = OT.type.callout, color = c.labelSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        "Watch ${parts.getOrNull(1) ?: "0:41"}",
                        onClick = { /* jumps the player to the moment */ },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.PlayArrow,
                    )
                    SecondaryButton("I meant it", onClick = close, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val TK_PASSED =
    "Framing, light, slate, format, length, file name, audio sync, eyeline (scene 2), reader level (scene 1), background, focus"

@Composable
private fun TkFindingRow(
    category: String,
    categoryColor: Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = OT.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(categoryColor))
                    Text(category, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = categoryColor)
                }
                Text(title, style = OT.type.body, color = c.labelPrimary)
                Text(detail, style = OT.type.footnote, color = c.labelTertiary)
            }
            if (trailing != null) trailing() else Chevron()
        }
        OTDivider(startIndent = 76.dp)
    }
}
