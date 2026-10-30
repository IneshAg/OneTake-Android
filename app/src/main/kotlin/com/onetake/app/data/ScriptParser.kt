package com.onetake.app.data

import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene

object ScriptParser {
    /** Scene headings and transitions. Word-anchored so dialogue that merely starts with the same
     *  letters ("Actually…", "Scenery…", "Faded…") isn't mistaken for one and dropped. */
    private val SLUG_REGEX = Regex(
        """^(?:(?:\d+[A-Z]?[.\s]+)?(?:INT\.?/EXT|EXT\.?/INT|INT|EXT|EST|I/E)[.\s]|(?:SCENE|ACT)\s+[0-9IVXLC]+[A-Z]?\b|FADE\s+(?:IN|OUT)\b|""" +
            """(?:CUT|DISSOLVE|SMASH CUT|MATCH CUT)\s+TO\b|TITLE:)""",
    )
    /** "NAME: line" — a speaker of at most three words, and the colon not followed by a digit,
     *  so "Submit by Oct 30 at 10:00 AM" isn't read as a character called "SUBMIT BY OCT 30 AT 10". */
    private val COLON_REGEX = Regex("""^([A-Za-z][A-Za-z0-9'\-\.#]*(?: [A-Za-z0-9'\-\.#]+){0,2})\s*:(?!\d)\s*(.*)$""")
    private val CHARACTER_HEADING_REGEX = Regex("""^(?=.*[A-Z])[A-Z0-9 '\-\.#/]{2,35}(?:\s*\([A-Z0-9 '\-\.,]+\))?$""")
    private val PARENTHETICAL_REGEX = Regex("""^\(.*\)$""")

    private val SLUGLINE_REGEX = Regex("""^(?:\d+[A-Z]?[.\s]+)?(?:INT\.?/EXT|EXT\.?/INT|INT|EXT|EST|I/E)[.\s]""")
    private val SCENE_HEADER_REGEX = Regex("""^(?:SCENE|SC\.?)\s*\d+[A-Z]?\b(.*)$""", RegexOption.IGNORE_CASE)
    private val LEADING_SCENE_NUM_REGEX = Regex("""^\d+[A-Z]?[.\s]+(?=(?:INT\.?/EXT|EXT\.?/INT|INT|EXT|EST|I/E)[.\s])""")
    private val TRAILING_SCENE_NUM_REGEX = Regex("""\s+\d+[A-Z]?$""")

    private val NON_CHARACTER_KEYS = setOf(
        "CONTINUED", "CONT'D", "MORE", "OMITTED", "START", "END", "THE END",
        "CONFIDENTIAL", "DRAFT", "SIDES", "BREAKDOWN", "SYNOPSIS", "STORYLINE",
        "WARDROBE", "PROPS", "NOTE", "NOTES", "PROJECT", "PROJECT TITLE", "PRODUCTION",
        "FILM", "SERIES", "SHOW", "TITLE", "TYPE", "PROJECT TYPE", "ROLE", "ROLES",
        "CHARACTER", "CHARACTER NAME", "ROLE SIZE", "CASTING", "CASTING DIRECTOR",
        "CASTING OFFICE", "CD", "DIRECTOR", "PRODUCER", "DUE", "DEADLINE",
        "SUBMISSION DEADLINE", "SELF TAPE", "SELF-TAPE", "TAPE", "SUBMIT", "SUBMIT VIA",
        "SUBMISSION", "SUBMISSIONS", "DATES", "SHOOT", "SHOOT DATES", "LOCATION",
        "RATE", "PAY", "UNION", "INSTRUCTIONS", "FORMAT", "SLATE", "AGE", "GENDER",
        "ETHNICITY", "EMAIL", "CONTACT", "LINK", "FROM", "TO", "CC", "SUBJECT", "RE", "FWD", "SENT", "DATE", "TIME",
        "FLASHBACK", "END FLASHBACK", "MONTAGE", "END MONTAGE", "INTERCUT",
        "BACK TO SCENE", "POV", "INSERT", "SUPER", "CHYRON", "SFX", "VFX",
    )

    private val NON_CHARACTER_PREFIX_REGEX = Regex(
        """^(?:CONTINUED\b|\(CONTINUED\)|\(MORE\)|(?:WHITE|BLUE|PINK|YELLOW|GREEN|GOLDENROD|BUFF|SALMON|CHERRY)\s+REV|REVISION\b|DRAFT\b|DO\s+NOT\s+DISTRIBUTE|PROPERTY\s+OF|COPYRIGHT\b|EPISODE\s+\d|ACT\s+[0-9IVX]+|END\s+OF\s+|ANGLE\s+ON\b|CLOSE\s+ON\b|WIDE\s+ON\b|BACK\s+TO\b|CUT\s+TO\b|SMASH\s+CUT\b|MATCH\s+CUT\b|DISSOLVE\s+TO\b|FADE\s+(?:IN|OUT)\b|PAGE\s+\d)"""
    )

    private val HONORIFICS = setOf(
        "dr", "mr", "mrs", "ms", "mx", "miss", "prof", "professor",
        "det", "detective", "off", "officer", "agt", "agent",
        "sgt", "sergeant", "capt", "captain", "lt", "lieutenant",
        "col", "colonel", "gen", "general", "nurse", "coach", "father", "sister", "rev",
    )

    fun cleanSlugHeading(line: String): String =
        line.trim()
            .replace(LEADING_SCENE_NUM_REGEX, "")
            .replace(TRAILING_SCENE_NUM_REGEX, "")
            .trim()

    fun isSceneHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 90) return false
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
        val rawLines = rawText.lines().map { it.trim() }
        val characters = LinkedHashSet<String>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (line.isBlank()) continue

            val colonMatch = COLON_REGEX.matchEntire(line)
            if (colonMatch != null) {
                val candidate = colonMatch.groupValues[1].trim().uppercase()
                val rhs = colonMatch.groupValues[2].trim()
                if (rhs.isNotBlank() && !isNonCharacterCue(candidate)) {
                    characters.add(candidate)
                }
                continue
            }

            if (isCharacterHeading(line) && hasSpokenFollowLine(rawLines, i)) {
                val name = line.substringBefore("(").trim()
                if (!isNonCharacterCue(name)) {
                    characters.add(name)
                }
            }
        }

        return characters.toList()
    }

    private fun hasSpokenFollowLine(lines: List<String>, cueIndex: Int): Boolean {
        val next = lines.getOrNull(cueIndex + 1)?.trim() ?: return false
        if (next.isBlank()) return false
        if (isSceneHeading(next) || isSlugOrTransition(next.uppercase())) return false
        return true
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

        for (line in rawLines) {
            if (line.isNotBlank() && isSceneHeading(line)) {
                detectedHeading = cleanSlugHeading(line)
                break
            }
        }
        if (detectedHeading.isBlank()) {
            detectedHeading = sceneTitle
        }

        val validColonCount = rawLines.count { line ->
            val m = COLON_REGEX.matchEntire(line) ?: return@count false
            m.groupValues[2].isNotBlank() && !isNonCharacterCue(m.groupValues[1].trim().uppercase())
        }
        val screenplayCueCount = rawLines.indices.count { idx ->
            isCharacterHeading(rawLines[idx]) && hasSpokenFollowLine(rawLines, idx)
        }
        val isColonFormat = validColonCount > 0 && validColonCount >= screenplayCueCount

        if (isColonFormat) {
            var activeChar: String? = null
            val activeText = StringBuilder()

            fun flushColon() {
                val c = activeChar
                val t = activeText.toString().trim()
                if (c != null && t.isNotBlank()) {
                    extractedLines.add(
                        ScriptLine(
                            character = c,
                            text = t,
                            isMine = isMyCue(c, myCharacter),
                        )
                    )
                }
                activeChar = null
                activeText.clear()
            }

            for (line in rawLines) {
                if (line.isBlank()) {
                    flushColon()
                    continue
                }
                if (isSceneHeading(line) || isSlugOrTransition(line.uppercase())) {
                    flushColon()
                    continue
                }
                val match = COLON_REGEX.matchEntire(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim().uppercase()
                    val text = match.groupValues[2].trim()
                    if (!isNonCharacterCue(candidate) && text.isNotBlank()) {
                        flushColon()
                        activeChar = candidate
                        activeText.append(text)
                    } else {
                        flushColon()
                    }
                } else if (activeChar != null) {
                    // Wrapped continuation line of the current colon-delimited speech turn
                    activeText.append(" ").append(line)
                }
            }
            flushColon()
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

            for (i in rawLines.indices) {
                val line = rawLines[i]
                if (line.isBlank()) {
                    flushDialogue()
                    currentCharacter = null
                    continue
                }

                if (isSceneHeading(line) || isSlugOrTransition(line.uppercase()) || isNonCharacterBannerLine(line)) {
                    flushDialogue()
                    currentCharacter = null
                    continue
                }

                // When currentCharacter == null, a valid character heading starts a speech block.
                // When currentCharacter != null, only switch speaker if the line is a valid character heading
                // AND has a spoken follow line (normally cues are preceded by a blank line anyway).
                if (isCharacterHeading(line) && hasSpokenFollowLine(rawLines, i) && currentCharacter == null) {
                    flushDialogue()
                    currentCharacter = line.substringBefore("(").trim()
                } else if (PARENTHETICAL_REGEX.matches(line)) {
                    if (currentCharacter != null) {
                        if (currentDialogue.isNotEmpty()) currentDialogue.append(" ")
                        currentDialogue.append(line)
                    }
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

    private fun isNonCharacterBannerLine(line: String): Boolean {
        val upper = line.trim().uppercase()
        if (upper.matches(Regex("""^\d{1,3}[A-Z]?\.?$"""))) return true
        return NON_CHARACTER_PREFIX_REGEX.containsMatchIn(upper) || upper in NON_CHARACTER_KEYS
    }

    private fun isNonCharacterCue(upperCandidate: String): Boolean {
        val cleaned = upperCandidate.trim().trimEnd(':').trim()
        if (cleaned.isEmpty()) return true
        if (isSlugOrTransition(cleaned)) return true
        if (cleaned in NON_CHARACTER_KEYS) return true
        if (NON_CHARACTER_PREFIX_REGEX.containsMatchIn(cleaned)) return true
        return false
    }

    private fun isSlugOrTransition(text: String): Boolean {
        val upper = text.trim().uppercase()
        return SLUG_REGEX.containsMatchIn(upper) || upper.endsWith(" TO:") || upper == "THE END"
    }

    /**
     * Whether a cue is the actor's role.
     * Supports exact matches (including 2-letter names like "Bo"), first/last name matches,
     * and honorific stripping ("Dr. Sarah Jenkins" matches "SARAH" or "DR. JENKINS", never "DR. PATEL").
     */
    fun isMyCue(cue: String, role: String): Boolean {
        val c = cue.trim()
        val r = role.trim()
        if (c.isEmpty() || r.isEmpty()) return false

        val cNorm = c.lowercase().replace(Regex("""\s+"""), " ")
        val rNorm = r.lowercase().replace(Regex("""\s+"""), " ")
        if (cNorm == rNorm) return true

        val cTokens = tokenizeName(cNorm)
        val rTokens = tokenizeName(rNorm)
        if (cTokens.isEmpty() || rTokens.isEmpty()) return false
        if (cTokens == rTokens) return true

        val cCore = stripHonorifics(cTokens)
        val rCore = stripHonorifics(rTokens)
        if (cCore.isEmpty() || rCore.isEmpty()) return false
        if (cCore == rCore) return true

        if (cCore.size == 1) {
            val token = cCore.first()
            return token.length >= 3 && (token == rCore.first() || token == rCore.last())
        }
        if (rCore.size == 1) {
            val token = rCore.first()
            return token.length >= 3 && (token == cCore.first() || token == cCore.last())
        }
        return cCore.first().length >= 3 && cCore.first() == rCore.first()
    }

    private fun tokenizeName(lower: String): List<String> =
        lower.split(Regex("""[\s.]+""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun stripHonorifics(tokens: List<String>): List<String> {
        var idx = 0
        while (idx < tokens.size && tokens[idx] in HONORIFICS) {
            idx++
        }
        return tokens.drop(idx)
    }

    fun isCharacterHeading(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 2 || trimmed.length > 38) return false
        if (trimmed != trimmed.uppercase()) return false
        val namePart = trimmed.substringBefore("(").trim()
        if (namePart.isEmpty()) return false
        // Reject sentence punctuation ("STOP!", "WHAT?", "24.", "GET OUT.") except Jr./Sr. suffixes
        val lastChar = namePart.last()
        if (lastChar in "!?.,;:") {
            val isJrSr = namePart.endsWith(" JR.") || namePart.endsWith(" SR.")
            if (!isJrSr) return false
        }
        if (isNonCharacterCue(namePart)) return false
        return CHARACTER_HEADING_REGEX.matches(trimmed)
    }
}
