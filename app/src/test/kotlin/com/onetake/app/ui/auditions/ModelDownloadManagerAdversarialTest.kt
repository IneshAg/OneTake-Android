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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import okio.buffer
import okio.source

class ModelDownloadManagerAdversarialTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class StubTestContext(private val baseDir: File) : ContextWrapper(Application()) {
        override fun getApplicationContext(): Context = this
        override fun getExternalFilesDir(type: String?): File = baseDir
        override fun getNoBackupFilesDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
    }

    // =========================================================================
    // 1. Memory and Storage Boundary Tests
    // =========================================================================

    @Test
    fun boundaryTotalMemAndAvailableBytesExactGemmaThreshold() {
        val ramExact = ModelDownloadManager.GEMMA_MIN_RAM_BYTES
        val storageExact = ModelDownloadManager.GEMMA_MIN_STORAGE_BYTES

        val selected = ModelDownloadManager.selectModel(totalMem = ramExact, availableBytes = storageExact)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.GEMMA_MODEL_NAME, selected?.name)
    }

    @Test
    fun boundaryTotalMemJustBelowGemmaThresholdFallsBackToQwen() {
        val ramJustBelow = ModelDownloadManager.GEMMA_MIN_RAM_BYTES - 1L
        val storageAdequate = ModelDownloadManager.GEMMA_MIN_STORAGE_BYTES

        val selected = ModelDownloadManager.selectModel(totalMem = ramJustBelow, availableBytes = storageAdequate)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.QWEN_MODEL_NAME, selected?.name)
    }

    @Test
    fun boundaryAvailableBytesJustBelowGemmaStorageFallsBackToQwen() {
        val ramAdequate = ModelDownloadManager.GEMMA_MIN_RAM_BYTES
        val storageJustBelow = ModelDownloadManager.GEMMA_MIN_STORAGE_BYTES - 1L

        val selected = ModelDownloadManager.selectModel(totalMem = ramAdequate, availableBytes = storageJustBelow)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.QWEN_MODEL_NAME, selected?.name)
    }

    @Test
    fun boundaryAvailableBytesExactQwenThresholdSelectsQwen() {
        val ramLow = 2L * 1024L * 1024L * 1024L
        val storageExactQwen = ModelDownloadManager.QWEN_MIN_STORAGE_BYTES

        val selected = ModelDownloadManager.selectModel(totalMem = ramLow, availableBytes = storageExactQwen)
        assertNotNull(selected)
        assertEquals(ModelDownloadManager.QWEN_MODEL_NAME, selected?.name)
    }

    @Test
    fun boundaryAvailableBytesJustBelowQwenThresholdReturnsNull() {
        val ramHigh = 16L * 1024L * 1024L * 1024L
        val storageJustBelowQwen = ModelDownloadManager.QWEN_MIN_STORAGE_BYTES - 1L

        val selected = ModelDownloadManager.selectModel(totalMem = ramHigh, availableBytes = storageJustBelowQwen)
        assertNull("Should return null when storage is 1 byte below Qwen minimum", selected)
    }

    @Test
    fun boundaryExtremeAndDegenerateInputsHandledSafely() {
        assertNull("Negative memory/storage must return null", ModelDownloadManager.selectModel(-1L, -1L))
        assertNull("Zero storage must return null", ModelDownloadManager.selectModel(16L * 1024L * 1024L * 1024L, 0L))

        val extremeGemma = ModelDownloadManager.selectModel(Long.MAX_VALUE, Long.MAX_VALUE)
        assertNotNull(extremeGemma)
        assertEquals(ModelDownloadManager.GEMMA_MODEL_NAME, extremeGemma?.name)
    }

    // =========================================================================
    // 2. HTTP Range Resume: 206 vs 200 vs 416
    // =========================================================================

    @Test
    fun http206PartialContentAppendsToExistingPartFile() = runBlocking {
        val baseDir = tempFolder.newFolder("resume_206")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")

        // 101 MB total desired: 50 MB initial part file, 51 MB remaining
        val initialBytes = 50L * 1024L * 1024L
        val raf = java.io.RandomAccessFile(partFile, "rw")
        raf.setLength(initialBytes)
        raf.close()

        val additionalBytes = (51L * 1024L * 1024L) + 100L

        var receivedRangeHeader: String? = null
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                receivedRangeHeader = request.header("Range")
                // Return 206 Partial Content with simulated remaining bytes
                val fakeData = ByteArray(64 * 1024) { 0x42 }
                val stream = object : java.io.InputStream() {
                    var sent = 0L
                    override fun read(): Int {
                        if (sent >= additionalBytes) return -1
                        sent++
                        return 0x42
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (sent >= additionalBytes) return -1
                        val toWrite = minOf(len.toLong(), additionalBytes - sent).toInt()
                        System.arraycopy(fakeData, 0, b, off, toWrite)
                        sent += toWrite
                        return toWrite
                    }
                }
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = additionalBytes
                    override fun source() = stream.source().buffer()
                }
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
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertEquals("bytes=${initialBytes}-", receivedRangeHeader)
        assertNotNull(result)
        assertTrue(result!!.exists())
        assertEquals(initialBytes + additionalBytes, result.length())
        assertTrue(manager.status.value is ModelStatus.Ready)
    }

    @Test
    fun http200OkOverwritesExistingPartFileFromByteZero() = runBlocking {
        val baseDir = tempFolder.newFolder("resume_200")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")

        // Pre-fill part file with 10MB of garbage
        val staleBytes = 10L * 1024L * 1024L
        val raf = java.io.RandomAccessFile(partFile, "rw")
        raf.setLength(staleBytes)
        raf.close()

        val fullServerSize = 101L * 1024L * 1024L // Server sends full file

        var receivedRangeHeader: String? = null
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                receivedRangeHeader = request.header("Range")
                // Server sends 200 OK ignoring range, sending entire 101 MB
                val fakeData = ByteArray(64 * 1024) { 0x5A }
                val stream = object : java.io.InputStream() {
                    var sent = 0L
                    override fun read(): Int {
                        if (sent >= fullServerSize) return -1
                        sent++
                        return 0x5A
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (sent >= fullServerSize) return -1
                        val toWrite = minOf(len.toLong(), fullServerSize - sent).toInt()
                        System.arraycopy(fakeData, 0, b, off, toWrite)
                        sent += toWrite
                        return toWrite
                    }
                }
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = fullServerSize
                    override fun source() = stream.source().buffer()
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertEquals("bytes=${staleBytes}-", receivedRangeHeader)
        assertNotNull(result)
        // Must equal fullServerSize (101MB), NOT staleBytes + fullServerSize (111MB)
        assertEquals(fullServerSize, result!!.length())
        assertTrue(manager.status.value is ModelStatus.Ready)
    }

    @Test
    fun http416DeletesPartFileAndRetriesSuccessfully() = runBlocking {
        val baseDir = tempFolder.newFolder("resume_416")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")

        // Corrupted part file with invalid size
        partFile.writeBytes(ByteArray(2048) { 0x11 })

        var requestCount = 0
        var secondRequestHadRange = false
        val fullServerSize = 101L * 1024L * 1024L

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestCount++
                val request = chain.request()
                if (requestCount == 1) {
                    // First request with Range -> return 416
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(416)
                        .message("Range Not Satisfiable")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    // Second request -> verify Range header was dropped
                    secondRequestHadRange = request.header("Range") != null
                    val fakeData = ByteArray(64 * 1024) { 0x77 }
                    val stream = object : java.io.InputStream() {
                        var sent = 0L
                        override fun read(): Int {
                            if (sent >= fullServerSize) return -1
                            sent++
                            return 0x77
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (sent >= fullServerSize) return -1
                            val toWrite = minOf(len.toLong(), fullServerSize - sent).toInt()
                            System.arraycopy(fakeData, 0, b, off, toWrite)
                            sent += toWrite
                            return toWrite
                        }
                    }
                    val body = object : okhttp3.ResponseBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength() = fullServerSize
                        override fun source() = stream.source().buffer()
                    }
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertEquals(2, requestCount)
        assertFalse("Second request after 416 must not have Range header", secondRequestHadRange)
        assertNotNull(result)
        assertEquals(fullServerSize, result!!.length())
        assertTrue(manager.status.value is ModelStatus.Ready)
    }

    @Test
    fun http416FollowedByServerErrorFailsCleanly() = runBlocking {
        val baseDir = tempFolder.newFolder("resume_416_fail")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")
        partFile.writeBytes(ByteArray(512))

        var requestCount = 0
        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestCount++
                val request = chain.request()
                if (requestCount == 1) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(416)
                        .message("Range Not Satisfiable")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Internal Server Error")
                        .body("Server down".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertNull(result)
        assertTrue(manager.status.value is ModelStatus.Failed)
        val failed = manager.status.value as ModelStatus.Failed
        assertTrue(failed.error.contains("500"))
    }

    // =========================================================================
    // 3. Completed File Size Boundary: <= 100MB vs > 100MB
    // =========================================================================

    @Test
    fun completedFileSizeExactly100MbFailsValidation() = runBlocking {
        val baseDir = tempFolder.newFolder("exact_100mb")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")

        // Exactly 100 MB = 100 * 1024 * 1024 = 104,857,600 bytes
        val exact100Mb = 100L * 1024L * 1024L
        val raf = java.io.RandomAccessFile(partFile, "rw")
        raf.setLength(exact100Mb)
        raf.close()

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                // Return empty 206 since file already reached exact 100MB
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        // Strict inequality (> 100MB) must reject exactly 100MB
        assertNull("File of exactly 100 MB must be rejected", result)
        val destFile = File(storageDir, "test-model.litertlm")
        assertFalse("Destination file must not be created", destFile.exists())
        assertTrue(manager.status.value is ModelStatus.Failed)
        val failed = manager.status.value as ModelStatus.Failed
        assertTrue(failed.error.contains("too small"))
    }

    @Test
    fun completedFileSize100MbPlusOneByteSucceedsValidation() = runBlocking {
        val baseDir = tempFolder.newFolder("gt_100mb")
        val stubContext = StubTestContext(baseDir)
        val storageDir = ModelDownloadManager.getStorageDir(stubContext)
        val partFile = File(storageDir, "test-model.litertlm.part")

        // 100 MB + 1 byte = 104,857,601 bytes
        val targetSize = (100L * 1024L * 1024L) + 1L
        val raf = java.io.RandomAccessFile(partFile, "rw")
        raf.setLength(targetSize - 1L)
        raf.close()

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(ByteArray(1) { 0x01 }.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertNotNull("File of 100MB + 1 byte must be accepted", result)
        assertEquals(targetSize, result!!.length())
        assertTrue(manager.status.value is ModelStatus.Ready)
    }

    @Test
    fun networkIoExceptionDuringStreamFailsGracefully() = runBlocking {
        val baseDir = tempFolder.newFolder("io_failure")
        val stubContext = StubTestContext(baseDir)

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val faultyStream = object : java.io.InputStream() {
                    override fun read(): Int {
                        throw IOException("Simulated network stream disconnect")
                    }
                }
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = 105L * 1024L * 1024L
                    override fun source() = faultyStream.source().buffer()
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val manager = ModelDownloadManager(stubContext)
        val targetInfo = ModelInfo("test-model.litertlm", "https://example.com/model.litertlm")
        val result = manager.downloadModel(testClient, targetInfo)

        assertNull(result)
        assertTrue(manager.status.value is ModelStatus.Failed)
        val failed = manager.status.value as ModelStatus.Failed
        assertTrue(failed.error.contains("Simulated network stream disconnect"))
    }
}
