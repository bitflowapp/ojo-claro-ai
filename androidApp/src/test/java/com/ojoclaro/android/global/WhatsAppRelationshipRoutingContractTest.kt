package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato del Trusted Contacts / Relationship Resolver.
 *
 * Garantiza que:
 *  - "abrí WhatsApp con mi novia" resuelve la RELACIÓN local ANTES de la
 *    resolución por nombre, y sin vínculo NO cae al LLM ni pide abrir a mano.
 *  - vincular/olvidar ("este contacto es mi novia" / "olvidá a mi novia") se
 *    rutea localmente ANTES de cámara/compose/blind/LLM y DESPUÉS de STOP/WA-5.
 *  - la confirmación de vínculo está cableada en el bloque de pendientes.
 *  - los handlers de relación NUNCA envían/llaman/escriben borrador ni tocan el
 *    LLM, y nunca loguean el número completo.
 *  - STOP global limpia el pendiente de vínculo.
 *  - los call-sites de tap siguen siendo únicos (no se duplica una acción real).
 */
class WhatsAppRelationshipRoutingContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val relDispatchIdx = service.indexOf("if (handleWhatsAppRelationshipCommand(text)) return")
    private val replyDispatchIdx = service.indexOf("if (handlePendingRelationshipLinkReply(text)) return")

    /** Cuerpo de los dos handlers de relación (link + confirmación). */
    private val handlers: String = service
        .substringAfter("private fun handleWhatsAppRelationshipCommand")
        .substringBefore("private fun handleWhatsAppNotificationQueryCommand")

    /** Cuerpo de la apertura blind por contacto (donde resuelve la relación). */
    private val blindOpen: String = service
        .substringAfter("private fun handleBlindOpenContactChat")
        .substringBefore("private fun handleBlindReplyToLastNotification")

    @Test
    fun relationshipCommandRunsBeforeCameraComposeBlindAndLlm() {
        assertTrue(relDispatchIdx > 0, "relationship command must be wired in dispatch")
        listOf(
            "if (handleCameraAssistCommand(text)) return",
            "if (handleWhatsAppBlindFirstCommand(text)) return",
            "if (handleSmartCompose(text)) return",
            "if (handleScreenIntelligenceCommand(text)) return",
            "orchestrator.process("
        ).forEach { later ->
            val idx = service.indexOf(later)
            assertTrue(idx > relDispatchIdx, "relationship must run before: $later")
        }
    }

    @Test
    fun relationshipCommandRunsAfterStopAndWa5CancelPriority() {
        val cancelPriority = service.indexOf("pendingWhatsAppReply != null && WhatsAppReplyPhrases.isCancel(text)")
        val stop = service.indexOf("isStopModeCommand(text) ->")
        assertTrue(cancelPriority in 1 until relDispatchIdx, "WA-5 cancel priority stays before relationship")
        assertTrue(stop in 1 until relDispatchIdx, "global STOP stays before relationship")
    }

    @Test
    fun pendingLinkConfirmationIsWiredInPendingBlock() {
        assertTrue(replyDispatchIdx > 0, "pending link reply must be wired")
        val ordinalReply = service.indexOf("if (handlePendingOrdinalChatOpenReply(text)) return")
        assertTrue(ordinalReply in 1 until replyDispatchIdx, "link reply runs with the other pendings")
        // ...y antes del ruteo general (no debe quedar detrás del dispatch de comandos).
        assertTrue(replyDispatchIdx < relDispatchIdx, "pending replies run before command routing")
    }

    @Test
    fun blindOpenResolvesRelationshipBeforeNameResolution() {
        val relIdx = blindOpen.indexOf("WhatsAppRelationshipAlias.canonicalKey(contactQuery)")
        val nameIdx = blindOpen.indexOf("smartComposeResolver.resolve(contactQuery)")
        assertTrue(relIdx > 0, "blind open must try the relationship resolver")
        assertTrue(nameIdx > 0, "blind open must still try name resolution")
        assertTrue(relIdx < nameIdx, "relationship must resolve BEFORE name resolution")
    }

    @Test
    fun unconfiguredRelationshipIsSafeAndDoesNotFallToLlm() {
        // Rama "no configurado": responde local (return true), nunca cae al LLM
        // ni resuelve por nombre, y NO le pide al usuario abrir el chat a mano.
        assertTrue(blindOpen.contains("relationship=not_configured"), "must log the not-configured branch")
        val notConfigured = blindOpen
            .substringAfter("relationship=not_configured")
            .substringBefore("smartComposeResolver.resolve(contactQuery)")
        assertTrue(notConfigured.contains("return true"), "not-configured returns locally, never LLM")
        listOf("abrila vos", "abrí el chat vos", "abrilo vos a mano", "tocá el chat")
            .forEach { manual ->
                assertFalse(blindOpen.contains(manual), "blind user must not be told to open manually: $manual")
            }
    }

    @Test
    fun relationshipHandlersNeverSendCallDraftOrUseLlm() {
        listOf(
            "tapWhatsAppSend", "tapWhatsAppVideoCall", "setWhatsAppDraft",
            "pendingWhatsAppSendDraft =", "orchestrator.process", "handleFreeConversation",
            "recordSendTap", "recordVideoCallTap", "recordAudioSendTap"
        ).forEach { forbidden ->
            assertFalse(handlers.contains(forbidden), "relationship handlers must not contain: $forbidden")
        }
    }

    @Test
    fun relationshipHandlersLogRedactedNeverFullNumber() {
        // Positivo: usan formas redactadas (longitud + últimos 4 / redactedForLog).
        assertTrue(handlers.contains("redactedForLog()"), "linked log must be redacted")
        assertTrue(handlers.contains("phoneLen="), "awaiting-confirm log uses length, not the number")
        // Negativo: jamás interpolan el número completo en un log/voz.
        assertFalse(handlers.contains("\${pending.phoneE164}"), "must not interpolate full number")
        assertFalse(handlers.contains("phoneE164}"), "must not interpolate full number")
        assertFalse(handlers.contains(".phoneDigits}"), "must not interpolate raw digits")
    }

    @Test
    fun stopClearsPendingRelationshipLink() {
        val stopMode = service
            .substringAfter("isStopModeCommand(text) ->")
            .substringBefore("VoiceCommandDispatcher.isStopCommand(text) ->")
        val stopCmd = service
            .substringAfter("VoiceCommandDispatcher.isStopCommand(text) ->")
            .substringBefore("// WhatsApp Fluency")
        assertTrue(stopMode.contains("pendingRelationshipLink = null"), "stop-mode clears pending link")
        assertTrue(stopCmd.contains("pendingRelationshipLink = null"), "global stop clears pending link")
    }

    @Test
    fun tapCallSitesRemainSingle() {
        assertEquals(1, Regex("tapWhatsAppSend\\(").findAll(service).count(), "tapWhatsAppSend single call-site")
        assertEquals(1, Regex("tapWhatsAppVideoCall\\(").findAll(service).count(), "tapWhatsAppVideoCall single call-site")
    }

    // --- Codex HOLD gaps: verificación fuerte de destino + compose por relación ---

    private val composeDispatchIdx = service.indexOf("if (handleWhatsAppRelationshipComposeCommand(text)) return")

    private val composeBlock: String = service
        .substringAfter("private fun handleWhatsAppRelationshipComposeCommand")
        .substringBefore("private fun handleWhatsAppNotificationQueryCommand")

    private val verifyBlock: String = service
        .substringAfter("private fun verifyOpenedDestination")
        .substringBefore("private fun handleWhatsAppOpenChatOrdinalCommand")

    private val openBlindBlock: String = service
        .substringAfter("private fun openBlindContactChat")
        .substringBefore("private fun verifyOpenedDestination")

    @Test
    fun composeRunsBeforeBlindReplySmartComposeAndLlm() {
        assertTrue(composeDispatchIdx > 0, "relationship compose must be wired")
        listOf(
            "if (handleWhatsAppBlindFirstCommand(text)) return",
            "if (handleWhatsAppReplyCommand(text)) return",
            "if (handleSmartCompose(text)) return",
            "orchestrator.process("
        ).forEach { later ->
            assertTrue(service.indexOf(later) > composeDispatchIdx, "compose must run before: $later")
        }
        // ...y después de forbidden / STOP / WA-5 cancel.
        assertTrue(service.indexOf("if (handleWhatsAppForbiddenActionCommand(text)) return") in 1 until composeDispatchIdx)
        assertTrue(service.indexOf("isStopModeCommand(text) ->") in 1 until composeDispatchIdx)
        assertTrue(
            service.indexOf("pendingWhatsAppReply != null && WhatsAppReplyPhrases.isCancel(text)") in 1 until composeDispatchIdx
        )
    }

    @Test
    fun composeResolvesRelationshipAndOpensBeforeVerifyBeforeDraft() {
        // resolver relación → abrir → verificar → recién escribir.
        val resolveIdx = composeBlock.indexOf("relationshipStore.resolve(request.key)")
        val openIdx = composeBlock.indexOf("openVerifyThenDraft(")
        assertTrue(resolveIdx in 1 until openIdx, "compose resolves the relationship before opening")
        val verifyIdx = composeBlock.indexOf("verifyOpenedDestination(destination)")
        val notVerifiedIdx = composeBlock.indexOf("if (!verdict.isVerified)")
        val draftIdx = composeBlock.indexOf("draftWhatsAppMessageAndConfirm(message,")
        assertTrue(verifyIdx in 1 until notVerifiedIdx, "must verify before deciding")
        assertTrue(notVerifiedIdx in 1 until draftIdx, "must gate the draft on VERIFIED")
        assertTrue(
            composeBlock.contains("couldNotConfirmDestination"),
            "non-verified compose must speak COULD_NOT_CONFIRM_DESTINATION"
        )
    }

    @Test
    fun unconfiguredComposeIsSafeAndDoesNotFallToLlm() {
        assertTrue(composeBlock.contains("relationship=not_configured"))
        assertTrue(composeBlock.contains("relationshipNotConfiguredMessage("))
        val notConfigured = composeBlock
            .substringAfter("relationship=not_configured")
            .substringBefore("openVerifyThenDraft(")
        assertTrue(notConfigured.contains("return true"), "not-configured returns locally, no LLM")
    }

    @Test
    fun blindOpenUsesStrongVerificationNotJustInChat() {
        // openBlindContactChat ahora verifica el destino (no sólo inChat) y, si no
        // confirma, no afirma nada falso.
        assertTrue(openBlindBlock.contains("verifyOpenedDestination(destination)"), "open must run strong verify")
        val verifyIdx = openBlindBlock.indexOf("verifyOpenedDestination(destination)")
        val openedIdx = openBlindBlock.indexOf("destination.spokenOpened()")
        assertTrue(verifyIdx in 1 until openedIdx, "must verify before claiming opened")
        assertTrue(openBlindBlock.contains("couldNotConfirmDestination"), "unverified open is honest, no false claim")
        // La verificación sólo LEE: nada de taps.
        assertTrue(verifyBlock.contains("readVisibleWhatsAppPhoneNumber"), "verify reads the visible header number")
        assertTrue(verifyBlock.contains("hasMessageField"), "verify uses the entry-field signal")
    }

    @Test
    fun composeAndVerifyNeverSendCallTapOrUseLlm() {
        listOf(
            "tapWhatsAppSend", "tapWhatsAppVideoCall",
            "recordSendTap", "recordVideoCallTap", "recordAudioSendTap", "recordCallTap",
            "orchestrator.process", "handleFreeConversation"
        ).forEach { forbidden ->
            assertFalse(composeBlock.contains(forbidden), "compose flow must not contain: $forbidden")
            assertFalse(verifyBlock.contains(forbidden), "verify must not contain: $forbidden")
        }
        // Sí puede preparar BORRADOR (no es envío) tras verificar.
        assertTrue(composeBlock.contains("draftWhatsAppMessageAndConfirm(message,"))
    }
}
