package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.models.Destination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NoticeParserTest {
    private val now = LocalDateTime.of(2026, 10, 24, 9, 0) // a Saturday

    @Test
    fun readsHeaderFieldsWithoutSpillingIntoTheNextLine() {
        val f = NoticeParser.parse("Project: Night Shift\nRole: Maya\nDue: Friday 5pm\nPlease slate first, landscape.", now)
        assertEquals("Night Shift", f.project)
        assertEquals("Maya", f.role)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), f.due)
        assertEquals(listOf("Slate first", "Landscape"), f.asks)
    }

    @Test
    fun lowercaseWordsAfterInAreNotAProject() {
        val f = NoticeParser.parse("Self-tape request for MAYA in Night Shift. Tapes due in two days.", now)
        assertEquals("Night Shift", f.project)
        assertEquals("MAYA", f.role)
    }

    @Test
    fun noticeHeaderLinesAreNotScript() {
        val f = NoticeParser.parse("Project: Night Shift\nRole: Maya\n\nMAYA: You came back.\nDEV: I never left.", now)
        assertEquals("MAYA: You came back.\nDEV: I never left.", f.scriptText)
    }

    @Test
    fun parsesCommonDueDateForms() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 10, 0), NoticeParser.parseDue("Submit by Oct 30 at 10:00 AM", now))
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), NoticeParser.parseDue("Send your tape by tomorrow noon.", now))
        assertEquals(LocalDateTime.of(2026, 10, 28, 10, 0), NoticeParser.parseDue("Shoot dates Nov 3-10.\nSelf tape due 10/28.", now))
        assertNull(NoticeParser.parseDue("Tapes due in two days.", now))
    }

    @Test
    fun picksDestinationAndSkipsSentenceAsProject() {
        val f = NoticeParser.parse("Hi! Please send your tape by email.", now)
        assertEquals("", f.project)
        assertEquals(Destination.EmailOrLink, f.destination)
        assertTrue(f.asks.isEmpty())
        assertEquals("Pure email notice without sides must have empty scriptText", "", f.scriptText)
        assertEquals(DocumentKind.CastingNotice, f.kind)
    }

    @Test
    fun `classifies pure screenplay sides as SidesOnly without hallucinating project due or asks from dialogue`() {
        val pureSides = """
            14 INT. APARTMENT 4B - DAY 14

            MAYA
            He's crashing in Room 4B until Sunday. She sat in the sun and didn't blink.
            Let's wipe the slate clean before we head to London.

            COLE
            You really think that's going to work?
        """.trimIndent()

        val f = NoticeParser.parse(pureSides, now)

        assertEquals(DocumentKind.SidesOnly, f.kind)
        assertEquals("Dialogue 'in Room 4B' or 'to London' must NEVER become project title", "", f.project)
        assertNull("Dialogue 'Sunday' / 'sat' / 'sun' must NEVER become a due date", f.due)
        assertTrue("Dialogue 'wipe the slate clean' must NEVER trigger 'Slate first' ask: ${f.asks}", f.asks.isEmpty())
        assertEquals(listOf("MAYA", "COLE"), f.detectedCharacters)
        assertTrue("Script text should keep the sides intact", f.scriptText.contains("wipe the slate clean"))
    }

    @Test
    fun `parses multi-line uppercase breakdown labels and extracts roleSize projectType castingOffice and notes`() {
        val breakdown = """
            PROJECT
            Star Trek: Legacy
            TYPE
            TV Series
            ROLE
            Dr. Sarah Jenkins (Guest Star)
            CASTING DIRECTOR
            Hart / Miller Casting
            SUBMISSION DEADLINE
            Friday, Oct 30 at 5:30 PM
            NOTES
            Keep the delivery grounded and understated. Do not slate on this tape.
            SUBMIT VIA
            Actors Access / Eco Cast
        """.trimIndent()

        val f = NoticeParser.parse(breakdown, now)

        assertEquals(DocumentKind.CastingNotice, f.kind)
        assertEquals("Star Trek: Legacy", f.project)
        assertEquals("TV series", f.projectType)
        assertEquals("Dr. Sarah Jenkins", f.role)
        assertEquals("Guest star", f.roleSize)
        assertEquals("Hart / Miller Casting", f.castingOffice)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 30), f.due)
        assertFalse("'Do not slate' must negate 'Slate first': ${f.asks}", "Slate first" in f.asks)
        assertTrue(f.notes.contains("grounded and understated"))
        assertEquals("Pure breakdown without dialogue must have empty scriptText", "", f.scriptText)
    }

    @Test
    fun `separates combined email notice and sides cleanly into NoticeWithSides`() {
        val combined = """
            From: Sarah Hart <sarah@hartcasting.com>
            Subject: Self-Tape Request - Role of DET. COLE in THE GLASS HARBOR

            Hi! Please submit your self-tape via Spotlight by tomorrow at 6:00 PM.
            Shoot in landscape, include a full-length shot and slate first.

            INT. DOCKSIDE WAREHOUSE - NIGHT

            DET. COLE
            Drop the flashlight and step where I can see you.

            MARCUS
            I came here alone, Detective.
        """.trimIndent()

        val f = NoticeParser.parse(combined, now)

        assertEquals(DocumentKind.NoticeWithSides, f.kind)
        assertEquals("THE GLASS HARBOR", f.project)
        assertEquals("DET. COLE", f.role)
        assertEquals(Destination.Spotlight, f.destination)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), f.due)
        assertTrue("Slate first" in f.asks)
        assertTrue("Landscape" in f.asks)
        assertTrue("Full length" in f.asks)
        assertFalse("Email From: header must not leak into scriptText: ${f.scriptText}", f.scriptText.contains("hartcasting.com"))
        assertFalse("Email greeting must not leak into scriptText: ${f.scriptText}", f.scriptText.contains("Please submit your self-tape"))
        assertTrue("Sides dialogue must remain in scriptText", f.scriptText.contains("Drop the flashlight"))
    }

    @Test
    fun agencyHeaderIgnoredInFavorOfRealProjectTitle() {
        val notice = """
            CAA TALENT UPDATE
            SELF-TAPE REQUEST
            Project: Untitled Sci-Fi
            Role: Maya
            Due: Friday 5pm
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals("Untitled Sci-Fi", f.project)
        assertEquals("Maya", f.role)
        assertFalse("Agency header must not be project title: ${f.project}", f.project.contains("CAA TALENT"))
        assertFalse("Self-tape request header must not be project title: ${f.project}", f.project.contains("SELF-TAPE REQUEST"))
    }

    @Test
    fun forwardedEmailSubjectExtractsCleanTitleAndRole() {
        val notice = """
            Fwd: AUDITION - PARAMOUNT - ESCAPE HORIZON - ROLE OF SARAH
            Please submit by Friday 5pm.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals("ESCAPE HORIZON", f.project)
        assertEquals("SARAH", f.role)
        assertFalse("Fwd: must be stripped: ${f.project}", f.project.contains("Fwd:"))
        assertFalse("PARAMOUNT studio prefix must not contaminate project title: ${f.project}", f.project.contains("PARAMOUNT"))
    }

    @Test
    fun actorNameDistinguishedFromCharacterRole() {
        val notice = """
            Audition for John Smith as Officer Dave in THE WIRE
            Due Oct 30 at 5pm.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals("Officer Dave", f.role)
        assertEquals("THE WIRE", f.project)
        assertFalse("Actor name John Smith must not be extracted as role: ${f.role}", f.role.contains("John Smith"))
    }

    @Test
    fun multiRoleBreakdownExtractsAllAvailableRoles() {
        val notice = """
            Project: Echoes
            LOOKING FOR:
            MARCUS (Lead) - Male, 30s
            DETECTIVE JANE (Guest Star) - Female, 40s
            OFFICER PETE (Co-Star) - Male, 20s
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals(listOf("MARCUS", "DETECTIVE JANE", "OFFICER PETE"), f.allAvailableRoles)
        assertEquals("MARCUS", f.role)
        assertTrue("allAvailableRoles must contain MARCUS", "MARCUS" in f.allAvailableRoles)
        assertTrue("allAvailableRoles must contain DETECTIVE JANE", "DETECTIVE JANE" in f.allAvailableRoles)
        assertTrue("allAvailableRoles must contain OFFICER PETE", "OFFICER PETE" in f.allAvailableRoles)
        assertEquals(3, f.allAvailableRoles.size)
    }

    @Test
    fun shootAndCallbackDatesIgnoredInFavorOfTapeDeadline() {
        val notice = """
            Project: Sunset Ridge
            Role: Alex
            SHOOT DATES: Nov 15 - Dec 2.
            CALLBACK DATES: Submit availability before Friday Nov 1.
            TAPE DEADLINE: Monday Oct 28 at 5:00 PM.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals(LocalDateTime.of(2026, 10, 28, 17, 0), f.due)
    }

    @Test
    fun screenplayStageDirectionsDoNotTriggerFalseDeliverableAsks() {
        val notice = """
            Project: Desert Mirage
            Role: Maya

            INT. DESERT - DAY

            The camera captures a wide landscape of dunes.
            Maya holds up a clean slate before stepping in front of a full length mirror.
            She looks at both profiles in the reflection for under 3 min.

            MAYA
            The heat is unbearable.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertTrue("Asks must be empty from sides directions: ${f.asks}", f.asks.isEmpty())
        assertFalse("Slate first must not trigger: ${f.asks}", "Slate first" in f.asks)
        assertFalse("Landscape must not trigger: ${f.asks}", "Landscape" in f.asks)
        assertFalse("Full length must not trigger: ${f.asks}", "Full length" in f.asks)
    }

    @Test
    fun quotedWorkingTitlesPreserved() {
        val notice = """
            Project: UNTITLED "HEIST" PROJECT
            Role: Detective Miller
            Due Oct 30 at 5pm.
        """.trimIndent()
        val f = NoticeParser.parse(notice, now)
        assertEquals("UNTITLED \"HEIST\" PROJECT", f.project)

        val curlyNotice = """
            Project: UNTITLED “HEIST” PROJECT
            Role: Detective Miller
        """.trimIndent()
        val f2 = NoticeParser.parse(curlyNotice, now)
        assertEquals("UNTITLED “HEIST” PROJECT", f2.project)
    }

    @Test
    fun decemberToJanuaryYearRollover() {
        val decDate = LocalDateTime.of(2026, 12, 28, 9, 0)
        val notice = """
            Project: New Year
            Role: Sam
            Due Jan 5 at 5:00 PM.
        """.trimIndent()
        val f = NoticeParser.parse(notice, decDate)
        assertEquals(LocalDateTime.of(2027, 1, 5, 17, 0), f.due)
    }

    @Test
    fun parsesCobEodAndMilitaryTime() {
        val cobNotice = "Please send tape by COB Friday"
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), NoticeParser.parseDue(cobNotice, now))

        val eodNotice = "Submissions due Thursday by EOD"
        assertEquals(LocalDateTime.of(2026, 10, 29, 17, 0), NoticeParser.parseDue(eodNotice, now))

        val militaryNotice = "No later than 1700 hrs GMT on Oct 30"
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), NoticeParser.parseDue(militaryNotice, now))
    }

    @Test
    fun parseSmartReturnsBaselineWhenExtractorIsNull() = runBlocking {
        val notice = """
            Project: Night Shift
            Role: Maya
            Due: Friday 5pm
        """.trimIndent()
        val f = NoticeParser.parseSmart(notice, extractor = null, today = now.toLocalDate())
        assertEquals("Night Shift", f.project)
        assertEquals("Maya", f.role)
    }

    @Test
    fun parseSmartMergesLlmMetadataAndFixesOcrTypos() = runBlocking {
        val notice = """
            Project: Night Shift
            Role: Maya

            INT. OFFICE - NIGHT

            DETE CTIVE C0LE
            Where were you last night?

            MAYA
            I was at home.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Night Shift (Rev 3)",
                "targetRoleName": "DETECTIVE COLE",
                "roleSize": "Guest Star",
                "tapeDueIsoDate": "2026-10-30",
                "tapeDueTime24h": "18:00",
                "asks": ["Slate first", "Landscape"],
                "characterNormalizationMap": {
                    "DETE CTIVE C0LE": "DETECTIVE COLE"
                }
            }
            """.trimIndent()
        }

        val f = NoticeParser.parseSmart(notice, extractor = extractor, today = now.toLocalDate())
        assertEquals("Night Shift (Rev 3)", f.project)
        assertEquals("DETECTIVE COLE", f.role)
        assertEquals("Guest Star", f.roleSize)
        assertEquals(LocalDateTime.of(2026, 10, 30, 18, 0), f.due)
        assertEquals(listOf("Slate first", "Landscape"), f.asks)
        assertTrue("Script text should contain normalized character name", f.scriptText.contains("DETECTIVE COLE"))
    }

    @Test
    fun parseSmartSlicesSidesStartingAtFirstScriptLineAnchor() = runBlocking {
        val noticeWithNoisyHeaders = """
            CONFIDENTIAL CASTING MATERIAL
            DO NOT DISTRIBUTE
            Some random preamble notes about the audition.

            START SIDES HERE:
            MAYA: You came back.
            DEV: I never left.
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Reunion",
                "targetRoleName": "MAYA",
                "firstScriptLineAnchor": "MAYA: You came back."
            }
            """.trimIndent()
        }

        val f = NoticeParser.parseSmart(noticeWithNoisyHeaders, extractor = extractor, today = now.toLocalDate())
        assertEquals("Reunion", f.project)
        assertEquals("MAYA", f.role)
        assertTrue("Script sides must start at the anchor", f.scriptText.startsWith("MAYA: You came back."))
        assertFalse("Script sides must not include noisy header preamble", f.scriptText.contains("CONFIDENTIAL CASTING MATERIAL"))
    }

    @Test
    fun parseExecutesUnderTwoMilliseconds() {
        val notice = """
            Project: Echoes
            LOOKING FOR:
            MARCUS (Lead) - Male, 30s
            DETECTIVE JANE (Guest Star) - Female, 40s
            OFFICER PETE (Co-Star) - Male, 20s
            Due Oct 30 at 5pm.
            Please slate first and shoot in landscape.

            INT. OFFICE - NIGHT

            MARCUS
            We need to move quickly.
        """.trimIndent()

        repeat(50) {
            NoticeParser.parse(notice, now)
        }

        val iterations = 100
        val start = System.nanoTime()
        repeat(iterations) {
            NoticeParser.parse(notice, now)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = elapsedMs / iterations
        assertTrue("NoticeParser.parse average execution time must be under 2ms (was $avgMs ms)", avgMs < 2.0)
    }

    @Test
    fun cleanLlmFieldReturnsNullForCommonPlaceholdersAndBlanks() {
        with(NoticeParser) {
            assertNull("Not specified".cleanLlmField())
            assertNull("not specified".cleanLlmField())
            assertNull("Unspecified".cleanLlmField())
            assertNull("Unknown".cleanLlmField())
            assertNull("None".cleanLlmField())
            assertNull("N/A".cleanLlmField())
            assertNull("NA".cleanLlmField())
            assertNull("null".cleanLlmField())
            assertNull("TBD".cleanLlmField())
            assertNull("Upcoming project".cleanLlmField())
            assertNull("   ".cleanLlmField())
            assertNull((null as String?).cleanLlmField())

            assertEquals("Hamlet", "Hamlet".cleanLlmField())
            assertEquals("Feature film", "Feature film".cleanLlmField())
        }
    }

    @Test
    fun parseSmartSanitizesPlaceholdersNormalizesVerboseProjectTypeAndFiltersGenericRoles() = runBlocking {
        val notice = """
            Casting notice details.
            Scene:
            DETECTIVE: Where were you?
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "projectTitle": "Not specified",
                "targetRoleName": "Unspecified",
                "allAvailableRoles": ["Detective Cole", "Not specified", "Male Actors", "Actors", "Female Actors"],
                "projectType": "Acting/Film/TV (Implied by casting call)",
                "roleSize": "Supporting Role (Recurring)",
                "tapeDueIsoDate": "TBD",
                "destination": "Not specified",
                "asks": ["Slate first", "None", "Not specified"]
            }
            """.trimIndent()
        }

        val f = NoticeParser.parseSmart(notice, extractor = extractor, today = now.toLocalDate())

        // "Not specified" project title sanitized to empty string
        assertEquals("", f.project)

        // Generic roles filtered out, leaving single character "Detective Cole", auto-promoted to role
        assertEquals(listOf("Detective Cole"), f.allAvailableRoles)
        assertEquals("Detective Cole", f.role)

        // Verbose project type normalized cleanly to "TV series"
        assertEquals("TV series", f.projectType)

        // Verbose role size normalized cleanly to "Supporting"
        assertEquals("Supporting", f.roleSize)

        // "TBD" due date sanitized to null
        assertNull(f.due)

        // "None" and "Not specified" filtered out of asks
        assertEquals(listOf("Slate first"), f.asks)
    }

    @Test
    fun parseMukeshChhabraCastingCallPosterExtractsAsksNotesDestinationAndZeroScenes() {
        val poster = """
            MUKESH CHHABRA CASTING COMPANY
            CASTING CALL
            LOOKING FOR SIKH ACTORS
            • Age: 30-55 years
            • Language: Hindi, English & Punjabi
            • Requirement: Must wear a turban
            • Location: Based in Bhopal
            Send:
            • CONTACT NUMBER
            • DETAILED INTRODUCTION
            • RECENT PICTURES
            to: ojas@mccc.in, nupur@mccc.in
        """.trimIndent()

        val f = NoticeParser.parse(poster, now)

        assertEquals(DocumentKind.CastingNotice, f.kind)
        assertEquals(0, f.sceneCount)
        assertEquals("", f.scriptText)
        assertEquals(Destination.EmailOrLink, f.destination)

        // Asks must ONLY include Headshot and Slate
        assertEquals(listOf("Headshot", "Slate"), f.asks)
        assertFalse(f.asks.contains("Full length"))
        assertFalse(f.asks.contains("Landscape"))
        assertFalse(f.asks.contains("Under 3 min"))

        // Notes must contain bulleted requirements and emails
        assertTrue(f.notes.contains("• Age: 30-55 years"))
        assertTrue(f.notes.contains("• Language: Hindi, English & Punjabi"))
        assertTrue(f.notes.contains("• Requirements: Must wear a turban"))
        assertTrue(f.notes.contains("• Location: Based in Bhopal"))
        assertTrue(f.notes.contains("ojas@mccc.in"))
    }

    @Test
    fun parseSmartMukeshChhabraPosterMapsSemanticAsksAndExtractsNotesAndArchetypeRole() = runBlocking {
        val poster = """
            MUKESH CHHABRA CASTING COMPANY
            CASTING CALL
            LOOKING FOR SIKH ACTORS
            • Age: 30-55 years
            • Language: Hindi, English & Punjabi
            • Requirement: Must wear a turban
            • Location: Based in Bhopal
            Send:
            • CONTACT NUMBER
            • DETAILED INTRODUCTION
            • RECENT PICTURES
            to: ojas@mccc.in, nupur@mccc.in
        """.trimIndent()

        val extractor = HybridNoticeExtractor(Application())
        extractor.testInferenceOverride = {
            """
            {
                "documentKind": "CastingNotice",
                "projectTitle": "Mukesh Chhabra Casting Call",
                "targetRoleName": "Sikh Actor",
                "allAvailableRoles": ["Sikh Actor"],
                "projectType": "Feature film",
                "roleSize": "Lead",
                "destination": "EmailOrLink",
                "asks": ["Recent Pictures", "Detailed Introduction"],
                "castingNotes": "• Age: 30-55 years\n• Language: Hindi, English & Punjabi\n• Requirement: Must wear a turban\n• Location: Based in Bhopal\n• Submit to: ojas@mccc.in, nupur@mccc.in"
            }
            """.trimIndent()
        }

        val f = NoticeParser.parseSmart(poster, extractor = extractor, today = now.toLocalDate())

        assertEquals(DocumentKind.CastingNotice, f.kind)
        assertEquals(0, f.sceneCount)
        assertEquals("", f.scriptText)
        assertEquals("Sikh Actor", f.role)
        assertEquals(listOf("Sikh Actor"), f.allAvailableRoles)
        assertEquals(Destination.EmailOrLink, f.destination)

        // Semantic deliverable mapping: "Recent Pictures" -> "Headshot", "Detailed Introduction" -> "Slate"
        assertEquals(listOf("Headshot", "Slate"), f.asks)

        assertTrue(f.notes.contains("• Age: 30-55 years"))
        assertTrue(f.notes.contains("• Requirement: Must wear a turban"))
        assertTrue(f.notes.contains("ojas@mccc.in"))

        // Verify draft and audition pieces creation
        val draft = addDraftFromNoticeFields(f)
        assertEquals(0, draft.scenes)
        assertEquals(listOf("Headshot", "Slate"), draft.asks)

        val audition = draft.makeAudition()
        // Must contain Headshot and Slate ONLY. No scenes, no full length!
        assertEquals(2, audition.pieces.size)
        assertEquals("Headshot", audition.pieces[0].title)
        assertEquals("Slate", audition.pieces[1].title)
        assertTrue(audition.scenes.isEmpty())
    }
}
