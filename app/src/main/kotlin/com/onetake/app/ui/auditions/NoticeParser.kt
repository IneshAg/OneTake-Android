package com.onetake.app.ui.auditions

import android.util.Log
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Destination
import kotlinx.coroutines.CancellationException
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
    val allAvailableRoles: List<String> = emptyList(),
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
        "tape deadline", "self-tape deadline", "self tape deadline", "tapes due", "submission due",
        "submit", "submit via", "submission", "submissions", "self tape", "self-tape", "tape",
        "casting", "casting director", "casting office", "cd", "director", "producer", "writer",
        "dates", "shoot", "shoot dates", "shoot date", "callback", "callbacks", "callback dates", "callback date",
        "location", "rate", "pay", "union", "notes", "note", "callback notes", "instructions",
        "wardrobe", "slate", "format", "sides", "synopsis", "storyline",
        "age", "gender", "ethnicity", "email", "contact", "link",
        "from", "to", "cc", "bcc", "subject", "re", "fwd", "fw", "date", "sent",
        "looking for", "seeking", "breakdown",
    )

    private val STANDALONE_NOTICE_LABELS = setOf(
        "project", "project title", "production", "title",
        "type", "project type",
        "role", "character", "character name", "role size",
        "casting", "casting director", "casting office", "cd",
        "director", "producer",
        "due", "deadline", "submission deadline", "self-tape due", "self tape due", "tape due",
        "tape deadline", "self-tape deadline", "self tape deadline",
        "submit", "submit via", "submission",
        "shoot dates", "shoot date", "dates", "callback dates", "callback date",
        "location", "rate", "pay", "union",
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

    private val AGENCY_HEADER_REGEX = Regex(
        """(?i)\b(?:CAA(?:\s+TALENT)?|UTA|WME|ICM|GERSI|GERSH|PARADIGM|INNOVATIVE|MANAGEMENT\s+360|ANONYMOUS\s+CONTENT|""" +
            """SELF-?TAPE(?:\s+REQUEST)?|CASTING\s+(?:NOTICE|CALL|ALERT|DIRECTOR|OFFICE)|AUDITION\s+ALERT|AUDITION|""" +
            """BREAKDOWN\s+SERVICES|ECO-?CAST|ACTORS\s+ACCESS|CASTING\s+NETWORKS|SPOTLIGHT|""" +
            """PARAMOUNT|DISNEY|NETFLIX|HBO|WARNER(?:\s+BROS)?|SONY|UNIVERSAL|ABC|NBC|CBS|FOX|AMAZON|APPLE|HULU|SHOWTIME|STARZ)\b"""
    )

    private val EMAIL_PREFIX_REGEX = Regex(
        """^(?i)(?:(?:fwd?|re|fw|subject)\s*:\s*|\[[^\]\n]{1,30}\]\s*|(?:audition(?:\s+alert)?|casting(?:\s+(?:notice|call|alert))?|self-?tape(?:\s+request)?|eco-?cast)\s*:\s*)+"""
    )
    private val DELIMITER_REGEX = Regex("""\s+[-–—|]\s+""")
    private val INSTRUCTION_SEGMENT_REGEX = Regex(
        """(?i)\b(?:read\s+carefully|please\s+read|confidential|do\s+not\s+distribute|urgent|important|update|attention|instructions?|notes?|action\s+required|notice|alert)\b"""
    )

    private val ROLE_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:role(?:[ \t]+of)?|character(?:[ \t]+name)?)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
        Regex("""(?i)\b(?:audition|tape|self-?tape)?\s*(?:for\s+[A-Za-z'.\-]+(?:\s+[A-Za-z'.\-]+)?\s+)?as\s+(?:the\s+role\s+of\s+)?([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,3})(?=[ \t]+in\b|[.,;\n]|$)"""),
        Regex("""(?i)\brole[ \t]+of[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,3})(?=[ \t]+in\b|[.,;\n]|$)"""),
        Regex("""\b(?:for|as)[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,2})[ \t]+in[ \t]+["“']?[A-Z]"""),
        Regex("""\b[Ss]eeking[ \t]+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,2})\b"""),
    )

    private val PROJECT_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:project(?:[ \t]+title)?|production|film|series|show|title)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
        Regex("""(?i)\b(?:for|as|role[ \t]+of)[ \t]+(?:the[ \t]+)?(?:role[ \t]+of[ \t]+)?[A-Za-z0-9'.#\- ]{0,35}?[ \t]+in[ \t]+["“']?([A-Z][A-Za-z0-9'&:\-"“” ]*[A-Za-z0-9'&"”])["”']?(?=[.,;\n]|$)"""),
    )

    private val CASTING_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:casting(?:[ \t]+(?:director|office))?|cd)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
    )

    private val NOTES_PATTERNS = listOf(
        Regex("""(?i)^\s*(?:callback[ \t]+notes|notes?|instructions|wardrobe)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE),
    )

    private val TYPE_KEY_PATTERN = Regex("""(?i)^\s*(?:project[ \t]+type|type|medium)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE)
    private val ROLE_SIZE_KEY_PATTERN = Regex("""(?i)^\s*(?:role[ \t]+size|billing)[ \t]*:[ \t]*([^\n]+)""", RegexOption.MULTILINE)

    private val MULTI_ROLE_HEADER = Regex("""(?i)^\s*(?:looking\s+for|seeking|roles?(?:\s+available)?|characters?|breakdown)\s*:\s*$""")
    private val ROLE_LINE = Regex("""(?i)^\s*(?:role(?:[ \t]+of)?|character(?:[ \t]+name)?)[ \t]*:[ \t]*([^\n]+)""")

    private val TAPE_DEADLINE_REGEX = Regex(
        """(?i)\b(?:self-?tape|selftape|tape\s+due|tapes?\s+due|submit\s+tape|tapes?|submissions?|deadline|eco-?cast)\b"""
    )
    private val GENERAL_DUE_REGEX = Regex(
        """(?i)\b(?:due|submit|by|before|no\s+later\s+than)\b"""
    )
    private val DEPRIORITIZED_DATE_REGEX = Regex(
        """(?i)\b(?:shoot(?:ing)?|filming|production\s+dates?|callbacks?|wardrobe|fitting|avail(?:ability)?)\b"""
    )
    private val DUE_HINT = Regex("""(?i)\b(due|deadline|submit|submissions?|tapes?|by|before|no later than)\b""")

    fun parse(text: String, today: LocalDateTime = LocalDateTime.now()): NoticeFields {
        val clean = text.trim()
        if (clean.isBlank()) {
            return NoticeFields("", "", null, emptyList(), Destination.ActorsAccess, "")
        }

        val normalized = normalizeMultiLineBreakdowns(clean)
        val partition = classifyAndPartition(normalized)
        val kind = partition.kind
        val noticePortion = partition.noticePortion
        val extractedSides = partition.sidesText
        val headerPartition = partition.headerPartition
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
                allAvailableRoles = detectedChars,
            )
        }

        val roleCandidates = extractAllAvailableRolesWithSizes(noticePortion)
        val allRoles = roleCandidates.map { it.name }

        val rawRole = firstMatch(ROLE_PATTERNS, noticePortion)
        val (cleanRoleFromNotice, roleSizeFromRole) = splitRoleAndSize(rawRole.orEmpty())
        val role = cleanRoleFromNotice.take(36).ifEmpty {
            allRoles.firstOrNull()?.take(36).orEmpty()
        }.ifEmpty {
            detectedChars.firstOrNull()?.take(36).orEmpty()
        }

        val roleSizeFromBreakdown = roleCandidates.firstOrNull { it.name.equals(role, ignoreCase = true) }?.size.orEmpty()

        val finalAvailableRoles = if (allRoles.isNotEmpty()) {
            allRoles
        } else if (role.isNotEmpty()) {
            listOf(role)
        } else if (detectedChars.isNotEmpty()) {
            detectedChars
        } else {
            emptyList()
        }

        val roleSizeExplicit = ROLE_SIZE_KEY_PATTERN.find(noticePortion)?.groupValues?.get(1)?.trim().orEmpty()
        val resolvedRoleSize = roleSizeExplicit
            .ifEmpty { roleSizeFromRole }
            .ifEmpty { roleSizeFromBreakdown }
            .ifEmpty { if (allRoles.size <= 1) detectInlineRoleSize(noticePortion) else "" }
        val roleSize = normalizeRoleSize(resolvedRoleSize)

        val rawProjectMatch = firstMatch(PROJECT_PATTERNS, noticePortion)
        val delimitedProject = if (rawProjectMatch == null) extractDelimitedSubjectTitle(noticePortion) else null
        val fallbackTitle = if (rawProjectMatch == null && delimitedProject == null) {
            noticePortion.lineSequence().map { it.trim() }.firstOrNull(::looksLikeTitle)
        } else null

        val project = cleanTitle(rawProjectMatch ?: delimitedProject ?: fallbackTitle ?: "")

        val rawType = TYPE_KEY_PATTERN.find(noticePortion)?.groupValues?.get(1)?.trim().orEmpty()
        val projectType = normalizeProjectType(rawType.ifEmpty { noticePortion })

        val castingOffice = firstMatch(CASTING_PATTERNS, noticePortion)?.take(48).orEmpty()
        val notes = NOTES_PATTERNS.asSequence()
            .flatMap { it.findAll(noticePortion) }
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

        val headerLower = headerPartition.lowercase()
        val negatedSlate = Regex("""(?i)\b(?:no|without|don'?t|do[ \t]+not)[ \t]+(?:[a-z]+[ \t]+){0,2}slate\b""")
            .containsMatchIn(headerLower)

        val asks = buildList {
            if (!negatedSlate && Regex("""(?i)\b(?:slate|ident)\b""").containsMatchIn(headerLower)) add("Slate first")
            if (Regex("""(?i)\b(?:landscape|horizontal)\b""").containsMatchIn(headerLower)) add("Landscape")
            if (Regex("""(?i)\b(?:full-length|full[ \t]+length|full[ \t]+body|head[ \t]+to[ \t]+toe)\b""").containsMatchIn(headerLower)) add("Full length")
            if (Regex("""(?i)\b(?:both[ \t]+profiles|profile[ \t]+shots|left[ \t]+and[ \t]+right[ \t]+profile)\b""").containsMatchIn(headerLower)) add("Both profiles")
            if (Regex("""(?i)\bunder[ \t]+(?:3|three)[ \t]*min""").containsMatchIn(headerLower)) add("Under 3 min")
        }

        val noticeLower = noticePortion.lowercase()
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
            allAvailableRoles = finalAvailableRoles,
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

    private data class PartitionResult(
        val kind: DocumentKind,
        val noticePortion: String,
        val sidesText: String,
        val headerPartition: String,
    )

    private fun classifyAndPartition(normalizedText: String): PartitionResult {
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

            if (AGENCY_HEADER_REGEX.containsMatchIn(line)) {
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
                    if (speakerKey !in NOTICE_KEYS && !EMAIL_PROSE_LINE_REGEX.containsMatchIn(line) && !AGENCY_HEADER_REGEX.containsMatchIn(line)) {
                        firstSidesIndex = i
                        continue
                    }
                }
                val nextLine = lines.getOrNull(i + 1)?.trim().orEmpty()
                val nextIsNoticeKey = KEY_LINE.matchEntire(nextLine)?.groupValues?.get(1)?.trim()?.lowercase() in NOTICE_KEYS
                if (ScriptParser.isCharacterHeading(line) &&
                    !AGENCY_HEADER_REGEX.containsMatchIn(line) &&
                    !isNoticeKeywordOrHeader(line) &&
                    nextLine.isNotEmpty() &&
                    !nextIsNoticeKey &&
                    !ScriptParser.isSceneHeading(nextLine)
                ) {
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
                val headerPartition = lines.take(firstSidesIndex).joinToString("\n").trim()
                return if (hasNoticeSignals) {
                    val noticeLines = lines.take(firstSidesIndex) + lines.drop(firstSidesIndex).filter { l ->
                        val k = KEY_LINE.matchEntire(l.trim())?.groupValues?.get(1)?.trim()?.lowercase()
                        k != null && k in NOTICE_KEYS
                    }
                    PartitionResult(DocumentKind.NoticeWithSides, noticeLines.joinToString("\n").trim(), rawSidesSection, headerPartition)
                } else {
                    PartitionResult(DocumentKind.SidesOnly, "", rawSidesSection, "")
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
            PartitionResult(DocumentKind.SidesOnly, "", remainingProse, "")
        } else {
            PartitionResult(DocumentKind.CastingNotice, normalizedText, "", normalizedText)
        }
    }

    private data class RoleCandidate(val name: String, val size: String)

    private fun extractAllAvailableRolesWithSizes(noticePortion: String): List<RoleCandidate> {
        val candidates = LinkedHashMap<String, String>()
        val lines = noticePortion.lines()

        // 1. Look for breakdown section headers like "LOOKING FOR:" or "ROLES:"
        var inRoleSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                inRoleSection = false
                continue
            }
            if (MULTI_ROLE_HEADER.matches(trimmed)) {
                inRoleSection = true
                continue
            }
            if (inRoleSection) {
                val keyMatch = KEY_LINE.matchEntire(trimmed)
                if (keyMatch != null && keyMatch.groupValues[1].trim().lowercase() in NOTICE_KEYS) {
                    inRoleSection = false
                    continue
                }
                if (ScriptParser.isSceneHeading(trimmed) ||
                    DUE_HINT.containsMatchIn(trimmed) ||
                    NOTICE_SIGNAL_REGEX.containsMatchIn(trimmed) ||
                    EMAIL_PROSE_LINE_REGEX.containsMatchIn(trimmed) ||
                    AGENCY_HEADER_REGEX.containsMatchIn(trimmed) ||
                    explicitDate(trimmed, LocalDate.now()) != null ||
                    weekday(trimmed, LocalDate.now()) != null
                ) {
                    inRoleSection = false
                    continue
                }
                val (cleanName, detectedSize) = splitRoleAndSize(trimmed)
                if (cleanName.isNotBlank() &&
                    cleanName.length in 2..36 &&
                    !isNoticeKeywordOrHeader(cleanName) &&
                    !DUE_HINT.containsMatchIn(cleanName) &&
                    !NOTICE_SIGNAL_REGEX.containsMatchIn(cleanName) &&
                    !EMAIL_PROSE_LINE_REGEX.containsMatchIn(cleanName)
                ) {
                    candidates.putIfAbsent(cleanName, detectedSize)
                }
            }
        }

        // 2. Look for multiple "ROLE: ..." or "CHARACTER: ..." lines
        for (match in ROLE_LINE.findAll(noticePortion)) {
            val raw = match.groupValues[1].trim()
            val (cleanName, detectedSize) = splitRoleAndSize(raw)
            if (cleanName.isNotBlank() && cleanName.length in 2..36 && !isNoticeKeywordOrHeader(cleanName)) {
                candidates.putIfAbsent(cleanName, detectedSize)
            }
        }

        // 3. Delimited subject lines for "role of <Role>"
        for (line in lines) {
            val stripped = line.trim().replace(EMAIL_PREFIX_REGEX, "").trim()
            if (DELIMITER_REGEX.containsMatchIn(stripped)) {
                val segments = stripped.split(DELIMITER_REGEX).map { it.trim() }
                for (seg in segments) {
                    val roleOfMatch = Regex("""(?i)^role\s+of\s+([A-Z][A-Za-z0-9'.#\-]*(?:[ \t]+[A-Z][A-Za-z0-9'.#\-]*){0,3})""").find(seg)
                    if (roleOfMatch != null) {
                        candidates.putIfAbsent(roleOfMatch.groupValues[1].trim(), "")
                    }
                }
            }
        }

        return candidates.map { RoleCandidate(it.key, it.value) }
    }

    private fun extractAllAvailableRoles(noticePortion: String): List<String> =
        extractAllAvailableRolesWithSizes(noticePortion).map { it.name }

    private fun isNoticeKeywordOrHeader(name: String): Boolean {
        val lower = name.lowercase()
        return lower in NOTICE_KEYS || AGENCY_HEADER_REGEX.containsMatchIn(name)
    }

    private fun cleanTitle(raw: String): String {
        var t = raw.replace(EMAIL_PREFIX_REGEX, "").trim()
        if ((t.startsWith("\"") && t.endsWith("\"")) ||
            (t.startsWith("“") && t.endsWith("”")) ||
            (t.startsWith("'") && t.endsWith("'"))
        ) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t.take(48).trim()
    }

    private fun extractDelimitedSubjectTitle(noticePortion: String): String? {
        for (line in noticePortion.lines()) {
            val trimmed = line.trim()
            val stripped = trimmed.replace(EMAIL_PREFIX_REGEX, "").trim()
            if (DELIMITER_REGEX.containsMatchIn(stripped)) {
                val segments = stripped.split(DELIMITER_REGEX).map { it.trim() }
                val candidate = segments.firstOrNull { seg ->
                    seg.isNotBlank() &&
                        seg.length in 2..48 &&
                        !AGENCY_HEADER_REGEX.containsMatchIn(seg) &&
                        !INSTRUCTION_SEGMENT_REGEX.containsMatchIn(seg) &&
                        !NOTICE_SIGNAL_REGEX.containsMatchIn(seg) &&
                        !DUE_HINT.containsMatchIn(seg) &&
                        !seg.startsWith("role of", ignoreCase = true) &&
                        !seg.startsWith("role:", ignoreCase = true) &&
                        !seg.startsWith("character:", ignoreCase = true) &&
                        !seg.startsWith("audition", ignoreCase = true) &&
                        !seg.startsWith("tape", ignoreCase = true) &&
                        !seg.startsWith("self-tape", ignoreCase = true) &&
                        seg.any { it.isLetter() }
                }
                if (candidate != null) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun looksLikeTitle(line: String): Boolean {
        val clean = line.replace(EMAIL_PREFIX_REGEX, "").trim()
        if (clean.isBlank()) return false
        if (clean.contains(":")) return false
        if (clean.length > 40) return false
        if (clean.split(' ').size > 6) return false
        if (clean.last() in ".!?,") return false
        if (AGENCY_HEADER_REGEX.containsMatchIn(clean)) return false
        if (DUE_HINT.containsMatchIn(clean)) return false
        if (EMAIL_PROSE_LINE_REGEX.containsMatchIn(clean)) return false
        if (NOTICE_SIGNAL_REGEX.containsMatchIn(clean)) return false
        return true
    }

    private fun splitRoleAndSize(rawRole: String): Pair<String, String> {
        if (rawRole.isBlank()) return "" to ""
        var role = rawRole.trim()
        var detectedSize = ""

        val dashMatch = Regex("""^(.*?)\s+[-–—|]\s+(.*)$""").matchEntire(role)
        if (dashMatch != null) {
            val left = dashMatch.groupValues[1].trim()
            val right = dashMatch.groupValues[2].trim()
            val rightSize = normalizeRoleSize(right)
            if (rightSize.isNotEmpty()) {
                detectedSize = rightSize
                role = left
            } else {
                role = left
            }
        }

        val parenMatch = Regex("""^(.*?)\s*[\(\[]([^)\]]+)[\)\]]\s*$""").matchEntire(role)
        if (parenMatch != null) {
            val inside = parenMatch.groupValues[2].trim()
            val size = normalizeRoleSize(inside)
            if (size.isNotEmpty()) {
                if (detectedSize.isEmpty()) detectedSize = size
            }
            role = parenMatch.groupValues[1].trim()
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
            "cameo" in l -> "Cameo"
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

    private fun firstMatch(patterns: List<Regex>, text: String): String? =
        patterns.firstNotNullOfOrNull { r -> r.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } }

    fun scriptText(text: String): String = parse(text).scriptText

    // MARK: - Due date

    private val TIME = Regex("""(?i)\b(\d{1,2})(?:[:.](\d{2}))?[ \t]*(a\.?m\.?|p\.?m\.?)(?![a-z])""")
    private val TIME_24 = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val TIME_COB_EOD = Regex("""(?i)\b(?:cob|eod|close\s+of\s+business|end\s+of\s+day)\b""")
    private val TIME_MILITARY = Regex("""(?i)\b([01]\d|2[0-3])([0-5]\d)\s*(?:hrs?|hours?)\b""")
    private val MONTH_DAY = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?[ \t]+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?[ \t]+(?:of[ \t]+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\b""")
    private val ISO_DATE = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val WEEKDAY = Regex(
        """(?i)\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|(?:sat|sun)(?=[, \t]+(?:\d|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|at\b|noon\b)))\b"""
    )

    fun parseDue(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val normalized = normalizeMultiLineBreakdowns(text)
        val lines = normalized.lines()
        val hintedIndices = lines.indices
            .filter { scoreDueLine(lines[it]) >= 0 }
            .sortedByDescending { scoreDueLine(lines[it]) }

        for (idx in hintedIndices) {
            dueIn(lines[idx], now)?.let { return it }
            // Support "Due Friday\nat 5:00 PM" split across two adjacent lines
            if (idx + 1 < lines.size) {
                dueIn("${lines[idx]} ${lines[idx + 1]}", now)?.let { return it }
            }
        }
        return if (hintedIndices.isEmpty()) dueIn(normalized, now) else null
    }

    private fun scoreDueLine(line: String): Int {
        val isDeprioritized = DEPRIORITIZED_DATE_REGEX.containsMatchIn(line)
        val isTapeDeadline = TAPE_DEADLINE_REGEX.containsMatchIn(line)
        val isGeneralDue = GENERAL_DUE_REGEX.containsMatchIn(line)

        return when {
            isTapeDeadline && !isDeprioritized -> 100
            isGeneralDue && !isDeprioritized -> 50
            isTapeDeadline && isDeprioritized -> 25
            isGeneralDue && isDeprioritized -> 0
            else -> -1
        }
    }

    private fun dueIn(text: String, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val lower = text.lowercase()
        val date: LocalDate = when {
            "tomorrow" in lower -> today.plusDays(1)
            "today" in lower || "tonight" in lower -> today
            else -> explicitDate(text, today)
                ?: weekday(text, today)
                ?: if (TIME_COB_EOD.containsMatchIn(lower)) today else return null
        }
        val time = time(text)
            ?: if ("tonight" in lower) LocalTime.of(20, 0)
            else if (TIME_COB_EOD.containsMatchIn(lower)) LocalTime.of(17, 0)
            else LocalTime.of(10, 0)
        return date.atTime(time)
    }

    private fun explicitDate(text: String, today: LocalDate): LocalDate? {
        fun upcoming(month: Int, day: Int, year: Int? = null): LocalDate? {
            val y = year ?: if (month < today.monthValue) today.year + 1 else today.year
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
        if (TIME_COB_EOD.containsMatchIn(text)) return LocalTime.of(17, 0)
        TIME.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            if (h in 1..12 && min <= 59) {
                val pm = m.groupValues[3].lowercase().startsWith("p")
                return LocalTime.of((h % 12) + if (pm) 12 else 0, min)
            }
        }
        TIME_24.find(text)?.let { m -> return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        TIME_MILITARY.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                return LocalTime.of(h, min)
            }
        }
        return null
    }

    suspend fun parseSmart(
        rawText: String,
        extractor: HybridNoticeExtractor? = null,
        today: LocalDate = LocalDate.now(),
    ): NoticeFields {
        if (extractor == null) {
            Log.i("NoticeParser", "parseSmart: extractor is null, using deterministic regex fallback")
            return parse(rawText, today.atStartOfDay())
        }

        Log.i("NoticeParser", "parseSmart: starting LLM extraction for ${rawText.length} chars...")
        val llm = try {
            extractor.extractNotice(rawText, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("NoticeParser", "parseSmart: extractor threw ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }

        if (llm == null) {
            Log.w("NoticeParser", "parseSmart: LLM extraction returned null. Using deterministic baseline regex fallback.")
            return parse(rawText, today.atStartOfDay())
        }

        Log.i("NoticeParser", "parseSmart: LLM extraction SUCCEEDED: project='${llm.projectTitle}', role='${llm.targetRoleName}', roles=${llm.allAvailableRoles}, due='${llm.tapeDueIsoDate} ${llm.tapeDueTime24h}'")

        var normalizedText = rawText
        if (llm.characterNormalizationMap.isNotEmpty()) {
            for ((typo, fix) in llm.characterNormalizationMap) {
                if (typo.isNotBlank() && fix.isNotBlank()) {
                    normalizedText = normalizedText.replace(typo, fix)
                }
            }
        }

        val targetRole = llm.targetRoleName.trim().ifEmpty {
            if (llm.allAvailableRoles.size == 1) llm.allAvailableRoles.first() else ""
        }

        val rawAnchor = llm.firstScriptLineAnchor.trim()
        var normalizedAnchor = rawAnchor
        if (llm.characterNormalizationMap.isNotEmpty() && rawAnchor.isNotBlank()) {
            for ((typo, fix) in llm.characterNormalizationMap) {
                if (typo.isNotBlank() && fix.isNotBlank()) {
                    normalizedAnchor = normalizedAnchor.replace(typo, fix)
                }
            }
        }

        var scriptText = ""
        var sceneCount = 1
        var detectedChars = emptyList<String>()

        if (rawAnchor.isNotBlank()) {
            var anchorIdx = normalizedText.indexOf(normalizedAnchor)
            if (anchorIdx < 0 && rawAnchor != normalizedAnchor) {
                anchorIdx = normalizedText.indexOf(rawAnchor)
            }

            val slicedSidesNullable = if (anchorIdx >= 0) {
                normalizedText.substring(anchorIdx).trim()
            } else {
                val rawIdx = rawText.indexOf(rawAnchor)
                if (rawIdx >= 0) {
                    var rawSliced = rawText.substring(rawIdx).trim()
                    for ((typo, fix) in llm.characterNormalizationMap) {
                        if (typo.isNotBlank() && fix.isNotBlank()) {
                            rawSliced = rawSliced.replace(typo, fix)
                        }
                    }
                    rawSliced
                } else null
            }

            if (slicedSidesNullable != null) {
                var slicedSides: String = slicedSidesNullable
                for ((typo, fix) in llm.characterNormalizationMap) {
                    if (typo.isNotBlank() && fix.isNotBlank()) {
                        slicedSides = slicedSides.replace(typo, fix)
                    }
                }
                val parsedScenes = ScriptParser.parseScenes(slicedSides, myCharacter = targetRole.ifBlank { "Role" })
                scriptText = slicedSides
                sceneCount = maxOf(1, parsedScenes.size)
                detectedChars = ScriptParser.extractCharacters(slicedSides)
            } else {
                val partition = classifyAndPartition(normalizedText)
                if (partition.sidesText.isNotBlank()) {
                    var sides = partition.sidesText
                    for ((typo, fix) in llm.characterNormalizationMap) {
                        if (typo.isNotBlank() && fix.isNotBlank()) {
                            sides = sides.replace(typo, fix)
                        }
                    }
                    val parsedScenes = ScriptParser.parseScenes(sides, myCharacter = targetRole.ifBlank { "Role" })
                    scriptText = sides
                    sceneCount = maxOf(1, parsedScenes.size)
                    detectedChars = ScriptParser.extractCharacters(sides)
                }
            }
        } else {
            val partition = classifyAndPartition(normalizedText)
            if (partition.sidesText.isNotBlank()) {
                var sides = partition.sidesText
                for ((typo, fix) in llm.characterNormalizationMap) {
                    if (typo.isNotBlank() && fix.isNotBlank()) {
                        sides = sides.replace(typo, fix)
                    }
                }
                val parsedScenes = ScriptParser.parseScenes(sides, myCharacter = targetRole.ifBlank { "Role" })
                scriptText = sides
                sceneCount = maxOf(1, parsedScenes.size)
                detectedChars = ScriptParser.extractCharacters(sides)
            }
        }

        val parsedDue = if (llm.tapeDueIsoDate.isNotBlank()) {
            runCatching {
                val date = LocalDate.parse(llm.tapeDueIsoDate.trim())
                val time = if (llm.tapeDueTime24h.isNotBlank()) {
                    runCatching {
                        val parts = llm.tapeDueTime24h.trim().split(":")
                        LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
                    }.getOrDefault(LocalTime.of(10, 0))
                } else {
                    LocalTime.of(10, 0)
                }
                date.atTime(time)
            }.getOrNull() ?: parseDue(rawText, today.atStartOfDay())
        } else null

        val parsedDestination = if (llm.destination.isNotBlank()) {
            val d = llm.destination.lowercase()
            when {
                "casting networks" in d -> Destination.CastingNetworks
                "spotlight" in d -> Destination.Spotlight
                "actors access" in d || "eco cast" in d || "ecocast" in d -> Destination.ActorsAccess
                "email" in d || "link" in d -> Destination.EmailOrLink
                else -> Destination.ActorsAccess
            }
        } else Destination.ActorsAccess

        val kind = when (llm.documentKind.trim().lowercase()) {
            "sidesonly", "sides_only" -> DocumentKind.SidesOnly
            "noticewithsides", "notice_with_sides" -> DocumentKind.NoticeWithSides
            "castingnotice", "casting_notice" -> if (scriptText.isNotBlank()) DocumentKind.NoticeWithSides else DocumentKind.CastingNotice
            else -> if (scriptText.isNotBlank()) DocumentKind.NoticeWithSides else DocumentKind.CastingNotice
        }

        return NoticeFields(
            project = llm.projectTitle.trim(),
            role = targetRole,
            allAvailableRoles = llm.allAvailableRoles,
            projectType = llm.projectType.trim().ifBlank { "Feature film" },
            roleSize = llm.roleSize.trim().ifBlank { "Lead" },
            due = parsedDue,
            destination = parsedDestination,
            asks = llm.asks,
            kind = kind,
            scriptText = scriptText,
            sceneCount = sceneCount,
            detectedCharacters = detectedChars,
            castingOffice = "",
            notes = "",
        )
    }
}
