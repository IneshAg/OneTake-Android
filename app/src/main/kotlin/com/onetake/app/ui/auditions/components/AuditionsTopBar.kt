package com.onetake.app.ui.auditions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
 * Top action bar for Auditions:
 * Large editorial headline "Auditions" paired with 3 anodized circular action buttons:
 * [ Filter (Sliders) ], [ + Add ], [ Avatar ].
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
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Screen Title: Pure, breathing editorial typography
        Text(
            text = "Auditions",
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.04).sp,
            color = Color.White,
        )

        // Actions cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Filter Sliders Button (Anchors the Dark Titanium Dropdown)
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(circleShape)
                        .background(
                            if (isFilterActive) Color.White else Color(0x1AFFFFFF)
                        )
                        .border(
                            width = GLASS_EDGE_WIDTH,
                            color = if (isFilterActive) Color.White else GLASS_EDGE_COLOR,
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
                        tint = if (isFilterActive) Color.Black else Color.White,
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

            // 2. Add (+) Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(circleShape)
                    .background(Color(0x1AFFFFFF))
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, circleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAddClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Audition",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            // 3. Avatar Profile Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(circleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(1.dp, Color(0x4DFFFFFF), circleShape)
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
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
