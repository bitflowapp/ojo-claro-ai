package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Blind Safety — contratos por INSPECCIÓN DE FUENTE sobre GlobalAssistantService
 * (la Service no es instanciable en unit test). Verifican el cableado de los
 * fixes HIGH: verificación de destino en TODO draft, guard crítico antes del
 * LLM, cancelación con copy canónico, feedback no-mudo y call-sites únicos.
 */
class WhatsAppBlindSafetyContractTest {

    private val service =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val accessibilityService =
        File("src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt").readText()

    private fun bodyOf(afterMarker: String, untilMarker: String): String =
        service.substringAfter(afterMarker).substringBefore(untilMarker)

    // #3 — el rastro de foreground se alimenta por window-state Y content-changed Y
    // por cada lectura de paquete (robusto a un único state-change perdido), y se
    // olvida cuando otro app real toma el frente.
    @Test
    fun foregroundTrackerPopulatedFromEventsAndReads() {
        assertTrue(
            accessibilityService.contains("TYPE_WINDOW_STATE_CHANGED") &&
                accessibilityService.contains("TYPE_WINDOW_CONTENT_CHANGED"),
            "tracker must update on BOTH window-state and content-changed events"
        )
        assertTrue(accessibilityService.contains("private fun noteForegroundPackage"), "tracker helper must exist")
        val readBody = accessibilityService
            .substringAfter("private fun readActiveWindowPackageName")
            .substringBefore("private fun readActiveWindowClassNameInternal")
        assertTrue(readBody.contains("noteForegroundPackage"), "package reads must feed the tracker too")
    }

    // #3 — diagnóstico sanitizado por comando para depurar el contexto en físico.
    @Test
    fun contextDiagnosticLoggedPerCommand() {
        assertTrue(service.contains("logWhatsAppContextDiag()"), "per-command context diag must be wired")
        assertTrue(service.contains("WHATSAPP_CONTEXT_DIAG"), "diag log must be tagged")
        assertTrue(service.contains("contextSource="), "diag must report the context source")
        // sanitizado: categoría de app, no nombre de paquete crudo en el log.
        assertTrue(service.contains("activePkg=\$activeCat"), "diag must log the app CATEGORY, not raw package")
    }

    // #2 — reply NO escribe si no puede identificar/anunciar el chat abierto.
    @Test
    fun replyBlocksWhenOpenChatUnidentifiable() {
        val body = bodyOf("private fun handleWhatsAppReplyCommand", "private fun describeOpenChatDestination")
        assertTrue(body.contains("readVisibleWhatsAppChatTitle"), "reply must read the open-chat identity")
        assertTrue(
            body.contains("No pude confirmar el chat correcto. No escribí nada."),
            "reply must speak the safe-block line when the chat is unidentifiable"
        )
        assertTrue(
            body.indexOf("openChatTitle.isNullOrBlank()") < body.indexOf("draftWhatsAppMessageAndConfirm"),
            "the identity gate must run BEFORE writing the draft"
        )
    }

    // #1 — smart-compose ya NO prellena con wa.me?text=; verifica destino primero.
    @Test
    fun smartComposeVerifiesBeforeDraftAndDropsTextPrefill() {
        val body = bodyOf("private fun prepareDraftAndAskSend", "// --- V1.7")
        assertFalse(body.contains("Uri.encode("), "smart-compose must NOT prefill the draft via a deep link")
        assertTrue(body.contains("verifyOpenedDestination"), "smart-compose must verify the opened destination")
        assertTrue(
            body.indexOf("verdict.isVerified") < body.indexOf("pendingWhatsAppSendDraft = message"),
            "pending draft must be armed only AFTER verification"
        )
        assertTrue(body.contains("couldNotConfirmDestination"), "non-verified must speak the safe block")
    }

    // #1 — el verificador del relationship-compose sigue gateando el draft.
    @Test
    fun relationshipComposeStillGatesDraftBehindVerification() {
        val body = bodyOf("private fun openVerifyThenDraft", "private fun handleWhatsAppNotificationQueryCommand")
        assertTrue(body.contains("verifyOpenedDestination"), "must verify")
        assertTrue(
            body.indexOf("if (!verdict.isVerified)") < body.indexOf("draftWhatsAppMessageAndConfirm"),
            "draft must be gated by isVerified"
        )
    }

    // #5 — el guard crítico corre ANTES de la salida a LLM (/conversation).
    @Test
    fun criticalGuardRunsBeforeLlm() {
        val guardDispatch = service.indexOf("if (handleWhatsAppCriticalGuardBeforeLlm(text)) return")
        val convGate = service.indexOf("if (ConversationGate.isConversational(text)) {")
        assertTrue(guardDispatch > 0, "critical guard must be dispatched")
        assertTrue(convGate > 0, "conversation gate must exist")
        assertTrue(guardDispatch < convGate, "guard must run before the conversation gate / LLM")
    }

    // #6 — feedback no-mudo: backstop de excepciones del turno de voz HABLA.
    @Test
    fun voiceTurnHasAudibleExceptionBackstop() {
        assertTrue(service.contains("CoroutineExceptionHandler"), "serviceScope needs an exception handler")
        val handler = bodyOf("voiceTurnExceptionHandler = CoroutineExceptionHandler", "private val serviceScope")
        assertTrue(handler.contains("speak("), "the exception backstop must speak (never mute)")
    }

    // #4(B) — cancelación V1.2 dice el copy canónico (no copy distinto, no mudo).
    @Test
    fun v12CancelSpeaksCanonicalReassurance() {
        val body = bodyOf("private fun handlePendingWhatsAppSendReply", "private fun handleWhatsAppVoiceSendCommand")
        assertTrue(body.contains("isCancelSend(text)"), "V1.2 must handle cancel")
        assertTrue(body.contains("cancelledReassurance()"), "V1.2 cancel must speak the canonical reassurance")
    }

    // #8 — envío/videollamada reales: UN solo call-site cada uno, detrás del flag.
    @Test
    fun sendAndVideoCallStayGatedSingleCallSite() {
        assertEquals(1, Regex("tapWhatsAppSend\\(").findAll(service).count(), "exactly one send call-site")
        assertEquals(1, Regex("tapWhatsAppVideoCall\\(").findAll(service).count(), "exactly one video-call call-site")
        assertTrue(service.contains("whatsAppFlags.realSendEnabled"), "send must be gated by realSendEnabled")
    }

    // #11/#13 — el helper de limpieza borra SÓLO el borrador propio (con pending)
    // y deja log; nunca toca chats de terceros ni envía.
    @Test
    fun clearOwnDraftHelperOnlyClearsWhenPendingAndNeverSends() {
        val body = bodyOf("private fun clearOwnWhatsAppDraftIfPending", "private fun completeOverlayVoiceTurn")
        assertTrue(
            body.contains("pendingWhatsAppReply != null || pendingWhatsAppSendDraft != null"),
            "must only clear when Estela actually armed a draft"
        )
        assertTrue(body.contains("setWhatsAppDraft(\"\")"), "must blank the composer")
        assertFalse(body.contains("tapWhatsAppSend"), "cleanup must never send")
    }

    // #11/#13 — teardown del turno y AMBOS STOP limpian el borrador propio antes
    // de soltar el pending (no dejar texto tipeado sin enviar y sin aviso).
    @Test
    fun teardownAndStopBranchesClearOwnDraft() {
        val teardown = bodyOf("private fun completeOverlayVoiceTurn", "private fun onVoiceFinalText")
        assertTrue(
            teardown.indexOf("clearOwnWhatsAppDraftIfPending()") < teardown.indexOf("pendingWhatsAppReply = null"),
            "turn teardown must clear the draft BEFORE nulling the pending"
        )
        // 4 call-sites: teardown + 2 STOP branches + legacy cancel.
        assertTrue(
            Regex("clearOwnWhatsAppDraftIfPending\\(\\)").findAll(service).count() >= 4,
            "clear-own-draft must be wired into teardown, both STOP branches and legacy cancel"
        )
    }

    // #13 — la cancelación legacy limpia el borrador Y habla el copy canónico.
    @Test
    fun legacyCancelClearsDraftAndSpeaks() {
        val body = bodyOf("private fun handlePendingWhatsAppSendReply", "private fun handleWhatsAppVoiceSendCommand")
        val cancelIdx = body.indexOf("isCancelSend(text) ->")
        val clearIdx = body.indexOf("clearOwnWhatsAppDraftIfPending()")
        val nullIdx = body.indexOf("pendingWhatsAppSendDraft = null")
        assertTrue(cancelIdx in 0 until clearIdx, "cancel branch must clear the own draft")
        assertTrue(clearIdx < nullIdx, "clear must run before nulling the pending")
        assertTrue(body.contains("cancelledReassurance()"), "legacy cancel must speak the canonical reassurance")
    }

    // #14 (TOCTOU) — un cancel/STOP durante la ventana async aborta la escritura:
    // ambos composes diferidos capturan la generación y la re-chequean antes de
    // escribir el borrador / armar el pending; y toda cancelación la invalida.
    @Test
    fun deferredComposeAbortsOnCancelDuringWindow() {
        // cualquier cancel/STOP invalida un compose en vuelo (aunque no haya pending).
        assertTrue(
            service.contains("invalidateInFlightWhatsAppCompose()"),
            "cancel/stop must invalidate an in-flight deferred compose"
        )
        val smart = bodyOf("private fun prepareDraftAndAskSend", "// --- V1.7")
        assertTrue(
            smart.indexOf("val composeGen = invalidateInFlightWhatsAppCompose()") in 1 until smart.indexOf("serviceScope.launch"),
            "smart-compose must capture the generation before launching the deferred write"
        )
        assertTrue(
            smart.indexOf("composeGen != whatsAppComposeGeneration.get()") < smart.indexOf("setWhatsAppDraft(message)"),
            "smart-compose must re-check the generation BEFORE writing the draft"
        )
        val rel = bodyOf("private fun openVerifyThenDraft", "private fun handleWhatsAppNotificationQueryCommand")
        assertTrue(
            rel.indexOf("composeGen != whatsAppComposeGeneration.get()") < rel.indexOf("draftWhatsAppMessageAndConfirm"),
            "relationship-compose must re-check the generation BEFORE drafting"
        )
    }

    // #8 — clarifier local: gateado por contexto WhatsApp, deja pasar Q&A, y NO
    // escribe / no draftea / no egresa al LLM. Sólo habla la aclaración.
    @Test
    fun ambiguousMessageClarifierIsLocalAndNonEgressing() {
        val body = bodyOf(
            "private fun handleWhatsAppAmbiguousMessageClarifier",
            "private fun handleWhatsAppCriticalGuardBeforeLlm"
        )
        assertTrue(body.contains("if (!isWhatsAppActiveContext()) return false"),
            "clarifier must only act when WhatsApp is the active context")
        assertTrue(body.contains("looksLikeQuestion(text)) return false"),
            "clear Q&A must be allowed through (B)")
        assertTrue(body.contains("looksLikeAmbiguousMessageContent(text)"),
            "must detect ambiguous message-like content (A)")
        assertTrue(body.contains("WhatsAppForbiddenCommandParser.parse(text) != null) return false"),
            "explicit forbidden actions must keep their own safe route")
        // local-only: never drafts, sends, or egresses to the LLM/backend.
        listOf(
            "setWhatsAppDraft", "draftWhatsAppMessageAndConfirm", "handleFreeConversation",
            "pendingWhatsAppSendDraft =", "tapWhatsAppSend", "ConversationGate"
        ).forEach { forbidden ->
            assertFalse(body.contains(forbidden), "clarifier must not contain: $forbidden")
        }
    }

    // #5/#6/#7/#8 — el clarifier corre DESPUÉS de forbidden/dangerous/trusted-contact
    // y ANTES del compose genérico y del LLM (así no le roba acciones explícitas).
    @Test
    fun ambiguousMessageClarifierRunsAfterTrustedAndBeforeGenericComposeAndLlm() {
        val clarifier = service.indexOf("if (handleWhatsAppAmbiguousMessageClarifier(text)) return")
        assertTrue(clarifier > 0, "clarifier must be wired")
        // Acciones explícitas (forbidden/dangerous/media) y trusted-contact: ANTES.
        listOf(
            "if (handleWhatsAppForbiddenActionCommand(text)) return",
            "if (handleWhatsAppRelationshipCommand(text)) return",
            "if (handleWhatsAppRelationshipComposeCommand(text)) return",
            "if (handleWhatsAppBlindFirstCommand(text)) return",
            "if (handleWhatsAppDangerousActionCommand(text)) return",
            "if (handleWhatsAppMediaCallRefusal(text)) return"
        ).forEach { before ->
            val idx = service.indexOf(before)
            assertTrue(idx in 1 until clarifier, "must run BEFORE clarifier: $before")
        }
        // Compose genérico + guard crítico + LLM: DESPUÉS.
        listOf(
            "if (handleWhatsAppVoiceSendCommand(text)) return",
            "if (handleWhatsAppReplyCommand(text)) return",
            "if (handleSmartCompose(text)) return",
            "if (handleWhatsAppCriticalGuardBeforeLlm(text)) return",
            "if (ConversationGate.isConversational(text)) {"
        ).forEach { after ->
            assertTrue(service.indexOf(after) > clarifier, "must run AFTER clarifier: $after")
        }
    }

    // #4/#5 — negativa específica de videollamada/audio/llamada: local, antes del
    // clarifier, sin UI/draft/LLM.
    @Test
    fun mediaCallRefusalIsLocalAndRunsBeforeClarifier() {
        val media = service.indexOf("if (handleWhatsAppMediaCallRefusal(text)) return")
        val clarifier = service.indexOf("if (handleWhatsAppAmbiguousMessageClarifier(text)) return")
        assertTrue(media in 1 until clarifier, "media/call refusal must run before the clarifier")
        // BUG 1: y ANTES del taskIntent viejo de videollamada (handleTaskAssistCommand
        // → handleVideoCallRequest, que buscaba el botón y armaba pendingVideoCallTap).
        val taskAssist = service.indexOf("if (handleTaskAssistCommand(text)) return")
        assertTrue(taskAssist > 0 && media in 1 until taskAssist,
            "media/call refusal must run BEFORE the old videoCall taskIntent")
        val body = bodyOf(
            "private fun handleWhatsAppMediaCallRefusal",
            "private fun handleWhatsAppAmbiguousMessageClarifier"
        )
        assertTrue(body.contains("WhatsAppMediaCallRefusalPhrases.classify"), "must classify media/call intent")
        assertTrue(body.contains("WhatsAppActionAudit.recordBlocked()"), "must count the block")
        listOf(
            "setWhatsAppDraft", "tapWhatsAppSend", "tapWhatsAppVideoCall",
            "pendingVideoCallTap = true", "handleFreeConversation", "ConversationGate"
        ).forEach { f -> assertFalse(body.contains(f), "media refusal must not contain: $f") }
    }

    // BUG 2 — la lectura de WhatsApp NO se activa fuera de contexto: requiere contexto
    // WhatsApp y, si no, da guía local segura (sin leer, sin LLM).
    @Test
    fun foregroundReadRequiresWhatsAppContext() {
        val body = bodyOf(
            "private suspend fun handleForegroundWhatsAppReadCommand",
            "private suspend fun handleTaskAssistCommand"
        )
        val gateIdx = body.indexOf("if (!isWhatsAppActiveContext())")
        val readIdx = body.indexOf("whatsappRead=foreground")
        assertTrue(gateIdx in 1 until readIdx, "context gate must run BEFORE the foreground read")
        assertTrue(body.contains("whatsappRead=blocked_no_context"), "must log the no-context block")
        assertTrue(body.contains("No estoy en WhatsApp"), "must give safe local guidance, not read")
        // sin lectura ni egress en la rama sin contexto: el guard habla y retorna.
        assertTrue(
            body.indexOf("No estoy en WhatsApp") < body.indexOf("whatsAppMessagesOutcome"),
            "the guidance must be reachable before any WhatsApp read call"
        )
    }

    // #3 — el contexto WhatsApp se reconoce aunque un overlay/actividad propia quede
    // encima: isWhatsAppActiveContext consulta el rastro de foreground del servicio.
    @Test
    fun whatsAppActiveContextConsultsForegroundTracker() {
        val body = bodyOf("private fun isWhatsAppActiveContext", "private fun textNamesWhatsApp")
        assertTrue(
            body.contains("wasWhatsAppForegroundWithin"),
            "context detection must consult the foreground tracker (#3)"
        )
    }

    // "sí" ambiguo NUNCA envía: la rama de confirmación débil sigue cableada.
    @Test
    fun weakYesStillNeverSends() {
        val body = bodyOf("private fun handlePendingWhatsAppSendReply", "private fun handleWhatsAppVoiceSendCommand")
        assertTrue(body.contains("isNeverConfirm(text)"), "weak affirmations must be rejected, not sent")
        assertTrue(body.contains("weak_confirmation_rejected"), "weak-yes must log a rejection, not a send")
    }
}
