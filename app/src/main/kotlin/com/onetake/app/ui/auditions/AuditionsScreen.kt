package com.onetake.app.ui.auditions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Navigator
import com.onetake.app.Route
import com.onetake.app.TabBarClearance
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.PieceKind
import com.onetake.app.models.otRelative
import com.onetake.app.ui.components.PrimaryStageCta
import com.onetake.app.ui.components.SpotlightStageScaffold
import com.onetake.app.ui.theme.OT

/**
 * Screen 1: Spotlight Stage Home / Auditions Screen
 * Radical focus: One Decision Per Screen.
 * Lit Stage displays "YOUR ONE THING TONIGHT" with 68dp Primary CTA.
 * In the Wings lists remaining auditions separated by 0.5dp hairlines.
 */
@Composable
fun AuditionsScreen(
    onOpenProfile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current

    // Active auditions sorted by due date ascending, followed by sent auditions
    val activeAuditions = store.active
    val sentAuditions = store.sent
    val allSorted = remember(activeAuditions, sentAuditions) { activeAuditions + sentAuditions }

    val heroAudition = allSorted.firstOrNull()
    val otherAuditions = if (allSorted.size > 1) allSorted.drop(1) else emptyList()

    SpotlightStageScaffold(
        modifier = modifier,
        topTrailingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF2A2723), CircleShape)
                    .clickable { onOpenProfile?.invoke() ?: nav.push(Route.Headshots) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = OT.colors.labelSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        wingsContent = if (heroAudition != null) {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "IN THE WINGS",
                        style = OT.type.stageOverline,
                        color = OT.colors.labelSecondary,
                    )
                    Text(
                        text = "+ New",
                        style = OT.type.actionPill,
                        color = OT.colors.accentGold,
                        modifier = Modifier.clickable { nav.push(Route.NewAudition) },
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = TabBarClearance + 16.dp),
                ) {
                    items(otherAuditions) { audition ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nav.push(Route.AuditionDetail(audition.id)) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = audition.project,
                                style = OT.type.wingRowTitle,
                                color = OT.colors.labelPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${audition.role} · " + if (audition.status == AuditionStatus.Sent) "Sent" else "Due " + audition.due.otRelative,
                                style = OT.type.footnote,
                                color = OT.colors.labelSecondary,
                            )
                        }
                        HorizontalDivider(
                            color = OT.colors.borderMuted.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (heroAudition != null) {
                Text(
                    text = "YOUR ONE THING TONIGHT",
                    style = OT.type.stageOverline,
                    color = OT.colors.accentGold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = heroAudition.nextAction.ifBlank { "Prepare Audition" },
                    style = OT.type.heroStageTitle,
                    color = OT.colors.labelPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${heroAudition.project} · ${heroAudition.role}",
                    style = OT.type.body.copy(fontWeight = FontWeight.Medium),
                    color = OT.colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Due ${heroAudition.due.otRelative}",
                    style = OT.type.footnote,
                    color = OT.colors.labelTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                PrimaryStageCta(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = { launchAuditionStep(nav, heroAudition) },
                )
            } else {
                Text(
                    text = "THE STAGE IS YOURS",
                    style = OT.type.stageOverline,
                    color = OT.colors.accentGold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Add an Audition",
                    style = OT.type.heroStageTitle,
                    color = OT.colors.labelPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Paste a breakdown, scan sides, or share a PDF",
                    style = OT.type.body,
                    color = OT.colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                PrimaryStageCta(
                    icon = Icons.Default.Add,
                    onClick = { nav.push(Route.NewAudition) },
                )
            }
        }
    }
}

private fun launchAuditionStep(nav: Navigator, audition: Audition) {
    val next = audition.nextPiece
    when (next?.kind) {
        PieceKind.Headshot -> nav.push(Route.Headshots(audition.id))
        PieceKind.Slate, PieceKind.FullLength -> nav.openCamera(CameraFlow.Shoot(audition.id))
        PieceKind.Scene -> {
            val sceneIndex = audition.scenes.indexOfFirst { it.title == next.title }.coerceAtLeast(0)
            nav.push(Route.Prepare(audition.id, sceneIndex))
        }
        null -> nav.push(Route.AuditionDetail(audition.id))
    }
}
