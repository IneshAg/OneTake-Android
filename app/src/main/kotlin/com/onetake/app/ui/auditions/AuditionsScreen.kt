package com.onetake.app.ui.auditions

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Navigator
import com.onetake.app.Route
import com.onetake.app.TabBarClearance
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.PieceKind
import com.onetake.app.ui.auditions.components.AuditionsTopBar
import com.onetake.app.ui.auditions.components.HeroStageCard
import com.onetake.app.ui.auditions.components.InsetRosterList
import com.onetake.app.ui.components.SFSymbols
import com.onetake.app.ui.components.StageLightBar
import com.onetake.app.ui.theme.OT
import java.time.Duration
import java.time.LocalDateTime

/**
 * Screen 1: Spotlight Stage Auditions Screen
 * Masterclass theatrical UX:
 * - Glowing Stage Light Bar at top of stage.
 * - Top Action Bar with Editorial Headline, Dark Titanium Filter dropdown, and PROMINENT "+ New" Button.
 * - Monolithic Hero Stage Card ("YOUR ONE THING TONIGHT").
 * - Inset Roster List ("IN THE WINGS").
 * - Fluid, intuitive, elegant.
 */
@Composable
fun AuditionsScreen(
    onOpenProfile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val slates = store.auditions

    var currentFilter by remember { mutableStateOf(AuditionFilter.ACTIVE) }

    // Filter computation
    val filteredSlates = remember(slates, currentFilter) {
        val now = LocalDateTime.now()
        val open = slates.filter { it.status != AuditionStatus.Sent }.sortedBy { it.due }
        val sent = slates.filter { it.status == AuditionStatus.Sent }.sortedByDescending { it.sentDate ?: it.due }
        when (currentFilter) {
            AuditionFilter.ACTIVE -> open
            AuditionFilter.DUE_THIS_WEEK -> open.filter {
                val days = Duration.between(now.toLocalDate().atStartOfDay(), it.due.toLocalDate().atStartOfDay()).toDays()
                days in 0..7
            }
            AuditionFilter.SENT -> sent
            AuditionFilter.ALL -> open + sent
        }
    }

    val heroSlate = filteredSlates.firstOrNull()
    val upcomingSlates = if (filteredSlates.size > 1) filteredSlates.drop(1) else emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas)
    ) {
        // Stage Light Ambient Glowing Cone radiating downward
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF8EB).copy(alpha = 0.12f),
                            Color(0xFFE9D2A8).copy(alpha = 0.05f),
                            Color.Transparent,
                        )
                    )
                )
        )

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (heroSlate != null) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 20.dp)
                .padding(bottom = TabBarClearance + 20.dp),
        ) {
            // Stage Light Bar at top of stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                StageLightBar()
            }

            // Top Action Bar with Editorial Headline + PROMINENT "+ New" Button
            AuditionsTopBar(
                currentFilter = currentFilter,
                onSelectFilter = { currentFilter = it },
                onAddClick = { nav.push(Route.NewAudition) },
                onAvatarClick = { onOpenProfile?.invoke() ?: nav.push(Route.Headshots) },
            )

            if (heroSlate != null) {
                Spacer(modifier = Modifier.height(14.dp))

                // Monolithic Hero Stage Card ("YOUR ONE THING TONIGHT")
                HeroStageCard(
                    slate = heroSlate,
                    onClick = { launchAuditionStep(nav, heroSlate) },
                )

                if (upcomingSlates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    // Inset Roster List ("IN THE WINGS")
                    InsetRosterList(
                        slates = upcomingSlates,
                        onSlateClick = { slate -> nav.push(Route.AuditionDetail(slate.id)) },
                    )
                }
            } else {
                // Centered Empty State with Prominent "+ Add Audition" CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(0.5.dp, OT.colors.borderMuted.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = SFSymbols.MovieClapperFill,
                                contentDescription = null,
                                tint = OT.colors.accentGold,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "THE STAGE IS YOURS",
                            style = OT.type.stageOverline,
                            color = OT.colors.accentGold,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "No Auditions",
                            style = OT.type.heroStageTitle.copy(fontSize = 28.sp),
                            color = OT.colors.labelPrimary,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Paste a breakdown, scan sides, or add an audition",
                            style = OT.type.body,
                            color = OT.colors.labelSecondary,
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        // Prominent Add Audition Button
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(OT.colors.accentGold)
                                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(24.dp))
                                .clickable { nav.push(Route.NewAudition) }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF141416),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Add Audition",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF141416),
                            )
                        }
                    }
                }
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
