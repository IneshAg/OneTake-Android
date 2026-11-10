package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.Route
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.otTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddAuditionNoticeParsingTest {

    @Test
    fun testAppStoreDefaultsAreCleanAndEmpty() {
        val store = AppStore(Application())
        assertTrue("Auditions should be empty by default so empty state shows", store.auditions.isEmpty())
        assertTrue("Takes should be empty by default", store.takes.isEmpty())
        assertTrue("Practice should be empty by default", store.practice.isEmpty())
        assertEquals(0, store.headshotsTaken)
        assertEquals("", store.slate.name)
        assertEquals("", store.slate.height)
        assertEquals("", store.slate.basedIn)
        assertEquals("", store.slate.spoken)
    }

    @Test
    fun testAuditionCreationWithRealParsedSides() {
        val sidesText = """
            SARAH: We have to leave right now.
            MARK: Not without the keys.
            SARAH: The building is collapsing, Mark!
        """.trimIndent()

        val audition = com.onetake.app.models.Audition(
            project = "Escape Horizon",
            projectType = "Feature",
            role = "SARAH",
            roleSize = "Lead",
            castingOffice = "",
            due = java.time.LocalDateTime.now().plusDays(2),
            asks = listOf("Landscape", "Slate first"),
            pieces = listOf(
                Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = false),
                Piece(kind = PieceKind.Slate, title = "Slate", detail = "", isDone = false),
                Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = false),
            ),
            scenes = listOf(
                com.onetake.app.data.ScriptParser.parse(sidesText, myCharacter = "SARAH", sceneTitle = "Scene 1")
            ),
            destination = Destination.ActorsAccess,
            status = AuditionStatus.Due,
        )

        assertEquals("Escape Horizon", audition.project)
        assertEquals("SARAH", audition.role)
        assertEquals(1, audition.scenes.size)
        assertEquals(3, audition.scenes[0].lines.size)
        assertEquals("SARAH", audition.scenes[0].lines[0].character)
        assertTrue(audition.scenes[0].lines[0].isMine)
    }

    @Test
    fun testLocalTimeOtTimeFormatting() {
        assertEquals("10:00 AM", LocalTime.of(10, 0).otTime)
        assertEquals("12:00 PM", LocalTime.of(12, 0).otTime)
        assertEquals("5:00 PM", LocalTime.of(17, 0).otTime)
        assertEquals("6:00 PM", LocalTime.of(18, 0).otTime)
        assertEquals("9:00 PM", LocalTime.of(21, 0).otTime)
        assertEquals("11:59 PM", LocalTime.of(23, 59).otTime)
    }

    @Test
    fun testAuditionDueDateTimeModificationPreservesComponents() {
        val initialDate = LocalDate.of(2026, 11, 15)
        val initialTime = LocalTime.of(10, 0)
        var due: LocalDateTime? = initialDate.atTime(initialTime)

        assertEquals("15 Nov", due?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US)))
        assertEquals("10:00 AM", due?.otTime)

        // Change time to 5:00 PM
        val newTime = LocalTime.of(17, 0)
        due = due?.toLocalDate()?.atTime(newTime)
        assertEquals("15 Nov", due?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US)))
        assertEquals("5:00 PM", due?.otTime)

        // Change date to 18 Nov, time should remain 5:00 PM
        val newDate = LocalDate.of(2026, 11, 18)
        due = newDate.atTime(due?.toLocalTime() ?: LocalTime.of(10, 0))
        assertEquals("18 Nov", due?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US)))
        assertEquals("5:00 PM", due?.otTime)
    }

    @Test
    fun testNewAuditionBlankDraftOpensInReviewStepNotCouldNotRead() {
        val draft = AddDraft()
        assertEquals(AddAuditionSource.Type, draft.source)
        val notice = ""
        val initialStep = when {
            notice.isNotBlank() -> "Reading"
            else -> "Review"
        }
        assertEquals("Review", initialStep)
    }

    @Test
    fun testContextAwareReviewTitlesAndSubtitles() {
        // Manual Type mode
        assertEquals("Audition details", addReviewTitle(AddAuditionSource.Type))
        assertEquals("Set up your role, deadline, and deliverables, then save.", addReviewSubtitle(AddAuditionSource.Type, asksCount = 2))
        assertEquals("What to prepare", addAsksSectionTitle(AddAuditionSource.Type))
        assertEquals("What to prepare", addEditHeaderTitle(AddAuditionSource.Type))
        assertEquals("Add a deliverable", addEditAddButtonLabel(AddAuditionSource.Type))

        // Scan mode
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Scan))
        assertEquals("3 items found from your scan. Tap any line to edit, then save.", addReviewSubtitle(AddAuditionSource.Scan, asksCount = 2))
        assertEquals("They asked for", addAsksSectionTitle(AddAuditionSource.Scan))
        assertEquals("What they want", addEditHeaderTitle(AddAuditionSource.Scan))
        assertEquals("Add something they asked for", addEditAddButtonLabel(AddAuditionSource.Scan))

        // PDF mode
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.PdfOrFile))
        assertEquals("2 items found in the PDF. Tap any line to edit, then save.", addReviewSubtitle(AddAuditionSource.PdfOrFile, asksCount = 1))
        assertEquals("They asked for", addAsksSectionTitle(AddAuditionSource.PdfOrFile))

        // Photo mode
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Photo))
        assertEquals("1 item found in the image. Tap any line to edit, then save.", addReviewSubtitle(AddAuditionSource.Photo, asksCount = 0))

        // Paste / Share mode
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Paste))
        assertEquals("4 items found in the notice. Tap any line to edit, then save.", addReviewSubtitle(AddAuditionSource.Paste, asksCount = 3))
    }

    @Test
    fun `AddDraft makeAudition syncs FullLength piece respects zero scenes and preserves breakdown metadata`() {
        val draftWithFullLength = AddDraft(
            project = "Glass Harbor",
            role = "Det. Cole",
            projectType = "TV series",
            roleSize = "Guest star",
            castingOffice = "Hart Casting",
            notes = "Keep delivery grounded",
            scenes = 1,
            asks = listOf("Slate first", "Full length", "Both profiles"),
            rawScriptText = "DET. COLE: Drop the weapon.\nMARCUS: Never.",
        )

        val audition = draftWithFullLength.makeAudition(hasHeadshot = true)
        assertEquals("TV series", audition.projectType)
        assertEquals("Guest star", audition.roleSize)
        assertEquals("Hart Casting", audition.castingOffice)
        assertEquals("Keep delivery grounded", audition.notes)
        assertTrue("Expected PieceKind.FullLength when 'Full length' is in asks", audition.pieces.any { it.kind == PieceKind.FullLength })
        assertTrue("Expected PieceKind.Slate", audition.pieces.any { it.kind == PieceKind.Slate })
        assertEquals(1, audition.scenes.size)

        // Setting scenes = 0 in Edit step must produce 0 scene pieces and 0 scenes
        val slateOnlyDraft = AddDraft(
            project = "Commercial Ident",
            role = "Self",
            scenes = 0,
            asks = listOf("Slate first", "Full length"),
        )
        val slateOnly = slateOnlyDraft.makeAudition(hasHeadshot = false)
        assertEquals(0, slateOnly.scenes.size)
        assertTrue(slateOnly.pieces.none { it.kind == PieceKind.Scene })
        assertTrue(slateOnly.pieces.any { it.kind == PieceKind.FullLength })
    }

    @Test
    fun `addParseNotice maps SidesOnly characters and preserves single-character sides`() {
        val singleCharSides = """
            INT. INTERROGATION ROOM - NIGHT

            DETECTIVE COLE
            You think I didn't check the harbour logs before walking in here?

            DETECTIVE COLE
            Every container on Pier 9 had your signature on the manifest.
        """.trimIndent()

        val draft = addParseNotice(singleCharSides)
        assertEquals(DocumentKind.SidesOnly, draft.kind)
        assertEquals(listOf("DETECTIVE COLE"), draft.detectedCharacters)
        assertEquals("DETECTIVE COLE", draft.role)

        val audition = draft.makeAudition(hasHeadshot = false)
        assertEquals("DETECTIVE COLE", audition.role)
        assertEquals(1, audition.scenes.size)
        assertEquals(2, audition.scenes.first().lines.size)
        assertTrue(audition.scenes.first().lines.all { it.isMine })
    }

    @Test
    fun `testNewAuditionStateLifecycleInAppStore and route`() {
        assertEquals("newAudition", Route.NewAudition.path)
        val store = AppStore(Application())
        org.junit.Assert.assertNull(store.newAuditionDraft)
        assertEquals("", store.newAuditionNotice)

        val draft = AddDraft(project = "Hamlet", role = "Hamlet", source = AddAuditionSource.Type)
        store.startNewAudition(draft = draft, notice = "Notice text")
        assertEquals("Hamlet", store.newAuditionDraft?.project)
        assertEquals("Notice text", store.newAuditionNotice)

        store.clearNewAudition()
        org.junit.Assert.assertNull(store.newAuditionDraft)
        assertEquals("", store.newAuditionNotice)
        assertTrue(store.pendingNewAuditionUris.isEmpty())
    }

    @Test
    fun `testContextAwareTitlesAndSubtitlesForTypeVsScanned`() {
        assertEquals("Audition details", addReviewTitle(AddAuditionSource.Type))
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Paste))
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Scan))
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.Photo))
        assertEquals("Here's what they want", addReviewTitle(AddAuditionSource.PdfOrFile))

        assertEquals("What to prepare", addAsksSectionTitle(AddAuditionSource.Type))
        assertEquals("They asked for", addAsksSectionTitle(AddAuditionSource.Paste))

        assertEquals("What to prepare", addEditHeaderTitle(AddAuditionSource.Type))
        assertEquals("What they want", addEditHeaderTitle(AddAuditionSource.Scan))

        assertEquals("Add a deliverable", addEditAddButtonLabel(AddAuditionSource.Type))
        assertEquals("Add something they asked for", addEditAddButtonLabel(AddAuditionSource.PdfOrFile))

        assertEquals("Set up your role, deadline, and deliverables, then save.", addReviewSubtitle(AddAuditionSource.Type, 0))
        assertTrue(addReviewSubtitle(AddAuditionSource.Scan, 2).contains("3 items found from your scan"))
        assertTrue(addReviewSubtitle(AddAuditionSource.PdfOrFile, 0).contains("1 item found in the PDF"))
    }

    private val now = LocalDateTime.of(2026, 10, 24, 9, 0) // a Saturday

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
    fun testZeroSceneCastingNoticeGeneratesAddScenePrompt() {
        val noticeWithoutScenes = """
            CASTING CALL: COMMERCIAL
            Project: Sunshine Juice
            Role: Runner
            Due: Tomorrow 10am
            Please submit front and side headshots and a 30s slate.
        """.trimIndent()

        val fields = NoticeParser.parse(noticeWithoutScenes)
        assertEquals("Sunshine Juice", fields.project)
        assertEquals("Runner", fields.role)
        assertEquals(0, fields.sceneCount)

        val draft = AddDraft(
            project = fields.project,
            role = fields.role,
            scenes = fields.sceneCount,
            asks = listOf("Front and side headshots", "30s slate")
        )
        val audition = draft.makeAudition()
        assertTrue("Audition with 0 parsed scenes must have empty scenes list", audition.scenes.isEmpty())
        org.junit.Assert.assertNull("No scene piece when scenes count is 0", audition.pieces.firstOrNull { it.kind == PieceKind.Scene })
    }
}
