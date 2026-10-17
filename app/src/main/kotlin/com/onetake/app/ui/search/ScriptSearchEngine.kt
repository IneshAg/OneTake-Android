package com.onetake.app.ui.search

data class ScriptSearchLine(
    val character: String,
    val project: String,
    val scene: String,
    val text: String,
)

data class DialogueMatch(
    val character: String,
    val project: String,
    val scene: String,
    val line: String,
    val highlightSpan: IntRange,
)

class ScriptSearchEngine(
    private val sampleScripts: List<ScriptSearchLine> = listOf(
        ScriptSearchLine(
            character = "Arjun",
            project = "The Last Summer",
            scene = "Scene 3",
            text = "I never made that promise to you, Elena. You know the train leaves at dawn.",
        ),
        ScriptSearchLine(
            character = "Detective Rao",
            project = "Northbound",
            scene = "Scene 1",
            text = "A detective doesn't give promises, councilman. We collect facts.",
        ),
        ScriptSearchLine(
            character = "Marcus Vance",
            project = "Neon Horizon",
            scene = "Scene 4",
            text = "If the mainframe locks us out, we burn the credentials.",
        )
    ),
) {
    fun search(query: String): List<DialogueMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return sampleScripts
            .filter { it.text.contains(trimmed, ignoreCase = true) || it.character.contains(trimmed, ignoreCase = true) }
            .map { line ->
                val startIndex = line.text.indexOf(trimmed, ignoreCase = true).coerceAtLeast(0)
                val endIndex = startIndex + trimmed.length
                DialogueMatch(
                    character = line.character,
                    project = line.project,
                    scene = line.scene,
                    line = line.text,
                    highlightSpan = startIndex until endIndex,
                )
            }
    }
}
