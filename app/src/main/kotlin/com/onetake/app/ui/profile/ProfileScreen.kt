package com.onetake.app.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.SlateInfo
import com.onetake.app.ui.components.HeadshotPlaceholder
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme

@Composable
fun ProfileScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditSlate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Profile",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = OneTakeTheme.colors.labelPrimary
        )
        Text(
            text = "Slate, headshots and recording settings",
            fontSize = 14.sp,
            color = OneTakeTheme.colors.labelTertiary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Slate Profile Card
        Surface(
            onClick = { showEditSlate = true },
            shape = RoundedCornerShape(20.dp),
            color = OneTakeTheme.colors.surfaceCard,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(18.dp)
            ) {
                HeadshotPlaceholder(size = 56.dp)

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.slate.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneTakeTheme.colors.labelPrimary
                    )
                    Text(
                        text = "${uiState.slate.height} · ${uiState.slate.basedIn}",
                        fontSize = 14.sp,
                        color = OneTakeTheme.colors.labelSecondary
                    )
                    if (uiState.slate.representation.isNotEmpty()) {
                        Text(
                            text = uiState.slate.representation,
                            fontSize = 13.sp,
                            color = OneTakeTheme.colors.labelTertiary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OneTakeTheme.colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit slate",
                        tint = OneTakeTheme.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recording Settings
        SectionLabel(text = "Every time you record")
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = OneTakeTheme.colors.surfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Slate at the start",
                    subtitle = "Always prompt your slate line first",
                    checked = uiState.slateAtStart,
                    onCheckedChange = { viewModel.updateSettings(slateAtStart = it) }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp).background(OneTakeTheme.colors.separator))

                SettingSwitchRow(
                    title = "Clean up room noise",
                    subtitle = "Reduce AC hum and room echoes",
                    checked = uiState.cleanNoise,
                    onCheckedChange = { viewModel.updateSettings(cleanNoise = it) }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp).background(OneTakeTheme.colors.separator))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column {
                        Text("Reader volume", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OneTakeTheme.colors.labelPrimary)
                        Text("Casting should hear you first", fontSize = 13.sp, color = OneTakeTheme.colors.labelTertiary)
                    }
                    Text(uiState.readerVolume, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneTakeTheme.colors.accent)
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp).background(OneTakeTheme.colors.separator))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column {
                        Text("Export format", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OneTakeTheme.colors.labelPrimary)
                        Text("Default render specs", fontSize = 13.sp, color = OneTakeTheme.colors.labelTertiary)
                    }
                    Text(uiState.exportSpec, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneTakeTheme.colors.accent)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // On device storage card
        SectionLabel(text = "Privacy & Storage")
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OneTakeTheme.colors.surfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ON THIS DEVICE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OneTakeTheme.colors.labelTertiary)
                    Text("626 MB", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OneTakeTheme.colors.labelPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Takes and recordings stay on this Android phone until you send them. Nothing is uploaded to third-party clouds.",
                    fontSize = 13.sp,
                    color = OneTakeTheme.colors.labelSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Edit Slate Dialog
    if (showEditSlate) {
        EditSlateDialog(
            currentSlate = uiState.slate,
            onDismiss = { showEditSlate = false },
            onSave = { updated ->
                viewModel.updateSlate(updated)
                showEditSlate = false
            }
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OneTakeTheme.colors.labelPrimary)
            Text(subtitle, fontSize = 13.sp, color = OneTakeTheme.colors.labelTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OneTakeTheme.colors.labelOnAccent,
                checkedTrackColor = OneTakeTheme.colors.accent
            )
        )
    }
}

@Composable
fun EditSlateDialog(
    currentSlate: SlateInfo,
    onDismiss: () -> Unit,
    onSave: (SlateInfo) -> Unit
) {
    var name by remember { mutableStateOf(currentSlate.name) }
    var height by remember { mutableStateOf(currentSlate.height) }
    var basedIn by remember { mutableStateOf(currentSlate.basedIn) }
    var representation by remember { mutableStateOf(currentSlate.representation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit slate", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Actor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Height (e.g. 5′ 11″)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = basedIn,
                    onValueChange = { basedIn = it },
                    label = { Text("Based in (e.g. Mumbai, LA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = representation,
                    onValueChange = { representation = it },
                    label = { Text("Representation (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spoken preview: \"$name. $height. $basedIn.\"",
                    fontSize = 12.sp,
                    color = OneTakeTheme.colors.accent
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        SlateInfo(
                            name = name.trim(),
                            height = height.trim(),
                            basedIn = basedIn.trim(),
                            representation = representation.trim()
                        )
                    )
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = OneTakeTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
