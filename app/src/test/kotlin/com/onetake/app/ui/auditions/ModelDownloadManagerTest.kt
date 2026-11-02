package com.onetake.app.ui.auditions

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class StubTestContext(private val baseDir: File) : ContextWrapper(Application()) {
        override fun getApplicationContext(): Context = this
        override fun getExternalFilesDir(type: String?): File = baseDir
        override fun getNoBackupFilesDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
    }

    @Test
    fun smartModelSelectionSelectsGemmaOnHighEndHardware() {
        val ram7Gb = (7.0 * 1024 * 1024 * 1024).toLong()
        val storage3_2Gb = (3.2 * 1024 * 1024 * 1024).toLong()

        val selected = ModelDownloadManager.selectModel(totalMem = ram7Gb, availableBytes = storage3_2Gb)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.GEMMA_MODEL_NAME, selected?.name)
        assertEquals(ModelDownloadManager.GEMMA_MODEL_URL, selected?.url)
    }

    @Test
    fun smartModelSelectionSelectsQwenOnMidRangeHardware() {
        val ram6Gb = (6.0 * 1024 * 1024 * 1024).toLong()
        val storage3_2Gb = (3.2 * 1024 * 1024 * 1024).toLong()

        val selected = ModelDownloadManager.selectModel(totalMem = ram6Gb, availableBytes = storage3_2Gb)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.QWEN_MODEL_NAME, selected?.name)
        assertEquals(ModelDownloadManager.QWEN_MODEL_URL, selected?.url)
    }

    @Test
    fun smartModelSelectionSelectsQwenWhenStorageBelow3_2Gb() {
        val ram8Gb = (8.0 * 1024 * 1024 * 1024).toLong()
        val storage2Gb = (2.0 * 1024 * 1024 * 1024).toLong()

        val selected = ModelDownloadManager.selectModel(totalMem = ram8Gb, availableBytes = storage2Gb)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.QWEN_MODEL_NAME, selected?.name)
    }

    @Test
    fun smartModelSelectionReturnsNullWhenStorageBelow1_2Gb() {
        val ram8Gb = (8.0 * 1024 * 1024 * 1024).toLong()
        val storage1Gb = (1.0 * 1024 * 1024 * 1024).toLong()

        val selected = ModelDownloadManager.selectModel(totalMem = ram8Gb, availableBytes = storage1Gb)
        assertNull("Should not select model when free storage is below 1.2 GB", selected)
    }

    @Test
    fun modelStatusSealedHierarchyCoversAllRequiredStates() {
        val testFile = File("test.litertlm")
        val states: List<ModelStatus> = listOf(
            ModelStatus.UsingSystemAiCore,
            ModelStatus.Ready(testFile, "gemma-4-E2B-it.litertlm"),
            ModelStatus.Downloading(progressPercent = 50, downloadedMb = 500, totalMb = 1000),
            ModelStatus.NotDownloaded("Low storage"),
            ModelStatus.Failed("Connection timed out")
        )

        assertEquals(5, states.size)
        assertTrue(states[0] is ModelStatus.UsingSystemAiCore)
        assertTrue(states[1] is ModelStatus.Ready)
        assertTrue(states[2] is ModelStatus.Downloading)
        assertTrue(states[3] is ModelStatus.NotDownloaded)
        assertTrue(states[4] is ModelStatus.Failed)
    }

    @Test
    fun chunkedDownloaderHandlesResumeAndRejectsUnder100Mb() = runBlocking {
        val baseDir = tempFolder.newFolder("base_dir")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")
        // Create an existing .part file of 100 bytes
        partFile.writeBytes(ByteArray(100))

        var recordedRangeHeader: String? = null

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                recordedRangeHeader = request.header("Range")
                // Return 206 Partial Content with 500 bytes of data
                val body = ByteArray(500).toResponseBody("application/octet-stream".toMediaType())
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(body)
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)

        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/test.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        // Resumed with Range header
        assertEquals("bytes=100-", recordedRangeHeader)
        // File is 600 bytes, which is <= 100MB threshold, so it should fail validation
        assertNull("Should fail because file size < 100MB", result)
        assertTrue(manager.status.value is ModelStatus.Failed)
    }

    @Test
    fun chunkedDownloaderCompletesAndEmitsReadyWhenFileExceeds100Mb() = runBlocking {
        val baseDir = tempFolder.newFolder("base_success")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "gemma-4-E2B-it.litertlm.part")

        // Simulate part file already having 101 MB
        val targetSize = 101L * 1024L * 1024L
        val raf = java.io.RandomAccessFile(partFile, "rw")
        raf.setLength(targetSize - 100)
        raf.close()

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val body = ByteArray(100).toResponseBody("application/octet-stream".toMediaType())
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(body)
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)

        val targetInfo = ModelInfo("gemma-4-E2B-it.litertlm", "https://example.com/gemma.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertNotNull("Should succeed when file size > 100MB", result)
        assertTrue("Destination file should exist", result!!.exists())
        assertEquals("gemma-4-E2B-it.litertlm", result.name)
        assertTrue("Status should be Ready", manager.status.value is ModelStatus.Ready)
        val ready = manager.status.value as ModelStatus.Ready
        assertEquals("gemma-4-E2B-it.litertlm", ready.modelName)
    }
}
