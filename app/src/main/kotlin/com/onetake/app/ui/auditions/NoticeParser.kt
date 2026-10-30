package com.onetake.app.ui.auditions

import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Destination
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

internal enum class DocumentKind {
    CastingNotice,
    SidesOnly,
    NoticeWithSides,
}

/** What a casting notice / sides document contains, classified and extracted cleanly offline. */
internal data class NoticeFields(
    val project: String,
    val role: String,
    val due: LocalDateTime?,
    val asks: List<String>,
    val destination: Destination,
    /** Pure sides/script text (empty when the document is a pure casting notice with no script). */
    val scriptText: String,
    val kind: DocumentKind = DocumentKind.CastingNotice,
    val projectType: String = "",
    val roleSize: String = "",
    val castingOffice: String = "",
    val notes: String = "",
    val detectedCharacters: List<String> = emptyList(),
    val sceneCount: Int = 1,
)

/**
 * Context-aware casting notice & screenplay sides classifier and parser.
 *
 * Separates notice headers/instructions from screenplay sides before extracting fields so:
 * - Pure sides (`SidesOnly`) never hallucinate projects from `"in Room 4B"`, due dates from `"sat in the sun"`,
 *   or deliverables from `"wipe the slate clean"`.
 * - Pure notices (`CastingNotice`) never dump email greetings or breakdown notes into `scriptText`.
 * - Combined documents (`NoticeWithSides`) extract metadata strictly from the notice portion and
 *   keep `scriptText` strictly to the dialogue scenes.
 */
internal object NoticeParser {

    private val NOTICE_KEYS = setOf(
        "project", "project title", "production", "film", "series", "show", "title",
        "type", "project type", "medium",
        "role", "roles", "character", "character name", "role size", "billing",
        "due", "deadline", "submission deadline", "self-tape due", "self tape due", "tape due",
        "submit", "submit via", "submission", "submissions", "self tape", "self-tape", "tape",
        "casting", "casting director", "casting office", "cd", "director", "producer", "writer",
        "dates", "shoot", "shoot dates", "callback", "callbacks",
        "location", "rate", "pay", "union", "notes", "note", "callback notes", "instructions",
        "wardrobe", "slate", "format", "sides", "synopsis", "storyline",
        "age", "gender", "ethnicity", "email", "contact", "link",
        "from", "to", "cc", "bcc", "subject", "re", "fwd", "fw", "date", "sent",
    )

    private val STANDALONE_NOTICE_LABELS = setOf(
        "project", "project title", "production", "title",
        "type", "project type",
        "role", "character", "character name", "role size",
        "casting", "casting director", "casting office", "cd",
        "director", "producer",
        "due", "deadline", "submission deadline", "self-tape due", "self tape due",
        "submit", "submit via", "submission",
        "shoot dates", "dates", "location", "rate", "pay", "union",
        "notes", "note", "callback notes", "instructions", "wardrobe", "format", "slate", "synopsis", "storyline",
    )

    private val KEY_LINE = Regex("""^\s*([A-Za-z][A-Za-z \-]{0,26})\s*:(?!\d)\s*(.*)$""")
    private val COLON_SPEAKER_LINE = Regex("""^([A-Za-z][A-Za-z0-9'\-\.#]*(?: [A-Za-z0-9'\-\.#]+){0,2})\s*:(?!\d)\s*(.+)$""")

    private val NOTICE_SIGNAL_REGEX = Regex(
        """(?i)\b(?:self-?tape|audition|eco\s*cast|actors\s+access|casting\s+networks|spotlight|submission\s+deadline|please\s+(?:send|submit|upload|tape|slate)|tapes?\s+due|submit\s+(?:by|via|before))\b"""
    )
    private val EMAIL_PROSE_LINE_REGEX = Regex(
        """(?i)^(?:hi|hello|hey|dear|hope\s+you|please\s+(?:send|submit|upload|find|see|read|slate|shoot|record|include)|shoot\s+in\s+(?:landscape|horizontal)|best|thanks|thank\s+you|cheers|warmly|regards|sincerely)\b"""
    )

    private val ROLE_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:role(?:[ \t]+of)?|character(?:[ \t]+name)?)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
        Regex("""(?i)\b(?:for|as)[ \t]+(?:the[ \t]+)?role[ \t]+of[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,3})(?=[ \t]+in\b|[.,;\n]|$)"""),
        Regex("""(?i)\brole[ \t]+of[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,3})(?=[ \t]+in\b|[.,;\n]|$)"""),
        Regex("""\b(?:for|as)[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,2})[ \t]+in[ \t]+["“']?[A-Z]"""),
        Regex("""\b[Ss]eeking[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,2})\b"""),
    )

    private val PROJECT_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:project(?:[ \t]+title)?|production|film|series|show|title)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
        Regex("""(?i)\b(?:for|as|role[ \t]+of)[ \t]+(?:the[ \t]+)?(?:role[ \t]+of[ \t]+)?[A-Z][A-Za-z0-9'.#\- ]{0,30}?[ \t]+in[ \t]+["“']?([A-Z][A-Za-z0-9'&:\- ]*[A-Za-z0-9'&])["”']?(?=[.,;\n]|$)"""),
    )

    private val CASTING_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:casting(?:[ \t]+(?:director|office))?|cd)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
    )

    private val NOTES_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:callback[ \t]+notes|notes?|instructions|wardrobe)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
    )

    private val TYPE_KEY_PATTERN = Regex("""(?i)^\s*(?:project[ \t]+type|type|medium)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE)
    private val ROLE_SIZE_KEY_PATTERN = Regex("""(?i)^\s*(?:role[ \t]+size|billing)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE)

    fun parse(text: String, today: LocalDateTime = LocalDateTime.now()): NoticeFields {
        val clean = text.trim()
        if (clean.isBlank()) {
            return NoticeFields("", "", null, emptyList(), Destination.ActorsAccess, "")
        }

        val normalized = normalizeMultiLineBreakdowns(clean)
        val (kind, noticePortion, extractedSides) = classifyAndPartition(normalized)
        val detectedChars = if (extractedSides.isNotBlank()) ScriptParser.extractCharacters(extractedSides) else emptyList()

        if (kind == DocumentKind.SidesOnly) {
            val role = detectedChars.firstOrNull()?.take(36).orEmpty()
            val scenes = ScriptParser.parseScenes(extractedSides, role).size.coerceAtLeast(1)
            return NoticeFields(
                project = "",
                role = role,
                due = null,
                asks = emptyList(),
                destination = Destination.ActorsAccess,
                scriptText = extractedSides,
                kind = DocumentKind.SidesOnly,
                detectedCharacters = detectedChars,
                sceneCount = scenes,
            )
        }

        val noticeLower = noticePortion.lowercase()

        val rawRole = firstMatch(ROLE_PATTERNS, noticePortion)
        val (cleanRoleFromNotice, roleSizeFromRole) = splitRoleAndSize(rawRole.orEmpty())
        val role = cleanRoleFromNotice.take(36).ifEmpty {
            detectedChars.firstOrNull()?.take(36).orEmpty()
        }

        val roleSizeExplicit = ROLE_SIZE_KEY_PATTERN.find(noticePortion)?.groupValues?.get(1)?.trim().orEmpty()
        val roleSize = normalizeRoleSize(roleSizeExplicit.ifEmpty { roleSizeFromRole }.ifEmpty { detectInlineRoleSize(noticePortion) })

        val project = (firstMatch(PROJECT_PATTERNS, noticePortion)?.trim('"', '“', '”', '\'')?.take(48)
            ?: noticePortion.lineSequence().map { it.trim() }.firstOrNull(::looksLikeTitle)
            ?: "").trim()

        val rawType = TYPE_KEY_PATTERN.find(noticePortion)?.groupValues?.get(1)?.trim().orEmpty()
        val projectType = normalizeProjectType(rawType.ifEmpty { noticePortion })

        val castingOffice = firstMatch(CASTING_PATTERNS, noticePortion)?.take(48).orEmpty()
        val notes = NOTES_PATTERNS.asSequence()
            .flatMap { it.findAll(noticePortion) }
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

        val negatedSlate = Regex("""(?i)\b(?:no|without|don'?t|do[ \t]+not)[ \t]+(?:[a-z]+[ \t]+){0,2}slate\b""")
            .containsMatchIn(noticeLower)

        val asks = buildList {
            if (!negatedSlate && Regex("""(?i)\b(?:slate|ident)\b""").containsMatchIn(noticeLower)) add("Slate first")
            if (Regex("""(?i)\b(?:landscape|horizontal)\b""").containsMatchIn(noticeLower)) add("Landscape")
            if (Regex("""(?i)\b(?:full-length|full[ \t]+length|full[ \t]+body|head[ \t]+to[ \t]+toe)\b""").containsMatchIn(noticeLower)) add("Full length")
            if (Regex("""(?i)\b(?:both[ \t]+profiles|profile[ \t]+shots|left[ \t]+and[ \t]+right[ \t]+profile)\b""").containsMatchIn(noticeLower)) add("Both profiles")
            if (Regex("""(?i)\bunder[ \t]+(?:3|three)[ \t]*min""").containsMatchIn(noticeLower)) add("Under 3 min")
        }

        val destination = when {
            "casting networks" in noticeLower -> Destination.CastingNetworks
            Regex("""(?i)\bspotlight\b""").containsMatchIn(noticeLower) -> Destination.Spotlight
            "actors access" in noticeLower || "eco cast" in noticeLower || "ecocast" in noticeLower -> Destination.ActorsAccess
            Regex("""(?i)\b(?:email|e-mail|link|wetransfer|we[ \t]+transfer|dropbox|vimeo|google[ \t]+drive)\b""").containsMatchIn(noticeLower) ->
                Destination.EmailOrLink
            else -> Destination.ActorsAccess
        }

        val sceneCount = if (extractedSides.isNotBlank()) {
            ScriptParser.parseScenes(extractedSides, role).size.coerceAtLeast(1)
        } else {
            1
        }

        return NoticeFields(
            project = project,
            role = role,
            due = parseDue(noticePortion, today),
            asks = asks,
            destination = destination,
            scriptText = extractedSides,
            kind = kind,
            projectType = projectType,
            roleSize = roleSize,
            castingOffice = castingOffice,
            notes = notes,
            detectedCharacters = detectedChars,
            sceneCount = sceneCount,
        )
    }

    /**
     * Combines two-line breakdown headers (`PROJECT\nNight Shift`) into `PROJECT: Night Shift`
     * so scanned/PDF breakdowns parse identically to single-line `Key: Value` emails.
     */
    internal fun normalizeMultiLineBreakdowns(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            val candidateKey = trimmed.trimEnd(':').trim().lowercase().replace(Regex("""\s+"""), " ")
            val isStandaloneLabel = !trimmed.contains(':') &&
                candidateKey in STANDALONE_NOTICE_LABELS &&
                (trimmed == trimmed.uppercase() || trimmed.firstOrNull()?.isUpperCase() == true)

            if (isStandaloneLabel && i + 1 < lines.size) {
                val nextTrimmed = lines[i + 1].trim()
                val nextKey = nextTrimmed.trimEnd(':').trim().lowercase().replace(Regex("""\s+"""), " ")
                if (nextTrimmed.isNotEmpty() &&
                    nextKey !in STANDALONE_NOTICE_LABELS &&
                    !ScriptParser.isSceneHeading(nextTrimmed)
                ) {
                    out.add("${trimmed.trimEnd(':')}: $nextTrimmed")
                    i += 2
                    continue
                }
            }
            out.add(lines[i])
            i++
        }
        return out.joinToString("\n")
    }

    private fun classifyAndPartition(normalizedText: String): Triple<DocumentKind, String, String> {
        val lines = normalizedText.lines()
        var hasNoticeHeaderLine = false
        var firstSidesIndex = -1

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val keyMatch = KEY_LINE.matchEntire(line)
            if (keyMatch != null && keyMatch.groupValues[1].trim().lowercase() in NOTICE_KEYS) {
                hasNoticeHeaderLine = true
                continue
            }

            if (firstSidesIndex == -1) {
                if (ScriptParser.isSceneHeading(line)) {
                    firstSidesIndex = i
                    continue
                }
                val colonMatch = COLON_SPEAKER_LINE.matchEntire(line)
                if (colonMatch != null) {
                    val speakerKey = colonMatch.groupValues[1].trim().lowercase()
                    if (speakerKey !in NOTICE_KEYS && !EMAIL_PROSE_LINE_REGEX.containsMatchIn(line)) {
                        firstSidesIndex = i
                        continue
                    }
                }
                val nextLine = lines.getOrNull(i + 1)?.trim().orEmpty()
                val nextIsNoticeKey = KEY_LINE.matchEntire(nextLine)?.groupValues?.get(1)?.trim()?.lowercase() in NOTICE_KEYS
                if (ScriptParser.isCharacterHeading(line) && nextLine.isNotEmpty() && !nextIsNoticeKey && !ScriptParser.isSceneHeading(nextLine)) {
                    firstSidesIndex = i
                    continue
                }
            }
        }

        val prefixText = if (firstSidesIndex >= 0) lines.take(firstSidesIndex).joinToString("\n") else normalizedText
        val hasNoticeSignals = hasNoticeHeaderLine ||
            NOTICE_SIGNAL_REGEX.containsMatchIn(prefixText) ||
            DUE_HINT.containsMatchIn(prefixText)

        if (firstSidesIndex >= 0) {
            val rawSidesSection = lines.drop(firstSidesIndex)
                .filterNot { l ->
                    val k = KEY_LINE.matchEntire(l.trim())?.groupValues?.get(1)?.trim()?.lowercase()
                    k != null && k in NOTICE_KEYS
                }
                .joinToString("\n")
                .trim()
            val chars = ScriptParser.extractCharacters(rawSidesSection)
            if (chars.isNotEmpty()) {
                return if (hasNoticeSignals) {
                    val noticeLines = lines.take(firstSidesIndex) + lines.drop(firstSidesIndex).filter { l ->
                        val k = KEY_LINE.matchEntire(l.trim())?.groupValues?.get(1)?.trim()?.lowercase()
                        k != null && k in NOTICE_KEYS
                    }
                    Triple(DocumentKind.NoticeWithSides, noticeLines.joinToString("\n").trim(), rawSidesSection)
                } else {
                    Triple(DocumentKind.SidesOnly, "", rawSidesSection)
                }
            }
        }

        // No structured character cues found: check if it's a pure CastingNotice vs an unmarked monologue
        val nonNoticeLines = lines.filterNot { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@filterNot false
            val key = KEY_LINE.matchEntire(trimmed)?.groupValues?.get(1)?.trim()?.lowercase()
            if (key != null && key in NOTICE_KEYS) return@filterNot true
            if (EMAIL_PROSE_LINE_REGEX.containsMatchIn(trimmed)) return@filterNot true
            if (NOTICE_SIGNAL_REGEX.containsMatchIn(trimmed)) return@filterNot true
            if (DUE_HINT.containsMatchIn(trimmed) && (explicitDate(trimmed, LocalDate.now()) != null || weekday(trimmed, LocalDate.now()) != null || "tomorrow" in trimmed.lowercase() || "today" in trimmed.lowercase())) return@filterNot true
            false
        }
        val remainingProse = nonNoticeLines.joinToString("\n").trim()
        val remainingWords = remainingProse.split(Regex("""\s+""")).count { it.isNotBlank() }

        return if (!hasNoticeSignals && remainingWords >= 8) {
            // Unmarked monologue pasted/scanned as sides
            Triple(DocumentKind.SidesOnly, "", remainingProse)
        } else {
            Triple(DocumentKind.CastingNotice, normalizedText, "")
        }
    }

    private fun splitRoleAndSize(rawRole: String): Pair<String, String> {
        if (rawRole.isBlank()) return "" to ""
        var role = rawRole.trim()
        var detectedSize = ""

        val parenMatch = Regex("""^(.*?)\s*[\(\[]([^)\]]+)[\)\]]\s*$""").matchEntire(role)
        if (parenMatch != null) {
            val inside = parenMatch.groupValues[2].trim()
            val size = normalizeRoleSize(inside)
            if (size.isNotEmpty()) {
                detectedSize = size
                role = parenMatch.groupValues[1].trim()
            }
        }

        val dashParts = role.split(Regex("""\s+[-–—|]\s+"""), limit = 2)
        if (dashParts.size == 2) {
            val size = normalizeRoleSize(dashParts[1])
            if (size.isNotEmpty()) {
                if (detectedSize.isEmpty()) detectedSize = size
                role = dashParts[0].trim()
            }
        }

        return role.trimEnd('.', ',', ';', '-') to detectedSize
    }

    private fun normalizeRoleSize(text: String): String {
        val l = text.lowercase()
        return when {
            "series regular" in l -> "Series regular"
            "guest star" in l -> "Guest star"
            "co-star" in l || "costar" in l -> "Co-star"
            "day player" in l -> "Day player"
            "supporting" in l -> "Supporting"
            "principal" in l -> "Principal"
            "recurring" in l -> "Recurring"
            Regex("""\blead\b""").containsMatchIn(l) -> "Lead"
            else -> ""
        }
    }

    private fun detectInlineRoleSize(noticeText: String): String = normalizeRoleSize(noticeText)

    private fun normalizeProjectType(text: String): String {
        val l = text.lowercase()
        return when {
            "commercial" in l -> "Commercial"
            "limited series" in l -> "Limited series"
            "tv" in l || "television" in l || "episodic" in l || "pilot" in l || Regex("""\bseries\b""").containsMatchIn(l) -> "TV series"
            "short film" in l || Regex("""\bshort\b""").containsMatchIn(l) -> "Short film"
            "theatre" in l || "theater" in l || "stage" in l -> "Theatre"
            "voiceover" in l || "voice over" in l || "animation" in l || "video game" in l -> "Voiceover"
            "feature" in l || "film" in l || "movie" in l -> "Feature film"
            else -> ""
        }
    }

    private fun looksLikeTitle(line: String): Boolean =
        line.isNotBlank() && !line.contains(":") && line.length <= 40 &&
            line.split(' ').size <= 6 && line.last() !in ".!?," &&
            !DUE_HINT.containsMatchIn(line) &&
            !EMAIL_PROSE_LINE_REGEX.containsMatchIn(line) &&
            !NOTICE_SIGNAL_REGEX.containsMatchIn(line)

    private fun firstMatch(patterns: List<Regex>, text: String): String? =
        patterns.firstNotNullOfOrNull { r -> r.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } }

    fun scriptText(text: String): String = parse(text).scriptText

    // MARK: - Due date

    private val DUE_HINT = Regex("""(?i)\b(due|deadline|submit|submissions?|tapes?|by|before|no later than)\b""")
    private val TIME = Regex("""(?i)\b(\d{1,2})(?:[:.](\d{2}))?[ \t]*(a\.?m\.?|p\.?m\.?)(?![a-z])""")
    private val TIME_24 = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val MONTH_DAY = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?[ \t]+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?[ \t]+(?:of[ \t]+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\b""")
    private val ISO_DATE = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    // Unambiguous weekdays: "sat" and "sun" require full "Saturday"/"Sunday" unless followed by a comma/month/time,
    // preventing prose like "She sat down in the sun" from ever matching a weekday.
    private val WEEKDAY = Regex(
        """(?i)\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|(?:sat|sun)(?=[, \t]+(?:\d|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|at\b|noon\b)))\b"""
    )

    fun parseDue(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val normalized = normalizeMultiLineBreakdowns(text)
        val lines = normalized.lines()
        val hintedIndices = lines.indices.filter { DUE_HINT.containsMatchIn(lines[it]) }
        for (idx in hintedIndices) {
            dueIn(lines[idx], now)?.let { return it }
            // Support "Due Friday\nat 5:00 PM" split across two adjacent lines
            if (idx + 1 < lines.size) {
                dueIn("${lines[idx]} ${lines[idx + 1]}", now)?.let { return it }
            }
        }
        return if (hintedIndices.isEmpty()) dueIn(normalized, now) else null
    }

    private fun dueIn(text: String, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val lower = text.lowercase()
        val date: LocalDate = when {
            "tomorrow" in lower -> today.plusDays(1)
            "today" in lower || "tonight" in lower || Regex("""\beod\b""").containsMatchIn(lower) -> today
            else -> explicitDate(text, today) ?: weekday(text, today) ?: return null
        }
        val time = time(text)
            ?: if ("tonight" in lower) LocalTime.of(20, 0)
            else if (Regex("""\beod\b""").containsMatchIn(lower)) LocalTime.of(17, 0)
            else LocalTime.of(10, 0)
        return date.atTime(time)
    }

    private fun explicitDate(text: String, today: LocalDate): LocalDate? {
        fun upcoming(month: Int, day: Int, year: Int? = null): LocalDate? {
            val y = year ?: today.year
            val d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
            return if (year == null && d.isBefore(today)) d.plusYears(1) else d
        }
        ISO_DATE.find(text)?.let { m ->
            return upcoming(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt())
        }
        MONTH_DAY.find(text)?.let { m -> return upcoming(month(m.groupValues[1]), m.groupValues[2].toInt()) }
        DAY_MONTH.find(text)?.let { m -> return upcoming(month(m.groupValues[2]), m.groupValues[1].toInt()) }
        NUMERIC.find(text)?.let { m ->
            val (mo, d) = m.groupValues[1].toInt() to m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            if (mo in 1..12 && d in 1..31) return upcoming(mo, d, year)
        }
        return null
    }

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun month(name: String): Int = MONTHS.indexOf(name.lowercase().take(3)) + 1

    private fun weekday(text: String, today: LocalDate): LocalDate? {
        val m = WEEKDAY.find(text) ?: return null
        val day = when (m.groupValues[1].lowercase().take(3)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        return today.with(TemporalAdjusters.nextOrSame(day))
    }

    private fun time(text: String): LocalTime? {
        if (Regex("""(?i)\bnoon\b""").containsMatchIn(text)) return LocalTime.NOON
        if (Regex("""(?i)\bmidnight\b""").containsMatchIn(text)) return LocalTime.of(23, 59)
        TIME.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            if (h in 1..12 && min <= 59) {
                val pm = m.groupValues[3].lowercase().startsWith("p")
                return LocalTime.of((h % 12) + if (pm) 12 else 0, min)
            }
        }
        TIME_24.find(text)?.let { m -> return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return null
    }
}
