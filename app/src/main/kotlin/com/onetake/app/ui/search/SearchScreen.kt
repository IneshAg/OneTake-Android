package com.onetake.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.TabBarClearance
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

enum class SearchScope(val label: String) {
    ALL("All"),
    DIALOGUE("Dialogue"),
    CHARACTERS("Characters"),
    CUES("Cues"),
}

/**
 * Dedicated Standalone Search Screen:
 * Deep script dialogue search engine across PDF sides, characters, and projects.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val searchEngine = remember { ScriptSearchEngine() }

    var query by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(SearchScope.ALL) }

    val dynamicLines = remember(store.auditions, store.practice) {
        val fromAuditions = store.auditions.flatMap { audition ->
            audition.scenes.flatMapIndexed { sceneIndex, scene ->
                scene.lines.map { line ->
                    ScriptSearchLine(
                        character = line.character,
                        project = audition.project,
                        scene = scene.title.ifBlank { "Scene ${scene.number}" },
                        text = line.text,
                        auditionId = audition.id,
                        sceneIndex = sceneIndex,
                    )
                }
            }
        }
        // A practice piece made from an audition scene carries a copy of that scene — skip it, or
        // every one of its lines would show up twice.
        val auditionSceneIds = store.auditions.flatMap { it.scenes }.mapTo(HashSet()) { it.id }
        val fromPractice = store.practice.mapNotNull { piece ->
            piece.scene?.takeIf { it.id !in auditionSceneIds }?.let { s -> piece to s }
        }.flatMap { (piece, scene) ->
            scene.lines.map { line ->
                ScriptSearchLine(
                    character = line.character,
                    project = piece.title,
                    scene = scene.title.ifBlank { "Practice" },
                    text = line.text,
                    practicePieceId = piece.id,
                )
            }
        }
        fromAuditions + fromPractice
    }

    val dialogueMatches = remember(query, dynamicLines) { searchEngine.search(query, dynamicLines) }
    val slateMatches = remember(query, store.auditions) {
        if (query.isBlank()) emptyList()
        else store.auditions.filter {
            it.role.contains(query, ignoreCase = true) || it.project.contains(query, ignoreCase = true)
        }
    }

    val containerShape = remember { RoundedCornerShape(24.dp) }
    val inputShape = remember { RoundedCornerShape(16.dp) }

    val hasDialogueMatches = selectedScope in listOf(SearchScope.ALL, SearchScope.DIALOGUE) && dialogueMatches.isNotEmpty()
    val hasSlateMatches = selectedScope in listOf(SearchScope.ALL, SearchScope.CHARACTERS) && slateMatches.isNotEmpty()
    val hasResults = query.isNotBlank() && (hasDialogueMatches || hasSlateMatches)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (hasResults) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 18.dp)
                .padding(bottom = TabBarClearance + 16.dp),
        ) {
            // Header Title: Apple Bold
            Text(
                text = "Search",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.03).sp,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )

            // Deep Search Input Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(inputShape)
                    .background(Color(0xFF111114))
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, inputShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF86868B),
                        modifier = Modifier.size(18.dp),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(OT.colors.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search lines, characters, sides...",
                                    color = Color(0xFF86868B),
                                    fontSize = 15.sp,
                                )
                            }
                            innerTextField()
                        },
                    )

                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF86868B),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { query = "" },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scope Pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchScope.entries) { scope ->
                    val isSelected = scope == selectedScope
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color(0x14FFFFFF))
                            .border(
                                width = GLASS_EDGE_WIDTH,
                                color = if (isSelected) Color.White else GLASS_EDGE_COLOR,
                                shape = CircleShape,
                            )
                            .clickable { selectedScope = scope }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = scope.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color(0xFFA1A1A6),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Results Section
            if (query.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF55555A),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Global Script Search",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF86868B),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search lines across all sides, character cues, and slates",
                            fontSize = 13.sp,
                            color = Color(0xFF55555A),
                        )
                    }
                }
            } else if (!hasResults) {
                // Empty search result state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF86868B),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Results",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF86868B),
                        )
                    }
                }
            } else {
                // Dialogue matches in PDF sides
                if (hasDialogueMatches) {
                    Text(
                        text = "DIALOGUE MATCHES IN SIDES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp,
                        color = Color(0xFF86868B),
                        modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(containerShape)
                            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, containerShape)
                            .background(OT.colors.surfaceCard),
                    ) {
                        dialogueMatches.forEachIndexed { index, match ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val targetId = match.auditionId ?: match.practicePieceId
                                        if (targetId != null) {
                                            nav.push(Route.Prepare(targetId, match.sceneIndex))
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "${match.scene} · ${match.character}".uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OT.colors.accent,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = match.project,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${match.line}\"",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD2D2D7),
                                    lineHeight = 18.sp,
                                )
                            }
                            if (index < dialogueMatches.size - 1) {
                                HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Roles and slates matches
                if (hasSlateMatches) {
                    Text(
                        text = "AUDITION SLATES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp,
                        color = Color(0xFF86868B),
                        modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(containerShape)
                            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, containerShape)
                            .background(OT.colors.surfaceCard),
                    ) {
                        slateMatches.forEachIndexed { index, slate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.push(Route.AuditionDetail(slate.id)) }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = slate.role,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = slate.project,
                                        fontSize = 13.sp,
                                        color = Color(0xFF86868B),
                                    )
                                }
                            }
                            if (index < slateMatches.size - 1) {
                                HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
