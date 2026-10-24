package com.onetake.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.R
import com.onetake.app.models.Piece
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Android twins of iOS DesignSystem/Components. Names and parameters match so screens port 1:1.

// MARK: - Surfaces

/** `.otCard()` — white card, 24 radius, very soft shadow. */
fun Modifier.otCard(radius: Dp = 24.dp, color: Color? = null): Modifier = this.then(
    Modifier
        .shadow(12.dp, RoundedCornerShape(radius), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
        .clip(RoundedCornerShape(radius))
)
    .then(if (color != null) Modifier.background(color) else Modifier)

/** Card helper that resolves the theme colour (use this rather than `otCard` when possible). */
@Composable
fun OTCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .otCard(radius, OT.colors.surfaceCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun OTDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = startIndent), thickness = 0.5.dp, color = OT.colors.separator)
}

// MARK: - Section label (v2/Section Label)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = OT.colors.labelTertiary) {
    Text(
        text.uppercase(),
        style = OT.type.eyebrow,
        color = color,
        modifier = modifier.semantics { heading() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// MARK: - Readiness dial (v2/Progress Ring, segmented)

/** One segment per package piece. Done = accent, next = 45% accent, todo = quiet track. */
@Composable
fun ReadinessDial(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    lineWidth: Dp = 6.dp,
    onDark: Boolean = false,
) {
    val c = OT.colors
    val doneColor = if (onDark) Color.White else c.accent
    val nextColor = doneColor.copy(alpha = 0.45f)
    val todoColor = if (onDark) Color.White.copy(alpha = 0.22f) else c.fillQuiet
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = "$done of $total pieces ready" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = maxOf(total, 1)
            val gap = 0.035f
            val seg = (1f - n * gap) / n
            val stroke = lineWidth.toPx()
            val inset = stroke / 2
            for (i in 0 until n) {
                val start = (i * (seg + gap) + gap / 2) * 360f - 90f
                drawArc(
                    color = when {
                        i < done -> doneColor
                        i == done -> nextColor
                        else -> todoColor
                    },
                    startAngle = start,
                    sweepAngle = seg * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val fg = if (onDark) Color.White else c.labelPrimary
            Text("$done", color = fg, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.32f).sp)
            Text("of $total", color = fg, fontSize = (size.value * 0.18f).sp, fontWeight = FontWeight.Medium, lineHeight = (size.value * 0.2f).sp)
        }
    }
}

// MARK: - Continuous progress ring (weekly minutes, lines learned)

@Composable
fun ProgressRing(value: Float, modifier: Modifier = Modifier, lineWidth: Dp = 8.dp, color: Color = OT.colors.accent) {
    val track = OT.colors.fillQuiet
    Canvas(modifier.semantics { contentDescription = "${(value * 100).toInt()} percent" }) {
        val stroke = lineWidth.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
        drawArc(color, -90f, 360f * value.coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// MARK: - Readiness dashes (v2/Readiness Dashes) — used on audition list rows

@Composable
fun ReadinessDashes(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = OT.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$done of $total ready" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { i ->
                Box(Modifier.size(14.dp, 4.dp).clip(CircleShape).background(if (i < done) c.accent else c.fillQuiet))
            }
        }
        Text("$done of $total", style = OT.type.caption, color = c.labelTertiary)
    }
}

// MARK: - Package steps bar (v2/Package Steps) — audition detail

@Composable
fun PackageStepsBar(pieces: List<Piece>, modifier: Modifier = Modifier) {
    val c = OT.colors
    val nextIndex = pieces.indexOfFirst { !it.isDone }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pieces.forEachIndexed { i, p ->
                Box(
                    Modifier.weight(1f).height(8.dp).clip(CircleShape).background(
                        when {
                            p.isDone -> c.accent
                            i == nextIndex -> c.accent.copy(alpha = 0.4f)
                            else -> c.fillQuiet
                        }
                    )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pieces.forEachIndexed { i, p ->
                Text(
                    p.title,
                    style = OT.type.caption.copy(fontWeight = if (i == nextIndex) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (i == nextIndex) c.accent else c.labelTertiary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - Buttons (capsule, 52 tall, like the designs — not Material's 40dp pills)

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    return if (pressed) 0.98f else 1f
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTrailing: Boolean = false,
    enabled: Boolean = true,
    tint: Color = OT.colors.accent,
    contentColor: Color = OT.colors.labelOnAccent,
) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .scale(pressScale(src))
            .clip(CircleShape)
            .background(if (enabled) tint else tint.copy(alpha = 0.35f))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && !iconTrailing) Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text, style = OT.type.headline, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null && iconTrailing) Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onDark: Boolean = false,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val fg = if (onDark) Color.White else OT.colors.labelPrimary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .scale(pressScale(src))
            .clip(CircleShape)
            .background(if (onDark) Color.White.copy(alpha = 0.14f) else OT.colors.fillField)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = OT.type.headline, color = if (enabled) fg else fg.copy(alpha = 0.4f), maxLines = 1)
    }
}

/** Borderless text button under a primary CTA ("Choose different takes"). 48 tall. */
@Composable
fun PlainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OT.colors.labelPrimary,
    style: androidx.compose.ui.text.TextStyle = OT.type.headline,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = color) }
}

/** Round icon button used in screen headers (back, ···, +). White circle with soft shadow in light mode. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    onDark: Boolean = false,
) {
    val c = OT.colors
    val bg = when {
        filled -> c.accent
        onDark -> OT.camera.glass
        c.isDark -> c.surfaceRaised
        else -> c.surfaceCard
    }
    val fg = when {
        filled -> c.labelOnAccent
        onDark -> Color.White
        else -> c.labelPrimary
    }
    Box(
        modifier
            .size(maxOf(size, 48.dp)) // keep the 48dp touch target even when the visual circle is 44
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .then(if (!onDark && !filled && !c.isDark) Modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f)) else Modifier)
                .clip(CircleShape)
                .background(bg)
                .then(if (!onDark && !filled) Modifier.border(0.5.dp, c.separator, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f)) }
    }
}

// MARK: - Screen scaffolds

/**
 * Header row for pushed screens: round back button on the left, optional trailing buttons.
 * Titles live in the content as large titles (Call Sheet style), not in a toolbar.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    title: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack ?: { nav.back() })
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) Text(title, style = OT.type.headline, color = OT.colors.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        if (title != null) Spacer(Modifier.width(48.dp))
    }
}

/**
 * Standard pushed screen: canvas background, back bar, scrolling content with 16dp gutters,
 * and an optional pinned bottom area (primary CTA) that sits above the system nav bar.
 */
@Composable
fun PushedScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
        TopBar(onBack = onBack, title = title, trailing = trailing)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            content = content,
        )
        if (bottomBar != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(OT.colors.bgCanvas)
                    .navigationBarsPadding()
                    .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = bottomBar,
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Large title block used at the top of most screens (eyebrow + 34pt title + optional subtitle). */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, subtitle: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) SectionLabel(eyebrow)
        Text(title, style = OT.type.largeTitle, color = OT.colors.labelPrimary, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = OT.type.callout, color = OT.colors.labelSecondary)
    }
}

// MARK: - Rows

@Composable
fun Chevron(modifier: Modifier = Modifier, tint: Color = OT.colors.labelTertiary) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tint, modifier = modifier.size(22.dp))
}

/** Plain list row (Call Sheet style): optional leading icon, title/detail, trailing value, chevron, hairline below. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = OT.colors.labelSecondary,
    valueColor: Color = OT.colors.labelSecondary,
    titleColor: Color = OT.colors.labelPrimary,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = OT.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = OT.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = OT.type.footnote, color = c.labelTertiary)
            }
            if (value != null) Text(value, style = OT.type.footnote, color = valueColor, maxLines = 1)
            trailing?.invoke()
            if (showChevron) Chevron()
        }
        if (showDivider) OTDivider()
    }
}

/** Switch styled with the accent (Material Switch, Velvet colours). */
@Composable
fun OTSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = OT.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.fillField,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showDivider: Boolean = true,
) {
    ListRow(
        title = title,
        detail = detail,
        modifier = modifier,
        showChevron = false,
        showDivider = showDivider,
        onClick = { onCheckedChange(!checked) },
        trailing = { OTSwitch(checked, onCheckedChange) },
    )
}

/** IconRow — 40dp tinted tile + title/detail + chevron (Add audition, Shoot setup, error states). */
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    tint: Color = OT.colors.accent,
    onClick: (() -> Unit)? = null,
) {
    val c = OT.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OT.type.body, color = c.labelPrimary)
            Text(detail, style = OT.type.footnote, color = c.labelTertiary)
        }
        Chevron()
    }
}

// MARK: - Sheet header (v2/Sheet Header) — Cancel · Title · Save

@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: String? = "Cancel",
    trailing: String? = null,
    trailingEnabled: Boolean = true,
    onLeading: () -> Unit = {},
    onTrailing: () -> Unit = {},
) {
    val c = OT.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = OT.dims.gutter).padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 72.dp)) {
            if (leading != null) {
                Box(
                    Modifier.heightIn(min = 36.dp).clip(CircleShape).background(c.fillField)
                        .clickable(role = Role.Button, onClick = onLeading).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(leading, style = OT.type.subhead, color = c.labelPrimary) }
            }
        }
        Text(title, style = OT.type.headline, color = c.labelPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        Box(Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            if (trailing != null) {
                Box(
                    Modifier.heightIn(min = 36.dp).clip(CircleShape)
                        .background(if (trailingEnabled) c.accent else c.accent.copy(alpha = 0.35f))
                        .clickable(enabled = trailingEnabled, role = Role.Button, onClick = onTrailing).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(trailing, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelOnAccent) }
            }
        }
    }
}

// MARK: - Headshot photo

@Composable
fun rememberBitmapFromUri(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    if (uriString.isNullOrBlank()) return null
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val stream = if (uri.scheme == "file" || uri.scheme == null) {
                    val file = File(uri.path ?: uriString)
                    if (file.exists()) file.inputStream() else null
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                stream?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }?.let { bitmap = it }
    }
    return bitmap
}

/**
 * Fills its bounds (crop). Loads the user's real captured headshot photo if available.
 * If no headshot has been captured, renders a neutral studio silhouette glyph.
 */
@Composable
fun HeadshotImage(
    modifier: Modifier = Modifier,
    name: String = "front",
    uri: String? = null,
    contentDescription: String? = null,
) {
    val store = LocalAppStore.current
    val photoUri = uri ?: store.headshot(name) ?: store.headshot("front") ?: store.headshots.values.firstOrNull()
    val bitmap = rememberBitmapFromUri(photoUri)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        HeadshotPlaceholder(modifier)
    }
}

/** Neutral studio silhouette glyph, for empty headshot slots. */
@Composable
fun HeadshotPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF2C2C32), Color(0xFF161618)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Person, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(36.dp)) }
}

// MARK: - Camera chrome helpers (always dark)

/** Glass capsule label on camera screens ("TK 3", "Exposure locked"). */
@Composable
fun CameraPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, background: Color = OT.camera.glass) {
    Row(
        modifier.heightIn(min = 32.dp).clip(CircleShape).background(background).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}

/** Shape helper so call-sites read like SwiftUI's `RoundedRectangle(cornerRadius:style:.continuous)`. */
fun rounded(radius: Dp): Shape = RoundedCornerShape(radius)

/** Box filling the screen with the canvas colour — tab roots use this. */
@Composable
fun CanvasBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(OT.colors.bgCanvas), content = content)
}
