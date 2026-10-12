package com.onetake.app.ui.tapecheck

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.onetake.app.models.SampleData
import com.onetake.app.models.TapeFinding
import com.onetake.app.ui.components.OtPrimaryButton
import com.onetake.app.ui.components.ReadinessDial
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapeCheckScreen(
    auditionId: String,
    onBack: () -> Unit,
    onContinueToPackage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFinding by remember { mutableStateOf<TapeFinding?>(null) }
    var passedExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Tape check",
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
            // Status Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                ReadinessDial(
                    done = 11,
                    total = 13,
                    size = 72.dp,
                    strokeWidth = 7.dp
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text(
                        text = "Ready to send",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneTakeTheme.colors.labelPrimary
                    )
                    Text(
                        text = "2 things are worth a look. Nothing stops you sending.",
                        fontSize = 14.sp,
                        color = OneTakeTheme.colors.labelSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Worth a look section
            SectionLabel(text = "Worth a look")
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneTakeTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SampleData.findings.forEachIndexed { index, finding ->
                        FindingRow(finding = finding, onClick = { selectedFinding = finding })
                        if (index < SampleData.findings.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(vertical = 8.dp)
                                    .background(OneTakeTheme.colors.separator)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 11 Checks Passed
            Surface(
                onClick = { passedExpanded = !passedExpanded },
                shape = RoundedCornerShape(16.dp),
                color = OneTakeTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OneTakeTheme.colors.success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "11 automated checks passed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OneTakeTheme.colors.success
                        )
                    }
                    if (passedExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Framing, lighting, slate presence, 1080p format, file naming, audio synchronization, background cleanliness, eyeline continuity, focus lock.",
                            fontSize = 13.sp,
                            color = OneTakeTheme.colors.labelSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Bottom Continue button
        Surface(
            color = OneTakeTheme.colors.bgCanvas,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OtPrimaryButton(
                    text = "Continue to package",
                    onClick = onContinueToPackage
                )
            }
        }

        // Finding Dialog
        selectedFinding?.let { f ->
            AlertDialog(
                onDismissRequest = { selectedFinding = null },
                title = {
                    Text("${f.category} · ${f.where}")
                },
                text = {
                    Column {
                        Text(f.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(f.advice, fontSize = 14.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedFinding = null }) {
                        Text("I meant it")
                    }
                }
            )
        }
    }
}

@Composable
fun FindingRow(finding: TapeFinding, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OneTakeTheme.colors.dueTrack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = OneTakeTheme.colors.due,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${finding.category} · ${finding.where}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneTakeTheme.colors.due
            )
            Text(
                text = finding.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneTakeTheme.colors.labelPrimary
            )
        }
    }
}
