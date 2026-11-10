package com.onetake.app.ui.auditions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.ui.auditions.AuditionFilter
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * Top Action Bar for Auditions:
 * Dramatic editorial headline "Auditions" paired with:
 * [ Filter Button ], [ PROMINENT High-Visibility + New Pill ], [ Profile Avatar ].
 */
@Composable
fun AuditionsTopBar(
    currentFilter: AuditionFilter,
    onSelectFilter: (AuditionFilter) -> Unit,
    onAddClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val isFilterActive = currentFilter != AuditionFilter.default
    val circleShape = remember { CircleShape }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Screen Title: Editorial Bold
        Text(
            text = "Auditions",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).sp,
            color = Color.White,
        )

        // Actions cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Filter Button with Dark Titanium Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(circleShape)
                        .background(if (isFilterActive) OT.colors.accentGold else Color(0x1AFFFFFF))
                        .border(
                            width = GLASS_EDGE_WIDTH,
                            color = if (isFilterActive) OT.colors.accentGold else GLASS_EDGE_COLOR,
                            shape = circleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                        ) {
                            dropdownExpanded = !dropdownExpanded
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter Slates",
                        tint = if (isFilterActive) Color(0xFF141416) else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                FilterDropdownMenu(
                    expanded = dropdownExpanded,
                    selectedFilter = currentFilter,
                    onSelect = onSelectFilter,
                    onDismiss = { dropdownExpanded = false },
                )
            }

            // 2. PROMINENT High-Visibility "+ New" Pill Button
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(OT.colors.accentGold)
                    .border(0.5.dp, Color(0x40FFFFFF), RoundedCornerShape(19.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAddClick,
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF141416),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "New",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF141416),
                )
            }

            // 3. Avatar Profile Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(circleShape)
                    .background(Color(0x1AFFFFFF))
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, circleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAvatarClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
