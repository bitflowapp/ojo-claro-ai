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

    private fun bodyOf(afterMarker: String, untilMarker: String): String =
        service.substringAfter(afterMarker).substringBefore(untilMarker)

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

    // "sí" ambiguo NUNCA envía: la rama de confirmación débil sigue cableada.
    @Test
    fun weakYesStillNeverSends() {
        val body = bodyOf("private fun handlePendingWhatsAppSendReply", "private fun handleWhatsAppVoiceSendCommand")
        assertTrue(body.contains("isNeverConfirm(text)"), "weak affirmations must be rejected, not sent")
        assertTrue(body.contains("weak_confirmation_rejected"), "weak-yes must log a rejection, not a send")
    }
}
