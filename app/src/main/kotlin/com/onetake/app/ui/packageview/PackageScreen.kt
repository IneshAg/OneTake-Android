package com.onetake.app.ui.packageview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.ui.components.OtPrimaryButton
import com.onetake.app.ui.components.OtSecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageScreen(
    auditionId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val audition = uiState.auditions.firstOrNull { it.id == auditionId }
    val scope = rememberCoroutineScope()

    var isSending by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(audition?.status?.name == "SENT") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Package tape",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = OneTakeTheme.colors.labelPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OneTakeTheme.colors.labelPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = OneTakeTheme.colors.bgCanvas
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            audition?.let { a ->
                // Destination card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OneTakeTheme.colors.surfaceCard,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = a.destination.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneTakeTheme.colors.labelPrimary
                        )
                        Text(
                            text = a.destination.spec,
                            fontSize = 13.sp,
                            color = OneTakeTheme.colors.labelSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Files in package
                SectionLabel(text = "Files included in submission")
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OneTakeTheme.colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PackageFileRow(name = "1. Headshot — Front.jpg", size = "3.2 MB")
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 8.dp).background(OneTakeTheme.colors.separator))
                        PackageFileRow(name = "2. Slate — ${uiState.slate.name}.mp4", size = "18.4 MB")
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 8.dp).background(OneTakeTheme.colors.separator))
                        PackageFileRow(name = "3. Scene 1 — Apartment Confrontation.mp4", size = "142.1 MB")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submission summary
                if (isSent) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = OneTakeTheme.colors.success.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OneTakeTheme.colors.success,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = "Audition successfully sent!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneTakeTheme.colors.success
                                )
                                Text(
                                    text = "Private link is active for 30 days.",
                                    fontSize = 13.sp,
                                    color = OneTakeTheme.colors.labelSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            color = OneTakeTheme.colors.bgCanvas,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (!isSent) {
                    OtPrimaryButton(
                        text = if (isSending) "Generating submission package..." else "Send to ${audition?.destination?.displayName ?: "Destination"}",
                        enabled = !isSending,
                        onClick = {
                            isSending = true
                            scope.launch {
                                delay(1200L)
                                isSending = false
                                isSent = true
                                audition?.let { viewModel.markSent(it.id) }
                            }
                        }
                    )
                } else {
                    OtPrimaryButton(
                        text = "Done · Return to Auditions",
                        onClick = onDone
                    )
                }
            }
        }
    }
}

@Composable
fun PackageFileRow(name: String, size: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = OneTakeTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = OneTakeTheme.colors.labelPrimary
            )
        }
        Text(
            text = size,
            fontSize = 12.sp,
            color = OneTakeTheme.colors.labelTertiary
        )
    }
}
