package com.ojoclaro.android.global

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalAssistantVisibleChatPendingTest {

    @Test
    fun creaPendingAntesDeAbrirChatVisible() {
        val pending = GlobalAssistantService.buildVisibleChatPendingConfirmation(
            rawText = "abrí el chat que ves en pantalla de Marco Antonio",
            match = WhatsAppVisibleChatMatch(
                requestedName = "Marco Antonio",
                displayName = "Marco Antonio",
                matchedLabel = "Marco Antonio · 2 mensajes",
                score = 94
            ),
            nowMillis = 1_000L
        )

        assertTrue(GlobalAssistantService.isVisibleChatPendingConfirmation(pending))
        assertEquals("Marco Antonio", pending.command.targetName)
        assertTrue(pending.spokenText.contains("¿Querés que lo abra?"))
    }

    @Test
    fun noEjecutaClickSinConfirmacionEstricta() {
        val pending = GlobalAssistantService.buildVisibleChatPendingConfirmation(
            rawText = "tocá Marco Antonio",
            match = WhatsAppVisibleChatMatch(
                requestedName = "Marco Antonio",
                displayName = "Marco Antonio",
                matchedLabel = "Marco Antonio",
                score = 100
            ),
            nowMillis = 1_000L
        )

        assertFalse(GlobalAssistantService.shouldExecuteVisibleChatClick("sí", pending))
        assertFalse(GlobalAssistantService.shouldExecuteVisibleChatClick("dale", pending))
        assertTrue(GlobalAssistantService.shouldExecuteVisibleChatClick("confirmar", pending))
    }
}
