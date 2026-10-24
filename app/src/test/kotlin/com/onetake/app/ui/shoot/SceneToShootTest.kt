package com.onetake.app.ui.shoot

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.Take
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SceneToShootTest {
    private fun audition(vararg done: Boolean) = Audition(
        id = "a1", project = "P", projectType = "Feature film", role = "Maya", roleSize = "Lead",
        castingOffice = "", due = LocalDateTime.now().plusDays(2), asks = emptyList(),
        pieces = done.mapIndexed { i, d -> Piece(kind = PieceKind.Scene, title = "Scene ${i + 1}", detail = "", isDone = d) },
        scenes = done.indices.map { ScriptScene.placeholder(it + 1, "Maya") },
        destination = Destination.ActorsAccess, status = AuditionStatus.Due,
    )

    private fun take(scene: Int, number: Int) = Take(
        number = number, durationSeconds = 30, recordedAt = LocalDateTime.now(), isCircled = false,
        notes = emptyList(), auditionId = "a1", sceneIndex = scene,
    )

    @Test
    fun opensOnTheFirstSceneNotYetDone() {
        val store = AppStore(Application())
        store.auditions = listOf(audition(true, false, false))
        assertEquals(1, store.sceneToShoot("a1"))
    }

    @Test
    fun allDoneFallsBackToTheFirstSceneWithoutATake() {
        val store = AppStore(Application())
        store.auditions = listOf(audition(true, true))
        store.takes = listOf(take(scene = 0, number = 1))
        assertEquals(1, store.sceneToShoot("a1"))
    }

    @Test
    fun takeNumbersNeverRepeatAfterADeletion() {
        val store = AppStore(Application())
        store.takes = listOf(take(scene = 0, number = 1), take(scene = 0, number = 3))
        assertEquals(4, store.nextTakeNumber("a1", 0))
        assertEquals(1, store.nextTakeNumber("a1", 1))
    }
}
