package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Capability model + feature flags: lo peligroso arranca SIEMPRE bloqueado.
 */
class WhatsAppActionCatalogTest {

    private val off = WhatsAppFeatureFlags.DISABLED
    private val allOn = WhatsAppFeatureFlags(
        realSendEnabled = true, audioRecordEnabled = true, audioSendEnabled = true,
        callEnabled = true, videoCallEnabled = true
    )

    @Test
    fun flagsDefaultAllDangerousOff() {
        assertFalse(off.realSendEnabled)
        assertFalse(off.audioRecordEnabled)
        assertFalse(off.audioSendEnabled)
        assertFalse(off.callEnabled)
        assertFalse(off.videoCallEnabled)
        // dangerous actions disabled, safe actions enabled
        listOf(
            WhatsAppActionType.SEND_MESSAGE, WhatsAppActionType.CALL,
            WhatsAppActionType.VIDEO_CALL, WhatsAppActionType.SEND_AUDIO,
            WhatsAppActionType.RECORD_AUDIO
        ).forEach { assertFalse(off.isEnabled(it), "$it should be disabled") }
        assertTrue(off.isEnabled(WhatsAppActionType.READ_MESSAGES))
        assertTrue(off.isEnabled(WhatsAppActionType.OPEN_CHAT))
    }

    @Test
    fun forbiddenAlwaysBlockedEvenWithAllFlagsOn() {
        WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.FORBIDDEN).forEach { a ->
            val g = WhatsAppActionCatalog.gate(a, allOn, hasDestination = true, WhatsAppDestinationConfidence.HIGH)
            assertTrue(
                g is WhatsAppActionGate.Blocked && g.reason == WhatsAppBlockedReason.FORBIDDEN_ACTION,
                "$a must be FORBIDDEN_ACTION, got $g"
            )
        }
        // sanity: there is a meaningful set of forbidden actions
        assertTrue(WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.FORBIDDEN).size >= 10)
    }

    @Test
    fun highBlockedWhenFlagOff() {
        listOf(
            WhatsAppActionType.SEND_MESSAGE, WhatsAppActionType.CALL,
            WhatsAppActionType.VIDEO_CALL, WhatsAppActionType.SEND_AUDIO,
            WhatsAppActionType.RECORD_AUDIO
        ).forEach { a ->
            val g = WhatsAppActionCatalog.gate(a, off, hasDestination = true, WhatsAppDestinationConfidence.HIGH)
            assertTrue(
                g is WhatsAppActionGate.Blocked && g.reason == WhatsAppBlockedReason.FEATURE_DISABLED,
                "$a must be FEATURE_DISABLED, got $g"
            )
        }
    }

    @Test
    fun highNeedsStrongDoubleWhenEnabledAndHighConfidence() {
        val g = WhatsAppActionCatalog.gate(
            WhatsAppActionType.SEND_MESSAGE, allOn, hasDestination = true, WhatsAppDestinationConfidence.HIGH
        )
        assertTrue(g is WhatsAppActionGate.NeedsConfirmation && g.level == WhatsAppConfirmationLevel.STRONG_DOUBLE)
    }

    @Test
    fun highBlockedNoDestinationOrLowConfidence() {
        val noDest = WhatsAppActionCatalog.gate(WhatsAppActionType.SEND_MESSAGE, allOn, false, null)
        assertTrue(noDest is WhatsAppActionGate.Blocked && noDest.reason == WhatsAppBlockedReason.NO_DESTINATION)
        val lowConf = WhatsAppActionCatalog.gate(WhatsAppActionType.SEND_MESSAGE, allOn, true, WhatsAppDestinationConfidence.MEDIUM)
        assertTrue(lowConf is WhatsAppActionGate.Blocked && lowConf.reason == WhatsAppBlockedReason.LOW_CONFIDENCE)
    }

    @Test
    fun safeActionsAllowedReadOnly() {
        WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.SAFE).forEach { a ->
            val g = WhatsAppActionCatalog.gate(a, off, hasDestination = false, null)
            assertTrue(g is WhatsAppActionGate.AllowedReadOnly, "$a should be read-only allowed, got $g")
        }
    }

    @Test
    fun openChatNeedsHighConfidenceDestinationButNoConfirmation() {
        assertTrue(WhatsAppActionCatalog.gate(WhatsAppActionType.OPEN_CHAT, off, false, null) is WhatsAppActionGate.Blocked)
        assertTrue(
            WhatsAppActionCatalog.gate(WhatsAppActionType.OPEN_CHAT, off, true, WhatsAppDestinationConfidence.MEDIUM)
                is WhatsAppActionGate.Blocked
        )
        assertTrue(
            WhatsAppActionCatalog.gate(WhatsAppActionType.OPEN_CHAT, off, true, WhatsAppDestinationConfidence.HIGH)
                is WhatsAppActionGate.AllowedReadOnly
        )
    }

    @Test
    fun prepareDraftNeedsSingleConfirmation() {
        val g = WhatsAppActionCatalog.gate(WhatsAppActionType.PREPARE_DRAFT, off, true, WhatsAppDestinationConfidence.HIGH)
        assertTrue(g is WhatsAppActionGate.NeedsConfirmation && g.level == WhatsAppConfirmationLevel.SINGLE)
    }

    @Test
    fun logMarkersAreDistinctAndPiiFree() {
        val markers = WhatsAppActionType.values().map { it.logMarker }
        assertEquals(markers.size, markers.toSet().size, "log markers must be unique")
        markers.forEach { assertFalse(Regex("\\d").containsMatchIn(it), "marker has digits: $it") }
    }
}
