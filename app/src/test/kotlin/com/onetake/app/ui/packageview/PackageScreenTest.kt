package com.onetake.app.ui.packageview

import com.onetake.app.data.MediaExporter
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PackageScreenTest {

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

        assertEquals("SMITH_JANE_OPHELIA_HAMLET.mp4", fileName)
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
