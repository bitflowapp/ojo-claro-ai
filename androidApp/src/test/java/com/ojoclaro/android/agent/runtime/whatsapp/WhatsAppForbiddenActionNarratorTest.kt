package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppForbiddenActionNarratorTest {

    @Test
    fun everyForbiddenActionHasASafeRefusal() {
        WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.FORBIDDEN).forEach { a ->
            val msg = WhatsAppForbiddenActionNarrator.refusal(a)
            assertTrue(msg.isNotBlank(), "$a refusal blank")
            assertTrue(msg.contains("No toqué nada"), "$a must reassure nothing touched")
            assertTrue(msg.length <= 220, "$a refusal too long: ${msg.length}")
            assertFalse(Regex("\\d{4,}").containsMatchIn(msg), "$a refusal has digits")
        }
    }

    @Test
    fun refusalsAreActionAppropriateAndOfferSafeAlternative() {
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.DELETE_CHAT).contains("borrar"))
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.BLOCK_CONTACT).contains("bloquear"))
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.PAYMENT).contains("pagos"))
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.SEND_PHOTO).contains("fotos"))
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.SHARE_LOCATION).contains("ubicación"))
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.OPEN_SUSPICIOUS_LINK).contains("enlace"))
        // ofrece una alternativa segura
        assertTrue(WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.DELETE_CHAT).contains("leerte los chats"))
    }
}
