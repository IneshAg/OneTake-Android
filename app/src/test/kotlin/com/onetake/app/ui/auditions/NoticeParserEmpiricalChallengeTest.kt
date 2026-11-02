package com.onetake.app.ui.auditions

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Empirical challenge test suite for Milestone 3 (Requirements R3: Smart NoticeParser & Section 7 Nuances).
 * Stress-tests Section 7 edge cases, latency benchmarks, and parseSmart integration.
 *
 * Verifies spec conformance across 14 challenge scenarios.
 */
class NoticeParserEmpiricalChallengeTest {

    private val now = LocalDateTime.of(2026, 10, 24, 9, 0) // Saturday Oct 24, 2026

    // =========================================================================
    // 7.1 Titles
    // =========================================================================

    @Test
    fun test71_complexEmailHeaderWithExternalTagAndWorkingTitle() {
        val notice = """
            Fwd: Re: [EXTERNAL] Audition Alert: UNTITLED "HEIST" PROJECT
            Role: Officer Dave
            Due: Oct 30 at 5pm
        """.trimIndent()

        val f = NoticeParser.parse(notice, now)
        assertEquals("UNTITLED \"HEIST\" PROJECT", f.project)
        assertEquals("Officer Dave", f.role)
    }

    @Test
    fun test71_agencyHeaderIgnoredInFavorOfTitleWithoutExplicitLabel() {
        val notice = """
            WME CASTING NOTICE - READ CAREFULLY
            THE HEIST
            Role: Officer Dave
            Due: Oct 30 at 5pm
        """.trimIndent()

        val f = NoticeParser.parse(notice, now)
        assertEquals(DocumentKind.CastingNotice, f.kind)
        assertEquals("THE HEIST", f.project)
        assertEquals("Officer Dave", f.role)
    }

    @Test
    fun test71_agencyHeaderWithExplicitProject_correctlyExtractsTitle() {
        val notice = """
            WME CASTING NOTICE - READ CAREFULLY
            Project: UNTITLED "HEIST" PROJECT
            Role: Officer Dave
            Due: Oct 30 at 5pm
        """.trimIndent()

        val f = NoticeParser.parse(notice, now)
        assertEquals("UNTITLED \"HEIST\" PROJECT", f.project)
        assertEquals("Officer Dave", f.role)
    }

    @Test
    fun test71_quotedWorkingTitlesPreserved() {
        val notice = """
            Project: UNTITLED "HEIST" PROJECT
            Role: Detective Miller
            Due Oct 30 at 5pm.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals("UNTITLED \"HEIST\" PROJECT", f.project)
    }

    // =========================================================================
    // 7.2 Roles & Multi-Role Breakdown
    // =========================================================================

    @Test
    fun test72_auditionForActorAsCharacterExtractsCharacterNotActor() {
        val noticeWithoutIn = """
            Audition for John Smith as Officer Dave
            Due Oct 30 at 5pm.
        """.trimIndent()

        val f1 = NoticeParser.parse(noticeWithoutIn, now)
        assertEquals("Officer Dave", f1.role)

        val noticeWithIn = """
            Audition for John Smith as Officer Dave in THE WIRE
            Due Oct 30 at 5pm.
        """.trimIndent()

        val f2 = NoticeParser.parse(noticeWithIn, now)
        assertEquals("Officer Dave", f2.role)
        assertEquals("THE WIRE", f2.project)
    }

    @Test
    fun test72_multiLineRoleBreakdownWithFivePlusCharacters() {
        val notice = """
            Project: City Under Siege
            LOOKING FOR:
            DETECTIVE MILLER (Lead) - Male, 40s
            OFFICER CHEN (Supporting) - Female, 30s
            CHIEF ADAMS (Guest Star) - Male, 50s
            SARAH CONNOR (Recurring) - Female, 20s
            MAYOR DAVIS (Co-Star) - Any, 60s
            GUARD BOB (Cameo) - Male, 30s

            Due: Oct 30 at 5pm.
        """.trimIndent()

        val f = NoticeParser.parse(notice, now)
        assertEquals("City Under Siege", f.project)
        assertEquals("DETECTIVE MILLER", f.role)

        // 6 characters are extracted into allAvailableRoles
        assertEquals(6, f.allAvailableRoles.size)
        assertTrue(f.allAvailableRoles.contains("DETECTIVE MILLER"))
        assertTrue(f.allAvailableRoles.contains("OFFICER CHEN"))
        assertTrue(f.allAvailableRoles.contains("CHIEF ADAMS"))
        assertTrue(f.allAvailableRoles.contains("SARAH CONNOR"))
        assertTrue(f.allAvailableRoles.contains("MAYOR DAVIS"))
        assertTrue(f.allAvailableRoles.contains("GUARD BOB"))
    }

    // =========================================================================
    // 7.3 Dates & Rollover
    // =========================================================================

    @Test
    fun test73_mixedNoticeShootAndCallbackIgnoredInFavorOfTapeDeadline() {
        val notice = """
            Project: The Last Frontier
            Role: Alex
            Callback Date: Nov 10
            Shoot Date: Dec 1-15
            Tape Due: Oct 28 at 5pm
        """.trimIndent()

        val f = NoticeParser.parse(notice, now)
        assertNotNull("Tape due date must be parsed", f.due)
        assertEquals(LocalDateTime.of(2026, 10, 28, 17, 0), f.due)
    }

    @Test
    fun test73_decemberToJanuaryYearRollover() {
        val decNow = LocalDateTime.of(2026, 12, 28, 9, 0)
        val rolloverNotice = "Project: New Year Pilot\nRole: Sam\nSelf-tape due Jan 5 at noon"

        val parsed = NoticeParser.parseDue(rolloverNotice, decNow)
        assertNotNull("Rollover date must be parsed", parsed)
        assertEquals(2027, parsed!!.year)
        assertEquals(1, parsed.monthValue)
        assertEquals(5, parsed.dayOfMonth)
        assertEquals(12, parsed.hour)
    }

    @Test
    fun test73_cobEodAndMilitaryTime() {
        val cobNotice = "Project: Secret\nRole: Alex\nSelf-tape due Oct 30 COB"
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), NoticeParser.parseDue(cobNotice, now))

        val eodNotice = "Project: Secret\nRole: Alex\nSubmit by Thursday EOD"
        assertEquals(LocalDateTime.of(2026, 10, 29, 17, 0), NoticeParser.parseDue(eodNotice, now))

        val militaryNotice = "Project: Secret\nRole: Alex\nTape due Oct 30 at 1700 hrs"
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), NoticeParser.parseDue(militaryNotice, now))
    }

    // =========================================================================
    // 7.4 Deliverables Scope Isolation
    // =========================================================================

    @Test
    fun test74_screenplayDirectionsDoNotTriggerFalseDeliverableAsks() {
        val noticeWithScreenplayProse = """
            Project: The Looking Glass
            Role: Dave
            Due: Oct 30 at 5pm

            INT. STUDIO - DAY

            Dave adjusts the full length mirror on the wide landscape set.
            He pauses, looking at both profiles under 3 min before turning.

            DAVE
            I see everything now.
        """.trimIndent()

        val f = NoticeParser.parse(noticeWithScreenplayProse, now)
        assertTrue(
            "Screenplay action text must NOT trigger deliverable asks, but got: ${f.asks}",
            f.asks.isEmpty()
        )
        assertEquals(DocumentKind.NoticeWithSides, f.kind)
        assertTrue(f.scriptText.contains("Dave adjusts the full length mirror"))
    }

    @Test
    fun test74_headerDeliverableAsksCorrectlyExtracted() {
        val noticeWithHeaderAsks = """
            Project: The Looking Glass
            Role: Dave
            Please slate first and shoot in landscape. Full length shot required.
            Due: Oct 30 at 5pm

            INT. STUDIO - DAY

            DAVE
            I see everything now.
        """.trimIndent()

        val f = NoticeParser.parse(noticeWithHeaderAsks, now)
        assertTrue("Must contain Slate first", "Slate first" in f.asks)
        assertTrue("Must contain Landscape", "Landscape" in f.asks)
        assertTrue("Must contain Full length", "Full length" in f.asks)
    }

    // =========================================================================
    // Latency Benchmark (< 2ms)
    // =========================================================================

    @Test
    fun testLatency_deterministicParseUnderTwoMilliseconds() {
        val realisticNotice = """
            WME CASTING NOTICE
            Project: Echoes of Eternity
            LOOKING FOR:
            DETECTIVE MILLER (Lead) - Male, 40s
            OFFICER CHEN (Supporting) - Female, 30s
            CHIEF ADAMS (Guest Star) - Male, 50s
            Due Oct 30 at 5:00 PM.
            Please slate first, shoot in landscape, full body framing.
            Submit via Actors Access.

            INT. PRECINCT - NIGHT

            DETECTIVE MILLER
            We don't have much time left.

            OFFICER CHEN
            The backup team is on their way.
        """.trimIndent()

        // Warm-up JIT
        repeat(100) {
            NoticeParser.parse(realisticNotice, now)
        }

        val iterations = 500
        val startTime = System.nanoTime()
        repeat(iterations) {
            NoticeParser.parse(realisticNotice, now)
        }
        val totalElapsedNanos = System.nanoTime() - startTime
        val avgMs = (totalElapsedNanos / iterations) / 1_000_000.0

        println("Deterministic parse benchmark: $avgMs ms per parse over $iterations iterations")
        assertTrue(
            "Deterministic parse must execute in < 2ms (measured: $avgMs ms)",
            avgMs < 2.0
        )
    }

    // =========================================================================
    // parseSmart with Mock HybridNoticeExtractor
    // =========================================================================

    @Test
    fun testParseSmart_characterNormalizationAndScriptLineAnchor() = runBlocking {
        val rawNotice = """
            CONFIDENTIAL CASTING NOTICE - DO NOT DISTRIBUTE
            Project: Detective Chronicles
            Role: Officer Dave
            Due: Friday Oct 30 at 5pm

            SIDE PREAMBLE AND NOTES:
            Actors should memorize all sides.

            SCENE 1 START:
            0FFICER DAVE: Suspect is running east.
            CHIEF: Do not lose him.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Detective Chronicles (Production)",
                "targetRoleName": "OFFICER DAVE",
                "roleSize": "Series Regular",
                "tapeDueIsoDate": "2026-10-30",
                "tapeDueTime24h": "17:00",
                "asks": ["Slate first", "Both profiles"],
                "characterNormalizationMap": {
                    "0FFICER DAVE": "OFFICER DAVE"
                },
                "firstScriptLineAnchor": "OFFICER DAVE: Suspect is running east."
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(rawNotice, extractor = extractor, today = now.toLocalDate())

        assertEquals("Detective Chronicles (Production)", result.project)
        assertEquals("OFFICER DAVE", result.role)
        assertEquals("Series Regular", result.roleSize)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), result.due)
        assertEquals(listOf("Slate first", "Both profiles"), result.asks)

        // Verify anchor slicing removed preamble
        assertFalse("Preamble must not be in scriptText", result.scriptText.contains("CONFIDENTIAL CASTING NOTICE"))
        assertFalse("Notes must not be in scriptText", result.scriptText.contains("Actors should memorize"))

        // Verify OCR normalization fixed "0FFICER DAVE" to "OFFICER DAVE"
        assertTrue("Normalized name must appear in scriptText", result.scriptText.contains("OFFICER DAVE:"))
        assertFalse("Typo name must not appear in scriptText", result.scriptText.contains("0FFICER DAVE:"))
    }

    @Test
    fun testParseSmart_gracefulFallbackWhenExtractorFails() = runBlocking {
        val rawNotice = """
            Project: Safe Fallback
            Role: Alex
            Due: Oct 30 at 5pm
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw RuntimeException("Simulated inference engine crash")
        }

        val result = NoticeParser.parseSmart(rawNotice, extractor = extractor, today = now.toLocalDate())
        assertEquals("Safe Fallback", result.project)
        assertEquals("Alex", result.role)
    }
}
