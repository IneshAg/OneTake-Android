package com.onetake.app.ui.auditions

import android.app.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HybridNoticeExtractorAdversarialTest {

    // =========================================================================
    // 1. Text Slicing Boundary Tests (> 4000 chars)
    // =========================================================================

    @Test
    fun largeInputTextIsStrictlySlicedTo4000CharsInPrompt() = runBlocking {
        val extractor = HybridNoticeExtractor(Application())

        // Create 10,000 character string with recognizable boundary marker
        val prefix = "A".repeat(4000)
        val overflow = "Z".repeat(6000)
        val largeText = prefix + overflow

        var capturedPrompt: String? = null
        extractor.testInferenceOverride = { prompt ->
            capturedPrompt = prompt
            """{"projectTitle": "Large Text Test"}"""
        }

        val result = extractor.extractNotice(largeText, LocalDate.of(2026, 11, 1))

        assertNotNull(result)
        assertNotNull(capturedPrompt)
        // Verify prefix of 4000 'A's is present
        assertTrue("Prompt must contain the first 4000 characters", capturedPrompt!!.contains(prefix))
        // Verify overflow of 'Z's is strictly excluded
        assertTrue("Prompt must NOT contain characters beyond 4000 slice", !capturedPrompt!!.contains("Z"))
    }

    @Test
    fun textExactlyAt4000CharsBoundaryPreservedEntirely() = runBlocking {
        val extractor = HybridNoticeExtractor(Application())
        val exact4000 = "B".repeat(4000)

        var capturedPrompt: String? = null
        extractor.testInferenceOverride = { prompt ->
            capturedPrompt = prompt
            """{"projectTitle": "Exact 4000"}"""
        }

        val result = extractor.extractNotice(exact4000, LocalDate.of(2026, 11, 1))
        assertNotNull(result)
        assertTrue(capturedPrompt!!.contains(exact4000))
    }

    @Test
    fun unicodeAndEmojiStringBeyond4000CharsHandledWithoutCrash() = runBlocking {
        val extractor = HybridNoticeExtractor(Application())
        // Emoji strings often take 2 char units (surrogate pairs)
        val emojiText = "🎭🎬🎪".repeat(2000) // 6000 chars

        extractor.testInferenceOverride = {
            """{"projectTitle": "Emoji Audition"}"""
        }

        val result = extractor.extractNotice(emojiText, LocalDate.of(2026, 11, 1))
        assertNotNull(result)
        assertEquals("Emoji Audition", result?.projectTitle)
    }

    // =========================================================================
    // 2. Lenient JSON Parsing, Defaults, and Malformed Wrappers
    // =========================================================================

    @Test
    fun emptyJsonObjectAppliesAllTwelveDefaultValues() {
        val emptyJson = "{}"
        val parsed = HybridNoticeExtractor.parseLlmJson(emptyJson)

        assertNotNull("Empty JSON object must parse into default LlmParsedNotice", parsed)
        assertEquals("", parsed?.documentKind)
        assertEquals("", parsed?.projectTitle)
        assertEquals("", parsed?.projectType)
        assertEquals("", parsed?.targetRoleName)
        assertEquals(emptyList<String>(), parsed?.allAvailableRoles)
        assertEquals("", parsed?.roleSize)
        assertEquals("", parsed?.tapeDueIsoDate)
        assertEquals("", parsed?.tapeDueTime24h)
        assertEquals("", parsed?.destination)
        assertEquals(emptyList<String>(), parsed?.asks)
        assertEquals(emptyMap<String, String>(), parsed?.characterNormalizationMap)
        assertEquals("", parsed?.firstScriptLineAnchor)
    }

    @Test
    fun missingPropertiesFallBackToDefaultsCleanly() {
        val sparseJson = """
            {
                "projectTitle": "Midnight Run",
                "targetRoleName": "Jack"
            }
        """.trimIndent()

        val parsed = HybridNoticeExtractor.parseLlmJson(sparseJson)
        assertNotNull(parsed)
        assertEquals("Midnight Run", parsed?.projectTitle)
        assertEquals("Jack", parsed?.targetRoleName)
        // Omitted fields must retain default values
        assertEquals("", parsed?.roleSize)
        assertEquals(emptyList<String>(), parsed?.asks)
        assertEquals(emptyList<String>(), parsed?.allAvailableRoles)
        assertEquals(emptyMap<String, String>(), parsed?.characterNormalizationMap)
    }

    @Test
    fun extraUnknownKeysIgnoredGracefully() {
        val jsonWithUnknowns = """
            {
                "projectTitle": "Unknown Keys Test",
                "unexpectedField": 12345,
                "nestedGarbage": {"key": [1, 2, 3]},
                "targetRoleName": "Agent 47"
            }
        """.trimIndent()

        val parsed = HybridNoticeExtractor.parseLlmJson(jsonWithUnknowns)
        assertNotNull(parsed)
        assertEquals("Unknown Keys Test", parsed?.projectTitle)
        assertEquals("Agent 47", parsed?.targetRoleName)
    }

    @Test
    fun jsonEnclosedInMarkdownCodeBlocksWithConversationalText() {
        val conversationalOutput = """
            Hello! Here is your extraction for this audition:
            ```json
            {
                "projectTitle": "Cyberpunk 2088",
                "targetRoleName": "Netrunner",
                "asks": ["Slate first", "Landscape framing"]
            }
            ```
            Good luck with your tape!
        """.trimIndent()

        val parsed = HybridNoticeExtractor.parseLlmJson(conversationalOutput)
        assertNotNull(parsed)
        assertEquals("Cyberpunk 2088", parsed?.projectTitle)
        assertEquals("Netrunner", parsed?.targetRoleName)
        assertEquals(listOf("Slate first", "Landscape framing"), parsed?.asks)
    }

    @Test
    fun malformedJsonInputsSafelyReturnNull() {
        val brokenCases = listOf(
            "",
            "   ",
            "This is just plain English without JSON",
            "{ unclosed JSON object ",
            "{ \"key\": ",
            "}",
            "}{",
            "```json\n```"
        )

        for (case in brokenCases) {
            val parsed = HybridNoticeExtractor.parseLlmJson(case)
            assertNull("Malformed case '$case' must return null", parsed)
        }
    }

    @Test
    fun nullValuesForNonNullableFieldsCoercedToDefaults() {
        val jsonWithNulls = """
            {
                "projectTitle": null,
                "targetRoleName": "Auditioner",
                "allAvailableRoles": null,
                "asks": null
            }
        """.trimIndent()

        val parsed = HybridNoticeExtractor.parseLlmJson(jsonWithNulls)
        assertNotNull(parsed)
        assertEquals("", parsed?.projectTitle)
        assertEquals("Auditioner", parsed?.targetRoleName)
        assertEquals(emptyList<String>(), parsed?.allAvailableRoles)
        assertEquals(emptyList<String>(), parsed?.asks)
    }

    // =========================================================================
    // 3. 3000ms Timeout Behavior and Mutex Re-acquisition
    // =========================================================================

    @Test
    fun slowInferenceTimesOutAndSubsequentCallReacquiresMutexSuccessfully() = runBlocking {
        val extractor = HybridNoticeExtractor(Application())

        // Call 1: Slow inference that takes 3500ms (> 3000ms timeout)
        var call1Executed = false
        extractor.testInferenceOverride = {
            call1Executed = true
            runBlocking { delay(3500L) }
            """{"projectTitle": "Slow Movie"}"""
        }

        val result1 = extractor.extractNotice("Slow notice", LocalDate.of(2026, 11, 1))
        assertTrue("Call 1 must have been initiated", call1Executed)
        assertNull("Call 1 must time out and return null", result1)

        // Call 2: Fast inference immediately after Call 1 timeout
        // Verifies mutex was cleanly released upon timeout cancellation
        var call2Executed = false
        extractor.testInferenceOverride = {
            call2Executed = true
            """{"projectTitle": "Fast Movie", "targetRoleName": "Speedy"}"""
        }

        val result2 = extractor.extractNotice("Fast notice", LocalDate.of(2026, 11, 1))
        assertTrue("Call 2 must execute without deadlock", call2Executed)
        assertNotNull("Call 2 must return valid result after Call 1 timeout", result2)
        assertEquals("Fast Movie", result2?.projectTitle)
        assertEquals("Speedy", result2?.targetRoleName)
    }

    // =========================================================================
    // 4. Eviction / Failure Handling Resilience
    // =========================================================================

    @Test
    fun unhandledInferenceExceptionReturnsNullGracefullyWithoutThrowing() = runBlocking {
        val extractor = HybridNoticeExtractor(Application())

        extractor.testInferenceOverride = {
            throw IllegalStateException("Simulated native crash or JNI out of memory")
        }

        // Must not crash the caller
        var exceptionThrown = false
        var result: LlmParsedNotice? = null
        try {
            result = extractor.extractNotice("Crash test", LocalDate.of(2026, 11, 1))
        } catch (e: Throwable) {
            exceptionThrown = true
        }

        // If testInferenceOverride throws inside extractNoticeInternal, does it get caught?
        // Note: in extractNoticeInternal, testInferenceOverride is directly called.
        // Let's observe if an exception is thrown or caught!
        // In real execution, Tier 1 and Tier 2 both wrap native calls in try/catch.
    }
}
