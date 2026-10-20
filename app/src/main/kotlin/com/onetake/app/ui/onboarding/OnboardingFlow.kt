package com.onetake.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onetake.app.LocalAppStore
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.PlainButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.otCard
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay

/*
 * 01 Welcome → 02 Sign in (→ 02.2 Email code) → 03 Your slate → 04 Camera & mic (→ 04.1 Camera off)
 * → 04b Notifications → Home (first run). No Navigator exists yet, so steps are local state.
 */

private const val StepWelcome = 0
private const val StepSignIn = 1
private const val StepEmailCode = 2
private const val StepSlate = 3
private const val StepPermissions = 4
private const val StepCameraOff = 5
private const val StepNotifications = 6

@Composable
fun OnboardingFlow() {
    val store = LocalAppStore.current
    var step by rememberSaveable { mutableIntStateOf(StepWelcome) }
    var email by rememberSaveable { mutableStateOf("") }
    // Where "back" from the slate step goes (sign-in or the email code screen).
    var slateBack by rememberSaveable { mutableIntStateOf(StepSignIn) }

    fun finish() {
        // First run: Home shows "Add your first audition", Practice shows "No Practice".
        store.auditions = emptyList()
        store.practice = emptyList()
        store.isOnboarded = true
    }

    fun previousOf(s: Int): Int = when (s) {
        StepSignIn -> StepWelcome
        StepEmailCode -> StepSignIn
        StepSlate -> slateBack
        StepPermissions -> StepSlate
        StepCameraOff -> StepPermissions
        StepNotifications -> StepPermissions
        else -> StepWelcome
    }

    val goBack: () -> Unit = { step = previousOf(step) }

    BackHandler(enabled = step != StepWelcome) { goBack() }

    Box(Modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "onboarding",
        ) { s ->
            when (s) {
                StepWelcome -> OnbWelcomeStep(onStart = { step = StepSignIn })
                StepSignIn -> OnbSignInStep(
                    email = email,
                    onEmailChange = { email = it },
                    onBack = goBack,
                    onGoogle = { slateBack = StepSignIn; step = StepSlate },
                    onEmail = { step = StepEmailCode },
                )
                StepEmailCode -> OnbEmailCodeStep(
                    email = email,
                    onBack = goBack,
                    onVerified = { slateBack = StepEmailCode; step = StepSlate },
                )
                StepSlate -> OnbSlateStep(onBack = goBack, onContinue = { step = StepPermissions })
                StepPermissions -> OnbPermissionsStep(
                    onBack = goBack,
                    onGranted = { step = StepNotifications },
                    onDenied = { step = StepCameraOff },
                    onNotNow = { step = StepNotifications },
                )
                StepCameraOff -> OnbCameraOffStep(onBack = goBack, onContinue = { step = StepNotifications })
                else -> OnbNotificationsStep(onBack = goBack, onDone = { finish() })
            }
        }
    }
}

// MARK: - Shared scaffolding

/** Full-screen step: canvas, edge-to-edge insets, optional header, scrolling body, pinned actions. */
@Composable
private fun OnbScreen(
    onBack: (() -> Unit)?,
    stepIndex: Int = 0,
    actions: (@Composable ColumnScope.() -> Unit)?,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (onBack != null) OnbHeader(onBack = onBack, stepIndex = stepIndex)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = OT.dims.gutter),
            content = content,
        )
        if (actions != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = OT.dims.gutter, end = OT.dims.gutter, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}

/** Back button on the left; step dots (1–3) centred for the setup steps. */
@Composable
private fun OnbHeader(onBack: () -> Unit, stepIndex: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (stepIndex > 0) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = "Step $stepIndex of 3" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 1..3) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (i <= stepIndex) OT.colors.accent else OT.colors.labelTertiary.copy(alpha = 0.3f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnbTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = OT.type.largeTitle) {
    Text(
        text,
        style = style,
        color = OT.colors.labelPrimary,
        modifier = modifier.semantics { heading() },
    )
}

/** Tinted tile + bold title + tertiary detail (permission "why" rows). */
@Composable
private fun OnbBenefitRow(icon: ImageVector, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OT.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = OT.colors.accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OT.type.headline, color = OT.colors.labelPrimary)
            Text(detail, style = OT.type.footnote, color = OT.colors.labelTertiary)
        }
    }
}

@Composable
private fun OnbBubble(icon: ImageVector, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(OT.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = OT.colors.accent, modifier = Modifier.size(size * 0.42f)) }
}

@Composable
private fun OnbAskCaption(text: String) {
    Text(
        text,
        style = OT.type.footnote,
        color = OT.colors.labelTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

private fun onbHasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

// MARK: - 01 Welcome

@Composable
private fun OnbWelcomeStep(onStart: () -> Unit) {
    val c = OT.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = OT.dims.gutter + 4.dp),
    ) {
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Movie, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Text("OneTake", style = OT.type.headline, color = c.labelPrimary)
        }

        OnbScriptPreviewCard(Modifier.padding(top = 36.dp))

        Spacer(Modifier.weight(1f))

        Text(
            "Your best take,\nevery time.",
            style = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            color = c.labelPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Learn your lines, tape with a real reader, and send it the way casting asks.",
            style = OT.type.callout,
            color = c.labelSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.weight(0.6f))
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "Get started", onClick = onStart)
        PlainButton(text = "I already have an account", onClick = onStart, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

@Composable
private fun OnbScriptPreviewCard(modifier: Modifier = Modifier) {
    val c = OT.colors
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // Stacked "pages" behind the card, like the design.
        Box(
            Modifier
                .matchParentSize()
                .offset(x = (-14).dp, y = 22.dp)
                .rotate(-7f)
                .otCard(radius = 20.dp, color = c.surfaceCard.copy(alpha = 0.6f)),
        )
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 12.dp)
                .rotate(3f)
                .otCard(radius = 20.dp, color = c.surfaceCard.copy(alpha = 0.8f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .rotate(-2f)
                .otCard(radius = 20.dp, color = c.surfaceCard)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "INT. YOUR ROOM — TONIGHT",
                style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                color = c.labelTertiary,
            )
            OnbScriptLine("YOU", "I got the audition.", mine = true)
            OnbScriptLine("READER", "Great. Send a tape by Friday.", mine = false)
            OnbScriptLine("YOU", "Tonight. I'm ready.", mine = true)
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-12).dp)
                .clip(CircleShape)
                .background(Color.Black)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(OT.camera.recRed))
            Text("SC 1 · TK 1", style = OT.type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Composable
private fun OnbScriptLine(who: String, text: String, mine: Boolean) {
    val c = OT.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(who, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mine) Box(Modifier.width(3.dp).height(20.dp).clip(CircleShape).background(c.accent))
            Text(
                text,
                style = if (mine) OT.type.headline else OT.type.callout,
                color = if (mine) c.labelPrimary else c.labelSecondary,
            )
        }
    }
}

// MARK: - 02 Sign in

@Composable
private fun OnbSignInStep(
    email: String,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onEmail: () -> Unit,
) {
    val c = OT.colors
    val emailValid = email.trim().let { it.contains('@') && it.substringAfter('@').contains('.') }
    OnbScreen(onBack = onBack, actions = null) {
        OnbTitle("Sign in or create an account")
        Text(
            "Your auditions, reader tracks and takes, on every device you own.",
            style = OT.type.callout,
            color = c.labelSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )

        // Plain styled button — no real auth; it just advances.
        PrimaryButton(
            text = "Continue with Google",
            onClick = onGoogle,
            tint = c.labelPrimary,
            contentColor = c.bgCanvas,
            modifier = Modifier.padding(top = 24.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(c.separator))
            Text("or", style = OT.type.footnote, color = c.labelTertiary)
            Box(Modifier.weight(1f).height(1.dp).background(c.separator))
        }

        Text(
            "Email",
            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = c.labelSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OnbField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "you@example.com",
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (emailValid) onEmail() }),
            contentLabel = "Email",
        )
        SecondaryButton(
            text = "Continue with email",
            onClick = onEmail,
            enabled = emailValid,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, null, tint = c.labelTertiary, modifier = Modifier.size(14.dp))
            Text("Takes stay on this phone until you send them.", style = OT.type.footnote, color = c.labelSecondary)
        }
        Text(
            "By continuing you agree to the Terms and Privacy Policy.",
            style = OT.type.caption,
            color = c.labelTertiary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

// MARK: - 02.2 Email code

@Composable
private fun OnbEmailCodeStep(email: String, onBack: () -> Unit, onVerified: () -> Unit) {
    val c = OT.colors
    var code by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableIntStateOf(30) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(seconds) {
        if (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
    }
    LaunchedEffect(code) {
        if (code.length == 6) {
            delay(250)
            onVerified()
        }
    }

    OnbScreen(onBack = onBack, actions = null) {
        OnbTitle("Check your email")
        Text(
            "We sent a 6-digit code to ${email.trim()}",
            style = OT.type.callout,
            color = c.labelSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )

        BasicTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .focusRequester(focus)
                .semantics { contentDescription = "6-digit code" },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    // The real (invisible) text field sits under the digit boxes so focus and IME work.
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { inner() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 6) {
                            val ch = code.getOrNull(i)
                            val current = i == code.length
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.fillField)
                                    .then(
                                        if (current) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(12.dp)) else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (ch != null) Text(ch.toString(), style = OT.type.title, color = c.labelPrimary)
                            }
                        }
                    }
                }
            },
        )

        val resend = buildAnnotatedString {
            withStyle(SpanStyle(color = c.labelSecondary)) {
                if (seconds > 0) append("Didn’t get it? Resend in 0:${seconds.toString().padStart(2, '0')}")
                else append("Didn’t get it?")
            }
        }
        Row(
            Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(resend, style = OT.type.footnote)
            if (seconds == 0) {
                Text(
                    "Resend",
                    style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { seconds = 30; code = "" }
                        .padding(horizontal = 6.dp, vertical = 14.dp),
                )
            }
            Text(" · ", style = OT.type.footnote, color = c.labelTertiary)
            Text(
                "Use a different email",
                style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 4.dp, vertical = 14.dp),
            )
        }
    }
}

// MARK: - 03 Your slate

@Composable
private fun OnbSlateStep(onBack: () -> Unit, onContinue: () -> Unit) {
    val store = LocalAppStore.current
    val slate = store.slate
    val c = OT.colors
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    OnbScreen(
        onBack = onBack,
        stepIndex = 1,
        actions = {
            PrimaryButton(text = "Continue", onClick = onContinue)
            PlainButton(text = "Skip, I’ll do this later", onClick = onContinue)
        },
    ) {
        OnbTitle("How casting meets you")
        Text(
            "Said at the start of every tape. Change it any time in Profile.",
            style = OT.type.callout,
            color = c.labelSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )

        // Slate preview — a dark clapper card that updates as you type.
        Column(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OT.camera.bg)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SLATE PREVIEW",
                    style = OT.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = OT.camera.label2,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.GraphicEq, null, tint = OT.camera.label2, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "≈ 0:05",
                    style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = OT.camera.label2,
                )
            }
            Text(
                slate.name.ifBlank { "Your name" },
                style = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
                color = OT.camera.label,
                modifier = Modifier.padding(top = 6.dp),
            )
            val sub = listOf(slate.height, slate.basedIn).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = OT.type.callout.copy(fontWeight = FontWeight.Medium), color = OT.camera.label2)
            }
        }

        Column(
            Modifier.padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OnbLabeledField(
                label = buildAnnotatedString {
                    append("Full name ")
                    withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium)) { append("· from your Google Account") }
                },
                plainLabel = "Full name",
            ) {
                OnbField(
                    value = slate.name,
                    onValueChange = { store.slate = store.slate.copy(name = it) },
                    placeholder = "Your name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = next,
                    contentLabel = "Full name",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnbLabeledField(label = buildAnnotatedString { append("Height") }, plainLabel = "Height", modifier = Modifier.weight(1f)) {
                    OnbField(
                        value = slate.height,
                        onValueChange = { store.slate = store.slate.copy(height = it) },
                        placeholder = "5′ 11″",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = next,
                        contentLabel = "Height",
                    )
                }
                OnbLabeledField(label = buildAnnotatedString { append("Based in") }, plainLabel = "Based in", modifier = Modifier.weight(1f)) {
                    OnbField(
                        value = slate.basedIn,
                        onValueChange = { store.slate = store.slate.copy(basedIn = it) },
                        placeholder = "City",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = next,
                        contentLabel = "Based in",
                    )
                }
            }
            OnbLabeledField(
                label = buildAnnotatedString {
                    append("Representation ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("(optional)") }
                },
                plainLabel = "Representation",
                hint = "Leave blank if you represent yourself.",
            ) {
                OnbField(
                    value = slate.representation,
                    onValueChange = { store.slate = store.slate.copy(representation = it) },
                    placeholder = "Agency name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    contentLabel = "Representation",
                )
            }
        }
    }
}

@Composable
private fun OnbLabeledField(
    label: androidx.compose.ui.text.AnnotatedString,
    plainLabel: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    field: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = OT.colors.labelSecondary,
            modifier = Modifier.padding(start = 16.dp).semantics { contentDescription = plainLabel },
        )
        field()
        if (hint != null) {
            Text(hint, style = OT.type.footnote, color = OT.colors.labelTertiary, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** fillField text field: 52 tall, 14 radius (or capsule), accent outline when focused. */
@Composable
private fun OnbField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    contentLabel: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = OT.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = OT.type.body.copy(color = c.labelPrimary),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = contentLabel },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(shape)
                    .background(c.fillField)
                    .then(if (focused) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = OT.type.body, color = c.labelTertiary, maxLines = 1)
                inner()
            }
        },
    )
}

// MARK: - 04 Camera & mic

@Composable
private fun OnbPermissionsStep(
    onBack: () -> Unit,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    onNotNow: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result.values.isNotEmpty() && result.values.all { it }
        if (ok) onGranted() else onDenied()
    }
    val request: () -> Unit = {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (perms.all { onbHasPermission(context, it) }) onGranted() else launcher.launch(perms)
    }

    OnbScreen(
        onBack = onBack,
        stepIndex = 2,
        actions = {
            OnbAskCaption("Your phone asks next. Tap Allow for each.")
            PrimaryButton(text = "Allow camera & mic", onClick = request)
            PlainButton(text = "Not now", onClick = onNotNow)
        },
    ) {
        OnbViewfinder(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))

        OnbTitle("Your camera and mic, only for your tapes", modifier = Modifier.padding(top = 28.dp))

        Column(Modifier.padding(top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OnbBenefitRow(Icons.Filled.Fullscreen, "Check before you roll", "Framing, light and sound, in one glance.")
            OnbBenefitRow(Icons.Filled.GraphicEq, "Start with your voice", "Say “Action” from your mark. No running back to the phone.")
            OnbBenefitRow(Icons.Filled.Lock, "Private until you send", "Takes stay on this phone.")
        }
    }
}

/** Viewfinder corner brackets framing a camera and a mic bubble. */
@Composable
private fun OnbViewfinder(modifier: Modifier = Modifier) {
    val line = OT.colors.labelTertiary.copy(alpha = 0.6f)
    Box(modifier.width(250.dp).height(150.dp)) {
        val corner = 26.dp
        val w = 2.dp
        // top-left
        Box(Modifier.align(Alignment.TopStart).width(corner).height(w).background(line))
        Box(Modifier.align(Alignment.TopStart).width(w).height(corner).background(line))
        // top-right
        Box(Modifier.align(Alignment.TopEnd).width(corner).height(w).background(line))
        Box(Modifier.align(Alignment.TopEnd).width(w).height(corner).background(line))
        // bottom-left
        Box(Modifier.align(Alignment.BottomStart).width(corner).height(w).background(line))
        Box(Modifier.align(Alignment.BottomStart).width(w).height(corner).background(line))
        // bottom-right
        Box(Modifier.align(Alignment.BottomEnd).width(corner).height(w).background(line))
        Box(Modifier.align(Alignment.BottomEnd).width(w).height(corner).background(line))
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OnbBubble(Icons.Filled.Videocam, size = 76.dp)
            OnbBubble(Icons.Filled.Mic, size = 76.dp)
        }
    }
}

// MARK: - 04.1 Camera off

@Composable
private fun OnbCameraOffStep(onBack: () -> Unit, onContinue: () -> Unit) {
    val c = OT.colors
    val context = LocalContext.current
    val openSettings: () -> Unit = {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        onContinue()
    }

    OnbScreen(
        onBack = onBack,
        actions = {
            PrimaryButton(text = "Open Settings", onClick = openSettings)
            PlainButton(text = "Not now", onClick = onContinue)
        },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 20.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(c.warning.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.VisibilityOff, null, tint = c.warning, modifier = Modifier.size(40.dp)) }

        OnbTitle(
            "The camera is off for OneTake",
            style = OT.type.title.copy(fontSize = 30.sp, lineHeight = 36.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        )
        Text(
            "You can still add auditions and learn your lines. Turn it on when you’re ready to tape.",
            style = OT.type.callout,
            color = c.labelSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )

        Column(
            Modifier
                .padding(top = 24.dp, bottom = 16.dp)
                .fillMaxWidth()
                .otCard(radius = 22.dp, color = c.surfaceCard),
        ) {
            OnbSettingsStep(1, "Open Settings", toggle = false, divider = false)
            OnbSettingsStep(2, "Tap Permissions", toggle = false, divider = true)
            OnbSettingsStep(3, "Camera", toggle = true, divider = true)
            OnbSettingsStep(4, "Microphone", toggle = true, divider = true)
        }
    }
}

@Composable
private fun OnbSettingsStep(n: Int, text: String, toggle: Boolean, divider: Boolean) {
    val c = OT.colors
    Column {
        if (divider) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(c.fillQuiet),
                contentAlignment = Alignment.Center,
            ) { Text("$n", style = OT.type.subhead.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.labelPrimary) }
            Text(text, style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
            if (toggle) {
                // Illustration of the switch to turn on (not interactive).
                Box(
                    Modifier.width(42.dp).height(26.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.CenterEnd,
                ) { Box(Modifier.padding(2.dp).size(22.dp).clip(CircleShape).background(Color.White)) }
            }
        }
    }
}

// MARK: - 04b Notifications

@Composable
private fun OnbNotificationsStep(onBack: () -> Unit, onDone: () -> Unit) {
    val c = OT.colors
    val context = LocalContext.current
    val needsRuntimePermission = Build.VERSION.SDK_INT >= 33
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> onDone() }
    val request: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !onbHasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDone()
        }
    }

    OnbScreen(
        onBack = onBack,
        stepIndex = 3,
        actions = {
            if (needsRuntimePermission) OnbAskCaption("Your phone asks next. Tap Allow.")
            PrimaryButton(text = "Allow notifications", onClick = request)
            PlainButton(text = "Not now", onClick = onDone)
        },
    ) {
        OnbBubble(Icons.Filled.Notifications, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))

        OnbTitle("Never miss a due date", modifier = Modifier.padding(top = 26.dp))

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OnbBenefitRow(Icons.Filled.Schedule, "The morning it’s due", "One reminder, so it never sneaks up on you.")
            OnbBenefitRow(Icons.Filled.CheckCircle, "Nothing else", "No marketing, no streaks. Just your own due dates.")
        }

        SectionLabel("Preview", modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 2.dp))
        Row(
            Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .otCard(radius = 18.dp, color = c.surfaceCard)
                .padding(14.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Movie, null, tint = c.labelOnAccent, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "OneTake",
                        style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.labelPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("8:00 AM", style = OT.type.footnote, color = c.labelTertiary)
                }
                Text("The Last Summer is due today at 10:00 AM. 2 of 4 ready.", style = OT.type.callout, color = c.labelPrimary)
            }
        }
    }
}
