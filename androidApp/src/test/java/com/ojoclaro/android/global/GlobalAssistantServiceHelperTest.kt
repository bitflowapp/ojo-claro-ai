package com.ojoclaro.android.global

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
}
