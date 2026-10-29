package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.otTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
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
}
