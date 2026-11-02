package com.onetake.app.ui.auditions

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.onetake.app.AppStore
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Empirical adversarial stress tests for UI components, state transitions,
 * multi-role switching, concurrency in AddReadingStep, and AppStore warmup.
 */
class AuditionUiStateAndConcurrencyChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestApplication(private val baseDir: File) : Application() {
        override fun getApplicationContext(): Context = this
        override fun getExternalFilesDir(type: String?): File = baseDir
        override fun getNoBackupFilesDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
        override fun getCacheDir(): File = baseDir
        override fun getSystemService(name: String): Any? = null
    }

    private lateinit var testContext: TestApplication

    @Before
    fun setUp() {
        HybridNoticeExtractor.resetInstanceForTesting()
        ModelDownloadManager.resetInstanceForTesting()
        testContext = TestApplication(tempFolder.root)
    }

    @After
    fun tearDown() {
        HybridNoticeExtractor.resetInstanceForTesting()
        ModelDownloadManager.resetInstanceForTesting()
    }

    // =========================================================================
    // 1. Rapid Multi-Role Switching & Line Attribution (isMine)
    // =========================================================================

    private val multiCharacterScript = """
        SCENE 1 - INT. PRECINCT - DAY
        MARCUS: We have thirty minutes before the warrant expires.
        DETECTIVE JANE: Make it count. The chief won't sign another extension.
        OFFICER BOB: Suspect vehicle just spotted on 5th Street.

        SCENE 2 - EXT. ALLEY - NIGHT
        MARCUS: Cover the back exit!
        SARAH: I'm already in position behind the dumpster.
        DETECTIVE JANE: Don't move until I give the signal.
    """.trimIndent()

    @Test
    fun testMultiRoleRapidSuccessiveSwitchingUpdatesIsMineConsistently() {
        val roles = listOf("MARCUS", "DETECTIVE JANE", "OFFICER BOB", "SARAH")
        var draft = AddDraft(
            project = "City Under Siege",
            role = "MARCUS",
            rawScriptText = multiCharacterScript,
            allAvailableRoles = roles,
            scenes = 2,
        )

        // Initial check: MARCUS active
        var scenes = ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role)
        assertEquals(2, scenes.size)
        var allLines = scenes.flatMap { it.lines }
        assertEquals(6, allLines.size)

        var marcusLines = allLines.filter { it.character == "MARCUS" }
        assertEquals(2, marcusLines.size)
        assertTrue(marcusLines.all { it.isMine })

        var otherLines = allLines.filter { it.character != "MARCUS" }
        assertEquals(4, otherLines.size)
        assertTrue(otherLines.none { it.isMine })

        // Switch 1: DETECTIVE JANE
        draft = draft.copy(role = "DETECTIVE JANE")
        scenes = ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role)
        allLines = scenes.flatMap { it.lines }
        assertEquals(6, allLines.size)
        val janeLines = allLines.filter { it.character == "DETECTIVE JANE" }
        assertEquals(2, janeLines.size)
        assertTrue("Jane lines must be isMine=true", janeLines.all { it.isMine })
        assertTrue("Marcus lines must now be isMine=false", allLines.filter { it.character == "MARCUS" }.none { it.isMine })
        assertTrue("Sarah lines must be isMine=false", allLines.filter { it.character == "SARAH" }.none { it.isMine })
        assertTrue("Bob lines must be isMine=false", allLines.filter { it.character == "OFFICER BOB" }.none { it.isMine })

        // Switch 2: OFFICER BOB
        draft = draft.copy(role = "OFFICER BOB")
        scenes = ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role)
        allLines = scenes.flatMap { it.lines }
        assertEquals(6, allLines.size)
        val bobLines = allLines.filter { it.character == "OFFICER BOB" }
        assertEquals(1, bobLines.size)
        assertTrue("Officer Bob lines must be isMine=true", bobLines.all { it.isMine })
        assertTrue("Others must be isMine=false", allLines.filter { it.character != "OFFICER BOB" }.none { it.isMine })

        // Switch 3: SARAH
        draft = draft.copy(role = "SARAH")
        scenes = ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role)
        allLines = scenes.flatMap { it.lines }
        assertEquals(6, allLines.size)
        val sarahLines = allLines.filter { it.character == "SARAH" }
        assertEquals(1, sarahLines.size)
        assertTrue("Sarah lines must be isMine=true", sarahLines.all { it.isMine })
        assertTrue("Others must be isMine=false", allLines.filter { it.character != "SARAH" }.none { it.isMine })

        // Switch back to MARCUS
        draft = draft.copy(role = "MARCUS")
        scenes = ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role)
        allLines = scenes.flatMap { it.lines }
        marcusLines = allLines.filter { it.character == "MARCUS" }
        assertEquals(2, marcusLines.size)
        assertTrue("Marcus lines must be restored to isMine=true", marcusLines.all { it.isMine })
        assertTrue("Others must be isMine=false", allLines.filter { it.character != "MARCUS" }.none { it.isMine })
    }

    @Test
    fun testMultiRoleRapidSwitchingUnderHeavyIteration() {
        val roles = listOf("MARCUS", "DETECTIVE JANE", "OFFICER BOB", "SARAH")
        val expectedLineCounts = mapOf(
            "MARCUS" to 2,
            "DETECTIVE JANE" to 2,
            "OFFICER BOB" to 1,
            "SARAH" to 1,
        )

        val rng = Random(42)
        var currentDraft = AddDraft(
            project = "Rapid Iteration Project",
            role = "MARCUS",
            rawScriptText = multiCharacterScript,
            allAvailableRoles = roles,
        )

        // 120 rapid successive role selections simulating frantic user taps
        for (i in 0 until 120) {
            val selectedRole = roles[rng.nextInt(roles.size)]
            val reparsedScenes = ScriptParser.parseScenes(currentDraft.rawScriptText, myCharacter = selectedRole)
            currentDraft = currentDraft.copy(
                role = selectedRole,
                scenes = maxOf(currentDraft.scenes, reparsedScenes.size.coerceAtLeast(1)),
            )

            val parsedScenes = ScriptParser.parseScenes(currentDraft.rawScriptText, myCharacter = currentDraft.role.ifBlank { "Role" })
            val allLines = parsedScenes.flatMap { it.lines }
            assertEquals(6, allLines.size)

            val myLines = allLines.filter { it.isMine }
            assertEquals(
                "Mismatch on iteration $i for role '$selectedRole'",
                expectedLineCounts[selectedRole],
                myLines.size
            )
            assertTrue(
                "All lines with isMine=true must match role '$selectedRole'",
                myLines.all { it.character.equals(selectedRole, ignoreCase = true) }
            )
        }
    }

    @Test
    fun testMultiRoleMakeAuditionProducesAccuratePiecesAndLineAttribution() {
        val draftJane = AddDraft(
            project = "City Under Siege",
            role = "DETECTIVE JANE",
            rawScriptText = multiCharacterScript,
            allAvailableRoles = listOf("MARCUS", "DETECTIVE JANE", "OFFICER BOB", "SARAH"),
            scenes = 2,
            asks = listOf("Slate first", "Full length"),
        )

        val auditionJane = draftJane.makeAudition(hasHeadshot = true)
        assertEquals("DETECTIVE JANE", auditionJane.role)
        assertEquals(2, auditionJane.scenes.size)

        val janeLines = auditionJane.scenes.flatMap { it.lines }.filter { it.isMine }
        assertEquals(2, janeLines.size)
        assertTrue(janeLines.all { it.character == "DETECTIVE JANE" })

        // Check audition pieces contain Slate, Full length, Scene 1, Scene 2
        val pieceKinds = auditionJane.pieces.map { it.kind }
        assertTrue(PieceKind.Slate in pieceKinds)
        assertTrue(PieceKind.FullLength in pieceKinds)
        assertEquals(2, auditionJane.pieces.count { it.kind == PieceKind.Scene })

        // Now switch role to OFFICER BOB and make audition
        val draftBob = draftJane.copy(role = "OFFICER BOB")
        val auditionBob = draftBob.makeAudition(hasHeadshot = true)
        assertEquals("OFFICER BOB", auditionBob.role)
        val bobLines = auditionBob.scenes.flatMap { it.lines }.filter { it.isMine }
        assertEquals(1, bobLines.size)
        assertEquals("OFFICER BOB", bobLines.first().character)
    }

    @Test
    fun testMultiRoleAttributionWithHonorificsAndAliases() {
        val scriptWithTitles = """
            DR. PATEL: Your vitals are stabilizing, Officer.
            OFFICER DAVE: Good. Because I have a suspect to interrogate.
            REV. THOMAS: Have mercy on his soul, my son.
        """.trimIndent()

        // Test with honorific-stripped role names
        val scenesPatel = ScriptParser.parseScenes(scriptWithTitles, myCharacter = "Patel")
        val linesPatel = scenesPatel.flatMap { it.lines }
        assertEquals(1, linesPatel.count { it.isMine })
        assertEquals("DR. PATEL", linesPatel.first { it.isMine }.character)

        val scenesDave = ScriptParser.parseScenes(scriptWithTitles, myCharacter = "Dave")
        val linesDave = scenesDave.flatMap { it.lines }
        assertEquals(1, linesDave.count { it.isMine })
        assertEquals("OFFICER DAVE", linesDave.first { it.isMine }.character)

        val scenesThomas = ScriptParser.parseScenes(scriptWithTitles, myCharacter = "Thomas")
        val linesThomas = scenesThomas.flatMap { it.lines }
        assertEquals(1, linesThomas.count { it.isMine })
        assertEquals("REV. THOMAS", linesThomas.first { it.isMine }.character)
    }

    @Test
    fun testMultiRoleAttributionWithParentheticalAndVoiceOverCues() {
        // Test standard screenplay cue format where cues are uppercase on their own line
        val scriptScreenplayFormat = """
            MARCUS (O.S.)
            Is anyone out there?

            SARAH (V.O.)
            I could hear his voice echoing through the comms.

            DETECTIVE JANE (CONT'D)
            Keep the line open!
        """.trimIndent()

        val scenesMarcus = ScriptParser.parseScenes(scriptScreenplayFormat, myCharacter = "MARCUS")
        val linesMarcus = scenesMarcus.flatMap { it.lines }
        assertEquals(3, linesMarcus.size)
        assertTrue(linesMarcus.first { it.character == "MARCUS" }.isMine)
        assertFalse(linesMarcus.first { it.character == "SARAH" }.isMine)
        assertFalse(linesMarcus.first { it.character == "DETECTIVE JANE" }.isMine)

        val scenesSarah = ScriptParser.parseScenes(scriptScreenplayFormat, myCharacter = "SARAH")
        val linesSarah = scenesSarah.flatMap { it.lines }
        assertTrue(linesSarah.first { it.character == "SARAH" }.isMine)
        assertFalse(linesSarah.first { it.character == "MARCUS" }.isMine)

        val scenesJane = ScriptParser.parseScenes(scriptScreenplayFormat, myCharacter = "DETECTIVE JANE")
        val linesJane = scenesJane.flatMap { it.lines }
        assertTrue(linesJane.first { it.character == "DETECTIVE JANE" }.isMine)
        assertFalse(linesJane.first { it.character == "MARCUS" }.isMine)

        // Also test colon format with parentheticals inside dialogue
        val scriptColonFormat = """
            MARCUS: (O.S.) Is anyone out there?
            SARAH: (V.O.) I could hear his voice echoing through the comms.
            DETECTIVE JANE: (CONT'D) Keep the line open!
        """.trimIndent()

        val scenesColonMarcus = ScriptParser.parseScenes(scriptColonFormat, myCharacter = "MARCUS")
        val linesColonMarcus = scenesColonMarcus.flatMap { it.lines }
        assertEquals(3, linesColonMarcus.size)
        assertTrue(linesColonMarcus.first { it.character == "MARCUS" }.isMine)
        assertFalse(linesColonMarcus.first { it.character == "SARAH" }.isMine)
    }

    // =========================================================================
    // 2. Empty/Blank Character Names & Non-Existent Roles
    // =========================================================================

    @Test
    fun testEmptyOrBlankRoleProducesNoClaimedLinesWithoutCrashing() {
        val blankRoles = listOf("", "   ", "\t", "\n", "  \r\n  ")
        for (blankRole in blankRoles) {
            val scenes = ScriptParser.parseScenes(multiCharacterScript, myCharacter = blankRole)
            val allLines = scenes.flatMap { it.lines }
            assertEquals(6, allLines.size)
            // No lines should be attributed when role is blank
            assertEquals("No lines should be claimed when role is '$blankRole'", 0, allLines.count { it.isMine })
        }
    }

    @Test
    fun testNonExistentRoleDoesNotMatchAnyLines() {
        val nonExistentRoles = listOf("GANDALF", "SHERLOCK", "UNKNOWN_EXTRAS", "RANDOM_PERSON_XYZ")
        for (ghostRole in nonExistentRoles) {
            val scenes = ScriptParser.parseScenes(multiCharacterScript, myCharacter = ghostRole)
            val allLines = scenes.flatMap { it.lines }
            assertEquals(6, allLines.size)
            assertEquals("Non-existent role '$ghostRole' must claim 0 lines", 0, allLines.count { it.isMine })

            val draft = AddDraft(
                project = "Test Project",
                role = ghostRole,
                rawScriptText = multiCharacterScript,
            )
            val audition = draft.makeAudition()
            assertEquals(ghostRole, audition.role)
            assertEquals(0, audition.scenes.flatMap { it.lines }.count { it.isMine })
        }
    }

    @Test
    fun testBlankRoleInAddDraftMakeAuditionFallbackBehavior() {
        // Case A: Multiple characters detected in script -> fallback to "Role"
        val draftMulti = AddDraft(
            project = "Multi Char",
            role = "   ", // Blank
            rawScriptText = multiCharacterScript,
            detectedCharacters = listOf("MARCUS", "DETECTIVE JANE"),
        )
        val auditionMulti = draftMulti.makeAudition()
        assertEquals("Role", auditionMulti.role)
        assertEquals(0, auditionMulti.scenes.flatMap { it.lines }.count { it.isMine })

        // Case B: Exactly 1 character detected in script -> fallback to that character!
        val singleCharScript = """
            HAMLET: To be, or not to be, that is the question.
        """.trimIndent()
        val draftSingle = AddDraft(
            project = "Solo Monologue",
            role = "", // Blank
            rawScriptText = singleCharScript,
            detectedCharacters = listOf("HAMLET"),
        )
        val auditionSingle = draftSingle.makeAudition()
        assertEquals("HAMLET", auditionSingle.role)
        val HamletLines = auditionSingle.scenes.flatMap { it.lines }
        assertEquals(1, HamletLines.size)
        assertTrue("Hamlet's single line must be isMine=true", HamletLines.first().isMine)
    }

    @Test
    fun testBlankScriptWithVariousRolesProducesGracefulPlaceholders() {
        val blankScripts = listOf("", "   ", "\n\n", "\t\t")
        for (blank in blankScripts) {
            val draft = AddDraft(
                project = "No Script Yet",
                role = "LEAD_ACTOR",
                rawScriptText = blank,
                scenes = 3,
            )
            val audition = draft.makeAudition()
            assertEquals("LEAD_ACTOR", audition.role)
            assertEquals(3, audition.scenes.size)
            for ((idx, scene) in audition.scenes.withIndex()) {
                assertEquals("Scene ${idx + 1}", scene.title)
                assertTrue("Placeholder scene lines must be empty", scene.lines.isEmpty())
                assertEquals(60, scene.estimatedSeconds)
            }
        }
    }

    @Test
    fun testAddDraftFromNoticeFieldsWithBlanksInAllAvailableRoles() {
        val fieldsWithBlanks = NoticeFields(
            project = "Clean Title",
            role = "",
            due = null,
            asks = emptyList(),
            destination = Destination.ActorsAccess,
            scriptText = multiCharacterScript,
            allAvailableRoles = listOf("", "   ", "MARCUS", "SARAH", ""),
        )
        val draft = addDraftFromNoticeFields(fieldsWithBlanks)
        assertEquals("Clean Title", draft.project)
        // Since f.role is empty and allAvailableRoles has > 1 item, autoRole is ""
        assertEquals("", draft.role)
        assertEquals(listOf("", "   ", "MARCUS", "SARAH", ""), draft.allAvailableRoles)

        // Calling makeAudition does not crash
        val audition = draft.makeAudition()
        assertEquals("Role", audition.role)
    }

    // =========================================================================
    // 3. Concurrency in AddReadingStep (parseSmart vs Reveal Animation)
    // =========================================================================

    @Test
    fun testParseSmartRunsAsynchronouslyWithoutBlockingProgressiveRevealTicks() = runBlocking {
        val notice = """
            Project: Concurrency Check
            Role: Alex
            Due: Friday 5pm
            Please slate first and shoot landscape.
        """.trimIndent()

        val rows = listOf("Project", "Your role", "Due", "Sides", "What they asked for")
        val tickTimestamps = CopyOnWriteArrayList<Long>()
        val startNano = System.nanoTime()

        // Simulate HybridNoticeExtractor with a slow 800ms background inference
        val extractor = HybridNoticeExtractor(testContext)
        extractor.testInferenceOverride = {
            // Intensive sleep on background IO thread
            Thread.sleep(800)
            """{"projectTitle": "Smart Concurrency Project", "targetRoleName": "Alex"}"""
        }

        // Replicate AddReadingStep coroutine structure
        val smartDeferred = async(Dispatchers.IO) {
            runCatching {
                NoticeParser.parseSmart(notice, extractor, today = LocalDate.now())
            }.getOrNull()
        }

        // 5-stage progressive reveal ticks (shortened to 100ms for reliable testing)
        var found = 0
        for (i in 1..rows.size) {
            delay(100)
            found = i
            tickTimestamps.add(System.nanoTime())
        }
        delay(50)

        // Await background result
        val smartFields = runCatching { smartDeferred.await() }.getOrNull()
        val finalDraft = if (smartFields != null) {
            addDraftFromNoticeFields(smartFields)
        } else {
            addParseNotice(notice)
        }

        // Verify ticks were NOT blocked by the 800ms IO work!
        assertEquals(5, tickTimestamps.size)
        // Verify all 5 ticks fired around ~100ms intervals (not delayed by 800ms)
        val totalTickDurationMs = (tickTimestamps.last() - startNano) / 1_000_000
        // The 5 ticks (5 * 100ms = 500ms) should complete well before 800ms background work blocks them
        assertTrue("Ticks should complete progressively without being serialized: ${totalTickDurationMs}ms", totalTickDurationMs < 850)

        // After awaiting, smart result should be merged
        assertNotNull(smartFields)
        assertEquals("Smart Concurrency Project", finalDraft.project)
        assertEquals("Alex", finalDraft.role)
    }

    @Test
    fun testParseSmartCompletionBeforeRevealEndsMergesCleanly() = runBlocking {
        val notice = """
            Project: Fast Notice
            Role: Detective Miller
            Due: tomorrow 5pm
        """.trimIndent()

        val extractor = HybridNoticeExtractor(testContext)
        extractor.testInferenceOverride = {
            // Instant inference (10ms)
            """{"projectTitle": "Fast Smart Project", "targetRoleName": "Detective Miller"}"""
        }

        val smartDeferred = async(Dispatchers.IO) {
            runCatching {
                NoticeParser.parseSmart(notice, extractor, today = LocalDate.now())
            }.getOrNull()
        }

        // Progressive reveal simulation
        val rows = 5
        for (i in 1..rows) {
            delay(40)
        }
        delay(20)

        val smartFields = runCatching { smartDeferred.await() }.getOrNull()
        assertNotNull(smartFields)
        val finalDraft = addDraftFromNoticeFields(smartFields!!)
        assertEquals("Fast Smart Project", finalDraft.project)
        assertEquals("Detective Miller", finalDraft.role)
    }

    @Test
    fun testParseSmartExceptionGracefullyFallsBackToDeterministicBaseline() = runBlocking {
        val notice = """
            Project: Fallback Baseline
            Role: Officer Dave
            Due: Oct 30 at 5pm
        """.trimIndent()

        val extractor = HybridNoticeExtractor(testContext)
        extractor.testInferenceOverride = {
            throw RuntimeException("Fatal native LiteRT-LM GPU context crash simulation")
        }

        val baseline = addParseNotice(notice)

        val smartDeferred = async(Dispatchers.IO) {
            runCatching {
                NoticeParser.parseSmart(notice, extractor, today = LocalDate.now())
            }.getOrNull()
        }

        for (i in 1..5) {
            delay(20)
        }

        // smartDeferred should catch the exception and fall back to baseline or return baseline
        val smartFields = runCatching { smartDeferred.await() }.getOrNull()
        val finalDraft = if (smartFields != null) {
            addDraftFromNoticeFields(smartFields)
        } else {
            baseline
        }

        // Result must NOT crash and must retain deterministic fields
        assertEquals("Fallback Baseline", finalDraft.project)
        assertEquals("Officer Dave", finalDraft.role)
    }

    @Test
    fun testParseSmartCancellationWhenStepCancelledMidFlight() = runBlocking {
        val notice = "Project: Cancel Notice\nRole: Dave\nDue: Friday 5pm"
        val extractor = HybridNoticeExtractor(testContext)
        val inferenceStarted = CompletableDeferred<Unit>()

        extractor.testInferenceOverride = {
            inferenceStarted.complete(Unit)
            Thread.sleep(2000)
            """{"projectTitle": "Should Not Arrive"}"""
        }

        val parentJob = launch {
            val deferred = async(Dispatchers.IO) {
                NoticeParser.parseSmart(notice, extractor, today = LocalDate.now())
            }
            delay(100)
            deferred.await()
        }

        // Wait until inference starts, then cancel parent job
        withTimeout(2000) { inferenceStarted.await() }
        parentJob.cancel()
        parentJob.join()

        assertTrue("Parent job must be cancelled", parentJob.isCancelled)
    }

    // =========================================================================
    // 4. AppStore Startup & warmUp() Thread Safety on Dispatchers.IO
    // =========================================================================

    @Test
    fun testAppStoreStartupIsNonBlockingAndInstantaneous() {
        val start = System.currentTimeMillis()
        val store = AppStore(testContext)
        val duration = System.currentTimeMillis() - start

        // AppStore init must be practically instantaneous (< 250ms), proving warmUp is asynchronous
        assertTrue("AppStore instantiation took too long (${duration}ms)", duration < 250)
        assertTrue("Auditions list should be empty by default", store.auditions.isEmpty())
        assertEquals(0, store.headshotsTaken)
    }

    @Test
    fun testWarmUpThreadSafetyUnderMassiveConcurrency() = runBlocking {
        val threadsCount = 40
        val latch = CountDownLatch(threadsCount)
        val errors = CopyOnWriteArrayList<Throwable>()

        val jobs = (0 until threadsCount).map { i ->
            launch(Dispatchers.IO) {
                try {
                    latch.countDown()
                    latch.await(3, TimeUnit.SECONDS)

                    // Concurrently instantiate AppStore and trigger warmUp
                    val store = AppStore(testContext)
                    HybridNoticeExtractor.getInstance(testContext).warmUp()
                    assertTrue(store.auditions.isEmpty())
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        jobs.joinAll()
        assertTrue("Concurrent warmup execution should produce 0 errors: $errors", errors.isEmpty())
    }

    @Test
    fun testConcurrentWarmUpAndExtractNoticeDoNotDeadlock() = runBlocking {
        val extractor = HybridNoticeExtractor.getInstance(testContext)
        extractor.testInferenceOverride = { prompt ->
            Thread.sleep(5)
            """{"projectTitle": "Deadlock Free"}"""
        }

        val iterations = 25
        val errors = CopyOnWriteArrayList<Throwable>()

        val warmupJobs = (0 until iterations).map {
            launch(Dispatchers.IO) {
                try {
                    extractor.warmUp()
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        val extractJobs = (0 until iterations).map { i ->
            launch(Dispatchers.IO) {
                try {
                    val res = extractor.extractNotice("Notice text $i", LocalDate.now())
                    assertNotNull(res)
                    assertEquals("Deadlock Free", res?.projectTitle)
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        withTimeout(10000) {
            (warmupJobs + extractJobs).joinAll()
        }

        assertTrue("Concurrent warmUp and extractNotice must not produce errors or deadlock: $errors", errors.isEmpty())
    }

    @Test
    fun testWarmUpResilienceWhenAICoreAndWorkManagerUnavailable() = runBlocking {
        // In plain unit test JVM, Generation.getClient() throws ClassNotFoundException or NoClassDefFoundError
        // performWarmUp must catch all Throwables cleanly and not crash.
        val extractor = HybridNoticeExtractor.getInstance(testContext)
        var exceptionThrown = false
        try {
            extractor.performWarmUp()
        } catch (t: Throwable) {
            exceptionThrown = true
        }

        assertFalse("performWarmUp must swallow JVM/native exceptions via runCatching / try-catch", exceptionThrown)
    }

    @Test
    fun testModelStatusStateFlowThreadSafeUpdatesAndCollectors() = runBlocking {
        val downloadManager = ModelDownloadManager.getInstance(testContext)
        val collectorErrors = CopyOnWriteArrayList<Throwable>()
        val observedStatuses = ConcurrentHashMap.newKeySet<String>()

        val collectJob = launch(Dispatchers.Default) {
            try {
                downloadManager.status.collect { status ->
                    observedStatuses.add(status.javaClass.simpleName)
                }
            } catch (c: CancellationException) {
                // Expected when job is cancelled
            } catch (t: Throwable) {
                collectorErrors.add(t)
            }
        }

        // Emit various status transitions concurrently from multiple threads
        val updateJobs = (0 until 30).map { i ->
            launch(Dispatchers.IO) {
                when (i % 4) {
                    0 -> downloadManager.updateStatus(ModelStatus.Downloading(i * 3, i * 10, 300))
                    1 -> downloadManager.updateStatus(ModelStatus.UsingSystemAiCore)
                    2 -> downloadManager.updateStatus(ModelStatus.Failed("Transient network error $i"))
                    else -> downloadManager.updateStatus(ModelStatus.NotDownloaded("Storage check"))
                }
            }
        }

        updateJobs.joinAll()
        delay(50)
        collectJob.cancel()

        assertTrue("Collector encountered errors: $collectorErrors", collectorErrors.isEmpty())
        assertTrue("Collector must have received state updates", observedStatuses.isNotEmpty())
    }
}
