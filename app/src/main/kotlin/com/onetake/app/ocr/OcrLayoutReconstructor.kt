package com.onetake.app.ocr

import com.onetake.app.data.ScriptParser
import kotlin.math.abs

data class OcrLineBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val angleDegrees: Float = 0f,
)

/**
 * Pure-Kotlin 2D layout reconstructor for ML Kit OCR results.
 *
 * Fixes the core failure modes of flat `visionText.text` extraction:
 * 1. Filters diagonal security watermarks (`abs(angleDegrees) > 10°`).
 * 2. Strips standalone margin page numbers, flanking scene numbers, and multi-page running headers/footers.
 * 3. Reconstructs horizontal visual rows so 2-column casting breakdowns pair into `Label: Value` lines.
 * 4. Emits `\n\n` paragraph breaks on vertical gaps AND screenplay X-indentation shifts (dialogue -> left-margin action,
 *    or centered character cues) so `ScriptParser` never glues stage directions onto spoken lines.
 */
object OcrLayoutReconstructor {

    data class PageInput(
        val lines: List<OcrLineBox>,
        val pageWidth: Int,
        val pageHeight: Int,
    )

    private data class VisualRow(
        val text: String,
        val leftNorm: Float,
        val top: Float,
        val bottom: Float,
        val height: Float,
    )

    private val PAGE_NUMBER_REGEX = Regex("""^(?:PAGE\s*)?\d{1,3}[A-Z]?\.?$""", RegexOption.IGNORE_CASE)
    private val MARGIN_SCENE_NUM_REGEX = Regex("""^\d{1,3}[A-Z]?\.?$""")
    private val SLUG_PREFIX_REGEX = Regex("""^(?:INT\.?/EXT|EXT\.?/INT|INT|EXT|EST|I/E)[.\s]""", RegexOption.IGNORE_CASE)
    private val WATERMARK_TEXT_REGEX = Regex("""(?i)\b(?:DO\s+NOT\s+DISTRIBUTE|WATERMARK)\b""")

    private val TABLE_LABEL_KEYS = setOf(
        "project", "project title", "production", "film", "series", "show", "title",
        "type", "project type", "format", "medium",
        "role", "roles", "character", "character name", "role size", "billing",
        "casting", "casting director", "casting office", "cd", "director", "producer", "writer",
        "due", "deadline", "submission deadline", "self-tape due", "self tape due", "tape due",
        "submit", "submit via", "submission", "submissions", "platform", "deliver to",
        "dates", "shoot", "shoot dates", "callback", "callbacks",
        "location", "rate", "pay", "union", "status",
        "notes", "note", "callback notes", "instructions", "wardrobe", "slate", "sides", "synopsis", "storyline",
    )

    fun reconstructDocument(pages: List<PageInput>): String {
        if (pages.isEmpty()) return ""
        if (pages.size == 1) {
            val single = pages.first()
            return reconstructPage(single.lines, single.pageWidth, single.pageHeight)
        }

        // Identify running headers/footers repeated in the top 10% or bottom 10% across >= 2 pages.
        val marginCounts = HashMap<String, Int>()
        for (page in pages) {
            val h = page.pageHeight.coerceAtLeast(1).toFloat()
            val seenOnPage = HashSet<String>()
            for (line in page.lines) {
                val topNorm = line.top / h
                val bottomNorm = line.bottom / h
                if (topNorm < 0.10f || bottomNorm > 0.90f) {
                    val norm = normalizeMarginKey(line.text)
                    if (norm.length >= 4 && seenOnPage.add(norm)) {
                        marginCounts[norm] = (marginCounts[norm] ?: 0) + 1
                    }
                }
            }
        }
        val repeatedMargins = marginCounts.filterValues { it >= 2 }.keys

        return pages.mapNotNull { page ->
            reconstructPage(page.lines, page.pageWidth, page.pageHeight, repeatedMargins)
                .takeIf { it.isNotBlank() }
        }.joinToString("\n\n")
    }

    fun reconstructPage(
        lines: List<OcrLineBox>,
        pageWidth: Int,
        pageHeight: Int,
        repeatedMargins: Set<String> = emptySet(),
    ): String {
        if (lines.isEmpty()) return ""
        val w = pageWidth.coerceAtLeast(1).toFloat()
        val h = pageHeight.coerceAtLeast(1).toFloat()

        // 1. Filter diagonal watermarks, repeated headers/footers, and top/bottom standalone page numbers.
        val cleanBoxes = lines.filter { box ->
            val trimmed = box.text.trim()
            if (trimmed.isEmpty()) return@filter false
            if (abs(box.angleDegrees) > 10f) return@filter false
            if (abs(box.angleDegrees) > 3f && WATERMARK_TEXT_REGEX.containsMatchIn(trimmed)) return@filter false

            val topNorm = box.top / h
            val bottomNorm = box.bottom / h
            val inVerticalMargin = topNorm < 0.095f || bottomNorm > 0.905f
            if (inVerticalMargin) {
                if (PAGE_NUMBER_REGEX.matches(trimmed)) return@filter false
                if (normalizeMarginKey(trimmed) in repeatedMargins) return@filter false
            }
            true
        }
        if (cleanBoxes.isEmpty()) return ""

        // 2. Group boxes into horizontal visual rows by vertical center overlap.
        val sortedByY = cleanBoxes.sortedWith(compareBy<OcrLineBox> { (it.top + it.bottom) * 0.5f }.thenBy { it.left })
        val rawRows = mutableListOf<MutableList<OcrLineBox>>()
        for (box in sortedByY) {
            val boxCenterY = (box.top + box.bottom) * 0.5f
            val boxH = (box.bottom - box.top).coerceAtLeast(6f)
            val targetRow = rawRows.lastOrNull()?.takeIf { row ->
                val rowCenterY = row.map { (it.top + it.bottom) * 0.5f }.average().toFloat()
                val rowH = row.map { (it.bottom - it.top).coerceAtLeast(6f) }.average().toFloat()
                abs(boxCenterY - rowCenterY) <= maxOf(boxH, rowH) * 0.55f
            }
            if (targetRow != null) {
                targetRow.add(box)
            } else {
                rawRows.add(mutableListOf(box))
            }
        }

        // 3. Merge each horizontal row (handling flanking scene numbers and 2-column breakdown tables).
        val visualRows = rawRows.mapNotNull { rowBoxes ->
            rowBoxes.sortBy { it.left }
            formatVisualRow(rowBoxes, w)
        }
        if (visualRows.isEmpty()) return ""

        val heights = visualRows.map { it.height }.sorted()
        val medianHeight = heights[heights.size / 2].coerceAtLeast(8f)

        // 4. Emit single newlines within contiguous blocks and double newlines (\n\n) at block boundaries.
        val out = StringBuilder()
        visualRows.forEachIndexed { idx, row ->
            if (idx > 0) {
                val prev = visualRows[idx - 1]
                val vGap = row.top - prev.bottom
                val largeVerticalGap = vGap > medianHeight * 0.62f
                val currLooksLikeCenteredCue = row.leftNorm in 0.31f..0.65f &&
                    row.text == row.text.uppercase() &&
                    row.text.any { it in 'A'..'Z' } &&
                    !row.text.contains(':')
                val dialogueToLeftMarginAction = prev.leftNorm >= 0.21f && row.leftNorm <= 0.17f
                val currIsSlug = ScriptParser.isSceneHeading(row.text)

                if (largeVerticalGap || currLooksLikeCenteredCue || dialogueToLeftMarginAction || currIsSlug) {
                    out.append("\n\n")
                } else {
                    out.append("\n")
                }
            }
            out.append(row.text)
        }
        return out.toString().trim()
    }

    private fun formatVisualRow(boxes: List<OcrLineBox>, pageWidth: Float): VisualRow? {
        if (boxes.isEmpty()) return null
        var working = boxes

        // Strip flanking left/right scene numbers when a slugline sits between them (e.g. "14  INT. ROOM - NIGHT  14").
        if (working.size >= 2 && working.any { SLUG_PREFIX_REGEX.containsMatchIn(it.text.trim()) }) {
            if (working.first().left / pageWidth < 0.16f && MARGIN_SCENE_NUM_REGEX.matches(working.first().text.trim())) {
                working = working.drop(1)
            }
            if (working.size >= 2 && working.last().left / pageWidth > 0.78f && MARGIN_SCENE_NUM_REGEX.matches(working.last().text.trim())) {
                working = working.dropLast(1)
            }
        }
        if (working.isEmpty()) return null

        val top = working.minOf { it.top }
        val bottom = working.maxOf { it.bottom }
        val height = (bottom - top).coerceAtLeast(6f)
        val leftNorm = (working.first().left / pageWidth).coerceIn(0f, 1f)

        val text = if (working.size >= 2) {
            val first = working.first()
            val second = working[1]
            val firstClean = first.text.trim().trimEnd(':').trim()
            val firstNormKey = firstClean.lowercase().replace(Regex("""\s+"""), " ")
            val isTablePair = (first.left / pageWidth) < 0.36f &&
                (second.left - first.right) > pageWidth * 0.025f &&
                firstNormKey in TABLE_LABEL_KEYS
            if (isTablePair) {
                val rhs = working.drop(1).joinToString(" ") { it.text.trim() }.trim()
                "$firstClean: $rhs"
            } else {
                working.joinToString(" ") { it.text.trim() }.trim()
            }
        } else {
            working.first().text.trim()
        }

        if (text.isBlank()) return null
        return VisualRow(text = text, leftNorm = leftNorm, top = top, bottom = bottom, height = height)
    }

    private fun normalizeMarginKey(text: String): String =
        text.trim().lowercase()
            .replace(Regex("""\bpage\s*\d+\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Converts RTF (`.rtf`) screenplay/notice files into clean plain text while preserving paragraph breaks.
     */
    fun stripRtfIfNeeded(raw: String): String {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("{\\rtf")) return raw

        // Remove non-body destination groups ({\fonttbl...}, {\colortbl...}, {\stylesheet...}, {\info...}, {\*\...})
        val sb = StringBuilder()
        var i = 0
        var skipDepth = 0
        var depth = 0
        val skipHeaders = listOf("\\fonttbl", "\\colortbl", "\\stylesheet", "\\info", "\\*")

        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '{') {
                depth++
                val lookahead = trimmed.substring(i + 1, minOf(trimmed.length, i + 18)).trimStart()
                if (skipDepth == 0 && skipHeaders.any { lookahead.startsWith(it) }) {
                    skipDepth = depth
                }
                i++
                continue
            }
            if (c == '}') {
                if (skipDepth == depth) {
                    skipDepth = 0
                }
                depth = (depth - 1).coerceAtLeast(0)
                i++
                continue
            }
            if (skipDepth > 0) {
                i++
                continue
            }
            if (c == '\\') {
                if (i + 1 < trimmed.length) {
                    val next = trimmed[i + 1]
                    if (next == '\\' || next == '{' || next == '}') {
                        sb.append(next)
                        i += 2
                        continue
                    }
                    if (next == '\n' || next == '\r') {
                        sb.append('\n')
                        i += 2
                        continue
                    }
                    if (next == '\'' && i + 3 < trimmed.length) {
                        val hex = trimmed.substring(i + 2, i + 4)
                        val code = hex.toIntOrNull(16)
                        if (code != null) sb.append(code.toChar())
                        i += 4
                        continue
                    }
                    // Parse control word
                    var j = i + 1
                    while (j < trimmed.length && trimmed[j].isLetter()) j++
                    val word = trimmed.substring(i + 1, j)
                    var k = j
                    if (k < trimmed.length && (trimmed[k] == '-' || trimmed[k].isDigit())) {
                        k++
                        while (k < trimmed.length && trimmed[k].isDigit()) k++
                    }
                    if (k < trimmed.length && trimmed[k] == ' ') k++

                    when (word) {
                        "par", "line", "sect", "page" -> sb.append('\n')
                        "tab" -> sb.append(' ')
                        "emdash" -> sb.append('—')
                        "endash" -> sb.append('–')
                        "lquote" -> sb.append('‘')
                        "rquote" -> sb.append('’')
                        "ldblquote" -> sb.append('“')
                        "rdblquote" -> sb.append('”')
                    }
                    i = k
                    continue
                }
            }
            if (c != '\r' && c != '\n') {
                sb.append(c)
            }
            i++
        }

        return sb.toString()
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
