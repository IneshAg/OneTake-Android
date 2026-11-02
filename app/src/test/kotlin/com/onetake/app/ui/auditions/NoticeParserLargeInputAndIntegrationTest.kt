package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Empirical stress test harness for Milestone 3 (Requirements R3):
 * 1. Performance and memory allocation on large notices (10k+ characters).
 * 2. Pathological regex inputs (ReDoS stress testing).
 * 3. Deep integration between NoticeParser.parseSmart and AddAuditionSheet (AddDraft.allAvailableRoles).
 * 4. Empirical verification of edge case failure modes in Tier 3 baseline & smart parser.
 */
class NoticeParserLargeInputAndIntegrationTest {

    private val testDate = LocalDateTime.of(2026, 10, 24, 10, 0)

    // =========================================================================
    // 1. Performance & Memory on Large Notices (10k+ characters)
    // =========================================================================

    @Test
    fun testLargeNotice_10kChars_performanceAndMemory() {
        val noticeHeader = """
            Project: The Grand Illusion
            Role: Arthur Pendelton (Series Regular)
            LOOKING FOR:
            Arthur Pendelton (Series Regular) - Male, 40s
            Clara Oswald (Lead) - Female, 30s
            Detective Miller (Supporting) - Male, 50s

            Submission Deadline: Friday Oct 30 at 5:00 PM
            Please slate first, shoot in landscape, full length framing.
            Submit via Actors Access / Eco Cast.

        """.trimIndent()

        // Generate ~15k characters of dialogue sides
        val sidesBuilder = StringBuilder()
        var sceneNum = 1
        while (sidesBuilder.length < 15_000) {
            sidesBuilder.append("SCENE $sceneNum - INT. DRAWING ROOM - NIGHT\n\n")
            sidesBuilder.append("ARTHUR\nWe have to decide what to do before the clock strikes midnight.\n\n")
            sidesBuilder.append("CLARA\nThere is no turning back from this decision, Arthur. Look at me.\n\n")
            sidesBuilder.append("MILLER\nThe perimeter is secured, but we cannot hold them indefinitely.\n\n")
            sceneNum++
        }

        val largeNoticeText = noticeHeader + sidesBuilder.toString()
        assertTrue("Input notice must be at least 10k chars", largeNoticeText.length > 10_000)

        // Warm up
        repeat(5) {
            NoticeParser.parse(largeNoticeText, testDate)
        }

        // Measure memory before
        System.gc()
        Thread.sleep(50)
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        // Measure execution time
        val iterations = 30
        val startTime = System.nanoTime()
        var lastResult: NoticeFields? = null
        repeat(iterations) {
            lastResult = NoticeParser.parse(largeNoticeText, testDate)
        }
        val elapsedNanos = System.nanoTime() - startTime
        val avgMs = (elapsedNanos / iterations) / 1_000_000.0

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val memDeltaMb = (memAfter - memBefore) / (1024.0 * 1024.0)

        println("Performance on 15k char notice: avg $avgMs ms per parse, mem delta: $memDeltaMb MB")

        assertNotNull(lastResult)
        assertEquals("The Grand Illusion", lastResult!!.project)
        assertEquals("Arthur Pendelton", lastResult!!.role)
        assertEquals(DocumentKind.NoticeWithSides, lastResult!!.kind)
        assertTrue("Must extract all available roles", lastResult!!.allAvailableRoles.size >= 3)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), lastResult!!.due)
        assertTrue("Must detect Slate first", "Slate first" in lastResult!!.asks)
        assertTrue("Must detect Landscape", "Landscape" in lastResult!!.asks)

        // On 15k characters with regex and line scanning, should execute under 50ms on modern JVM
        assertTrue("15k char parse must execute in under 50ms (was $avgMs ms)", avgMs < 50.0)
    }

    @Test
    fun testExtremelyLargeNotice_50kChars_noMemoryLeakOrCrash() {
        val noticeHeader = """
            Project: Odyssey 2026
            Role: Commander Vance
            Due: Oct 30 at 1700 hrs
            Submit via Spotlight.

        """.trimIndent()

        val sb = StringBuilder(noticeHeader)
        var i = 0
        while (sb.length < 50_000) {
            sb.append("COMMANDER VANCE: Status report on sector $i.\n")
            sb.append("LIEUTENANT: Systems operating at normal capacity, Commander.\n")
            i++
        }

        val text50k = sb.toString()
        assertTrue("Text must exceed 50,000 characters", text50k.length >= 50_000)

        val startTime = System.nanoTime()
        val result = NoticeParser.parse(text50k, testDate)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        println("50k char notice executed in: $elapsedMs ms")
        assertEquals("Odyssey 2026", result.project)
        assertEquals("Commander Vance", result.role)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), result.due)
        assertEquals(DocumentKind.NoticeWithSides, result.kind)
        assertTrue("50k char parse must finish in under 200ms", elapsedMs < 200.0)
    }

    // =========================================================================
    // 2. ReDoS & Pathological Edge Cases
    // =========================================================================

    @Test(timeout = 3000)
    fun testReDosResistance_repetitiveDelimiterPatterns() {
        // Thousands of consecutive delimiters
        val pathological = "Fwd: " + " - - ".repeat(2500) + "\nRole: Dave\nDue: Oct 30 at 5pm"
        val start = System.currentTimeMillis()
        val f = NoticeParser.parse(pathological, testDate)
        val duration = System.currentTimeMillis() - start
        println("Pathological delimiter parse completed in: $duration ms")
        assertTrue("Must complete in under 500ms without catastrophic backtracking", duration < 500)
        assertEquals("Dave", f.role)
    }

    @Test(timeout = 3000)
    fun testReDosResistance_unmatchedQuotesAndAsPatterns() {
        // Pathological input designed to stress PROJECT_PATTERNS and ROLE_PATTERNS
        val longUppercase = "as " + "WORD ".repeat(2000) + "in \"" + "TITLE ".repeat(2000)
        val start = System.currentTimeMillis()
        val f = NoticeParser.parse(longUppercase, testDate)
        val duration = System.currentTimeMillis() - start
        println("Pathological quotes parse completed in: $duration ms")
        assertTrue("Must complete in under 500ms without ReDoS", duration < 500)
    }

    @Test
    fun testMultiRoleBreakdown_hundredRoles_noOverflow() {
        val sb = StringBuilder("Project: Mega Cast\nLOOKING FOR:\n")
        for (i in 1..100) {
            sb.append("CHARACTER_$i (Supporting) - Role description for character $i\n")
        }
        sb.append("\nDue: Oct 30 at 5pm\n")

        val f = NoticeParser.parse(sb.toString(), testDate)
        assertEquals("Mega Cast", f.project)
        assertEquals(100, f.allAvailableRoles.size)
        assertEquals("CHARACTER_1", f.allAvailableRoles.first())
        assertEquals("CHARACTER_100", f.allAvailableRoles.last())
        assertEquals("CHARACTER_1", f.role)
    }

    // =========================================================================
    // 3. Integration with AddAuditionSheet (AddDraft.allAvailableRoles)
    // =========================================================================

    @Test
    fun testIntegration_addParseNotice_cleanBreakdown_propagatesAllAvailableRolesToAddDraft() {
        val notice = """
            Project: The Succession
            LOOKING FOR:
            LOGAN (Lead) - Patriarch
            KENDALL (Supporting) - Eldest son
            SHIV (Supporting) - Daughter
            ROMAN (Supporting) - Youngest son

            Due: Oct 30 at 5pm.
            Please slate first.

            INT. BOARDROOM - DAY

            LOGAN
            You are not serious people.

            KENDALL
            We have the votes, Dad.
        """.trimIndent()

        val draft = addParseNotice(notice)

        assertEquals("The Succession", draft.project)
        assertEquals("LOGAN", draft.role)
        assertEquals(listOf("LOGAN", "KENDALL", "SHIV", "ROMAN"), draft.allAvailableRoles)
        assertEquals(DocumentKind.NoticeWithSides, draft.kind)
        assertTrue("Slate first" in draft.asks)

        // Make audition for initial role (LOGAN)
        val auditionLogan = draft.makeAudition()
        assertEquals("LOGAN", auditionLogan.role)
        val loganScene = auditionLogan.scenes.firstOrNull()
        assertNotNull("Should parse scene", loganScene)
        // Check that LOGAN's lines are marked isMine = true
        val loganLine = loganScene!!.lines.firstOrNull { it.character == "LOGAN" }
        assertNotNull("LOGAN line must exist", loganLine)
        assertTrue("LOGAN line must have isMine=true", loganLine!!.isMine)

        val kendallLine = loganScene.lines.firstOrNull { it.character == "KENDALL" }
        assertNotNull("KENDALL line must exist", kendallLine)
        assertFalse("KENDALL line must have isMine=false when role is LOGAN", kendallLine!!.isMine)

        // Simulate user clicking on KENDALL in Multi-Role Switcher Chips:
        // draft.copy(role = "KENDALL")
        val draftKendall = draft.copy(role = "KENDALL")
        val auditionKendall = draftKendall.makeAudition()
        assertEquals("KENDALL", auditionKendall.role)
        val kendallScene = auditionKendall.scenes.firstOrNull()
        assertNotNull(kendallScene)

        val newLoganLine = kendallScene!!.lines.firstOrNull { it.character == "LOGAN" }
        val newKendallLine = kendallScene.lines.firstOrNull { it.character == "KENDALL" }
        assertFalse("LOGAN line must have isMine=false when role is KENDALL", newLoganLine!!.isMine)
        assertTrue("KENDALL line must have isMine=true when role is KENDALL", newKendallLine!!.isMine)
    }

    @Test
    fun testIntegration_parseSmart_withLlmRoles_propagatesToAddDraft() = runBlocking {
        val rawNotice = """
            Project: Frontier
            Role: Alex
            Due: Oct 30 at 5pm

            INT. CABIN - NIGHT

            ALEX
            Did you hear that?

            MORGAN
            It's just the wind.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Frontier (Season 2)",
                "targetRoleName": "MORGAN",
                "allAvailableRoles": ["MORGAN", "ALEX", "SHERIFF"],
                "roleSize": "Guest Star",
                "tapeDueIsoDate": "2026-10-31",
                "tapeDueTime24h": "18:00",
                "asks": ["Slate first", "Both profiles"],
                "firstScriptLineAnchor": "ALEX\nDid you hear that?"
            }
            """.trimIndent()
        }

        val fields = NoticeParser.parseSmart(rawNotice, extractor, today = testDate.toLocalDate())

        assertEquals("Frontier (Season 2)", fields.project)
        assertEquals("MORGAN", fields.role)
        assertEquals(listOf("MORGAN", "ALEX", "SHERIFF"), fields.allAvailableRoles)
        assertEquals("Guest Star", fields.roleSize)
        assertEquals(LocalDateTime.of(2026, 10, 31, 18, 0), fields.due)

        // Map into AddDraft (simulating UI ingestion in AddAuditionSheet / NewAuditionScreen)
        val draft = AddDraft(
            project = fields.project,
            role = fields.role,
            projectType = fields.projectType.ifBlank { "Feature film" },
            roleSize = fields.roleSize.ifBlank { "Lead" },
            castingOffice = fields.castingOffice,
            notes = fields.notes,
            due = fields.due,
            scenes = maxOf(1, fields.sceneCount),
            asks = fields.asks,
            destination = fields.destination,
            rawScriptText = fields.scriptText,
            kind = fields.kind,
            detectedCharacters = fields.detectedCharacters,
            allAvailableRoles = fields.allAvailableRoles,
        )

        assertEquals("MORGAN", draft.role)
        assertEquals(3, draft.allAvailableRoles.size)

        val audition = draft.makeAudition()
        assertEquals("Frontier (Season 2)", audition.project)
        assertEquals("MORGAN", audition.role)
        assertEquals("Guest Star", audition.roleSize)
        assertEquals(AuditionStatus.Due, audition.status)

        // Verify MORGAN lines have isMine=true in the created audition
        val scene = audition.scenes.firstOrNull()
        assertNotNull(scene)
        val morganLine = scene!!.lines.firstOrNull { it.character == "MORGAN" }
        assertNotNull(morganLine)
        assertTrue(morganLine!!.isMine)
    }

    // =========================================================================
    // 4. Failure Mode Detections (Empirical Bug Replications)
    // =========================================================================

    /**
     * BUG 1: Multi-role breakdown leaks non-character lines into `allAvailableRoles`
     * when there is no blank line or colon header after the role list.
     */
    @Test
    fun testBug1_multiRoleBreakdown_nonCharacterLinesLeakIntoAvailableRoles() {
        val notice = """
            Project: The Succession
            LOOKING FOR:
            LOGAN (Lead) - Patriarch
            KENDALL (Supporting) - Eldest son
            SHIV (Supporting) - Daughter
            ROMAN (Supporting) - Youngest son
            Due Oct 30 at 5pm.
            Please slate first.
        """.trimIndent()

        val f = NoticeParser.parse(notice, testDate)
        println("All available roles extracted: ${f.allAvailableRoles}")

        // Check if non-character instructions leaked into allAvailableRoles
        val leakedLines = f.allAvailableRoles.filter {
            it.contains("Due", ignoreCase = true) || it.contains("slate", ignoreCase = true)
        }
        if (leakedLines.isNotEmpty()) {
            println("EMPIRICAL FINDING BUG 1: Non-character lines leaked into allAvailableRoles: $leakedLines")
        }
        // This assertion verifies whether the bug is present
        assertFalse(
            "BUG DETECTED: allAvailableRoles contains non-character notice lines: $leakedLines",
            leakedLines.isNotEmpty()
        )
    }

    /**
     * BUG 2: Role size in multi-role breakdown is stolen from a subsequent character
     * because extractAllAvailableRoles discards role size and detectInlineRoleSize scans globally.
     */
    @Test
    fun testBug2_multiRoleBreakdown_roleSizeStolenFromSubsequentCharacter() {
        val notice = """
            Project: Echoes
            LOOKING FOR:
            MARCUS (Lead) - Male, 30s
            DETECTIVE JANE (Guest Star) - Female, 40s
            Due Oct 30 at 5pm.
        """.trimIndent()

        val f = NoticeParser.parse(notice, testDate)
        println("Selected role: '${f.role}', roleSize: '${f.roleSize}'")

        // Role is MARCUS whose declared size is Lead. But NoticeParser assigns "Guest star"
        // because normalizeRoleSize checks "guest star" before "lead" globally!
        assertEquals("MARCUS", f.role)
        assertEquals("BUG DETECTED: Role MARCUS (Lead) was assigned wrong size '${f.roleSize}'", "Lead", f.roleSize)
    }

    /**
     * BUG 3: parseSmart crashes the app if HybridNoticeExtractor throws an unhandled exception.
     */
    @Test
    fun testBug3_parseSmart_unhandledExtractorExceptionCrashesCaller() = runBlocking {
        val rawNotice = """
            Project: Resilience Test
            Role: Survivor
            Due: Oct 30 at 5pm
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            throw OutOfMemoryError("Simulated LiteRT-LM GPU context OOM")
        }

        try {
            val result = NoticeParser.parseSmart(rawNotice, extractor, today = testDate.toLocalDate())
            assertEquals("Resilience Test", result.project)
        } catch (e: Throwable) {
            println("EMPIRICAL FINDING BUG 3: parseSmart crashed with ${e.javaClass.name}: ${e.message}")
            fail("BUG DETECTED: parseSmart threw unhandled exception instead of falling back to baseline: ${e.javaClass.name}")
        }
    }

    /**
     * BUG 4: Typo normalization is lost when firstScriptLineAnchor is present.
     */
    @Test
    fun testBug4_parseSmart_typoNormalizationLostWhenAnchorPresent() = runBlocking {
        val notice = """
            Project: Anchor Test
            Role: Maya

            SCENE 1:
            0FFICER DAVE: Stop right there!
            MAYA: Never!
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Anchor Test",
                "targetRoleName": "OFFICER DAVE",
                "characterNormalizationMap": {
                    "0FFICER DAVE": "OFFICER DAVE"
                },
                "firstScriptLineAnchor": "0FFICER DAVE: Stop right there!"
            }
            """.trimIndent()
        }

        val result = NoticeParser.parseSmart(notice, extractor, today = testDate.toLocalDate())
        println("Script text after anchor slicing: ${result.scriptText}")

        val containsFixedName = result.scriptText.contains("OFFICER DAVE:")
        if (!containsFixedName) {
            println("EMPIRICAL FINDING BUG 4: Normalized name 'OFFICER DAVE:' missing from scriptText: '${result.scriptText}'")
        }
        assertTrue("BUG DETECTED: scriptText must contain normalized character name 'OFFICER DAVE:'", containsFixedName)
    }
}
