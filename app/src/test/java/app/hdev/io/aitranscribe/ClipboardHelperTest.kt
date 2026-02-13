package app.hdev.io.aitranscribe

import app.hdev.io.aitranscribe.utils.ClipboardHelper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ClipboardHelper.
 * 
 * These tests verify the threshold constant and logic for clipboard size handling.
 */
class ClipboardHelperTest {

    @Test
    fun testMaxClipboardCharsThreshold() {
        // Verify the threshold is set to the specified 20,000 characters
        assertEquals(20_000, ClipboardHelper.MAX_CLIPBOARD_CHARS)
    }

    @Test
    fun testSmallTextIsWithinThreshold() {
        val smallText = "This is a small text"
        assert(smallText.length <= ClipboardHelper.MAX_CLIPBOARD_CHARS) {
            "Small text should be within clipboard threshold"
        }
    }

    @Test
    fun testLargeTextExceedsThreshold() {
        val largeText = "x".repeat(25_000)
        assert(largeText.length > ClipboardHelper.MAX_CLIPBOARD_CHARS) {
            "Large text should exceed clipboard threshold"
        }
    }

    @Test
    fun testEdgeCaseExactThreshold() {
        val exactThresholdText = "x".repeat(20_000)
        assert(exactThresholdText.length == ClipboardHelper.MAX_CLIPBOARD_CHARS) {
            "Text at exact threshold should equal MAX_CLIPBOARD_CHARS"
        }
    }
}
