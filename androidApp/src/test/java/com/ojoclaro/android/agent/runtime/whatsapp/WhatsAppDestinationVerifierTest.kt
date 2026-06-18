package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationVerifier.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppDestinationVerifierTest {

    private fun verify(
        expectedEnding: String? = "5678",
        inChat: Boolean = true,
        hasEntryField: Boolean = true,
        timedOut: Boolean = false,
        visibleEnding: String? = "5678",
        labelMatches: Boolean? = null
    ) = WhatsAppDestinationVerifier.verify(
        expectedEnding, inChat, hasEntryField, timedOut, visibleEnding, labelMatches
    )

    @Test
    fun matchingEndingVerifies() {
        val r = verify(expectedEnding = "5678", visibleEnding = "5678")
        assertEquals(Status.VERIFIED, r.status)
        assertTrue(r.isVerified)
        assertEquals("phone_ending", r.signal)
    }

    @Test
    fun differentEndingIsMismatchAndBlocks() {
        val r = verify(expectedEnding = "5678", visibleEnding = "9999")
        assertEquals(Status.MISMATCH, r.status)
        assertFalse(r.isVerified)
    }

    @Test
    fun timeoutBlocks() {
        assertEquals(Status.TIMEOUT, verify(timedOut = true, inChat = false).status)
    }

    @Test
    fun notInChatBlocks() {
        assertEquals(Status.NOT_IN_CHAT, verify(inChat = false).status)
    }

    @Test
    fun noEntryFieldBlocks() {
        assertEquals(Status.NO_ENTRY_FIELD, verify(inChat = true, hasEntryField = false).status)
    }

    @Test
    fun inChatWithFieldButNoSignalsIsUnverified() {
        // wa.me abrió, estamos en un chat con campo, pero no hay número visible ni
        // etiqueta para confirmar: NO se puede afirmar el destino → bloquea.
        val r = verify(expectedEnding = "5678", visibleEnding = null, labelMatches = null)
        assertEquals(Status.UNVERIFIED_NO_SIGNALS, r.status)
        assertFalse(r.isVerified)
    }

    @Test
    fun labelMatchVerifiesWhenNoEndingSignal() {
        val r = verify(expectedEnding = null, visibleEnding = null, labelMatches = true)
        assertEquals(Status.VERIFIED, r.status)
        assertEquals("label", r.signal)
    }

    @Test
    fun labelMismatchBlocksWhenNoEndingSignal() {
        val r = verify(expectedEnding = null, visibleEnding = null, labelMatches = false)
        assertEquals(Status.MISMATCH, r.status)
    }

    @Test
    fun endingSignalWinsOverLabel() {
        // Si hay número visible, decide el ending aunque la etiqueta "coincida".
        val r = verify(expectedEnding = "5678", visibleEnding = "9999", labelMatches = true)
        assertEquals(Status.MISMATCH, r.status)
    }

    @Test
    fun redactedLogHasNoDigits() {
        val log = verify().redactedForLog()
        assertFalse(Regex("\\d{4,}").containsMatchIn(log), "verifier log must carry no number")
        assertTrue(log.contains("status="))
    }
}
