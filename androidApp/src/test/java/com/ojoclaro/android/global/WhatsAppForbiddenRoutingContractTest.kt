package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato: las acciones PROHIBIDAS de WhatsApp se rutean localmente ANTES de
 * cámara/compose/navegación y del LLM, rechazan sin tocar nada, y no rompen
 * WA-5 / blind-first / STOP / los call-sites únicos de tap.
 */
class WhatsAppForbiddenRoutingContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val dispatchIdx = service.indexOf("if (handleWhatsAppForbiddenActionCommand(text)) return")

    private val body: String = service
        .substringAfter("private fun handleWhatsAppForbiddenActionCommand")
        .substringBefore("private fun isWhatsAppActiveContext")

    @Test
    fun forbiddenRunsBeforeCameraComposeNavAndLlm() {
        assertTrue(dispatchIdx > 0, "forbidden handler must be wired in dispatch")
        listOf(
            "if (handleInstagramTaskCommand(text)) return",
            "if (handleCameraAssistCommand(text)) return",
            "if (handleWhatsAppFirstControlCommand(text)) return",
            "if (handleSmartCompose(text)) return",
            "if (handleScreenIntelligenceCommand(text)) return",
            "orchestrator.process("
        ).forEach { later ->
            val idx = service.indexOf(later)
            assertTrue(idx > dispatchIdx, "forbidden must run before: $later")
        }
    }

    @Test
    fun forbiddenRunsAfterStopAndWa5CancelPriority() {
        val cancelPriority = service.indexOf("pendingWhatsAppReply != null && WhatsAppReplyPhrases.isCancel(text)")
        val stop = service.indexOf("isStopModeCommand(text) ->")
        assertTrue(cancelPriority in 1 until dispatchIdx, "WA-5 cancel priority stays before forbidden")
        assertTrue(stop in 1 until dispatchIdx, "global STOP stays before forbidden")
    }

    @Test
    fun forbiddenHandlerBlocksWithoutTouchingAnything() {
        assertTrue(body.contains("WhatsAppActionAudit.recordBlocked()"), "must increment blocked counter")
        assertTrue(body.contains("WhatsAppForbiddenActionNarrator.refusal("), "must speak a safe refusal")
        // never touches UI, draft, send, calls, backend/LLM
        listOf(
            "tapWhatsAppSend", "tapWhatsAppVideoCall", "setWhatsAppDraft",
            "pendingWhatsAppSendDraft =", "orchestrator.process", "handleFreeConversation",
            "recordSendTap", "recordVideoCallTap", "recordAudioSendTap"
        ).forEach { forbidden ->
            assertFalse(body.contains(forbidden), "forbidden handler must not contain: $forbidden")
        }
    }

    @Test
    fun nonRegressionRoutesStillWired() {
        assertTrue(service.contains("if (handleWhatsAppBlindFirstCommand(text)) return"), "blind-first still wired")
        assertTrue(service.contains("if (handlePendingWhatsAppReplyConfirmation(text)) return"), "WA-5 reply still wired")
        assertTrue(service.contains("if (handlePendingWhatsAppSendReply(text)) return"), "WA-5 send-pending still wired")
    }

    @Test
    fun tapCallSitesRemainSingle() {
        assertEquals(1, Regex("tapWhatsAppSend\\(").findAll(service).count(), "tapWhatsAppSend single call-site")
        assertEquals(1, Regex("tapWhatsAppVideoCall\\(").findAll(service).count(), "tapWhatsAppVideoCall single call-site")
    }
}
