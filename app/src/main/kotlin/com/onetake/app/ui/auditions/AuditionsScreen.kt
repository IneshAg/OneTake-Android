package com.onetake.app.ui.auditions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.TabBarClearance
import com.onetake.app.models.AuditionStatus
import com.onetake.app.ui.auditions.components.AuditionsTopBar
import com.onetake.app.ui.auditions.components.HeroStageCard
import com.onetake.app.ui.auditions.components.InsetRosterList
import com.onetake.app.ui.components.SFSymbols
import com.onetake.app.ui.theme.OT
import java.time.Duration
import java.time.LocalDateTime

/**
 * System 2: Monolithic Auditions Screen
 * Radical reductionism: 0% blue status dots, 0% neon badges, 0% progress bar clutter.
 * Top bar actions -> Monolithic Hero Stage Card ("Up Next") -> Inset Roster List ("Upcoming Slates").
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
        when (currentFilter) {
            AuditionFilter.ACTIVE -> slates.filter { it.status != AuditionStatus.Sent }
            AuditionFilter.DUE_THIS_WEEK -> slates.filter { slate ->
                val days = Duration.between(now, slate.due).toDays()
                days in 0..7
            }
            AuditionFilter.SENT -> slates.filter { it.status == AuditionStatus.Sent }
            AuditionFilter.ALL -> slates
        }
    }

    val heroSlate = filteredSlates.firstOrNull()
    val upcomingSlates = if (filteredSlates.size > 1) filteredSlates.drop(1) else emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (heroSlate != null) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 18.dp)
                .padding(bottom = TabBarClearance + 16.dp),
        ) {
            // Top Action Bar with locked Dark Titanium Dropdown
            AuditionsTopBar(
                currentFilter = currentFilter,
                onSelectFilter = { currentFilter = it },
                onAddClick = { nav.showAddAudition() },
                onAvatarClick = { onOpenProfile?.invoke() ?: nav.push(Route.Headshots) },
            )

            if (heroSlate != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Exactly ONE Monolithic Hero Stage Card
                HeroStageCard(
                    slate = heroSlate,
                    onClick = { nav.push(Route.Prepare(heroSlate.id, 0)) },
                )

                if (upcomingSlates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    // Unified Inset Roster List
                    InsetRosterList(
                        slates = upcomingSlates,
                        onSlateClick = { slate -> nav.push(Route.Prepare(slate.id, 0)) },
                    )
                }
            } else {
                // Centered empty state with clapperboard symbol
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = SFSymbols.MovieClapperFill,
                            contentDescription = null,
                            tint = Color(0xFF86868B),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Auditions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF86868B),
                        )
                    }
                }
            }
        }

        // Add Audition Modal Sheet
        if (nav.isAddAuditionVisible) {
            AddAuditionSheet(onDismiss = { nav.isAddAuditionVisible = false })
        }
    }
}
