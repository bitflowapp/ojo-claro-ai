package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalAssistantServiceHelperTest {

    @Test
    fun extractsBareContactForWhatsAppContinuation() {
        assertEquals("Marco Antonio", GlobalAssistantService.extractBareContact("Marco Antonio"))
        assertEquals("Marco", GlobalAssistantService.extractBareContact("con Marco"))
        assertEquals("Marco", GlobalAssistantService.extractBareContact("el de Marco"))
    }

    @Test
    fun doesNotTreatConfirmationNoiseAsContact() {
        assertNull(GlobalAssistantService.extractBareContact("si"))
        assertNull(GlobalAssistantService.extractBareContact("dale"))
        assertNull(GlobalAssistantService.extractBareContact("confirmar"))
    }

    @Test
    fun extractsMessageWithoutContactOnlyWhenThereIsMessageVerb() {
        assertEquals("llego en 10", GlobalAssistantService.extractMessageWithoutContact("decile que llego en 10"))
        assertEquals("estoy llegando", GlobalAssistantService.extractMessageWithoutContact("que estoy llegando"))
        assertNull(GlobalAssistantService.extractMessageWithoutContact("perro"))
    }

    @Test
    fun stopModeCommandIsSeparateFromCallar() {
        assertTrue(GlobalAssistantService.isStopModeCommand("detener"))
        assertFalse(GlobalAssistantService.isStopModeCommand("callar"))
    }

    @Test
    fun affirmativeNoiseNeverConfirms() {
        assertTrue(GlobalAssistantService.isNonConfirmingAffirmative("si"))
        assertTrue(GlobalAssistantService.isNonConfirmingAffirmative("dale"))
        assertFalse(GlobalAssistantService.isNonConfirmingAffirmative("confirmar"))
    }

    @Test
    fun cancelWithoutPendingStaysLocalAndDoesNotReachLlm() {
        assertTrue(com.ojoclaro.android.voice.VoiceCommandDispatcher.isBareCancelCommand("me arrepentí"))
        assertTrue(com.ojoclaro.android.voice.VoiceCommandDispatcher.isBareCancelCommand("cancelá"))

        val service = File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()
        val localCancel = service.indexOf("if (handleBasicConversationCommand(text)) return")
        val safeLlm = service.indexOf("if (handleSafeLlmFallback(text)) return")
        assertTrue(localCancel in 1 until safeLlm, "cancel local must run before LLM fallback")
        assertTrue(service.contains("No había nada pendiente, pero queda cancelado."))
    }
}
