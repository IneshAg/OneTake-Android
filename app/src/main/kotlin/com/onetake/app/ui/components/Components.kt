package com.onetake.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.ui.theme.OneTakeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReadinessDial(
    done: Int,
    total: Int,
    size: Dp = 56.dp,
    strokeWidth: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val sweepFraction = if (total > 0) done.toFloat() / total.toFloat() else 0f
    val isComplete = done >= total && total > 0
    val progressColor = if (isComplete) OneTakeTheme.colors.success else OneTakeTheme.colors.accent
    val trackColor = OneTakeTheme.colors.separator

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val radius = (size.toPx() - strokePx) / 2
            // Track
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = strokePx)
            )
            // Progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * sweepFraction,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        if (isComplete) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Complete",
                tint = progressColor,
                modifier = Modifier.size(size * 0.45f)
            )
        } else {
            Text(
                text = "$done/$total",
                fontSize = (size.value * 0.28).sp,
                fontWeight = FontWeight.Bold,
                color = OneTakeTheme.colors.labelPrimary
            )
        }
    }
}

@Composable
fun HeadshotPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(OneTakeTheme.colors.accentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Headshot",
            tint = OneTakeTheme.colors.accent,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = OneTakeTheme.colors.labelTertiary,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun AuditionCard(
    audition: Audition,
    onClick: () -> Unit,
    onNextActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = OneTakeTheme.colors.surfaceCard,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Due / status badge
                StatusBadge(audition = audition)

                ReadinessDial(
                    done = audition.readyCount,
                    total = audition.totalCount,
                    size = 40.dp,
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = audition.project,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OneTakeTheme.colors.labelPrimary
            )

            Text(
                text = "${audition.projectType} · ${audition.role} (${audition.roleSize})",
                fontSize = 14.sp,
                color = OneTakeTheme.colors.labelSecondary
            )

            if (audition.castingOffice.isNotEmpty()) {
                Text(
                    text = audition.castingOffice,
                    fontSize = 13.sp,
                    color = OneTakeTheme.colors.labelTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OneTakeTheme.colors.separator)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onNextActionClick != null) Modifier.clickable { onNextActionClick() }
                        else Modifier
                    )
            ) {
                Text(
                    text = "Next: ${audition.nextAction}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (audition.status == AuditionStatus.SENT) OneTakeTheme.colors.success else OneTakeTheme.colors.accent,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = OneTakeTheme.colors.labelTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(audition: Audition) {
    when (audition.status) {
        AuditionStatus.SENT -> {
            val dateStr = audition.sentDateEpochMs?.let {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it))
            } ?: "Sent"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneTakeTheme.colors.success.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Sent · $dateStr",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneTakeTheme.colors.success
                )
            }
        }
        AuditionStatus.READY -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneTakeTheme.colors.success.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Ready to send",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneTakeTheme.colors.success
                )
            }
        }
        AuditionStatus.DUE -> {
            val hours = audition.hoursLeft
            val text = when {
                hours < 24 -> "Due in $hours h"
                hours < 48 -> "Due tomorrow"
                else -> {
                    val days = hours / 24
                    "Due in $days d"
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneTakeTheme.colors.dueTrack)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneTakeTheme.colors.due
                )
            }
        }
    }
}

@Composable
fun OtPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OneTakeTheme.colors.accent,
            contentColor = OneTakeTheme.colors.labelOnAccent
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun OtSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OneTakeTheme.colors.labelPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
