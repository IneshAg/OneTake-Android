package com.onetake.app.ui.packageview

import android.app.Application
import com.onetake.app.data.MediaExporter
import com.onetake.app.data.TapeChecker
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PackageScreenTest {

    @Test
    fun testTapeCheckerAutomatedChecksEvaluation() {
        val audition = Audition(
            id = "aud-1", project = "Project Alpha", projectType = "Feature", role = "Lead",
            roleSize = "Lead", castingOffice = "CD", due = LocalDateTime.now().plusDays(2),
            asks = listOf("Both profiles"),
            pieces = listOf(
                Piece(kind = PieceKind.Headshot, title = "Headshot", isDone = true),
                Piece(kind = PieceKind.Slate, title = "Slate", isDone = true)
            ),
            scenes = emptyList(), destination = Destination.Spotlight
        )
        val headshots = mapOf("front" to "content://h/front.jpg", "left" to "content://h/left.jpg", "right" to "content://h/right.jpg")
        val slate = SlateInfo(name = "Jane Actor", height = "5'8\"", basedIn = "NY", representation = "Agency")

        val checks = TapeChecker.run(
            context = Application(),
            audition = audition,
            takes = emptyList(),
            headshots = headshots,
            slate = slate,
            slateAtStart = true
        )

        val headshotCheck = checks.firstOrNull { it.title == "Headshot" }
        assertNotNull(headshotCheck)
        val profilesCheck = checks.firstOrNull { it.title == "Both profiles" }
        assertNotNull(profilesCheck)
    }

    @Test
    fun testCurtainDeliveryTimingFormattingPastDeadline() {
        val due = LocalDateTime.of(2026, 11, 6, 10, 0)
        val deliveredPast = LocalDateTime.of(2026, 11, 6, 10, 15)
        val deliveredEarly = LocalDateTime.of(2026, 11, 5, 10, 0)
        val deliveredOnTime = LocalDateTime.of(2026, 11, 6, 9, 30)

        assertEquals("Delivered — 15 minutes past deadline", formatCurtainDeliveryTiming(due, deliveredPast))
        assertEquals("Delivered — a day early", formatCurtainDeliveryTiming(due, deliveredEarly))
        assertEquals("Delivered on time", formatCurtainDeliveryTiming(due, deliveredOnTime))
    }

    @Test
    fun testDeliverableFileNameFormat() {
        val audition = Audition(
            id = "aud-1",
            project = "Hamlet",
            projectType = "Feature Film",
            role = "Ophelia",
            roleSize = "Lead",
            castingOffice = "Sarah Finn",
            due = LocalDateTime.now(),
            asks = emptyList(),
            pieces = emptyList(),
            scenes = emptyList(),
            destination = Destination.Spotlight,
            status = AuditionStatus.Ready,
        )
        val slate = SlateInfo(name = "Jane Smith", height = "5'7\"", basedIn = "London", representation = "CAA")
        val fileName = MediaExporter.deliverableFileName(audition, slate)
        val zipFileName = MediaExporter.deliverableZipFileName(audition, slate)

        assertEquals("SMITH_JANE_OPHELIA_HAMLET.mp4", fileName)
        assertEquals("SMITH_JANE_OPHELIA_HAMLET.zip", zipFileName)
    }

    @Test
    fun testDurationCalculationOnlyCountsCircledTakes() {
        val takes = listOf(
            Take(id = "t1", auditionId = "aud-1", number = 1, durationSeconds = 30, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), sceneIndex = 0),
            Take(id = "t2", auditionId = "aud-1", number = 2, durationSeconds = 45, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList(), sceneIndex = 0),
            Take(id = "t3", auditionId = "aud-1", number = 1, durationSeconds = 60, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), sceneIndex = 1),
            Take(id = "t4", auditionId = "aud-other", number = 1, durationSeconds = 120, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), sceneIndex = 0),
        )

        val circled = takes.filter { it.auditionId == "aud-1" && it.isCircled }
        val sumSeconds = circled.sumOf { it.durationSeconds }

        assertEquals(90, sumSeconds)
        assertEquals("1:30", formatDuration(sumSeconds))
    }

    @Test
    fun testDestinationSpecsAreConsistent() {
        Destination.entries.forEach { d ->
            assertTrue(d.label.isNotBlank())
            assertTrue(d.spec.isNotBlank())
        }
    }
}
