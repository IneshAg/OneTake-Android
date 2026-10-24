package com.onetake.app.ui.search

data class ScriptSearchLine(
    val character: String,
    val project: String,
    val scene: String,
    val text: String,
    val auditionId: String? = null,
    val practicePieceId: String? = null,
)

data class DialogueMatch(
    val character: String,
    val project: String,
    val scene: String,
    val line: String,
    val highlightSpan: IntRange,
    val auditionId: String? = null,
    val practicePieceId: String? = null,
)

class ScriptSearchEngine(
    private val sampleScripts: List<ScriptSearchLine> = emptyList(),
) {
    fun search(query: String, dynamicLines: List<ScriptSearchLine> = emptyList()): List<DialogueMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val allLines = sampleScripts + dynamicLines
        return allLines
            .filter { it.text.contains(trimmed, ignoreCase = true) || it.character.contains(trimmed, ignoreCase = true) }
            .map { line ->
                // Empty when only the character's name matched — nothing in the line to highlight.
                val start = line.text.indexOf(trimmed, ignoreCase = true)
                DialogueMatch(
                    character = line.character,
                    project = line.project,
                    scene = line.scene,
                    line = line.text,
                    highlightSpan = if (start >= 0) start until start + trimmed.length else IntRange.EMPTY,
                    auditionId = line.auditionId,
                    practicePieceId = line.practicePieceId,
                )
            }
    }
}
