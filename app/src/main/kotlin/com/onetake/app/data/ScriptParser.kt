package com.onetake.app.data

import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene

object ScriptParser {
    /** Scene headings and transitions. Word-anchored so dialogue that merely starts with the same
     *  letters ("Actually…", "Scenery…", "Faded…") isn't mistaken for one and dropped. */
    private val SLUG_REGEX = Regex(
        """^(?:(?:INT|EXT|EST|I/E|INT\./EXT)[.\s]|(?:SCENE|ACT)\s+[0-9IVXLC]+\b|FADE\s+(?:IN|OUT)\b|""" +
            """(?:CUT|DISSOLVE|SMASH CUT|MATCH CUT)\s+TO\b|TITLE:)""",
    )
    /** "NAME: line" — a speaker of at most three words, and the colon not followed by a digit,
     *  so "Submit by Oct 30 at 10:00 AM" isn't read as a character called "SUBMIT BY OCT 30 AT 10". */
    private val COLON_REGEX = Regex("""^([A-Za-z][A-Za-z0-9'\-\.]*(?: [A-Za-z0-9'\-\.]+){0,2})\s*:(?!\d)\s*(.*)$""")
    private val CHARACTER_HEADING_REGEX = Regex("""^([A-Z0-9 '\-\.]{2,30})(\s*\(.*\))?$""")
    private val PARENTHETICAL_REGEX = Regex("""^\(.*\)$""")

    /** Where a new scene starts: an all-caps slugline ("INT. CAFÉ — DAY", "EXT/INT CAR", "I/E") or a
     *  "SCENE 2" / "SC. 14A" header followed by nothing or a separator — so dialogue like "Scene two
     *  was better" or "Int. was the word" isn't taken for one. Transitions don't start scenes. */
    private val SLUGLINE_REGEX = Regex("""^(?:INT\.?/EXT|EXT\.?/INT|INT|EXT|EST|I/E)[.\s]""")
    private val SCENE_HEADER_REGEX = Regex("""^(?:SCENE|SC\.?)\s*\d+[A-Z]?\b(.*)$""", RegexOption.IGNORE_CASE)

    fun isSceneHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 80) return false
        if (t == t.uppercase() && SLUGLINE_REGEX.containsMatchIn(t)) return true
        val rest = SCENE_HEADER_REGEX.matchEntire(t)?.groupValues?.get(1)?.trim() ?: return false
        return rest.isEmpty() || rest.first() in ":.-–—("
    }

    /** Sides often hold several scenes; each becomes its own `ScriptScene` ("Scene 1", "Scene 2"…)
     *  so it can be prepared and shot on its own. Stretches with no dialogue between two headings
     *  are dropped. With no headings at all this is just `parse` on the whole text. */
    fun parseScenes(rawText: String, myCharacter: String): List<ScriptScene> {
        val chunks = mutableListOf<MutableList<String>>(mutableListOf())
        for (line in rawText.lines()) {
            if (isSceneHeading(line) && chunks.last().any { it.isNotBlank() && !isSceneHeading(it) }) {
                chunks.add(mutableListOf())
            }
            chunks.last().add(line)
        }
        val withDialogue = chunks.map { it.joinToString("\n") }.filter { extractCharacters(it).isNotEmpty() }
        if (withDialogue.size <= 1) return listOf(parse(rawText, myCharacter))
        return withDialogue.mapIndexed { i, chunk ->
            parse(chunk, myCharacter, sceneTitle = "Scene ${i + 1}", sceneNumber = i + 1)
        }
    }

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
                                isMine = isMyCue(char, myCharacter),
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
                            isMine = isMyCue(char, myCharacter),
                        )
                    )
                }
                currentDialogue.clear()
            }

            for (line in rawLines) {
                if (line.isBlank()) {
                    // A blank line ends the speech: what follows is action until the next cue,
                    // and must not be glued onto this character's dialogue.
                    flushDialogue()
                    currentCharacter = null
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
        val upper = text.trim().uppercase()
        return SLUG_REGEX.containsMatchIn(upper) || upper.endsWith(" TO:") || upper == "THE END"
    }

    /** Whether a cue is the actor's role. Exact match, or the same first name — casting lists
     *  "Maya Reyes" while the sides cue "MAYA". First names must be 3+ letters so a short cue
     *  like "AN" never matches "Anaya". */
    fun isMyCue(cue: String, role: String): Boolean {
        val c = cue.trim()
        val r = role.trim()
        if (c.isEmpty() || r.isEmpty()) return false
        if (c.equals(r, ignoreCase = true)) return true
        val cFirst = c.substringBefore(' ')
        val rFirst = r.substringBefore(' ')
        return cFirst.length >= 3 && cFirst.equals(rFirst, ignoreCase = true)
    }

    private fun isCharacterHeading(line: String): Boolean {
        if (line.length > 35) return false
        val upper = line.uppercase()
        if (isSlugOrTransition(upper)) return false
        return CHARACTER_HEADING_REGEX.matches(line) && line == upper
    }
}
