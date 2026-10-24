package com.onetake.app.ui.auditions

import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Destination
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** What a casting notice says, pulled out by plain rules (no network, no model). */
internal data class NoticeFields(
    val project: String,
    val role: String,
    val due: LocalDateTime?,
    val asks: List<String>,
    val destination: Destination,
    /** The notice minus its "Key: value" header lines — what's left is (possibly) the sides. */
    val scriptText: String,
)

/**
 * Rule-based casting-notice parser. Pure Kotlin so it's unit-testable on the JVM.
 * Patterns only ever match within a line ([ \t], never \s), so a value can't swallow the next line,
 * and capitalised-name patterns are case-sensitive so "due in two days" isn't read as a project.
 */
internal object NoticeParser {
    /** "Key:" prefixes that belong to the notice, not the script. */
    private val NOTICE_KEYS = setOf(
        "project", "production", "film", "series", "show", "title", "role", "roles", "character",
        "due", "deadline", "submit", "submission", "submissions", "self tape", "self-tape", "tape",
        "casting", "casting director", "cd", "director", "producer", "dates", "shoot", "shoot dates",
        "location", "rate", "pay", "union", "notes", "note", "instructions", "slate", "format",
        "sides", "age", "gender", "ethnicity", "type", "email", "contact", "link",
    )
    private val KEY_LINE = Regex("""^\s*([A-Za-z][A-Za-z \-]{0,24})\s*:""")

    private val ROLE_PATTERNS = listOf(
        Regex("""(?i)\brole(?:[ \t]+of)?[ \t]*:[ \t]*([A-Za-z0-9'][A-Za-z0-9' \t]*)"""),
        Regex("""(?i)\bcharacter[ \t]*:[ \t]*([A-Za-z0-9'][A-Za-z0-9' \t]*)"""),
        Regex("""\b(?:for|as)[ \t]+(?:the[ \t]+role[ \t]+of[ \t]+)?([A-Z][A-Za-z']+(?:[ \t][A-Z][A-Za-z']+)?)[ \t]+in\b"""),
        Regex("""\b[Ss]eeking[ \t]+([A-Z][A-Za-z']+(?:[ \t][A-Z][A-Za-z']+)?)\b"""),
    )
    private val PROJECT_PATTERNS = listOf(
        Regex("""(?i)\b(?:project|production|film|series|show|title)[ \t]*:[ \t]*([A-Za-z0-9'][A-Za-z0-9'&!?.,\- \t]*)"""),
        // A capitalised title after "in": "for MAYA in Night Shift" / "in NIGHT SHIFT".
        Regex("""\bin[ \t]+([A-Z][A-Za-z0-9'&]*(?:[ \t]+[A-Z0-9][A-Za-z0-9'&]*)*)"""),
    )

    fun parse(text: String, today: LocalDateTime = LocalDateTime.now()): NoticeFields {
        val clean = text.trim()
        if (clean.isBlank()) return NoticeFields("", "", null, emptyList(), Destination.ActorsAccess, "")
        val lower = clean.lowercase()
        val scriptText = scriptText(clean)

        val role = firstMatch(ROLE_PATTERNS, clean)?.take(30)
            ?: ScriptParser.extractCharacters(scriptText).firstOrNull()?.take(30)
            ?: ""
        val project = firstMatch(PROJECT_PATTERNS, clean)?.take(40)
            ?: clean.lineSequence().map { it.trim() }.firstOrNull(::looksLikeTitle)
            ?: ""

        val asks = buildList {
            if ("slate" in lower) add("Slate first")
            if ("landscape" in lower || "horizontal" in lower) add("Landscape")
            if ("full-length" in lower || "full length" in lower || "full body" in lower) add("Full length")
            if ("both profiles" in lower || "profile shots" in lower) add("Both profiles")
            if (Regex("""under[ \t]+(?:3|three)[ \t]*min""").containsMatchIn(lower)) add("Under 3 min")
        }
        val destination = when {
            "casting networks" in lower -> Destination.CastingNetworks
            "spotlight" in lower -> Destination.Spotlight
            "actors access" in lower || "eco cast" in lower -> Destination.ActorsAccess
            "email" in lower || "link" in lower -> Destination.EmailOrLink
            else -> Destination.ActorsAccess
        }
        return NoticeFields(project, role, parseDue(clean, today), asks, destination, scriptText)
    }

    /** A bare first line used as the project only if it reads like a title, not a sentence
     *  ("Hi! Please send your tape…") or a deadline ("Due Friday"). */
    private fun looksLikeTitle(line: String): Boolean =
        line.isNotBlank() && !line.contains(":") && line.length <= 40 &&
            line.split(' ').size <= 6 && line.last() !in ".!?," && !DUE_HINT.containsMatchIn(line)

    private fun firstMatch(patterns: List<Regex>, text: String): String? =
        patterns.firstNotNullOfOrNull { r -> r.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } }

    /** The notice without its "Key: value" header lines. Dialogue in "NAME: line" form survives
     *  because character names aren't notice keys. */
    fun scriptText(text: String): String = text.lineSequence()
        .filterNot { line ->
            val key = KEY_LINE.find(line)?.groupValues?.get(1)?.trim()?.lowercase() ?: return@filterNot false
            key in NOTICE_KEYS
        }
        .joinToString("\n")
        .trim()

    // MARK: - Due date

    private val DUE_HINT = Regex("""(?i)\b(due|deadline|submit|submissions?|tapes?|by|before|no later than)\b""")
    private val TIME = Regex("""(?i)\b(\d{1,2})(?:[:.](\d{2}))?[ \t]*(a\.?m\.?|p\.?m\.?)(?![a-z])""")
    private val TIME_24 = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val MONTH_DAY = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?[ \t]+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?[ \t]+(?:of[ \t]+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val WEEKDAY = Regex("""(?i)\b(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|nesday|rsday|urday|sday)?\b""")

    /**
     * Finds the due date/time. Lines that talk about the deadline ("due", "by", "submit"…) are
     * searched first so shoot dates elsewhere in the notice aren't mistaken for it. A date with
     * no time defaults to 10:00 AM; a time with no date isn't enough (returns null → ask).
     */
    fun parseDue(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val lines = text.lines()
        val hinted = lines.filter { DUE_HINT.containsMatchIn(it) }
        return hinted.firstNotNullOfOrNull { dueIn(it, now) }
            ?: if (hinted.isEmpty()) dueIn(text, now) else null
    }

    private fun dueIn(text: String, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val lower = text.lowercase()
        val date: LocalDate = when {
            "tomorrow" in lower -> today.plusDays(1)
            "today" in lower || "tonight" in lower || "eod" in lower -> today
            else -> explicitDate(text, today) ?: weekday(text, today) ?: return null
        }
        val time = time(text) ?: if ("tonight" in lower) LocalTime.of(20, 0) else LocalTime.of(10, 0)
        return date.atTime(time)
    }

    private fun explicitDate(text: String, today: LocalDate): LocalDate? {
        fun upcoming(month: Int, day: Int, year: Int? = null): LocalDate? {
            val y = year ?: today.year
            val d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
            // "Oct 3" in November means next October — notices don't give past deadlines.
            return if (year == null && d.isBefore(today)) d.plusYears(1) else d
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
            "mon" -> DayOfWeek.MONDAY; "tue" -> DayOfWeek.TUESDAY; "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY; "fri" -> DayOfWeek.FRIDAY; "sat" -> DayOfWeek.SATURDAY
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
