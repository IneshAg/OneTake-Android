package com.onetake.app.data

import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene

object ScriptParser {
    private val SLUG_PREFIXES = listOf("INT.", "EXT.", "INT ", "EXT ", "SCENE", "ACT", "FADE", "CUT TO", "TITLE:")
    private val COLON_REGEX = Regex("""^([A-Za-z0-9 '\-\.]{2,30})\s*:\s*(.*)$""")
    private val CHARACTER_HEADING_REGEX = Regex("""^([A-Z0-9 '\-\.]{2,30})(\s*\(.*\))?$""")
    private val PARENTHETICAL_REGEX = Regex("""^\(.*\)$""")

    fun extractCharacters(rawText: String): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val characters = LinkedHashSet<String>()

        for (line in lines) {
            val colonMatch = COLON_REGEX.matchEntire(line)
            if (colonMatch != null) {
                val candidate = colonMatch.groupValues[1].trim().uppercase()
                if (!isSlugOrTransition(candidate)) {
                    characters.add(candidate)
                }
                continue
            }

            if (isCharacterHeading(line)) {
                val name = line.substringBefore("(").trim()
                if (!isSlugOrTransition(name)) {
                    characters.add(name)
                }
            }
        }

        return characters.toList()
    }

    fun parse(
        rawText: String,
        myCharacter: String,
        sceneTitle: String = "Scene 1",
        sceneNumber: Int = 1,
    ): ScriptScene {
        val rawLines = rawText.lines().map { it.trim() }
        val extractedLines = mutableListOf<ScriptLine>()
        var detectedHeading = ""

        // First check if there is a scene heading like INT. COFFEE SHOP - DAY
        for (line in rawLines) {
            if (line.isNotBlank() && isSlugOrTransition(line.uppercase())) {
                detectedHeading = line
                break
            }
        }
        if (detectedHeading.isBlank()) {
            detectedHeading = sceneTitle
        }

        // Check if format is predominantly COLON_REGEX
        val colonMatchesCount = rawLines.count { COLON_REGEX.matches(it) }
        val isColonFormat = colonMatchesCount > 0 && colonMatchesCount >= rawLines.count { isCharacterHeading(it) }

        if (isColonFormat) {
            for (line in rawLines) {
                if (line.isBlank() || isSlugOrTransition(line.uppercase())) continue
                val match = COLON_REGEX.matchEntire(line)
                if (match != null) {
                    val char = match.groupValues[1].trim().uppercase()
                    val text = match.groupValues[2].trim()
                    if (text.isNotBlank()) {
                        extractedLines.add(
                            ScriptLine(
                                character = char,
                                text = text,
                                isMine = char.equals(myCharacter, ignoreCase = true),
                            )
                        )
                    }
                }
            }
        } else {
            var currentCharacter: String? = null
            val currentDialogue = StringBuilder()

            fun flushDialogue() {
                val char = currentCharacter
                val text = currentDialogue.toString().trim()
                if (char != null && text.isNotBlank()) {
                    extractedLines.add(
                        ScriptLine(
                            character = char,
                            text = text,
                            isMine = char.equals(myCharacter, ignoreCase = true),
                        )
                    )
                }
                currentDialogue.clear()
            }

            for (line in rawLines) {
                if (line.isBlank()) {
                    continue
                }

                if (isSlugOrTransition(line.uppercase())) {
                    continue
                }

                if (isCharacterHeading(line)) {
                    flushDialogue()
                    currentCharacter = line.substringBefore("(").trim()
                } else if (PARENTHETICAL_REGEX.matches(line)) {
                    // Include parenthetical or skip; we include it smoothly in the dialogue
                    if (currentDialogue.isNotEmpty()) currentDialogue.append(" ")
                    currentDialogue.append(line)
                } else if (currentCharacter != null) {
                    if (currentDialogue.isNotEmpty()) currentDialogue.append(" ")
                    currentDialogue.append(line)
                }
            }
            flushDialogue()
        }

        // Monologue fallback: if no dialogue blocks were recognized
        if (extractedLines.isEmpty() && rawText.isNotBlank()) {
            extractedLines.add(
                ScriptLine(
                    character = myCharacter.ifBlank { "Actor" },
                    text = rawText.trim(),
                    isMine = true,
                )
            )
        }

        val totalWords = extractedLines.sumOf { it.text.split(Regex("\\s+")).size }
        val estimatedSeconds = maxOf(20, (totalWords * 60) / 130)

        return ScriptScene(
            number = sceneNumber,
            title = sceneTitle,
            heading = detectedHeading,
            lines = extractedLines,
            estimatedSeconds = estimatedSeconds,
        )
    }

    private fun isSlugOrTransition(text: String): Boolean {
        val upper = text.uppercase()
        return SLUG_PREFIXES.any { upper.startsWith(it) } ||
                upper.endsWith("TO:") ||
                upper == "FADE OUT." ||
                upper == "THE END"
    }

    private fun isCharacterHeading(line: String): Boolean {
        if (line.length > 35) return false
        val upper = line.uppercase()
        if (isSlugOrTransition(upper)) return false
        return CHARACTER_HEADING_REGEX.matches(line) && line == upper
    }
}
