package com.onetake.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT
import com.onetake.app.ui.theme.StudioFinish

/**
 * Profile & Studio Settings Sheet:
 * Triggered by the Avatar button in the top action bar.
 * Contains the Dual Studio Appearance Switcher (Velvet Studio vs Obsidian Studio),
 * actor slate specs, headshots, and preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    currentFinish: StudioFinish,
    onSelectFinish: (StudioFinish) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val slate = store.slate
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cardShape = remember { RoundedCornerShape(24.dp) }
    val segShape = remember { RoundedCornerShape(16.dp) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF2121016),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Slate & Studio",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Dual Studio Appearance Switcher
            Text(
                text = "STUDIO FINISH",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.sp,
                color = Color(0xFF86868B),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(segShape)
                    .background(Color(0xFF0D0D10))
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, segShape)
                    .padding(4.dp),
            ) {
                val isVelvet = currentFinish == StudioFinish.VELVET
                val isObsidian = currentFinish == StudioFinish.OBSIDIAN

                // Velvet Studio Segment
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isVelvet) Color(0x33C8A8E0) else Color.Transparent)
                        .border(
                            width = if (isVelvet) 0.5.dp else 0.dp,
                            color = if (isVelvet) Color(0x66C8A8E0) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelectFinish(StudioFinish.VELVET) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isVelvet) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFC8A8E0),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Velvet Purple",
                        fontSize = 13.sp,
                        fontWeight = if (isVelvet) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isVelvet) Color.White else Color(0xFF86868B),
                    )
                }

                // Obsidian Studio Segment
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isObsidian) Color(0x33FFFFFF) else Color.Transparent)
                        .border(
                            width = if (isObsidian) 0.5.dp else 0.dp,
                            color = if (isObsidian) Color(0x66FFFFFF) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelectFinish(StudioFinish.OBSIDIAN) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isObsidian) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Obsidian Silver",
                        fontSize = 13.sp,
                        fontWeight = if (isObsidian) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isObsidian) Color.White else Color(0xFF86868B),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Actor Slate Info Card
            Text(
                text = "ACTOR SLATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.sp,
                color = Color(0xFF86868B),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color(0xFF0D0D10))
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            nav.push(Route.EditSlate)
                        }
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x44FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = slate.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${slate.height} · ${slate.basedIn}",
                                fontSize = 13.sp,
                                color = Color(0xFF86868B),
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Edit Slate",
                        tint = Color(0xFF86868B),
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)

                // Headshots shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            nav.push(Route.Headshots)
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Headshots & Representation",
                        fontSize = 14.sp,
                        color = Color.White,
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF86868B),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
