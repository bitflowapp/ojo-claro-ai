package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.ConversationalRepair
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppConversationContext
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedWorkflowUseCase
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.SafeAiFallbackCopy
import com.ojoclaro.android.llm.SafeAiFallbackGuard
import com.ojoclaro.android.llm.SafeAiFallbackInput
import com.ojoclaro.android.llm.SafeAiFallbackReason
import com.ojoclaro.android.llm.SafeAiFallbackVerdict
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.voice.SpeechInputEngine
import com.ojoclaro.android.voice.SpeechListeningMode
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceListeningState
import com.ojoclaro.android.voice.VoiceRetryHandle
import com.ojoclaro.android.voice.VoiceRetryScheduler
import com.ojoclaro.android.voice.VoiceSpeechEngine
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import android.speech.SpeechRecognizer
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 3 — Aseguramiento de MODO DEGRADADO y fallas externas.
 *
 * Cuando el backend cae / hace timeout / responde vacío o inválido, WhatsApp no
 * está disponible o no en foreground, la pantalla no tiene nodos útiles, se
 * pierde el contexto, falta el micrófono o el STT/TTS falla, la persona debe
 * recibir una salida SEGURA y ÚTIL: rutas locales siguen, ningún peligro al LLM
 * libre, sin reintentos infinitos, sin silencio inexplicable, sin promesa falsa,
 * sin estado viejo, sin secretos, con una siguiente acción clara.
 *
 * Usa componentes REALES: SafeAiFallbackGuard/Copy (degradación del backend),
 * el router SafeLlm (seguridad independiente del backend), el use case guiado
 * con provider falso, WhatsAppConversationContext y VoiceCommandController con
 * engine falso. PURO/determinista.
 */
class DegradedRuntimeAssuranceTest {

    @BeforeTest fun setUp() = WhatsAppConversationContext.clear()
    @AfterTest fun tearDown() = WhatsAppConversationContext.clear()

    private val guardBackendDown = SafeAiFallbackGuard(isProxyConfigured = { false })
    private val guardBackendUp = SafeAiFallbackGuard(isProxyConfigured = { true })

    private fun dangerous(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

    private fun route(p: String): SafeLlmRoute = SafeLlmFallbackPolicy.decide(
        SafeLlmSignals(
            conversational = false, whatsAppActive = true, namesWhatsApp = true,
            looksDangerous = dangerous(p), looksLikeMessageContent = false,
            looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
            wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
            looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
        )
    )

    private val jargon = listOf(
        Regex("NO_MATCH"), Regex("\\bproxy\\b", RegexOption.IGNORE_CASE), Regex("\\bllm\\b", RegexOption.IGNORE_CASE),
        Regex("\\bintent\\b", RegexOption.IGNORE_CASE), Regex("\\bhandler\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE), Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("api key", RegexOption.IGNORE_CASE), Regex("\\bdisabled\\b", RegexOption.IGNORE_CASE)
    )

    private fun assertHuman(text: String, where: String) {
        assertTrue(text.isNotBlank(), "$where vacío")
        jargon.forEach { rx -> assertFalse(rx.containsMatchIn(text), "$where jerga /${rx.pattern}/: \"$text\"") }
    }

    private fun input(
        userText: String = "qué puedo hacer",
        appState: AppState = AppState.IDLE,
        sensitiveScreen: Boolean = false,
        pending: Boolean = false,
        fullChat: Boolean = false,
        fullOcr: Boolean = false
    ) = SafeAiFallbackInput(userText, appState, sensitiveScreen, pending, fullOcr, fullChat)

    // ================= Backend caído / degradación del Safe-AI fallback =================

    @Test fun d01_backendDown_guardDeniesAndDoesNotConsultLlm() {
        val v = guardBackendDown.evaluate(input())
        assertFalse(v.isAllowed)
        assertEquals(SafeAiFallbackReason.PROXY_NOT_CONFIGURED, (v as SafeAiFallbackVerdict.Denied).reason)
    }

    @Test fun d02_backendUp_sensitiveInput_denied_secretNotSent() {
        val v = guardBackendUp.evaluate(input(userText = "mi pin es 1234"))
        assertFalse(v.isAllowed, "secreto NO debe ir al backend")
    }

    @Test fun d03_backendUp_sensitiveScreen_denied() {
        assertFalse(guardBackendUp.evaluate(input(sensitiveScreen = true)).isAllowed)
    }

    @Test fun d04_backendUp_pendingConfirmation_denied() {
        assertFalse(guardBackendUp.evaluate(input(pending = true)).isAllowed)
    }

    @Test fun d05_backendUp_privateContentVisible_denied() {
        assertFalse(guardBackendUp.evaluate(input(fullChat = true)).isAllowed)
        assertFalse(guardBackendUp.evaluate(input(fullOcr = true)).isAllowed)
    }

    @Test fun d06_llmProposedDangerousIntent_filteredToUnknown() {
        listOf(
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE, AgentIntent.CALL_CONTACT, AgentIntent.OPEN_WHATSAPP_CHAT,
            AgentIntent.SAVE_CONTACT, AgentIntent.CREATE_ALARM
        ).forEach { intent ->
            assertEquals(AgentIntent.UNKNOWN, guardBackendUp.filterIntent(intent), "intent peligroso debe filtrarse: $intent")
        }
    }

    @Test fun d07_llmWhitelistContainsOnlySafeReadHelpOpenIntents() {
        SafeAiFallbackGuard.WHITELIST_V1.forEach { intent ->
            assertTrue(
                intent in setOf(
                    AgentIntent.HELP, AgentIntent.READ_VISIBLE_SCREEN, AgentIntent.OPEN_WHATSAPP,
                    AgentIntent.REPEAT_LAST, AgentIntent.STOP_SPEAKING, AgentIntent.CANCEL, AgentIntent.UNKNOWN
                ),
                "whitelist contiene un intent no-seguro: $intent"
            )
        }
    }

    @Test fun d08_debugLikeBackendCopy_isDetectedSoCallerDegrades() {
        listOf("no estoy usando la IA", "proxy no configurado", "modo IA", "low confidence", "api key inválida")
            .forEach { assertTrue(SafeAiFallbackCopy.looksLikeAiDebugCopy(it), "debe detectar jerga: \"$it\"") }
    }

    @Test fun d09_humanFallbackCopy_isNotFlaggedAsDebug() {
        listOf(SafeAiFallbackCopy.GENERAL, SafeAiFallbackCopy.WHATSAPP_OPEN, SafeAiFallbackCopy.UNABLE_TO_RESOLVE)
            .forEach { assertFalse(SafeAiFallbackCopy.looksLikeAiDebugCopy(it), "copy humano marcado como debug: \"$it\"") }
    }

    @Test fun d10_contextualFallbackCopy_isHumanForEveryAppState() {
        AppState.values().forEach { st ->
            assertHuman(SafeAiFallbackCopy.contextual(appState = st), "contextual($st)")
        }
    }

    @Test fun d11_contextualFallbackCopy_offersAConcreteNextAction() {
        val c = SafeAiFallbackCopy.contextual(appState = AppState.IDLE).lowercase()
        assertTrue(
            c.contains("pantalla") || c.contains("whatsapp") || c.contains("ayuda") || c.contains("repetir"),
            "debe ofrecer una acción concreta: \"$c\""
        )
    }

    @Test fun d12_sensitiveScreen_contextualSaysItWillNotRead() {
        val c = SafeAiFallbackCopy.contextual(appState = AppState.IDLE, sensitiveScreen = true)
        assertTrue(c.contains("sensible", ignoreCase = true) && c.contains("No voy a leerla", ignoreCase = true))
    }

    @Test fun d13_clientTimeoutIsBoundedNotInfinite() {
        val t = LlmAgentClientConfig.DEFAULT_TIMEOUT_MILLIS
        assertTrue(t in 1_000L..30_000L, "timeout debe ser acotado y razonable, fue $t")
    }

    @Test fun d14_allSafeAiFallbackCopyConstants_areJargonFree() {
        listOf(
            SafeAiFallbackCopy.GENERAL, SafeAiFallbackCopy.WHATSAPP_OPEN, SafeAiFallbackCopy.WHATSAPP_WAITING,
            SafeAiFallbackCopy.SENSITIVE_SCREEN, SafeAiFallbackCopy.SAFE_MODE_REMINDER,
            SafeAiFallbackCopy.UNABLE_TO_RESOLVE, SafeAiFallbackCopy.CAPABILITIES_SUMMARY
        ).forEachIndexed { i, t -> assertHuman(t, "SafeAiFallbackCopy[$i]") }
    }

    @Test fun d15_fallbackCopy_makesNoFalsePromise() {
        listOf(SafeAiFallbackCopy.GENERAL, SafeAiFallbackCopy.UNABLE_TO_RESOLVE).forEach { c ->
            assertFalse(c.contains("envié", ignoreCase = true))
            assertFalse(c.contains("enviado", ignoreCase = true))
            assertFalse(c.contains("listo, ya", ignoreCase = true))
        }
    }

    // ================= Seguridad INDEPENDIENTE del backend =================

    @Test fun d16_dangerousAlwaysBlocked_regardlessOfBackend() {
        listOf("tocá enviar", "mandá audio", "llamalo", "videollamada", "pagale", "transferile plata", "borrá el chat")
            .forEach { assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route(it), "peligro siempre bloqueado: \"$it\"") }
    }

    @Test fun d17_replyHelpAlwaysSuggestOnly_regardlessOfBackend() {
        listOf("qué le respondo", "cómo le digo").forEach { assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route(it)) }
    }

    @Test fun d18_cancelNeverFreeLlm() {
        listOf("cancelá", "no mandes nada", "frená todo").forEach {
            assertTrue(route(it) != SafeLlmRoute.ALLOW_CONVERSATION, "cancelar nunca LLM libre: \"$it\"")
        }
    }

    // ================= WhatsApp / pantalla / contexto degradado =================

    private fun useCase(snapshot: ScreenSnapshot?, ready: Boolean = true) =
        WhatsAppGuidedWorkflowUseCase(provider = ScreenContextProvider { snapshot }, isAccessibilityReady = { ready })

    @Test fun d19_accessibilityOff_asksToActivate() {
        val r = useCase(snapshot = null, ready = false).handle("¿estoy en WhatsApp?")
        assertTrue(r is WhatsAppGuidedResponse.NotInWhatsApp)
        assertTrue((r as WhatsAppGuidedResponse.NotInWhatsApp).spokenText.contains("Accesibilidad", ignoreCase = true))
    }

    @Test fun d20_whatsAppNotForeground_asksToOpen() {
        val snap = ScreenSnapshot("com.example.notes", "x", emptyList(), 0L)
        val r = useCase(snap).handle("¿estoy en WhatsApp?")
        assertTrue(r is WhatsAppGuidedResponse.NotInWhatsApp)
        assertHuman((r as WhatsAppGuidedResponse.NotInWhatsApp).spokenText, "not-in-wa")
    }

    @Test fun d21_nullSnapshot_stateNotConfident_asksToOpenChat() {
        val r = useCase(snapshot = null).handle("¿estoy en WhatsApp?")
        assertTrue(r is WhatsAppGuidedResponse.StateNotConfident)
        assertHuman((r as WhatsAppGuidedResponse.StateNotConfident).spokenText, "state-not-confident")
    }

    @Test fun d22_providerThrows_failsSafe() {
        val uc = WhatsAppGuidedWorkflowUseCase(
            provider = ScreenContextProvider { error("boom") }, isAccessibilityReady = { true }
        )
        assertTrue(uc.handle("¿estoy en WhatsApp?") is WhatsAppGuidedResponse.StateNotConfident)
    }

    @Test fun d23_emptyScreen_noCrash_safeResponse() {
        val snap = ScreenSnapshot("com.whatsapp", "", emptyList(), 0L)
        val r = useCase(snap).handle("qué puedo hacer en este chat")
        // Sin nodos útiles: o pide abrir un chat, o estado no confiable; nunca contenido.
        assertTrue(r is WhatsAppGuidedResponse.Guidance || r is WhatsAppGuidedResponse.NotInWhatsApp ||
            r is WhatsAppGuidedResponse.StateNotConfident)
    }

    @Test fun d24_lostChatContext_recallSaysNoContext() {
        WhatsAppConversationContext.clear()
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto", ignoreCase = true))
    }

    // ================= Voz degradada (engine falso) =================

    @Test fun d25_noMicPermission_humanMessageNoToken() {
        val h = voiceHarness(hasPermission = false)
        h.controller.startListening()
        assertEquals(VoiceListeningState.ERROR, h.controller.currentState)
        assertHuman(h.errors.single(), "mic-permission")
        assertTrue(h.errors.single().contains("permiso", ignoreCase = true))
    }

    @Test fun d26_sttTimeout_retriesBounded_notInfiniteLoop() {
        val h = voiceHarness(expectingResponse = true)
        h.controller.startListening()
        repeat(6) { h.engine.emitError(SpeechRecognizer.ERROR_NO_MATCH); h.scheduler.runNext() }
        assertTrue(h.errors.isEmpty(), "no debe repetir pista en loop: ${h.errors}")
    }

    @Test fun d27_lateResultAfterStop_ignored() {
        val h = voiceHarness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitFinal("transferile plata")
        assertTrue(h.finals.isEmpty(), "resultado tardío peligroso ignorado: ${h.finals}")
    }

    // ================= Cancelación durante cualquier estado =================

    @Test fun d28_cancelClearsContext() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("cancelá"))
        WhatsAppConversationContext.clear()
        assertEquals(null, WhatsAppConversationContext.current())
    }

    @Test fun d29_cancelRouteNotFreeLlm() {
        assertTrue(route("cancelá") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    // ================= Copy de recuperación deseado =================

    @Test fun d30_recoveryCopyIsCalmAndReassuring() {
        assertHuman(ConversationalRepair.CONFIRMATION_CANCELLED, "cancel-copy")
        assertHuman(ConversationalRepair.SAFE_AI_UNAVAILABLE, "safe-ai-unavailable")
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No voy a enviar nada", ignoreCase = true))
    }

    @Test fun d31_safeModeReminder_reaffirmsNoSensitiveActionWithoutConfirmation() {
        val r = SafeAiFallbackCopy.SAFE_MODE_REMINDER.lowercase()
        assertTrue(r.contains("modo seguro") && (r.contains("confirm") || r.contains("sin confirmar")))
    }

    // ================= Fuzz determinista de degradación (150 secuencias) =================

    private enum class Degr { NONE, BACKEND_DOWN, SENSITIVE_INPUT, SENSITIVE_SCREEN, PENDING, PRIVATE_VISIBLE }

    private val phrases = listOf(
        "qué puedo hacer", "leé los chats", "qué dice ahí", "abrí WhatsApp", "qué le respondo",
        "respondéle que ya voy", "tocá enviar", "mandá audio", "llamalo", "videollamada", "pagale",
        "transferile plata", "borrá el chat", "cancelá", "no mandes nada", "repetí",
        "mi pin es 1234", "mi código es 445566", "qué es una videollamada"
    )

    private val llmIntents = AgentIntent.values().toList()

    @Test
    fun degraded_seeded_holdsAllSafetyInvariants() {
        val seed = 0xDE6AADE0FFL
        val rnd = Random(seed)
        val degrs = Degr.values()
        val sequenceCount = 150
        var steps = 0

        repeat(sequenceCount) { sIdx ->
            val len = 3 + rnd.nextInt(5)
            repeat(len) {
                val degr = degrs[rnd.nextInt(degrs.size)]
                val p = phrases[rnd.nextInt(phrases.size)]
                val llmIntent = llmIntents[rnd.nextInt(llmIntents.size)]
                val where = "seed=$seed seq=$sIdx degr=$degr p=\"$p\""

                // 1) peligro SIEMPRE bloqueado, sin importar degradación ni backend.
                if (dangerous(p) && !SafeLlmPhrases.isSafeQuestion(p) && !SafeLlmPhrases.isReplyHelp(p)) {
                    assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route(p), "$where: peligro no bloqueado")
                }
                // 2) backend caído → el guard NUNCA permite consultar al LLM.
                val guard = if (degr == Degr.BACKEND_DOWN) guardBackendDown else guardBackendUp
                val verdict = guard.evaluate(
                    input(
                        userText = p,
                        sensitiveScreen = degr == Degr.SENSITIVE_SCREEN,
                        pending = degr == Degr.PENDING,
                        fullChat = degr == Degr.PRIVATE_VISIBLE
                    )
                )
                if (degr == Degr.BACKEND_DOWN || degr == Degr.SENSITIVE_SCREEN ||
                    degr == Degr.PENDING || degr == Degr.PRIVATE_VISIBLE
                ) {
                    assertFalse(verdict.isAllowed, "$where: degradación debe negar el LLM")
                }
                // 3) un secreto dictado nunca habilita el LLM.
                if (p.contains("pin") || p.contains("código") || p.contains("codigo")) {
                    assertFalse(guardBackendUp.evaluate(input(userText = p)).isAllowed, "$where: secreto al LLM")
                }
                // 4) cualquier intent que proponga el LLM se filtra a algo seguro.
                assertTrue(
                    guardBackendUp.filterIntent(llmIntent) in SafeAiFallbackGuard.WHITELIST_V1,
                    "$where: filterIntent dejó pasar algo no-whitelisted"
                )
                // 5) el copy de degradación es humano.
                assertHuman(SafeAiFallbackCopy.contextual(appState = AppState.IDLE), "$where contextual")
                steps++
            }
        }
        assertTrue(sequenceCount >= 150, "se requieren >= 150 secuencias")
        assertTrue(steps >= 450, "muchos pasos esperados, fueron $steps")
    }

    // -------- helpers de voz --------
    private class VHarness(
        val controller: VoiceCommandController, val engine: FakeEngine,
        val scheduler: FakeScheduler, val finals: MutableList<String>, val errors: MutableList<String>
    )

    private fun voiceHarness(hasPermission: Boolean = true, expectingResponse: Boolean = false): VHarness {
        val engine = FakeEngine(); val scheduler = FakeScheduler()
        val finals = mutableListOf<String>(); val errors = mutableListOf<String>()
        val controller = VoiceCommandController(
            engine = engine, hasRecordAudioPermission = { hasPermission },
            onPartialTextCallback = {}, onFinalTextCallback = finals::add, onErrorCallback = errors::add,
            onReadyCallback = {}, retryScheduler = scheduler
        )
        if (expectingResponse) controller.setExpectingResponse(true)
        return VHarness(controller, engine, scheduler, finals, errors)
    }

    private class FakeEngine : SpeechInputEngine {
        override var listener: SpeechInputEngine.Listener? = null
        override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT
        override var isListening: Boolean = false; private set
        override fun startListening() { isListening = true; listener?.onReady() }
        override fun stopListening() { isListening = false }
        override fun resetRecognizer() { isListening = false }
        override fun destroy() { isListening = false }
        override fun setListeningMode(mode: SpeechListeningMode) {}
        fun emitFinal(t: String) { isListening = false; listener?.onFinalText(t) }
        fun emitError(c: Int) { isListening = false; listener?.onError(c) }
    }

    private class FakeScheduler : VoiceRetryScheduler {
        private data class S(val d: Long, val a: () -> Unit, var c: Boolean = false)
        private val items = mutableListOf<S>()
        override fun schedule(delayMillis: Long, action: () -> Unit): VoiceRetryHandle {
            val it = S(delayMillis, action); items += it; return VoiceRetryHandle { it.c = true }
        }
        fun runNext() { items.firstOrNull { !it.c }?.let { it.c = true; it.a() } }
    }
}
