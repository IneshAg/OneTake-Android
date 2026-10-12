package com.onetake.app.ui.practice

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.PracticePiece
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme

@Composable
fun PracticeScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Practice & Lines",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = OneTakeTheme.colors.labelPrimary
        )
        Text(
            text = "Run sides with your recorded reader",
            fontSize = 14.sp,
            color = OneTakeTheme.colors.labelTertiary
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionLabel(text = "Your practice sides")
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(uiState.practice) { piece ->
                PracticePieceCard(piece = piece)
            }
        }
    }
}

@Composable
fun PracticePieceCard(piece: PracticePiece) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = OneTakeTheme.colors.surfaceCard,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = piece.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneTakeTheme.colors.labelPrimary
                    )
                    Text(
                        text = piece.detail,
                        fontSize = 13.sp,
                        color = OneTakeTheme.colors.labelSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OneTakeTheme.colors.accentSoft)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${piece.linesLearned}/${piece.linesTotal} lines",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneTakeTheme.colors.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val progress = if (piece.linesTotal > 0) piece.linesLearned.toFloat() / piece.linesTotal.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                color = OneTakeTheme.colors.accent,
                trackColor = OneTakeTheme.colors.surfaceSunken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Run lines with reader",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneTakeTheme.colors.accent
                )
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = OneTakeTheme.colors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
