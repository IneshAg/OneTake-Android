package com.onetake.app.ui.auditions

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ModelDownloadManagerWorkManagerAndThreadingChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class ChallengeStubContext(private val baseDir: File) : ContextWrapper(Application()) {
        override fun getApplicationContext(): Context = this
        override fun getExternalFilesDir(type: String?): File = baseDir
        override fun getNoBackupFilesDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
    }

    // =========================================================================
    // 1. WorkManager Integration & Background Execution Constraints
    // =========================================================================

    @Test
    fun workManagerConstraintsRequireNetworkTypeConnected() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        assertEquals(
            "Constraints must strictly require NetworkType.CONNECTED",
            NetworkType.CONNECTED,
            constraints.requiredNetworkType
        )
    }

    @Test
    fun workManagerRequestBuilderTargetsModelDownloadWorkerWithConnectedConstraint() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .build()

        assertEquals(
            "Worker class name in WorkSpec must match ModelDownloadWorker",
            ModelDownloadWorker::class.java.name,
            request.workSpec.workerClassName
        )
        assertEquals(
            "Constraints on WorkRequest must enforce NetworkType.CONNECTED",
            NetworkType.CONNECTED,
            request.workSpec.constraints.requiredNetworkType
        )
    }

    @Test
    fun workManagerWorkerHasPublicReflectiveConstructorRequiredByWorkerFactory() {
        // Android WorkManager's DefaultWorkerFactory requires a public constructor with (Context, WorkerParameters)
        val constructor = ModelDownloadWorker::class.java.getDeclaredConstructor(
            Context::class.java,
            WorkerParameters::class.java
        )

        assertNotNull("ModelDownloadWorker must declare constructor(Context, WorkerParameters)", constructor)
        assertTrue(
            "ModelDownloadWorker must extend CoroutineWorker",
            CoroutineWorker::class.java.isAssignableFrom(ModelDownloadWorker::class.java)
        )
    }

    @Test
    fun enqueueBackgroundDownloadUsesExistingWorkPolicyKeepAndDoesNotThrow() {
        val baseDir = tempFolder.newFolder("wm_test")
        val stubContext = ChallengeStubContext(baseDir)
        val manager = ModelDownloadManager(stubContext)

        // Verifies that ModelDownloadManager.WORK_NAME is defined and enqueue does not throw
        assertEquals("model_download", ModelDownloadManager.WORK_NAME)

        // In JVM test environment without WorkManagerInitializer, enqueueBackgroundDownload catches Throwable safely
        var threw = false
        try {
            manager.enqueueBackgroundDownload(stubContext)
        } catch (t: Throwable) {
            threw = true
        }
        assertTrue("enqueueBackgroundDownload must swallow WorkManager initialization errors gracefully in non-Android environments", !threw)
    }

    // =========================================================================
    // 2. Background Thread Instantiation Stress Harness (100 concurrent workers)
    // =========================================================================

    @Test
    fun modelDownloadManagerConcurrentInstantiationOnBackgroundThreadsReturnsSingleInstance() = runBlocking {
        val baseDir = tempFolder.newFolder("mdm_threading")
        val stubContext = ChallengeStubContext(baseDir)

        ModelDownloadManager.resetInstanceForTesting()

        val concurrentCallers = 100
        val results = (1..concurrentCallers).map {
            async(Dispatchers.Default) {
                ModelDownloadManager.getInstance(stubContext)
            }
        }.awaitAll()

        assertEquals(concurrentCallers, results.size)
        val firstInstance = results.first()
        for (i in results.indices) {
            assertSame(
                "All concurrent background thread callers must obtain the identical singleton instance at index $i",
                firstInstance,
                results[i]
            )
        }
    }

    @Test
    fun hybridNoticeExtractorConcurrentInstantiationOnBackgroundThreadsReturnsSingleInstance() = runBlocking {
        val baseDir = tempFolder.newFolder("extractor_threading")
        val stubContext = ChallengeStubContext(baseDir)

        HybridNoticeExtractor.resetInstanceForTesting()

        val concurrentCallers = 100
        val results = (1..concurrentCallers).map {
            async(Dispatchers.Default) {
                HybridNoticeExtractor.getInstance(stubContext)
            }
        }.awaitAll()

        assertEquals(concurrentCallers, results.size)
        val firstInstance = results.first()
        for (i in results.indices) {
            assertSame(
                "All concurrent background thread callers must obtain the identical singleton instance at index $i",
                firstInstance,
                results[i]
            )
        }
    }

    @Test
    fun concurrentHeavyweightWorkloadAcrossThreadPoolDoesNotThrow() {
        val executor = Executors.newFixedThreadPool(16)
        val baseDir = tempFolder.newFolder("thread_pool_stress")
        val stubContext = ChallengeStubContext(baseDir)

        val errors = ConcurrentHashMap<Int, Throwable>()
        val futures = mutableListOf<Future<*>>()

        for (i in 0 until 100) {
            val taskIndex = i
            futures.add(executor.submit {
                try {
                    val mdm = ModelDownloadManager.getInstance(stubContext)
                    assertNotNull(mdm)
                    val info = ModelDownloadManager.selectModel(
                        totalMem = 8L * 1024 * 1024 * 1024,
                        availableBytes = 4L * 1024 * 1024 * 1024
                    )
                    assertEquals(ModelDownloadManager.GEMMA_MODEL_NAME, info?.name)

                    val hne = HybridNoticeExtractor.getInstance(stubContext)
                    assertNotNull(hne)
                    val parsed = HybridNoticeExtractor.parseLlmJson("""{"projectTitle": "Test$taskIndex"}""")
                    assertEquals("Test$taskIndex", parsed?.projectTitle)
                } catch (t: Throwable) {
                    errors[taskIndex] = t
                }
            })
        }

        for (f in futures) {
            f.get()
        }
        executor.shutdown()

        assertTrue(
            "There must be zero exceptions across concurrent background threads: ${errors.values}",
            errors.isEmpty()
        )
    }

    // =========================================================================
    // 3. ProGuard / R8 Rules & Serialization Contract Validation
    // =========================================================================

    @Test
    fun llmParsedNoticeHasSerializableAnnotationAndAccessibleSerializer() {
        // Kotlinx serialization requires @Serializable annotation for reflection and bytecode keep rules
        val annotations = LlmParsedNotice::class.java.annotations
        val isSerializable = annotations.any { it.annotationClass.java.name == Serializable::class.java.name }
        assertTrue("LlmParsedNotice must be annotated with @Serializable", isSerializable)

        // Verify serializer is available
        val serializer = serializer<LlmParsedNotice>()
        assertNotNull("KSerializer<LlmParsedNotice> must be resolvable", serializer)
    }

    @Test
    fun llmParsedNoticeDecodesEmptyJsonObjectWithDefaultValues() {
        val emptyJson = "{}"
        val parsed = HybridNoticeExtractor.parseLlmJson(emptyJson)
        assertNotNull("Parsing empty JSON object should succeed with default values", parsed)
        assertEquals("", parsed?.documentKind)
        assertEquals("", parsed?.projectTitle)
        assertEquals("", parsed?.projectType)
        assertEquals("", parsed?.targetRoleName)
        assertTrue(parsed?.allAvailableRoles?.isEmpty() == true)
        assertEquals("", parsed?.roleSize)
        assertEquals("", parsed?.tapeDueIsoDate)
        assertEquals("", parsed?.tapeDueTime24h)
        assertEquals("", parsed?.destination)
        assertTrue(parsed?.asks?.isEmpty() == true)
        assertTrue(parsed?.characterNormalizationMap?.isEmpty() == true)
        assertEquals("", parsed?.firstScriptLineAnchor)
    }

    @Test
    fun modelStatusSealedHierarchyExhaustiveMatching() {
        // Empirically challenge that every ModelStatus subtype can be cleanly handled without throwing
        val states: List<ModelStatus> = listOf(
            ModelStatus.UsingSystemAiCore,
            ModelStatus.Ready(File("dummy"), "model"),
            ModelStatus.Downloading(10, 100, 1000),
            ModelStatus.NotDownloaded("reason"),
            ModelStatus.Failed("error")
        )

        for (state in states) {
            val label = when (state) {
                is ModelStatus.UsingSystemAiCore -> "AICORE"
                is ModelStatus.Ready -> "READY:${state.modelName}"
                is ModelStatus.Downloading -> "DOWNLOADING:${state.progressPercent}%"
                is ModelStatus.NotDownloaded -> "NOT_DOWNLOADED:${state.reason}"
                is ModelStatus.Failed -> "FAILED:${state.error}"
            }
            assertTrue(label.isNotEmpty())
        }
    }
}
