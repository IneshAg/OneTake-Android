package com.onetake.app.ui.auditions

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed interface ModelStatus {
    data object UsingSystemAiCore : ModelStatus
    data class Ready(val file: File, val modelName: String) : ModelStatus
    data class Downloading(val progressPercent: Int, val downloadedMb: Int, val totalMb: Int) : ModelStatus
    data class NotDownloaded(val reason: String? = null) : ModelStatus
    data class Failed(val error: String) : ModelStatus
}

data class ModelInfo(
    val name: String,
    val url: String
)

class ModelDownloadManager internal constructor(
    private val context: Context
) {
    companion object {
        const val WORK_NAME = "model_download"
        const val GEMMA_MODEL_NAME = "gemma-4-E2B-it.litertlm"
        const val GEMMA_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
        const val QWEN_MODEL_NAME = "Qwen3-0.6B.litertlm"
        const val QWEN_MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true"

        // Thresholds
        const val MIN_MODEL_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB
        const val GEMMA_MIN_RAM_BYTES = (7.0 * 1024 * 1024 * 1024).toLong() // 7.0 GB
        const val GEMMA_MIN_STORAGE_BYTES = (3.2 * 1024 * 1024 * 1024).toLong() // 3.2 GB
        const val QWEN_MIN_STORAGE_BYTES = (1.2 * 1024 * 1024 * 1024).toLong() // 1.2 GB

        @Volatile
        private var instance: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager =
            instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }

        internal fun resetInstanceForTesting() {
            synchronized(this) {
                instance = null
            }
        }

        fun selectModel(totalMem: Long, availableBytes: Long): ModelInfo? {
            return when {
                totalMem >= GEMMA_MIN_RAM_BYTES && availableBytes >= GEMMA_MIN_STORAGE_BYTES -> {
                    ModelInfo(GEMMA_MODEL_NAME, GEMMA_MODEL_URL)
                }
                availableBytes >= QWEN_MIN_STORAGE_BYTES -> {
                    ModelInfo(QWEN_MODEL_NAME, QWEN_MODEL_URL)
                }
                else -> null
            }
        }

        fun selectModel(context: Context): ModelInfo? {
            val totalMem = try {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                memInfo.totalMem
            } catch (e: Throwable) {
                0L
            }

            val availableBytes = try {
                val stat = StatFs(context.filesDir.absolutePath)
                stat.availableBytes
            } catch (e: Throwable) {
                0L
            }

            return selectModel(totalMem, availableBytes)
        }

        fun getStorageDir(context: Context): File {
            val external = try {
                context.getExternalFilesDir(null)
            } catch (e: Throwable) {
                null
            }
            val dir = if (external != null && external.canWrite()) {
                File(external, "models")
            } else {
                File(context.noBackupFilesDir, "models")
            }
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val downloadMutex = Mutex()

    init {
        checkExistingModel()
    }

    fun setUsingSystemAiCore() {
        _status.value = ModelStatus.UsingSystemAiCore
    }

    fun updateStatus(newStatus: ModelStatus) {
        _status.value = newStatus
    }

    fun checkExistingModel(): File? {
        val modelInfo = selectModel(context) ?: return null
        val destFile = File(getStorageDir(context), modelInfo.name)
        if (destFile.exists() && destFile.length() > MIN_MODEL_SIZE_BYTES) {
            val ready = ModelStatus.Ready(destFile, modelInfo.name)
            _status.value = ready
            return destFile
        }
        return null
    }

    suspend fun ensureModelReadyOrStartDownload(): File? {
        val currentStatus = _status.value
        if (currentStatus is ModelStatus.Ready) {
            return currentStatus.file
        }
        val existing = checkExistingModel()
        if (existing != null) {
            return existing
        }
        enqueueBackgroundDownload(context)
        return null
    }

    fun enqueueBackgroundDownload(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        } catch (e: Throwable) {
            // Safe fallback if WorkManager is not initialized in unit tests
        }
    }

    suspend fun downloadModel(
        customClient: OkHttpClient? = null,
        targetModelInfo: ModelInfo? = null
    ): File? = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = checkExistingModel()
            if (existing != null && targetModelInfo == null) {
                return@withContext existing
            }

            val modelInfo = targetModelInfo ?: selectModel(context)
            if (modelInfo == null) {
                val notDownloaded = ModelStatus.NotDownloaded("Insufficient device storage or memory")
                _status.value = notDownloaded
                return@withContext null
            }

            val storageDir = getStorageDir(context)
            val destFile = File(storageDir, modelInfo.name)
            if (destFile.exists() && destFile.length() > MIN_MODEL_SIZE_BYTES) {
                val ready = ModelStatus.Ready(destFile, modelInfo.name)
                _status.value = ready
                return@withContext destFile
            }

            val partFile = File(storageDir, "${modelInfo.name}.part")
            var existingBytes = if (partFile.exists()) partFile.length() else 0L

            val okHttpClient = customClient ?: OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(modelInfo.url)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            var response = try {
                okHttpClient.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                _status.value = ModelStatus.Failed(e.message ?: "Network error")
                return@withContext null
            }

            // If 416 (Range Not Satisfiable), delete part file and retry from scratch
            if (response.code == 416) {
                response.close()
                partFile.delete()
                existingBytes = 0L
                response = try {
                    okHttpClient.newCall(Request.Builder().url(modelInfo.url).build()).execute()
                } catch (e: Exception) {
                    _status.value = ModelStatus.Failed(e.message ?: "Network retry error")
                    return@withContext null
                }
            }

            val isPartial = response.code == 206
            val isOk = response.code == 200

            if (!isPartial && !isOk) {
                val code = response.code
                val message = response.message
                response.close()
                _status.value = ModelStatus.Failed("HTTP $code: $message")
                return@withContext null
            }

            val append = isPartial
            if (!append) {
                existingBytes = 0L
            }

            val responseBody = response.body
            if (responseBody == null) {
                response.close()
                _status.value = ModelStatus.Failed("Empty response body")
                return@withContext null
            }

            val bodyContentLength = responseBody.contentLength()
            val totalBytes = if (bodyContentLength > 0L) {
                existingBytes + bodyContentLength
            } else {
                -1L
            }

            var downloadedBytes = existingBytes
            var lastProgressEmit = 0L
            val buffer = ByteArray(64 * 1024)

            try {
                FileOutputStream(partFile, append).use { out ->
                    responseBody.byteStream().use { inp ->
                        var read: Int
                        while (inp.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmit >= 250L) {
                                lastProgressEmit = now
                                val progressPercent = if (totalBytes > 0L) {
                                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else 0
                                val downloadedMb = (downloadedBytes / (1024L * 1024L)).toInt()
                                val totalMb = if (totalBytes > 0L) {
                                    (totalBytes / (1024L * 1024L)).toInt()
                                } else {
                                    downloadedMb
                                }
                                _status.value = ModelStatus.Downloading(progressPercent, downloadedMb, totalMb)
                            }
                        }
                        out.flush()
                    }
                }
            } catch (e: Exception) {
                _status.value = ModelStatus.Failed(e.message ?: "Download stream error")
                return@withContext null
            } finally {
                response.close()
            }

            if (partFile.length() > MIN_MODEL_SIZE_BYTES) {
                if (destFile.exists()) {
                    destFile.delete()
                }
                val renamed = partFile.renameTo(destFile)
                val finalFile = if (renamed) {
                    destFile
                } else {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                    destFile
                }

                if (finalFile.exists() && finalFile.length() > MIN_MODEL_SIZE_BYTES) {
                    val ready = ModelStatus.Ready(finalFile, modelInfo.name)
                    _status.value = ready
                    return@withContext finalFile
                } else {
                    _status.value = ModelStatus.Failed("Failed to finalize downloaded model file")
                    return@withContext null
                }
            } else {
                _status.value = ModelStatus.Failed(
                    "Downloaded file is too small: ${partFile.length()} bytes (expected > 100 MB)"
                )
                return@withContext null
            }
        }
    }
}

class ModelDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val manager = ModelDownloadManager.getInstance(applicationContext)
            val file = manager.downloadModel()
            if (file != null && file.exists()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
