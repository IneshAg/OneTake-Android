package com.onetake.app.widget

import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class NextUpTest {
    private val now = LocalDateTime.of(2026, 10, 24, 9, 0)

    private fun audition(project: String, dueInHours: Long, status: AuditionStatus = AuditionStatus.Due, vararg done: Boolean) = Audition(
        project = project, projectType = "Feature film", role = "Maya", roleSize = "Lead", castingOffice = "",
        due = now.plusHours(dueInHours), asks = emptyList(),
        pieces = done.mapIndexed { i, d -> Piece(kind = PieceKind.Scene, title = "Scene ${i + 1}", detail = "", isDone = d) },
        scenes = emptyList(), destination = Destination.ActorsAccess, status = status,
    )

    @Test
    fun picksTheSoonestUnsentAudition() {
        val next = NextUp.from(listOf(
            audition("Later", 72),
            audition("Sent already", 1, AuditionStatus.Sent),
            audition("Soonest", 13, AuditionStatus.Due, true, false),
        ))
        assertEquals("Soonest", next?.project)
        assertEquals("1 of 2 · Scene 2 next", next?.progressLine)
        assertEquals("13" to "h", next?.timeLeft(now))
    }

    @Test
    fun daysOnceItsTwoDaysOrMoreAndNeverNegative() {
        assertEquals("3" to "d", NextUp.from(listOf(audition("A", 72)))?.timeLeft(now))
        assertEquals("0" to "h", NextUp.from(listOf(audition("Late", -5)))?.timeLeft(now))
    }

    @Test
    fun readyOnceEverythingIsIn() {
        assertEquals("Ready to send", NextUp.from(listOf(audition("Done", 5, AuditionStatus.Ready, true, true)))?.progressLine)
    }

    @Test
    fun nothingWhenAllAreSent() {
        assertNull(NextUp.from(listOf(audition("Sent", 5, AuditionStatus.Sent))))
    }
}
