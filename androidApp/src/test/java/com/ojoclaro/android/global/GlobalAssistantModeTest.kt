package com.ojoclaro.android.global

import com.ojoclaro.android.external.ExternalActionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalAssistantModeTest {

    @Test
    fun detectsWhatsAppContinuationHandoff() {
        val handoff = ExternalActionEvent.ExternalAppHandoff(
            externalAppName = "WhatsApp",
            reason = GlobalAssistantMode.WHATSAPP_CONTINUATION_TEXT,
            returnHint = "Volve a Ojo Claro.",
            spokenText = GlobalAssistantMode.WHATSAPP_CONTINUATION_TEXT,
            delegate = ExternalActionEvent.OpenWhatsApp
        )

        assertTrue(GlobalAssistantMode.shouldExpectWhatsAppAction(handoff))
    }

    @Test
    fun principalWhatsAppDoesNotEnterGuidedContinuationContext() {
        val handoff = ExternalActionEvent.ExternalAppHandoff(
            externalAppName = "WhatsApp",
            reason = "Abri WhatsApp principal.",
            returnHint = "Volve a Ojo Claro.",
            spokenText = "Abri WhatsApp principal. Mientras estes ahi no escucho comandos.",
            delegate = ExternalActionEvent.OpenWhatsApp
        )

        assertFalse(GlobalAssistantMode.shouldExpectWhatsAppAction(handoff))
    }

    @Test
    fun strictConfirmationOnlyAcceptsSafeWords() {
        assertTrue(GlobalAssistantMode.isStrictConfirmation("confirmar"))
        assertTrue(GlobalAssistantMode.isStrictConfirmation("confirmo"))
        assertTrue(GlobalAssistantMode.isStrictConfirmation("aceptar"))
        assertFalse(GlobalAssistantMode.isStrictConfirmation("si"))
        assertFalse(GlobalAssistantMode.isStrictConfirmation("dale"))
        assertFalse(GlobalAssistantMode.isStrictConfirmation("ok"))
    }

    @Test
    fun mapsAndPhoneNamesNormalize() {
        assertEquals(ExternalAppName.WHATSAPP, ExternalAppName.fromHandoffName("WhatsApp"))
        assertEquals(ExternalAppName.MAPS, ExternalAppName.fromHandoffName("Google Maps"))
        assertEquals(ExternalAppName.PHONE, ExternalAppName.fromHandoffName("Telefono"))
    }
}
