package com.onetake.app.data

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.Take
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AppStoreEditingTest {

    private fun createAppStore(): AppStore = AppStore(Application())

    @Test
    fun testDeleteTakeRemovesTakeAndUpdatesCircled() {
        val store = createAppStore()
        val take1 = Take(id = "tk-1", number = 1, durationSeconds = 30, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), auditionId = "aud-1", sceneIndex = 0)
        val take2 = Take(id = "tk-2", number = 2, durationSeconds = 35, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList(), auditionId = "aud-1", sceneIndex = 0)
        store.takes = listOf(take1, take2)
        val aud = Audition(
            id = "aud-1", project = "Hamlet", projectType = "Film", role = "Hamlet", roleSize = "Lead",
            castingOffice = "BBC", due = LocalDateTime.now().plusDays(2), asks = emptyList(),
            pieces = listOf(Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = true)),
            scenes = emptyList(), destination = Destination.EmailOrLink, status = AuditionStatus.Ready,
            exportedFileUri = "file:///exports/hamlet.zip",
        )
        store.auditions = listOf(aud)

        store.deleteTake("tk-1")

        assertEquals(1, store.takes.size)
        assertEquals("tk-2", store.takes.first().id)
        assertTrue("Remaining take should now be circled", store.takes.first().isCircled)
        assertNull("Exported deliverable should be invalidated when a take is deleted", store.audition("aud-1")?.exportedFileUri)
    }

    @Test
    fun testSaveTrimmedTakeAsNewTakeVsReplaceOriginal() {
        val store = createAppStore()
        val original = Take(id = "tk-1", number = 1, durationSeconds = 40, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), auditionId = "aud-1", sceneIndex = 0, fileUri = "file:///media/tk-1.mp4")
        store.takes = listOf(original)
        val aud = Audition(
            id = "aud-1", project = "Hamlet", projectType = "Film", role = "Hamlet", roleSize = "Lead",
            castingOffice = "BBC", due = LocalDateTime.now().plusDays(2), asks = emptyList(),
            pieces = listOf(Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = true)),
            scenes = emptyList(), destination = Destination.EmailOrLink, status = AuditionStatus.Ready,
            exportedFileUri = "file:///exports/hamlet.zip",
        )
        store.auditions = listOf(aud)

        // 1. Save as new take
        val newTake = store.saveTrimmedTake(
            originalTakeId = "tk-1",
            trimmedFileUri = "file:///media/tk-2-trimmed.mp4",
            durationSeconds = 25,
            replaceOriginal = false,
        )

        assertNotNull(newTake)
        assertEquals(2, store.takes.size)
        assertEquals(2, newTake?.number)
        assertEquals("file:///media/tk-2-trimmed.mp4", newTake?.fileUri)
        assertEquals(25, newTake?.durationSeconds)
        assertEquals("Original take should still exist", "file:///media/tk-1.mp4", store.takes.first { it.id == "tk-1" }.fileUri)
        assertNull("Exported file should be invalidated", store.audition("aud-1")?.exportedFileUri)

        // 2. Replace take
        val replaced = store.saveTrimmedTake(
            originalTakeId = "tk-1",
            trimmedFileUri = "file:///media/tk-1-cut.mp4",
            durationSeconds = 20,
            replaceOriginal = true,
        )

        assertEquals("tk-1", replaced?.id)
        assertEquals("file:///media/tk-1-cut.mp4", store.takes.first { it.id == "tk-1" }.fileUri)
        assertEquals(20, store.takes.first { it.id == "tk-1" }.durationSeconds)
        assertEquals(2, store.takes.size)
    }

    @Test
    fun testUpdateTakeNotes() {
        val store = createAppStore()
        val take = Take(id = "tk-1", number = 1, durationSeconds = 40, recordedAt = LocalDateTime.now(), isCircled = true, notes = listOf("Rushed start"))
        store.takes = listOf(take)

        store.addTakeNote("tk-1", "Good eye contact")
        assertEquals(listOf("Rushed start", "Good eye contact"), store.takes.first().notes)

        store.removeTakeNote("tk-1", 0)
        assertEquals(listOf("Good eye contact"), store.takes.first().notes)
    }

    @Test
    fun testPersistentScriptLineAndSceneMutations() {
        val store = createAppStore()
        val line1 = ScriptLine(id = "l-1", character = "HAMLET", text = "To be or not to be.", isMine = true)
        val line2 = ScriptLine(id = "l-2", character = "OPHELIA", text = "My lord.", isMine = false)
        val scene1 = ScriptScene(id = "sc-1", number = 1, title = "Scene 1", heading = "INT. CASTLE - DAY", lines = listOf(line1, line2), estimatedSeconds = 45)
        val aud = Audition(
            id = "aud-1", project = "Hamlet", projectType = "Film", role = "Hamlet", roleSize = "Lead",
            castingOffice = "Royal", due = LocalDateTime.now().plusDays(1), asks = emptyList(),
            pieces = listOf(Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = false)),
            scenes = listOf(scene1), destination = Destination.EmailOrLink, status = AuditionStatus.Due,
        )
        store.auditions = listOf(aud)

        // 1. Edit line
        store.updateScriptLine("aud-1", "sc-1", "l-1", newCharacter = "PRINCE HAMLET", newText = "To be, or not to be, that is the question.", isMine = true)
        val updatedLine = store.audition("aud-1")?.scenes?.first()?.lines?.first()
        assertEquals("PRINCE HAMLET", updatedLine?.character)
        assertEquals("To be, or not to be, that is the question.", updatedLine?.text)
        assertTrue(updatedLine?.isMine == true)

        // 2. Add line
        store.addScriptLine("aud-1", "sc-1", afterLineId = "l-1", character = "POLONIUS", text = "What do you read, my lord?", isMine = false)
        val linesAfterAdd = store.audition("aud-1")?.scenes?.first()?.lines.orEmpty()
        assertEquals(3, linesAfterAdd.size)
        assertEquals("POLONIUS", linesAfterAdd[1].character)

        // 3. Delete line
        store.deleteScriptLine("aud-1", "sc-1", "l-2")
        val linesAfterDelete = store.audition("aud-1")?.scenes?.first()?.lines.orEmpty()
        assertEquals(2, linesAfterDelete.size)
        assertTrue(linesAfterDelete.none { it.id == "l-2" })

        // 4. Add scene
        store.addScene("aud-1", title = "Scene 2", heading = "EXT. BATTLEFIELD - NIGHT")
        val audAfterScene = store.audition("aud-1")
        assertEquals(2, audAfterScene?.scenes?.size)
        assertEquals("Scene 2", audAfterScene?.scenes?.get(1)?.title)
        assertTrue("Pieces should now contain Scene 2", audAfterScene?.pieces?.any { it.title == "Scene 2" } == true)

        // 5. Rename scene
        store.renameScene("aud-1", "sc-1", newTitle = "Act III Soliloquy", newHeading = "INT. CASTLE HALL - DAY")
        val renamed = store.audition("aud-1")?.scenes?.first()
        assertEquals("Act III Soliloquy", renamed?.title)
        assertEquals("INT. CASTLE HALL - DAY", renamed?.heading)
        assertTrue("Pieces should reflect renamed scene title", store.audition("aud-1")?.pieces?.any { it.title == "Act III Soliloquy" } == true)

        // 6. Delete scene
        store.deleteScene("aud-1", "sc-1")
        val audAfterDelete = store.audition("aud-1")
        assertEquals(1, audAfterDelete?.scenes?.size)
        assertFalse("Pieces should no longer contain deleted scene", audAfterDelete?.pieces?.any { it.title == "Act III Soliloquy" } == true)
    }

    @Test
    fun testPracticePieceCrudMutations() {
        val store = createAppStore()
        val line = ScriptLine(id = "pl-1", character = "ROMEO", text = "Soft, what light through yonder window breaks?", isMine = true)
        val scene = ScriptScene(id = "psc-1", number = 1, title = "Balcony Scene", heading = "EXT. CAPULET ORCHARD", lines = listOf(line), estimatedSeconds = 60)
        val piece = PracticePiece(id = "prac-1", title = "Balcony Scene", detail = "Romeo · 1 lines", linesLearned = 0, linesTotal = 1, character = "Romeo", scene = scene)
        store.practice = listOf(piece)

        // Edit line
        store.updatePracticeLine("prac-1", "pl-1", newCharacter = "ROMEO", newText = "It is the east, and Juliet is the sun.", isMine = true)
        val updatedPracLine = store.practice.first().scene?.lines?.first()
        assertEquals("It is the east, and Juliet is the sun.", updatedPracLine?.text)

        // Add line
        store.addPracticeLine("prac-1", afterLineId = "pl-1", character = "JULIET", text = "O Romeo, Romeo!", isMine = false)
        assertEquals(2, store.practice.first().scene?.lines?.size)

        // Update piece metadata
        store.updatePracticePiece("prac-1", title = "Act II Scene II", character = "Romeo")
        assertEquals("Act II Scene II", store.practice.first().title)

        // Delete practice piece
        store.deletePracticePiece("prac-1")
        assertTrue(store.practice.isEmpty())
    }

    @Test
    fun testEditDetailsSyncsProjectTypeRoleSizeAndAsksWithPieces() {
        val store = createAppStore()
        val aud = Audition(
            id = "aud-1", project = "Project Alpha", projectType = "Feature", role = "Detective",
            roleSize = "Lead", castingOffice = "Sarah Finn", due = LocalDateTime.of(2026, 11, 15, 14, 0),
            asks = listOf("Slate first"),
            pieces = listOf(
                Piece(kind = PieceKind.Slate, title = "Slate", detail = "", isDone = false),
                Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = false),
            ),
            scenes = emptyList(), destination = Destination.ActorsAccess, status = AuditionStatus.Due,
        )
        store.auditions = listOf(aud)

        // Edit details: add "Full length" to asks, change minute accurate time (14:30), change status
        store.editDetailsExtended(
            id = "aud-1",
            project = "Project Alpha (Updated)",
            role = "Detective Miller",
            projectType = "TV Series",
            roleSize = "Series Regular",
            castingOffice = "Sarah Finn Casting",
            due = LocalDateTime.of(2026, 11, 15, 14, 30),
            destination = Destination.CastingNetworks,
            notes = "Needs British accent",
            asks = listOf("Slate first", "Full length"),
            status = AuditionStatus.Sent,
        )

        val updated = store.audition("aud-1")
        assertNotNull(updated)
        assertEquals("Project Alpha (Updated)", updated?.project)
        assertEquals("Detective Miller", updated?.role)
        assertEquals("TV Series", updated?.projectType)
        assertEquals("Series Regular", updated?.roleSize)
        assertEquals("Sarah Finn Casting", updated?.castingOffice)
        assertEquals(LocalDateTime.of(2026, 11, 15, 14, 30), updated?.due)
        assertEquals(Destination.CastingNetworks, updated?.destination)
        assertEquals("Needs British accent", updated?.notes)
        assertEquals(AuditionStatus.Sent, updated?.status)
        assertTrue("Pieces should now contain Full length piece", updated?.pieces?.any { it.kind == PieceKind.FullLength } == true)

        // Remove Full length from asks
        store.editDetailsExtended(
            id = "aud-1",
            project = "Project Alpha (Updated)",
            role = "Detective Miller",
            projectType = "TV Series",
            roleSize = "Series Regular",
            castingOffice = "Sarah Finn Casting",
            due = LocalDateTime.of(2026, 11, 15, 14, 30),
            destination = Destination.CastingNetworks,
            notes = "Needs British accent",
            asks = listOf("Slate first"),
            status = AuditionStatus.Due,
        )

        val afterRemove = store.audition("aud-1")
        assertFalse("Pieces should no longer contain Full length piece", afterRemove?.pieces?.any { it.kind == PieceKind.FullLength } == true)
        assertEquals(AuditionStatus.Due, afterRemove?.status)
    }

    @Test
    fun testHeadshotDeletionUpdatesStoreAndAuditions() {
        val store = createAppStore()
        store.saveHeadshot("front", "file:///headshots/front.jpg")
        store.saveHeadshot("left", "file:///headshots/left.jpg")
        assertEquals(2, store.headshotsTaken)
        assertTrue(store.hasHeadshot("front"))
        assertTrue(store.hasHeadshot("left"))

        val aud = Audition(
            id = "aud-1", project = "Film", projectType = "Feature", role = "Hero", roleSize = "Lead",
            castingOffice = "", due = LocalDateTime.now().plusDays(1), asks = emptyList(),
            pieces = listOf(Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "", isDone = true)),
            scenes = emptyList(), destination = Destination.EmailOrLink, status = AuditionStatus.Due,
        )
        store.auditions = listOf(aud)

        // Delete left profile
        store.deleteHeadshot("left")
        assertEquals(1, store.headshotsTaken)
        assertFalse(store.hasHeadshot("left"))
        assertTrue("Front headshot should still exist", store.hasHeadshot("front"))
        assertTrue("Audition headshot piece should still be done since front exists", store.audition("aud-1")?.pieces?.first()?.isDone == true)

        // Delete front headshot
        store.deleteHeadshot("front")
        assertEquals(0, store.headshotsTaken)
        assertFalse(store.hasHeadshot("front"))
        assertFalse("Audition headshot piece should now be marked undone", store.audition("aud-1")?.pieces?.first()?.isDone == true)
    }
}
