package com.onetake.app.ui.search

enum class SearchScope(val label: String) {
    ALL("All"),
    DIALOGUE("Dialogue"),
    CHARACTERS("Characters"),
    CUES("Cues"),
}

data class ScriptSearchLine(
    val character: String,
    val project: String,
    val scene: String,
    val text: String,
    val auditionId: String? = null,
    val practicePieceId: String? = null,
    /** Which scene of the audition the line is in, so a result opens that scene. */
    val sceneIndex: Int = 0,
    val isMine: Boolean = true,
)

data class DialogueMatch(
    val character: String,
    val project: String,
    val scene: String,
    val line: String,
    val highlightSpan: IntRange,
    val auditionId: String? = null,
    val practicePieceId: String? = null,
    val sceneIndex: Int = 0,
    val isMine: Boolean = true,
)

class ScriptSearchEngine(
    private val sampleScripts: List<ScriptSearchLine> = emptyList(),
) {
    fun search(
        query: String,
        dynamicLines: List<ScriptSearchLine> = emptyList(),
        scope: SearchScope = SearchScope.ALL,
    ): List<DialogueMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val allLines = sampleScripts + dynamicLines
        val filteredLines = when (scope) {
            SearchScope.ALL -> allLines.filter {
                it.text.contains(trimmed, ignoreCase = true) || it.character.contains(trimmed, ignoreCase = true)
            }
            SearchScope.DIALOGUE -> allLines.filter {
                it.text.contains(trimmed, ignoreCase = true)
            }
            SearchScope.CHARACTERS -> allLines.filter {
                it.character.contains(trimmed, ignoreCase = true)
            }
            SearchScope.CUES -> allLines.filter {
                !it.isMine && (it.text.contains(trimmed, ignoreCase = true) || it.character.contains(trimmed, ignoreCase = true))
            }
        }

        return filteredLines.map { line ->
            val start = line.text.indexOf(trimmed, ignoreCase = true)
            DialogueMatch(
                character = line.character,
                project = line.project,
                scene = line.scene,
                line = line.text,
                highlightSpan = if (start >= 0) start until start + trimmed.length else IntRange.EMPTY,
                auditionId = line.auditionId,
                practicePieceId = line.practicePieceId,
                sceneIndex = line.sceneIndex,
                isMine = line.isMine,
            )
        }
    }
}
