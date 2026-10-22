package com.onetake.app.ui.auditions

import com.onetake.app.AppStore
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddAuditionNoticeParsingTest {

    @Test
    fun testAppStoreDefaultsAreCleanAndEmpty() {
        val store = AppStore()
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
}
