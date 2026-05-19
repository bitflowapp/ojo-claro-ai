package com.ojoclaro.android.agent.core.screen

import android.view.accessibility.AccessibilityEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessibilityEventClassifierTest {

    @Test
    fun `window state changed is relevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        )
    }

    @Test
    fun `window content changed is relevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        )
    }

    @Test
    fun `view text changed is relevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
        )
    }

    @Test
    fun `view focused is relevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        )
    }

    @Test
    fun `windows changed is relevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        )
    }

    @Test
    fun `view clicked is irrelevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.IRRELEVANT,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_VIEW_CLICKED)
        )
    }

    @Test
    fun `view scrolled is irrelevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.IRRELEVANT,
            AccessibilityEventClassifier.classify(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        )
    }

    @Test
    fun `notification state changed is irrelevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.IRRELEVANT,
            AccessibilityEventClassifier.classify(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            )
        )
    }

    @Test
    fun `unknown event type is irrelevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.IRRELEVANT,
            AccessibilityEventClassifier.classify(0x40000000)
        )
    }

    @Test
    fun `zero event type is irrelevant`() {
        assertEquals(
            AccessibilityEventClassifier.Relevance.IRRELEVANT,
            AccessibilityEventClassifier.classify(0)
        )
    }
}
