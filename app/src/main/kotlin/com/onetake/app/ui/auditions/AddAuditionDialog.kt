package com.onetake.app.ui.auditions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.ui.components.OtPrimaryButton
import com.onetake.app.ui.components.OtSecondaryButton
import com.onetake.app.ui.theme.OneTakeTheme

@Composable
fun AddAuditionDialog(
    onDismiss: () -> Unit,
    onAdd: (Audition) -> Unit
) {
    var project by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("Feature film") }
    var role by remember { mutableStateOf("") }
    var roleSize by remember { mutableStateOf("Lead") }
    var castingOffice by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OneTakeTheme.colors.surfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add audition",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneTakeTheme.colors.labelPrimary
                )
                Text(
                    text = "OneTake will build your pieces checklist.",
                    fontSize = 13.sp,
                    color = OneTakeTheme.colors.labelSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = projectType,
                    onValueChange = { projectType = it },
                    label = { Text("Project type (e.g. Feature, Series)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = castingOffice,
                    onValueChange = { castingOffice = it },
                    label = { Text("Casting office") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OtSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    OtPrimaryButton(
                        text = "Create",
                        enabled = project.isNotBlank() && role.isNotBlank(),
                        onClick = {
                            val newAudition = Audition(
                                project = project.trim(),
                                projectType = projectType.ifBlank { "Feature film" },
                                role = role.trim(),
                                roleSize = roleSize,
                                castingOffice = castingOffice.trim(),
                                dueEpochMs = System.currentTimeMillis() + (48 * 3600 * 1000L),
                                asks = listOf("Slate first", "Actors Access"),
                                pieces = listOf(
                                    Piece(kind = PieceKind.HEADSHOT, title = "Headshot", detail = "Front", isDone = false),
                                    Piece(kind = PieceKind.SLATE, title = "Slate", detail = "Name, height, location", isDone = false),
                                    Piece(kind = PieceKind.FULL_LENGTH, title = "Full length", detail = "Head to toe", isDone = false),
                                    Piece(kind = PieceKind.SCENE, title = "Scene 1", detail = "", isDone = false)
                                ),
                                destination = Destination.ACTORS_ACCESS,
                                status = AuditionStatus.DUE
                            )
                            onAdd(newAudition)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
