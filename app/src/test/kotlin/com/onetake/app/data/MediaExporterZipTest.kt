package com.onetake.app.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipFile

class MediaExporterZipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    private class TestApp(private val testDir: File) : Application() {
        override fun getFilesDir(): File = testDir
        override fun getCacheDir(): File = testDir
    }

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = TestApp(filesDir)
    }

    @Test
    fun testDeliverableZipFileName() {
        val audition = Audition(
            id = "aud-1",
            project = "Hamlet: The Prince",
            projectType = "Feature Film",
            role = "Hamlet",
            roleSize = "Lead",
            castingOffice = "Sarah Finn",
            due = LocalDateTime.now(),
            destination = Destination.Spotlight,
            notes = "",
            asks = emptyList(),
            pieces = emptyList(),
            scenes = emptyList(),
            status = AuditionStatus.Ready,
        )
        val slate = SlateInfo(
            name = "John Doe",
            representation = "Creative Artists",
            height = "5'11\"",
            basedIn = "London",
        )

        val name = MediaExporter.deliverableZipFileName(audition, slate)
        assertEquals("DOE_JOHN_HAMLET_HAMLETTHEPRINCE.zip", name)
    }

    @Test
    fun testExportZipPackageCreatesValidArchiveWithManifestAndMedia() {
        val slateFile = tempFolder.newFile("slate.mp4").apply {
            writeBytes("mock-slate-video-bytes".toByteArray())
        }
        val headshotFile = tempFolder.newFile("headshot_front.jpg").apply {
            writeBytes("mock-headshot-jpeg-bytes".toByteArray())
        }
        val sceneTakeFile = tempFolder.newFile("take_1.mp4").apply {
            writeBytes("mock-scene-take-video-bytes".toByteArray())
        }

        val audition = Audition(
            id = "aud-test",
            project = "Dune 3",
            projectType = "Feature Film",
            role = "Paul",
            roleSize = "Lead",
            castingOffice = "Sarah Finn",
            due = LocalDateTime.now(),
            destination = Destination.Spotlight,
            notes = "Intense and focused",
            asks = listOf("Slate first"),
            slateVideoUri = "file://${slateFile.absolutePath}",
            scenes = listOf(
                ScriptScene(
                    id = "scene-1",
                    number = 1,
                    heading = "EXT. ARRAKIS - DAY",
                    title = "The Desert Walk",
                    estimatedSeconds = 45,
                    lines = listOf(ScriptLine(character = "PAUL", text = "Fear is the mind-killer.", isMine = true)),
                )
            ),
            pieces = listOf(
                Piece(kind = PieceKind.Slate, title = "Slate", detail = "Recorded", isDone = true),
                Piece(kind = PieceKind.Scene, title = "The Desert Walk", detail = "Take 1", isDone = true),
                Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = true),
            ),
            status = AuditionStatus.Ready,
        )

        val slate = SlateInfo(
            name = "Timothee Chalamet",
            representation = "UTA",
            basedIn = "NYC",
        )

        val takes = listOf(
            Take(
                id = "take-1",
                auditionId = "aud-test",
                number = 1,
                sceneIndex = 0,
                durationSeconds = 45,
                recordedAt = LocalDateTime.now(),
                isCircled = true,
                notes = emptyList(),
                fileUri = "file://${sceneTakeFile.absolutePath}",
            )
        )

        val headshots = mapOf(
            "front" to "file://${headshotFile.absolutePath}",
        )

        val zipFile = MediaExporter.exportZipPackage(
            context = context,
            audition = audition,
            takes = takes,
            slate = slate,
            headshots = headshots,
            includeCombinedTape = false, // test individual package assembly
        )

        assertTrue("Zip file should exist", zipFile.exists())
        assertTrue("Zip file should have size > 0", zipFile.length() > 0)
        assertTrue("Zip filename should end with .zip", zipFile.name.endsWith(".zip"))

        // Inspect zip entries
        ZipFile(zipFile).use { zf ->
            val entryNames = zf.entries().asSequence().map { it.name }.toList()
            assertTrue("Should contain MANIFEST.txt", entryNames.any { it.endsWith("MANIFEST.txt") })
            assertTrue("Should contain slate mp4", entryNames.any { it.contains("SLATE") && it.endsWith(".mp4") })
            assertTrue("Should contain scene 1 mp4", entryNames.any { it.contains("SCENE_1") && it.endsWith(".mp4") })
            assertTrue("Should contain front headshot jpg", entryNames.any { it.contains("HEADSHOT_FRONT") && it.endsWith(".jpg") })

            // Verify content of manifest
            val manifestEntry = zf.entries().asSequence().first { it.name.endsWith("MANIFEST.txt") }
            val manifestText = zf.getInputStream(manifestEntry).bufferedReader().readText()
            assertTrue("Manifest must mention project", manifestText.contains("Dune 3"))
            assertTrue("Manifest must mention role", manifestText.contains("Paul"))
            assertTrue("Manifest must mention actor name", manifestText.contains("Timothee Chalamet"))
        }
    }
}
