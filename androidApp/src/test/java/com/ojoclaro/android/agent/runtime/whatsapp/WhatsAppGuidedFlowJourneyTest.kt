package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.EstelaCompanionPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MISIÓN — Flujo WhatsApp GUIADO para una persona no vidente. Estela LEE lo
 * visible, ORIENTA cuando no puede abrir un chat sola, AYUDA a redactar SIN
 * enviar, CANCELA limpio y JAMÁS actúa por inferencia.
 *
 * Principio de interacción guiada:
 *   A. puede leer lo visible/no sensible;
 *   B. puede orientar ("abrí el chat y te lo leo", "decime cuál");
 *   C. puede ayudar a redactar (sugerir, preparar en flujo seguro, sin enviar);
 *   D. NUNCA elige chat ambiguo, toca enviar, llama, manda audio, video, infiere
 *      destinatario, ni convierte una pregunta-concepto en acción.
 *
 * PURO/determinista (JVM): clasificadores reales + router SafeLlm + el use case
 * guiado con un ScreenContextProvider falso. Sin Android, sin red, sin envío.
 */
class WhatsAppGuidedFlowJourneyTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.clear()
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
        WhatsAppConversationContext.clear()
    }

    // ---------------- routing seguro (peor caso: WhatsApp activo) ----------------
    private fun dangerous(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

    private fun cancelDetected(p: String): Boolean =
        VoiceCommandDispatcher.isBareCancelCommand(p) ||
            VoiceCommandDispatcher.isStopCommand(p) ||
            WhatsAppVoiceSendPhrases.isCancelSend(p) ||
            WhatsAppReplyPhrases.isCancel(p)

    private fun route(p: String): SafeLlmRoute = SafeLlmFallbackPolicy.decide(
        SafeLlmSignals(
            conversational = false,
            whatsAppActive = true,
            namesWhatsApp = true,
            looksDangerous = dangerous(p),
            looksLikeMessageContent = false,
            looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
            wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
            looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
        )
    )

    // ---------------- screen fixtures + guided use case --------------------------
    private fun useCase(snapshot: ScreenSnapshot?, isReady: Boolean = true) =
        WhatsAppGuidedWorkflowUseCase(
            provider = ScreenContextProvider { snapshot },
            isAccessibilityReady = { isReady }
        )

    private fun chatOpenSnapshot() = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
        ),
        capturedAtMillis = 0L
    )

    private fun chatListSnapshot() = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Ana Prueba", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("José Demo", ScreenElementRole.BUTTON, isInteractive = true)
        ),
        capturedAtMillis = 0L
    )

    private fun notWhatsAppSnapshot() = ScreenSnapshot(
        packageName = "com.example.notes",
        text = "Lista de tareas",
        elements = emptyList(),
        capturedAtMillis = 0L
    )

    private val jargon = listOf(
        Regex("NO_MATCH"), Regex("\\bblocked\\b", RegexOption.IGNORE_CASE),
        Regex("\\bintent\\b", RegexOption.IGNORE_CASE), Regex("\\bhandler\\b", RegexOption.IGNORE_CASE),
        Regex("not[_ ]?found", RegexOption.IGNORE_CASE), Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE), Regex("AccessibilityNodeInfo"),
        Regex("com\\.whatsapp"), Regex("critical guard", RegexOption.IGNORE_CASE)
    )

    private fun assertHuman(text: String, where: String) {
        assertTrue(text.isNotBlank(), "$where: copy vacío")
        jargon.forEach { rx -> assertFalse(rx.containsMatchIn(text), "$where: jerga /${rx.pattern}/ en \"$text\"") }
    }

    // ============================================================
    // 1-9 — abrir, capacidades, leer chats, buscar, ordinal
    // ============================================================

    @Test fun c01_openWhatsApp_recognizedNotDangerous() {
        listOf("abrí WhatsApp", "abrí guasap", "abrí wasat", "abrí el wsp").forEach { p ->
            assertTrue(WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"), "open: \"$p\"")
            assertFalse(dangerous(p), "abrir no es peligroso: \"$p\"")
        }
    }

    @Test fun c02_whatCanIDoWithWhatsApp_listsCapabilitiesWithLimit() {
        val answer = EstelaCompanionPhrases.respond("qué puedo hacer con WhatsApp")
        assertTrue(answer != null, "debe responder capacidades")
        assertTrue(answer!!.contains("confirma", ignoreCase = true), "menciona límite (confirmación): \"$answer\"")
        assertHuman(answer, "capabilities")
    }

    @Test fun c03_readChats_withListVisible_isAReadCommandNotDangerous() {
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("leé los chats"), "es comando de lista")
        assertFalse(dangerous("leé los chats"))
    }

    @Test fun c04_readChats_withoutForeground_guidedUseCaseAsksToOpen() {
        val r = useCase(notWhatsAppSnapshot()).handle("¿estoy en WhatsApp?")
        assertTrue(r is WhatsAppGuidedResponse.NotInWhatsApp, "debe pedir abrir WhatsApp")
        assertHuman((r as WhatsAppGuidedResponse.NotInWhatsApp).spokenText, "not-in-wa")
    }

    @Test fun c05_searchContact_withChatVisible_isNotDangerousNoSend() {
        listOf("buscá a Ana Prueba", "buscá a José Demo en guasap").forEach { p ->
            assertFalse(dangerous(p), "buscar no es peligroso: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.BLOCK_DANGEROUS, "buscar no se bloquea: \"$p\"")
        }
    }

    @Test fun c06_searchContact_withoutVisibleChat_matcherFindsNothingToGuideManualOpen() {
        val matcher = WhatsAppVisibleChatMatcher()
        val best = matcher.findBest("Luz Test", chatOpenSnapshot().elements)
        assertNull(best, "sin el chat visible no debe inventar un match (orienta a abrir manual)")
    }

    @Test fun c07_openFirstChat_clearList_ordinalParsedForSafeRequest() {
        // El parser es 0-based: "primer" -> 0, "segundo" -> 1.
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el primer chat"))
        assertEquals(1, WhatsAppOrdinalChatParser.parse("abrí el segundo chat"))
    }

    @Test fun c08_openAChat_ambiguous_noOrdinalNoName_doesNotGuess() {
        // Pedido vago sin ordinal ni nombre: no hay índice → no adivina un chat.
        assertNull(WhatsAppOrdinalChatParser.parse("abrí cualquier chat"))
        assertNull(WhatsAppOrdinalChatParser.parse("abrí algún chat"))
        assertFalse(dangerous("abrí cualquier chat"))
    }

    @Test fun c09_openContactChat_screenChanges_matcherUsesCurrentElementsOnly() {
        // El matcher opera sobre los elementos PASADOS (pantalla actual), nunca
        // sobre una lista vieja: si la pantalla cambió, no encuentra el viejo.
        val matcher = WhatsAppVisibleChatMatcher()
        val onList = matcher.findBest("Ana Prueba", chatListSnapshot().elements)
        assertTrue(onList != null, "con la lista visible la encuentra")
        val afterChange = matcher.findBest("Ana Prueba", chatOpenSnapshot().elements)
        assertNull(afterChange, "tras cambiar la pantalla no reutiliza el chat viejo")
    }

    // ============================================================
    // 10-16 — leer mensajes, reply-help, redacción
    // ============================================================

    @Test fun c10_queDiceAhi_withChat_safeReadNotDangerous() {
        assertFalse(dangerous("qué dice ahí"))
        assertTrue(route("qué dice ahí") != SafeLlmRoute.BLOCK_DANGEROUS)
    }

    @Test fun c11_queDiceAhi_noChat_routesSafeNotFreeLlm() {
        val r = route("qué dice ahí")
        assertTrue(r != SafeLlmRoute.ALLOW_CONVERSATION && r != SafeLlmRoute.BLOCK_DANGEROUS, "ruta segura: $r")
    }

    @Test fun c12_readMessages_withChat_isReadCommandNotDangerous() {
        assertTrue(WhatsAppMessageReadPhrases.classify("leé los mensajes") != null, "es lectura")
        assertFalse(dangerous("leé los mensajes"))
    }

    @Test fun c13_readMessages_doesNotLeakChatContentViaGuidance() {
        val snap = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Ana Prueba: te paso el código 445566",
            elements = listOf(
                ScreenElement("Ana Prueba: te paso el código 445566", ScreenElementRole.TEXT, isInteractive = false),
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val r = useCase(snap).handle("qué puedo hacer en este chat")
        val text = (r as WhatsAppGuidedResponse.Guidance).spokenText
        assertFalse(text.contains("445566"), "no debe filtrar contenido del chat: $text")
        assertFalse(text.contains("código", ignoreCase = true), "no debe quotear el chat: $text")
    }

    @Test fun c14_queLeRespondo_withChat_suggestOnlyNeverSends() {
        assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route("qué le respondo"))
    }

    @Test fun c15_queLeRespondo_noChat_stillSuggestOnlyNotSend() {
        // La ayuda de redacción nunca envía; pide/sugiere, no ejecuta.
        listOf("qué le escribo", "cómo le digo").forEach { p ->
            assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route(p), "suggest-only: \"$p\"")
        }
    }

    @Test fun c16_comoLeDigoQueEstoyOcupado_ideasNotAutoDraft() {
        assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route("cómo le digo que estoy ocupado"))
    }

    // ============================================================
    // 17-21 — borrador + cancelaciones
    // ============================================================

    @Test fun c17_respondeleQueYaVoy_withChat_replyAttemptNeverFreeLlm() {
        val p = "respondéle que ya voy"
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p))
        assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "nunca charla libre")
    }

    @Test fun c18_respondeleQueYaVoy_noChat_failsSafeNoSend() {
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("respondéle que ya voy"))
    }

    @Test fun c19_draftThenNoMandesNada_pendingDraftClean() {
        // El borrador queda en contexto; la cancelación real lo limpia.
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt("respondéle que ya voy"))
        WhatsAppConversationContext.noteDraftLen(10, 1L)
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        assertTrue(cancelDetected("no, no mandes nada"))
        WhatsAppConversationContext.clear() // cancelación limpia el contexto
        assertNull(WhatsAppConversationContext.current(), "no debe quedar draft/pending")
    }

    @Test fun c20_draftThenCancela_humanCancelConfirmation() {
        assertTrue(cancelDetected("cancelá"))
        assertHuman(com.ojoclaro.android.agent.runtime.conversation.ConversationalRepair.CONFIRMATION_CANCELLED, "cancel-copy")
    }

    @Test fun c21_draftThenMeArrepenti_noResidualText() {
        assertTrue(cancelDetected("me arrepentí"))
        assertTrue(route("me arrepentí") != SafeLlmRoute.ALLOW_CONVERSATION, "cancelar no es LLM libre")
    }

    // ============================================================
    // 22-31 — bloqueos de acciones peligrosas
    // ============================================================

    @Test fun c22_tocaEnviar_calmRefusal() {
        assertTrue(dangerous("tocá enviar"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("tocá enviar"))
    }

    @Test fun c23_tocaElAvioncito_localBlockNoFreeLlm() {
        assertTrue(dangerous("tocá el avioncito"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("tocá el avioncito"))
    }

    @Test fun c24_tocaElBotonVerde_noAmbiguousTap() {
        assertTrue(dangerous("tocá el botón verde"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("tocá el botón verde"))
    }

    @Test fun c25_mandaAudio_localBlock() {
        assertTrue(dangerous("mandá audio"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("mandá audio"))
    }

    @Test fun c26_llamalo_localBlock() {
        assertTrue(dangerous("llamalo")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("llamalo"))
    }

    @Test fun c27_videollamada_localBlock() {
        assertTrue(dangerous("videollamada")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("videollamada"))
    }

    @Test fun c28_pagale_localBlock() {
        assertTrue(dangerous("pagale")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("pagale"))
    }

    @Test fun c29_transferilePlata_localBlock() {
        assertTrue(dangerous("transferile plata")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("transferile plata"))
    }

    @Test fun c30_borraElChat_localBlock() {
        assertTrue(dangerous("borrá el chat")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("borrá el chat"))
    }

    @Test fun c31_bloquealo_localBlock() {
        assertTrue(dangerous("bloquealo")); assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("bloquealo"))
    }

    // ============================================================
    // 32-38 — conceptual + cancelaciones varias
    // ============================================================

    @Test fun c32_queEsUnaVideollamada_conceptualNoAction() {
        assertTrue(SafeLlmPhrases.isSafeQuestion("qué es una videollamada"))
        assertEquals(SafeLlmRoute.ALLOW_CONVERSATION, route("qué es una videollamada"))
    }

    @Test fun c33_comoSeBloqueaAAlguien_conceptualNoAction() {
        assertTrue(SafeLlmPhrases.isSafeQuestion("cómo se bloquea a alguien"))
        assertEquals(SafeLlmRoute.ALLOW_CONVERSATION, route("cómo se bloquea a alguien"))
    }

    @Test fun c34_cancelaAfterRead_cleanState() {
        WhatsAppConversationContext.noteMessageCount(3, 1L)
        assertTrue(cancelDetected("cancelá"))
        WhatsAppConversationContext.clear()
        assertNull(WhatsAppConversationContext.current())
    }

    @Test fun c35_cancelaAfterDraftRequest_cleanState() {
        WhatsAppConversationContext.noteDraftLen(8, 1L)
        assertTrue(cancelDetected("cancelá"))
        WhatsAppConversationContext.clear()
        assertNull(WhatsAppConversationContext.current())
    }

    @Test fun c36_paraTodo_clearsStateEvenIfCopySilent() {
        assertTrue(cancelDetected("pará todo"))
        assertTrue(route("pará todo") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    @Test fun c37_aborta_confirmAndClean() {
        assertTrue(cancelDetected("abortá"))
        assertTrue(route("abortá") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    @Test fun c38_frenaTodo_confirmAndClean() {
        assertTrue(cancelDetected("frená todo"))
        assertTrue(route("frená todo") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    // ============================================================
    // 39-45 — contexto viejo + privacidad
    // ============================================================

    @Test fun c39_foregroundThenHome_doesNotUseOldContext() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.clear() // volver al home descarta contexto
        assertNull(WhatsAppConversationContext.current())
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto", ignoreCase = true))
    }

    @Test fun c40_switchChat_doesNotKeepPreviousDestinatario() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.noteDestination("José Demo", null, 2L) // cambió de chat
        assertEquals("José Demo", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    @Test fun c41_cancel_thenLateReadCallback_hasNoOldContextToSpeak() {
        // Tras cancelar, el contexto queda vacío: una lectura tardía no tiene
        // contenido viejo que hablar (spokenRecall no recuerda nada).
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.noteMessageCount(2, 1L)
        assertTrue(cancelDetected("cancelá"))
        WhatsAppConversationContext.clear()
        assertNull(WhatsAppConversationContext.current())
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto", ignoreCase = true))
    }

    @Test fun c42_draftRequestThenCancel_lateDraftHasNoPendingToAnnounce() {
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        WhatsAppConversationContext.noteDraftLen(7, 1L)
        assertTrue(cancelDetected("no mandes nada"))
        WhatsAppConversationContext.clear()
        assertNull(WhatsAppConversationContext.current()?.pendingAction)
    }

    @Test fun c43_miPinThenQueLeRespondo_doesNotLeakPin() {
        val sanitized = LlmInputSanitizer.sanitize("mi pin es 1234")
        assertFalse(sanitized.contains("1234"), "PIN redactado antes del LLM: $sanitized")
        assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route("qué le respondo"))
    }

    @Test fun c44_syntheticNumberOnScreen_isRedactedInContext() {
        WhatsAppConversationContext.noteDestination("Ana 2991234567", "2991234567", 1L)
        val snap = WhatsAppConversationContext.current()!!
        assertFalse(snap.chatLabelRedacted!!.contains("2991234567"), "número crudo en label: ${snap.chatLabelRedacted}")
        assertEquals("4567", snap.phoneEnding, "solo los últimos 4 dígitos")
        assertFalse(WhatsAppConversationContext.spokenRecall().contains("2991234567"))
    }

    @Test fun c45_repetiAfterSyntheticSecret_doesNotRepeatSecret() {
        // El recall hablado nunca incluye un secreto crudo: solo metadatos seguros.
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.noteDraftLen(12, 2L)
        val recall = WhatsAppConversationContext.spokenRecall()
        assertFalse(recall.contains("1234"))
        assertFalse(recall.contains("445566"))
        assertTrue(recall.contains("No voy a enviar nada", ignoreCase = true), "recall reafirma que no envía")
    }
}
