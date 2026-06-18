package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppActionAuditTest {

    @Test
    fun countersStartAtZeroAndIncrement() {
        WhatsAppActionAudit.reset()
        assertEquals(0L, WhatsAppActionAudit.sendTapCount())
        assertEquals(0L, WhatsAppActionAudit.callTapCount())
        assertEquals(0L, WhatsAppActionAudit.videoCallTapCount())
        assertEquals(0L, WhatsAppActionAudit.audioSendTapCount())

        WhatsAppActionAudit.recordSendTap()
        WhatsAppActionAudit.recordSendTap()
        WhatsAppActionAudit.recordVideoCallTap()
        WhatsAppActionAudit.recordBlocked()

        assertEquals(2L, WhatsAppActionAudit.sendTapCount())
        assertEquals(1L, WhatsAppActionAudit.videoCallTapCount())
        assertEquals(1L, WhatsAppActionAudit.blockedCount())
        WhatsAppActionAudit.reset()
        assertEquals(0L, WhatsAppActionAudit.sendTapCount())
    }

    @Test
    fun redactedSummaryHasCountsButNoPii() {
        WhatsAppActionAudit.reset()
        WhatsAppActionAudit.recordSendTap()
        val s = WhatsAppActionAudit.redactedSummary()
        assertTrue(s.contains("sendTap=1"))
        assertTrue(s.contains("callTap=0"))
        assertTrue(s.contains("blocked="))
        // no phone-length digit runs
        assertFalse(Regex("\\d{5,}").containsMatchIn(s))
        WhatsAppActionAudit.reset()
    }
}
