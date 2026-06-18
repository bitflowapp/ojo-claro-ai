package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.Capability
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.Inputs
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.NextStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppCapabilityMatrixTest {

    private fun inputs(
        whatsappInstalled: Boolean = true,
        accessibilityBound: Boolean = false,
        notificationListener: Boolean = false,
        inWhatsApp: Boolean = false,
        inChat: Boolean = false,
        backendAvailable: Boolean = false
    ) = Inputs(whatsappInstalled, accessibilityBound, notificationListener, inWhatsApp, inChat, backendAvailable)

    @Test
    fun notInstalledBlocksEverythingAndAsksInstall() {
        val v = WhatsAppCapabilityMatrix.evaluate(inputs(whatsappInstalled = false))
        assertEquals(NextStep.INSTALL_WHATSAPP, v.nextStep)
        assertFalse(v.canDo(Capability.OPEN_WHATSAPP))
    }

    @Test
    fun notificationsWorkWithoutAccessibility() {
        val v = WhatsAppCapabilityMatrix.evaluate(inputs(notificationListener = true))
        assertTrue(v.canDo(Capability.CHECK_NOTIFICATIONS))
        assertTrue(v.canDo(Capability.OPEN_WHATSAPP))
        assertFalse(v.canDo(Capability.READ_SCREEN))
        assertEquals(NextStep.ENABLE_ACCESSIBILITY, v.nextStep)
    }

    @Test
    fun readScreenNeedsAccessibilityAndForeground() {
        val noFg = WhatsAppCapabilityMatrix.evaluate(inputs(accessibilityBound = true, inWhatsApp = false))
        assertFalse(noFg.canDo(Capability.READ_SCREEN))
        assertEquals(NextStep.OPEN_WHATSAPP, noFg.nextStep)

        val fg = WhatsAppCapabilityMatrix.evaluate(inputs(accessibilityBound = true, inWhatsApp = true))
        assertTrue(fg.canDo(Capability.READ_SCREEN))
        assertEquals(NextStep.OPEN_CHAT, fg.nextStep)
    }

    @Test
    fun prepareReplyNeedsAccessibilityAndInChat() {
        val v = WhatsAppCapabilityMatrix.evaluate(
            inputs(accessibilityBound = true, inWhatsApp = true, inChat = true)
        )
        assertTrue(v.canDo(Capability.PREPARE_REPLY_DRY_RUN))
        assertTrue(v.canDo(Capability.READ_SCREEN))
    }

    @Test
    fun allGreenIsReady() {
        val v = WhatsAppCapabilityMatrix.evaluate(
            inputs(
                accessibilityBound = true, notificationListener = true,
                inWhatsApp = true, inChat = true, backendAvailable = true
            )
        )
        assertEquals(NextStep.READY, v.nextStep)
        assertEquals(4, v.can.size)
    }

    @Test
    fun narratorIsHonestAndNeverLeavesUserLost() {
        val noAcc = inputs(notificationListener = true)
        val gated = WhatsAppCapabilityNarrator.accessibilityGatedRead(noAcc)
        assertTrue(gated.contains("no puedo leer la pantalla", ignoreCase = true))
        assertTrue(gated.contains("notificaciones", ignoreCase = true))

        val v = WhatsAppCapabilityMatrix.evaluate(noAcc)
        assertTrue(WhatsAppCapabilityNarrator.whatCanIDoNow(v).contains("Ahora puedo", ignoreCase = true))
        assertTrue(WhatsAppCapabilityNarrator.whatIsMissing(v).contains("Accesibilidad", ignoreCase = true))
        assertTrue(WhatsAppCapabilityNarrator.whyNotWorking(noAcc).contains("Accesibilidad", ignoreCase = true))
    }

    @Test
    fun gatedReadWithoutListenerOffersOnboarding() {
        val none = inputs()
        val gated = WhatsAppCapabilityNarrator.accessibilityGatedRead(none)
        assertTrue(gated.contains("activar Estela", ignoreCase = true))
    }
}
