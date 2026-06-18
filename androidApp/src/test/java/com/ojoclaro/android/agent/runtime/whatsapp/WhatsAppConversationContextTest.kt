package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppConversationContextTest {

    @Test
    fun recordsAndRecallsRedacted() {
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteDestination("Juan", "+5490000001234", nowMillis = 1000)
        WhatsAppConversationContext.noteIntent("prepare_draft", 1000)
        WhatsAppConversationContext.notePendingAction("send_message", 1000)
        WhatsAppConversationContext.noteDraftLen(14, 1000)

        val s = WhatsAppConversationContext.current()!!
        assertEquals("Juan", s.chatLabelRedacted)
        assertEquals("1234", s.phoneEnding)
        assertEquals("prepare_draft", s.lastUserIntent)
        assertEquals("send_message", s.pendingAction)
        assertEquals(14, s.lastDraftLen)

        val recall = WhatsAppConversationContext.spokenRecall()
        assertTrue(recall.contains("Juan"))
        assertTrue(recall.contains("1234"))
        assertTrue(recall.contains("No voy a enviar"))
        WhatsAppConversationContext.clear()
    }

    @Test
    fun phoneEndingIsOnlyLastFour() {
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteDestination("X", "+5491150000000", 1)
        assertEquals("0000", WhatsAppConversationContext.current()!!.phoneEnding)
        WhatsAppConversationContext.clear()
    }

    @Test
    fun neverStoresFullNumberInLabel() {
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteDestination("Cliente 5490000001234", null, 1)
        val label = WhatsAppConversationContext.current()!!.chatLabelRedacted!!
        assertFalse(label.contains("5490000001234"))
        assertTrue(label.contains("[número]"))
        WhatsAppConversationContext.clear()
    }

    @Test
    fun summaryStripsEmbeddedNumbers() {
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteAssistantSummary("Le dije a Juan al 5490000001234 que llego", 1)
        val sum = WhatsAppConversationContext.current()!!.lastAssistantSummary!!
        assertFalse(sum.contains("5490000001234"))
        WhatsAppConversationContext.clear()
    }

    @Test
    fun clearForgetsEverything() {
        WhatsAppConversationContext.noteIntent("x", 1)
        assertTrue(WhatsAppConversationContext.current() != null)
        WhatsAppConversationContext.clear()
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun emptyRecallIsSafe() {
        WhatsAppConversationContext.clear()
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto"))
    }
}
