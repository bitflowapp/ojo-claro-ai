package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WhatsApp Blind-First — destino seguro: confianza, redacción y PII.
 */
class WhatsAppDestinationTest {

    @Test
    fun onlyHighConfidenceCanPrepareDraft() {
        assertTrue(
            WhatsAppDestination.of(
                WhatsAppDestinationSource.CONTACT,
                WhatsAppDestinationConfidence.HIGH,
                "Juan"
            ).canPrepareDraft
        )
        assertFalse(
            WhatsAppDestination.of(
                WhatsAppDestinationSource.CONTACT,
                WhatsAppDestinationConfidence.MEDIUM,
                "Juan"
            ).canPrepareDraft
        )
        assertFalse(
            WhatsAppDestination.of(
                WhatsAppDestinationSource.NOTIFICATION,
                WhatsAppDestinationConfidence.LOW,
                "Juan"
            ).canPrepareDraft
        )
    }

    @Test
    fun phoneEndingIsOnlyLastFourDigits() {
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.CONTACT,
            WhatsAppDestinationConfidence.HIGH,
            "Marco",
            phoneE164 = "+5491150000000"
        )
        assertEquals("0000", dest.phoneEnding)
    }

    @Test
    fun shortOrMissingNumberYieldsNoEnding() {
        assertNull(
            WhatsAppDestination.of(
                WhatsAppDestinationSource.CONTACT,
                WhatsAppDestinationConfidence.HIGH,
                "Ana",
                phoneE164 = "123"
            ).phoneEnding
        )
        assertNull(
            WhatsAppDestination.of(
                WhatsAppDestinationSource.CONTACT,
                WhatsAppDestinationConfidence.HIGH,
                "Ana",
                phoneE164 = null
            ).phoneEnding
        )
    }

    @Test
    fun fullNumberIsNeverRetainedOrSpoken() {
        val full = "+5491150000000"
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.CONTACT,
            WhatsAppDestinationConfidence.HIGH,
            "Marco",
            phoneE164 = full
        )
        // El número completo no aparece en ningún texto producido por el destino.
        val digitsOnly = full.filter(Char::isDigit)
        listOf(dest.spokenConfirmation(), dest.spokenOpened(), dest.redactedForLog())
            .forEach { text ->
                assertFalse(text.contains(digitsOnly), "número completo filtrado en: $text")
                assertFalse(text.contains(full), "número completo filtrado en: $text")
            }
    }

    @Test
    fun redactedLogHasNoLabelOrDigits() {
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.NOTIFICATION,
            WhatsAppDestinationConfidence.HIGH,
            "María José",
            phoneE164 = "+5490000004444"
        )
        val log = dest.redactedForLog()
        assertFalse(log.contains("María"), "label filtrado en log")
        assertFalse(log.contains("4444"), "dígitos en log")
        assertTrue(log.contains("source=NOTIFICATION"))
        assertTrue(log.contains("conf=HIGH"))
        assertTrue(log.contains("labelLen="))
    }

    @Test
    fun spokenTextsMentionLabelAndEndingAndNeverPromiseSend() {
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.CONTACT,
            WhatsAppDestinationConfidence.HIGH,
            "Walter",
            phoneE164 = "+5490000001234"
        )
        assertTrue(dest.spokenOpened().contains("Walter"))
        assertTrue(dest.spokenOpened().contains("1234"))
        assertTrue(dest.spokenOpened().contains("No escribí nada"))
        assertTrue(dest.spokenConfirmation().contains("Walter"))
        assertTrue(dest.spokenConfirmation().contains("1234"))
    }

    @Test
    fun labelIsTrimmedAndCapped() {
        val long = "x".repeat(200)
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.CONTACT,
            WhatsAppDestinationConfidence.HIGH,
            "  $long  "
        )
        assertEquals(WhatsAppDestination.MAX_LABEL_CHARS, dest.redactedLabel.length)
    }

    @Test
    fun blankLabelFallsBackToGenericInSpeech() {
        val dest = WhatsAppDestination.of(
            WhatsAppDestinationSource.DEEP_LINK,
            WhatsAppDestinationConfidence.HIGH,
            "   "
        )
        assertFalse(dest.hasLabel)
        assertTrue(dest.spokenOpened().contains("contacto"))
        assertTrue(dest.spokenOpened().contains("No escribí nada"))
    }
}
