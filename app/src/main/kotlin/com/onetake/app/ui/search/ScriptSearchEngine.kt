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
                val startIndex = line.text.indexOf(trimmed, ignoreCase = true).let { idx ->
                    if (idx >= 0) idx else 0
                }
                val endIndex = (startIndex + trimmed.length).coerceAtMost(line.text.length)
                DialogueMatch(
                    character = line.character,
                    project = line.project,
                    scene = line.scene,
                    line = line.text,
                    highlightSpan = startIndex until endIndex,
                    auditionId = line.auditionId,
                    practicePieceId = line.practicePieceId,
                )
            }
    }
}
