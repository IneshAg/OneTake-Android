package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.models.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Empirical Adversarial Challenger Stress Harness for NoticeParser & parseSmart.
 *
 * Verifies:
 * 1. Large inputs (100k+ characters, 500 roles, 10,000 lines, nested formatting)
 * 2. ReDoS resistance & pathological backtracking patterns
 * 3. Extractor crash, OOM, Error, and Cancellation resilience in parseSmart
 * 4. OCR character normalization, anchor matching variants, and sides attribution
 * 5. Section 7 edge cases: Agency prefixes, email subjects, working titles, dates
 */
class NoticeParserChallengerStressTest {

    private val testDate = LocalDateTime.of(2026, 11, 2, 10, 0)
    private val testLocalDate = testDate.toLocalDate()

    // =========================================================================
    // 1. Extreme Scale & Deep Breakdown Inputs
    // =========================================================================

    @Test(timeout = 5000)
    fun testExtremeScale_100kNoticeWithDeepSides_executesUnderThreshold() {
        val header = """
            Project: Star Empire Saga
            Role: Admiral Thrawn (Series regular)
            LOOKING FOR:
            Admiral Thrawn (Series regular) - Chiss imperial strategist
            Captain Pellaeon (Supporting) - Imperial officer
            Rukh (Cameo) - Noghri bodyguard
            
            Due: Nov 15 at 1700 hrs
            Submit via Spotlight.
            Please slate first, shoot in landscape, full body framing.
            
        """.trimIndent()

        val sb = StringBuilder(header)
        var scene = 1
        while (sb.length < 120_000) {
            sb.append("\nSCENE $scene - INT. BRIDGE - STAR DESTROYER - NIGHT\n\n")
            sb.append("ADMIRAL THRAWN\nTo defeat an enemy, you must know them. Not simply their battle tactics, but their art.\n\n")
            sb.append("CAPTAIN PELLAEON\nSir, the Rebel fleet has emerged from hyperspace at coordinates zero-three-one.\n\n")
            sb.append("RUKH\nI smell betrayal on the lower decks, Grand Admiral.\n\n")
            scene++
        }

        val largeNotice = sb.toString()
        assertTrue("Text must exceed 100,000 chars", largeNotice.length > 100_000)

        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(largeNotice, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("Parsed 120k char document with $scene scenes in: $elapsed ms")
        assertEquals("Star Empire Saga", result.project)
        assertEquals("Admiral Thrawn", result.role)
        assertEquals("Series regular", result.roleSize)
        assertEquals(DocumentKind.NoticeWithSides, result.kind)
        assertEquals(3, result.allAvailableRoles.size)
        assertEquals(LocalDateTime.of(2026, 11, 15, 17, 0), result.due)
        assertEquals(Destination.Spotlight, result.destination)
        assertTrue(result.asks.contains("Slate first"))
        assertTrue(result.asks.contains("Landscape"))
        assertTrue(result.asks.contains("Full length"))
        assertTrue("120k char parse must finish in under 1000ms", elapsed < 1000)
    }

    @Test(timeout = 10000)
    fun testExtremeScale_1MillionChars_executesWithoutOom() {
        val header = """
            Project: Infinite Script
            Role: Cipher
            Due: Dec 1 at 12:00 PM
            Submit via Casting Networks.
            Please slate first.
            
        """.trimIndent()

        val sb = StringBuilder(header)
        var lineCount = 0
        while (sb.length < 1_000_000) {
            sb.append("CIPHER: Processing block $lineCount of the infinite stream.\n")
            sb.append("OPERATOR: Roger that, maintaining quantum integrity.\n")
            lineCount++
        }

        val text1Mb = sb.toString()
        assertTrue("Input must be at least 1MB", text1Mb.length >= 1_000_000)

        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(text1Mb, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("1,000,000 char (1MB) notice parsed in: $elapsed ms")
        assertEquals("Infinite Script", result.project)
        assertEquals("Cipher", result.role)
        assertEquals(Destination.CastingNetworks, result.destination)
        assertEquals(DocumentKind.NoticeWithSides, result.kind)
        assertTrue(result.asks.contains("Slate first"))
        assertTrue("1MB parse must finish in under 5000ms", elapsed < 5000)
    }

    @Test(timeout = 5000)
    fun testDeepBreakdown_500Roles_parsesAccuratelyWithoutStackOverflow() {
        val sb = StringBuilder("Project: The Multiverse Audition\nLOOKING FOR:\n")
        for (i in 1..500) {
            val size = when (i % 4) {
                0 -> "Lead"
                1 -> "Supporting"
                2 -> "Guest star"
                else -> "Co-star"
            }
            sb.append("HERO_$i ($size) - Character variant #$i from Earth-$i\n")
        }
        sb.append("\nDue: Nov 10 at 5:00 PM\nSubmit via Actors Access\n")

        val doc = sb.toString()
        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(doc, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("500-role breakdown parsed in: $elapsed ms")
        assertEquals("The Multiverse Audition", result.project)
        assertEquals(500, result.allAvailableRoles.size)
        assertEquals("HERO_1", result.role)
        assertEquals("Supporting", result.roleSize) // i=1 -> Supporting
        assertEquals("HERO_500", result.allAvailableRoles.last())
        assertTrue("500-role breakdown must parse under 500ms", elapsed < 500)
    }

    // =========================================================================
    // 2. ReDoS & Pathological Regular Expression Resistance
    // =========================================================================

    @Test(timeout = 3000)
    fun testReDosResistance_massiveRepeatedEmailPrefixesAndTags() {
        // Build realistic long forwarding chain with 50 compound prefix tokens
        val sb = StringBuilder()
        repeat(50) {
            sb.append("Fwd: Re: [EXTERNAL] [TAG-$it] Audition Alert: ")
        }
        sb.append("THE CHOSEN ONE\n")
        sb.append("Role: Gideon\nDue: Tomorrow at 5pm\n")

        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(sb.toString(), testDate)
        val elapsed = System.currentTimeMillis() - start

        println("Massive email prefix (50 reps = 250 prefix tags) parsed in: $elapsed ms")
        assertEquals("THE CHOSEN ONE", result.project)
        assertEquals("Gideon", result.role)
        assertTrue("Must not suffer exponential ReDoS", elapsed < 500)
    }

    @Test(timeout = 3000)
    fun testReDosResistance_deeplyNestedDelimitersAndDashes() {
        // Construct string with 5,000 delimiter segments
        val segments = (1..5000).joinToString(" - ") { "SEG_$it" }
        val doc = "Project: $segments\nRole: Dave\n"

        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(doc, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("5,000 delimiter segments parsed in: $elapsed ms")
        assertEquals("Dave", result.role)
        assertTrue("Delimiter splitting must not hang", elapsed < 500)
    }

    @Test(timeout = 3000)
    fun testReDosResistance_pathologicalAsInAndRolePatterns() {
        // String designed to stress greedy/reluctant matching in ROLE_PATTERNS & PROJECT_PATTERNS
        val pathological = "audition for " + "NAME ".repeat(3000) + " as the role of " + "CHARACTER ".repeat(3000) + " in " + "PROJECT ".repeat(3000)
        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(pathological, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("Pathological 'as/in' pattern executed in: $elapsed ms")
        assertTrue("Must not hang on pathological role/project regexes", elapsed < 500)
    }

    @Test(timeout = 3000)
    fun testReDosResistance_unmatchedQuotesAndColons() {
        val pathological = "Project: " + "\"".repeat(10000) + " TITLE " + "\"".repeat(10000) + "\nRole: Neo\n"
        val start = System.currentTimeMillis()
        val result = NoticeParser.parse(pathological, testDate)
        val elapsed = System.currentTimeMillis() - start

        println("Unmatched quotes executed in: $elapsed ms")
        assertEquals("Neo", result.role)
        assertTrue("Must complete under 500ms", elapsed < 500)
    }

    // =========================================================================
    // 3. Extractor Crash, OOM, Error, and Cancellation Resilience in parseSmart
    // =========================================================================

    @Test
    fun testParseSmart_extractorThrowsOutOfMemoryError_fallsBackToBaseline() = runBlocking {
        val notice = """
            Project: Robustness 101
            Role: Tester
            Due: Nov 10 at 5pm
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw OutOfMemoryError("Simulated JVM / native OOM in LiteRT")
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertNotNull(result)
        assertEquals("Robustness 101", result.project)
        assertEquals("Tester", result.role)
    }

    @Test
    fun testParseSmart_extractorThrowsStackOverflowError_fallsBackToBaseline() = runBlocking {
        val notice = """
            Project: Stack Test
            Role: Deep Recurser
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw StackOverflowError("Simulated deep recursion error")
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertNotNull(result)
        assertEquals("Stack Test", result.project)
        assertEquals("Deep Recurser", result.role)
    }

    @Test
    fun testParseSmart_extractorThrowsSecurityOrRuntime_fallsBackToBaseline() = runBlocking {
        val notice = """
            Project: Security Gate
            Role: Guard
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw SecurityException("Sandbox denial or permission failure")
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertNotNull(result)
        assertEquals("Security Gate", result.project)
        assertEquals("Guard", result.role)
    }

    @Test
    fun testParseSmart_cancellationExceptionIsNotSwallowed() = runBlocking {
        val notice = "Project: Cancel Test\nRole: Ghost\n"
        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw CancellationException("Job was cancelled by user navigation")
        }

        try {
            NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
            fail("CancellationException MUST be rethrown and not swallowed!")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled by user navigation", e.message)
        }
    }

    @Test
    fun testParseSmart_nullOrEmptyExtractor_returnsBaselineCleanly() = runBlocking {
        val notice = "Project: Zero AI\nRole: Analog Actor\n"
        val resultNullExtractor = NoticeParser.parseSmart(notice, extractor = null, today = testLocalDate)
        assertEquals("Zero AI", resultNullExtractor.project)
        assertEquals("Analog Actor", resultNullExtractor.role)

        val extractorReturnsNull = HybridNoticeExtractor(Application())
        extractorReturnsNull.testInferenceOverride = { null }
        val resultNullResponse = NoticeParser.parseSmart(notice, extractor = extractorReturnsNull, today = testLocalDate)
        assertEquals("Zero AI", resultNullResponse.project)
        assertEquals("Analog Actor", resultNullResponse.role)
    }

    @Test
    fun testParseSmart_malformedDatesAndTimesInLlmResponse_fallBackToBaselineDue() = runBlocking {
        val notice = """
            Project: Time Warp
            Role: Chrono
            Due: Nov 10 at 5:00 PM
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Time Warp Remake",
                "tapeDueIsoDate": "9999-99-99-INVALID",
                "tapeDueTime24h": "99:99:99"
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertEquals("Time Warp Remake", result.project)
        // Due date must fall back to baseline's valid Nov 10 at 17:00
        assertEquals(LocalDateTime.of(2026, 11, 10, 17, 0), result.due)
    }

    // =========================================================================
    // 4. OCR Normalization & Anchor Matching
    // =========================================================================

    @Test
    fun testParseSmart_ocrTypoInAnchorItself_matchesAndNormalizesSides() = runBlocking {
        val notice = """
            Project: Blade Runner
            Role: Deckard
            
            SCENE 1:
            0FFICER DECKARD: Replicants are like any other machine.
            RACHAEL: Have you ever retired a human by mistake?
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        // LLM provides anchor with the OCR typo as seen in raw text, and specifies normalization map
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Blade Runner 2049",
                "targetRoleName": "OFFICER DECKARD",
                "characterNormalizationMap": {
                    "0FFICER DECKARD": "OFFICER DECKARD"
                },
                "firstScriptLineAnchor": "0FFICER DECKARD: Replicants are like any other machine."
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertEquals("Blade Runner 2049", result.project)
        assertEquals("OFFICER DECKARD", result.role)
        assertTrue("Script text must have OCR typo fixed", result.scriptText.contains("OFFICER DECKARD: Replicants"))
        assertFalse("Script text must not retain raw OCR typo", result.scriptText.contains("0FFICER DECKARD"))
    }

    @Test
    fun testParseSmart_anchorAlreadyNormalizedByLlm_matchesInRawText() = runBlocking {
        val notice = """
            Project: Matrix
            LOOKING FOR:
            NEO (Lead)
            
            NE0: I know kung fu.
            M0RPHEUS: Show me.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        // LLM emitted already normalized anchor "NEO: I know kung fu."
        extractor.testInferenceOverride = {
            """
            {
                "targetRoleName": "NEO",
                "characterNormalizationMap": {
                    "NE0": "NEO",
                    "M0RPHEUS": "MORPHEUS"
                },
                "firstScriptLineAnchor": "NEO: I know kung fu."
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertEquals("NEO", result.role)
        assertTrue("Script text must contain normalized NEO", result.scriptText.contains("NEO: I know kung fu."))
        assertTrue("Script text must contain normalized MORPHEUS", result.scriptText.contains("MORPHEUS: Show me."))
    }

    @Test
    fun testParseSmart_multipleOcrTyposAcrossSceneLines() = runBlocking {
        val notice = """
            Project: Space Ops
            Role: Ripley
            
            INT. NOSTROMO - NIGHT
            
            R1PLEY: Final report of the commercial starship Nostromo.
            ASH: You still don't understand what you're dealing with.
            R1PLEY: This is officer R1PLEY signing off.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "targetRoleName": "RIPLEY",
                "characterNormalizationMap": {
                    "R1PLEY": "RIPLEY"
                },
                "firstScriptLineAnchor": "INT. NOSTROMO - NIGHT"
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertEquals("RIPLEY", result.role)
        assertFalse("All occurrences of R1PLEY must be replaced", result.scriptText.contains("R1PLEY"))
        assertTrue(result.scriptText.contains("RIPLEY: Final report"))
        assertTrue(result.scriptText.contains("This is officer RIPLEY signing off."))
    }

    @Test
    fun testParseSmart_anchorNotFoundInText_gracefullyPreservesBaselineSides() = runBlocking {
        val notice = """
            Project: Phantom Anchor
            Role: Ghost
            
            INT. ATTIC - DAY
            GHOST: Boo!
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Phantom Anchor True",
                "firstScriptLineAnchor": "NON_EXISTENT_ANCHOR_HALLUCINATION_LINE"
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testLocalDate)
        assertEquals("Phantom Anchor True", result.project)
        // Baseline script text must be preserved despite missing anchor
        assertTrue(result.scriptText.contains("GHOST: Boo!"))
    }

    // =========================================================================
    // 5. Section 7 Edge Cases & Agency Headers
    // =========================================================================

    @Test
    fun testSection7_agencyHeadersFilteredOut() {
        val agencies = listOf("CAA TALENT", "UTA", "WME", "ICM", "GERSH", "PARADIGM", "INNOVATIVE", "MANAGEMENT 360", "ANONYMOUS CONTENT")
        for (agency in agencies) {
            val notice = """
                $agency
                Project: The Last Kingdom
                Role: Uhtred
            """.trimIndent()
            val result = NoticeParser.parse(notice, testDate)
            assertEquals("Agency '$agency' must not be selected as project", "The Last Kingdom", result.project)
            assertEquals("Uhtred", result.role)
        }
    }

    @Test
    fun testSection7_workingTitleWithQuotesAndPrefixes() {
        val notice = """
            Fwd: Re: [EXTERNAL] Audition Alert: UNTITLED "CRIME NOIR" PROJECT
            Role: Detective Vance (Lead)
            Due: Nov 10 at 5pm
        """.trimIndent()

        val result = NoticeParser.parse(notice, testDate)
        assertEquals("UNTITLED \"CRIME NOIR\" PROJECT", result.project)
        assertEquals("Detective Vance", result.role)
        assertEquals("Lead", result.roleSize)
    }

    @Test
    fun testSection7_militaryTimeAndCobEod() {
        val notice1 = "Project: Op Alpha\nRole: Bravo\nDue: Nov 10 at 1700 hrs\n"
        val r1 = NoticeParser.parse(notice1, testDate)
        assertEquals(LocalTime.of(17, 0), r1.due?.toLocalTime())

        val notice2 = "Project: Op Beta\nRole: Bravo\nDue: Nov 10 COB\n"
        val r2 = NoticeParser.parse(notice2, testDate)
        assertEquals(LocalTime.of(17, 0), r2.due?.toLocalTime())

        val notice3 = "Project: Op Gamma\nRole: Bravo\nDue: Nov 10 EOD\n"
        val r3 = NoticeParser.parse(notice3, testDate)
        assertEquals(LocalTime.of(17, 0), r3.due?.toLocalTime())
    }

    @Test
    fun testSection7_deliverableAsksIsolatedFromDialogue() {
        val notice = """
            Project: Dialogue Trap
            Role: Actor
            
            INT. STAGE - DAY
            
            ACTOR
            Don't worry about the slate, just wipe the slate clean.
            Look at this landscape outside the window.
            She stood full length before me.
            We will review both profiles in 3 min.
        """.trimIndent()

        val result = NoticeParser.parse(notice, testDate)
        assertEquals("Actor", result.role)
        // Dialogue inside sides must NOT trigger deliverables
        assertTrue("Asks must be empty since deliverables appeared only in dialogue", result.asks.isEmpty())
    }

    @Test
    fun testSection7_headerNegatedSlateIgnored() {
        val notice = """
            Project: No Slate Show
            Role: Alex
            Please do not slate. No slate needed.
            Shoot in landscape.
        """.trimIndent()

        val result = NoticeParser.parse(notice, testDate)
        assertFalse("Must NOT include 'Slate first' when explicitly negated", "Slate first" in result.asks)
        assertTrue("Must include 'Landscape'", "Landscape" in result.asks)
    }
}
