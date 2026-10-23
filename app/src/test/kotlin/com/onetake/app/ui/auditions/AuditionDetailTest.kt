package com.onetake.app.ui.auditions

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AuditionDetailTest {
    @Test
    fun testAuditionPackageProgressComputation() {
        val pieces = listOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = true),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "Spoken", isDone = true),
            Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "Lead", isDone = false),
        )
        val audition = Audition(
            id = "aud-test",
            project = "Dark Waters",
            projectType = "Series",
            role = "Elena",
            roleSize = "Lead",
            castingOffice = "Northlight",
            due = LocalDateTime.now().plusDays(2),
            asks = listOf("Slate first", "Landscape"),
            pieces = pieces,
            scenes = emptyList(),
            destination = Destination.ActorsAccess,
            status = AuditionStatus.Due,
        )

        assertEquals(2, audition.readyCount)
        assertEquals(3, audition.totalCount)
        assertEquals("Record Scene 1", audition.nextAction)
        assertNotNull(audition.nextPiece)
        assertEquals(PieceKind.Scene, audition.nextPiece?.kind)
    }

    @Test
    fun testAddAuditionWithParsedScriptScenes() {
        val script = """
            DETECTIVE: Where were you at midnight?
            SUSPECT: At the diner across town.
            DETECTIVE: We checked. Nobody saw you.
        """.trimIndent()

        val scene = ScriptParser.parse(script, myCharacter = "SUSPECT", sceneTitle = "Interrogation")
        val audition = Audition(
            id = "aud-parsed",
            project = "The Night Shift",
            projectType = "Feature",
            role = "SUSPECT",
            roleSize = "Supporting",
            castingOffice = "Granite Casting",
            due = LocalDateTime.now().plusDays(3),
            asks = listOf("Under 3 min"),
            pieces = listOf(
                Piece(kind = PieceKind.Scene, title = "Interrogation", detail = "SUSPECT · 3 lines", isDone = false)
            ),
            scenes = listOf(scene),
            destination = Destination.ActorsAccess,
            status = AuditionStatus.Due,
        )

        val store = AppStore(Application())
        store.add(audition)

        val retrieved = store.audition("aud-parsed")
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.scenes.size)
        assertEquals(3, retrieved.scenes[0].lines.size)
        assertTrue(retrieved.scenes[0].lines[1].isMine)
    }

    @Test
    fun testAuditionHeadshotMarkDoneAdvancesToNextPiece() {
        val pieces = listOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = false),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "Name, height, city", isDone = false),
            Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = false),
        )
        val audition = Audition(
            id = "aud-flow-test",
            project = "Neon Sun",
            projectType = "Feature film",
            role = "Pilot",
            roleSize = "Lead",
            castingOffice = "",
            due = LocalDateTime.now().plusDays(2),
            asks = emptyList(),
            pieces = pieces,
            scenes = emptyList(),
            destination = Destination.ActorsAccess,
            status = AuditionStatus.Due,
        )

        val store = AppStore(Application())
        store.add(audition)

        assertEquals(PieceKind.Headshot, store.audition("aud-flow-test")?.nextPiece?.kind)

        store.markDone(PieceKind.Headshot, "aud-flow-test")

        val updated = store.audition("aud-flow-test")
        assertNotNull(updated)
        assertTrue(updated!!.pieces.first { it.kind == PieceKind.Headshot }.isDone)
        assertEquals(PieceKind.Slate, updated.nextPiece?.kind)

        store.markDone(PieceKind.Slate, "aud-flow-test")
        val updated2 = store.audition("aud-flow-test")
        assertEquals(PieceKind.Scene, updated2?.nextPiece?.kind)
    }
}
