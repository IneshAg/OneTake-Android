package com.onetake.app.ui.auditions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.ui.auditions.AuditionFilter
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.liquidGlass
import com.onetake.app.ui.theme.OT

/**
 * Locked Dark Titanium Glass Dropdown:
 * Directly matches the squircle floating contextual card from filter.png.
 * Rounded 26dp continuous curvature, specular hairline edge, and left-aligned checkmark.
 */
@Composable
fun FilterDropdownMenu(
    expanded: Boolean,
    selectedFilter: AuditionFilter,
    onSelect: (AuditionFilter) -> Unit,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset((-80).dp, 10.dp),
) {
    val menuShape = remember { RoundedCornerShape(26.dp) }
    val darkTitaniumBg = Color(0xFA1A1A1E)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .widthIn(min = 200.dp, max = 220.dp)
            .clip(menuShape)
            .liquidGlass(menuShape, fallbackColor = darkTitaniumBg)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, menuShape)
            .background(darkTitaniumBg)
            .padding(vertical = 8.dp),
    ) {
        AuditionFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                    ) {
                        onSelect(filter)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = OT.colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = filter.label,
                    color = if (isSelected) Color.White else OT.colors.labelSecondary,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = (-0.01).sp,
                )
            }
        }
    }
}
