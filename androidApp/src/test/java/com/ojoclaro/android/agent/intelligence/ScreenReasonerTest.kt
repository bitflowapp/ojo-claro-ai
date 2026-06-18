package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V2.2 — razonador de pantalla. PURO. NO ejecutado (disco C: crítico;
 * ver docs/V22_SCREEN_REASONER_REPORT.md).
 */
class ScreenReasonerTest {

    private fun row(name: String) = ReasonerNode(text = name, isClickable = true)
    private val editText = "android.widget.EditText"
    private val button = "android.widget.Button"

    @Test
    fun whatsappChatListDetected() {
        val m = ScreenReasoner.reason(
            RawScreen("com.whatsapp", listOf(row("Marco Luna"), row("Sofía"), row("Juan")))
        )
        assertEquals(ActiveApp.WHATSAPP, m.activeApp)
        assertEquals(ScreenType.CHAT_LIST, m.screenType)
        assertEquals(3, m.visibleContacts.size)
        assertEquals(Confidence.HIGH, m.confidence)
    }

    @Test
    fun whatsappConversationDetectedWithTitle() {
        val m = ScreenReasoner.reason(
            RawScreen(
                "com.whatsapp",
                listOf(
                    ReasonerNode(text = "Marco Luna", isHeading = true),
                    ReasonerNode(text = "Hola, ¿cómo estás?"),
                    ReasonerNode(text = "Todo bien"),
                    ReasonerNode(hint = "Mensaje", isEditable = true, className = editText),
                    ReasonerNode(contentDescription = "Enviar", isClickable = true, className = button),
                )
            )
        )
        assertEquals(ScreenType.CONVERSATION, m.screenType)
        assertEquals("Marco Luna", m.currentChatTitle)
        assertTrue(m.hasMessageInput)
        assertTrue(m.hasSendButton)
        assertEquals(Confidence.HIGH, m.confidence)
    }

    @Test
    fun instagramInboxDetected() {
        val m = ScreenReasoner.reason(
            RawScreen("com.instagram.android", listOf(row("Sofi"), row("Ana"), row("Lu")))
        )
        assertEquals(ActiveApp.INSTAGRAM, m.activeApp)
        assertEquals(ScreenType.CHAT_LIST, m.screenType)
        assertTrue(m.visibleContacts.all { it.app == TargetApp.INSTAGRAM })
    }

    @Test
    fun instagramConversationDetected() {
        val m = ScreenReasoner.reason(
            RawScreen(
                "com.instagram.android",
                listOf(
                    ReasonerNode(text = "Sofi", isHeading = true),
                    ReasonerNode(hint = "Mensaje", isEditable = true, className = editText),
                )
            )
        )
        assertEquals(ActiveApp.INSTAGRAM, m.activeApp)
        assertEquals(ScreenType.CONVERSATION, m.screenType)
    }

    @Test
    fun unknownScreenIsLowConfidence() {
        val m = ScreenReasoner.reason(
            RawScreen("com.android.settings", listOf(ReasonerNode(text = "Wi-Fi", isClickable = true)))
        )
        assertEquals(ActiveApp.OTHER, m.activeApp)
        assertEquals(ScreenType.UNKNOWN, m.screenType)
        assertEquals(Confidence.LOW, m.confidence)
    }

    @Test
    fun riskyControlsAreDetected() {
        val m = ScreenReasoner.reason(
            RawScreen(
                "com.whatsapp",
                listOf(
                    ReasonerNode(contentDescription = "Videollamada", isClickable = true, className = button),
                    ReasonerNode(text = "Pagar", isClickable = true, className = button),
                    ReasonerNode(hint = "Mensaje", isEditable = true, className = editText),
                )
            )
        )
        assertTrue(RiskyControl.VIDEO_CALL in m.riskyControls)
        assertTrue(RiskyControl.PAYMENT in m.riskyControls)
    }

    @Test
    fun lockedScreenIsReportedAndEmpty() {
        val m = ScreenReasoner.reason(RawScreen("com.whatsapp", emptyList(), screenLocked = true))
        assertTrue(m.screenLocked)
        assertFalse(m.hasMessageInput)
        assertEquals(0, m.visibleContacts.size)
    }
}
