package com.ojoclaro.android.global

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionCatalog
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionGate
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionType
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppBlockedReason
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationConfidence
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppFeatureFlags
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppRiskLevel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato de Full Control Hardening de WhatsApp: por defecto NADA peligroso se
 * ejecuta. Mezcla inspección de fuente (cableado) + comportamiento del modelo.
 */
class WhatsAppFullControlSafetyContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    @Test
    fun defaultFlagsDisableEveryDangerousAction() {
        val off = WhatsAppFeatureFlags.DISABLED
        assertFalse(off.realSendEnabled)
        assertFalse(off.callEnabled)
        assertFalse(off.videoCallEnabled)
        assertFalse(off.audioSendEnabled)
        assertFalse(off.audioRecordEnabled)
        listOf(
            WhatsAppActionType.SEND_MESSAGE, WhatsAppActionType.CALL,
            WhatsAppActionType.VIDEO_CALL, WhatsAppActionType.SEND_AUDIO,
            WhatsAppActionType.RECORD_AUDIO
        ).forEach { a ->
            val g = WhatsAppActionCatalog.gate(a, off, hasDestination = true, WhatsAppDestinationConfidence.HIGH)
            assertTrue(
                g is WhatsAppActionGate.Blocked && g.reason == WhatsAppBlockedReason.FEATURE_DISABLED,
                "$a must be blocked by default"
            )
        }
        // y las prohibidas, incluso con todos los flags en true
        val allOn = WhatsAppFeatureFlags(true, true, true, true, true)
        WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.FORBIDDEN).forEach { a ->
            val g = WhatsAppActionCatalog.gate(a, allOn, true, WhatsAppDestinationConfidence.HIGH)
            assertTrue(g is WhatsAppActionGate.Blocked && g.reason == WhatsAppBlockedReason.FORBIDDEN_ACTION)
        }
    }

    @Test
    fun serviceUsesDisabledFlagsByDefault() {
        assertTrue(service.contains("WhatsAppFeatureFlags.DISABLED"), "GAS must default flags to DISABLED")
    }

    @Test
    fun sendTapIsFlagGatedBeforeTapping() {
        val branch = service.substringAfter("WhatsAppVoiceSendPhrases.isConfirmSend(text) ->")
            .substringBefore("VoicePhraseNormalizer.isNeverConfirm(text)")
        val gateIdx = branch.indexOf("if (!whatsAppFlags.realSendEnabled)")
        val tapIdx = branch.indexOf("tapWhatsAppSend(")
        assertTrue(gateIdx in 0 until tapIdx, "real-send flag gate must precede tapWhatsAppSend")
        assertTrue(branch.contains("WhatsAppActionAudit.recordSendTap()"), "send tap must be counted")
    }

    @Test
    fun videoCallTapIsFlagGatedBeforeTapping() {
        val section = service.substringAfter("private fun handlePendingVideoCallReply")
            .substringBefore("private fun speakAudioFlowGuide")
        val gateIdx = section.indexOf("if (!whatsAppFlags.videoCallEnabled)")
        val tapIdx = section.indexOf("tapWhatsAppVideoCall(")
        assertTrue(gateIdx in 0 until tapIdx, "video-call flag gate must precede tapWhatsAppVideoCall")
        assertTrue(section.contains("WhatsAppActionAudit.recordVideoCallTap()"), "video tap must be counted")
        // single video-call tap call-site
        assertEquals(1, Regex("tapWhatsAppVideoCall\\(").findAll(service).count())
    }

    @Test
    fun dangerousActionAndContextRouteBeforeLlmFallback() {
        val idxDangerous = service.indexOf("if (handleWhatsAppDangerousActionCommand(text)) return")
        val idxContext = service.indexOf("if (handleWhatsAppContextCommand(text)) return")
        val idxFallback = service.indexOf("orchestrator.process(")
        assertTrue(idxDangerous in 1 until idxFallback, "dangerous-action route before LLM")
        assertTrue(idxContext in 1 until idxFallback, "context route before LLM")
    }
}
