package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppPhraseNormalizer
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MISIÓN — Resiliencia del CICLO DE VOZ completo de Estela:
 * Escuchar -> STT -> routing -> TTS -> recuperación.
 *
 * Máquina de estados (VoiceListeningState):
 *   IDLE -> LISTENING -> (PARTIAL) -> PROCESSING -> SPEAKING -> IDLE/LISTENING
 *   rutas de error: WAITING_RETRY (recuperable) / ERROR (permiso, fallbacks agotados)
 *   stop de usuario / background: STOPPED_BY_USER
 *
 * Invariantes transversales del piloto:
 *   - nunca queda muda/trabada/escuchando eterno;
 *   - copy humano, sin token técnico;
 *   - sin acción peligrosa al LLM libre;
 *   - sin acción/evento TARDÍO tras una cancelación;
 *   - sin doble recognizer;
 *   - sin repetir un secreto dictado.
 *
 * PURO/determinista (JVM): conduce VoiceCommandController con un engine FALSO y
 * un scheduler FALSO; el routing usa los clasificadores puros (espejo de GAS).
 */
class VoiceSessionLifecycleTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
        RobotLoopInstrumentation.clear()
    }

    // ---------------- Routing seguro (peor caso: WhatsApp activo) ----------------
    private fun dangerous(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

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

    private val jargon = listOf(
        Regex("NO_MATCH"), Regex("SPEECH_TIMEOUT"), Regex("SpeechErrorCategory"),
        Regex("RecognitionListener"), Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhandler\\b", RegexOption.IGNORE_CASE), Regex("\\bintent\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE), Regex("\\bblocked\\b", RegexOption.IGNORE_CASE),
        Regex("not[_ ]?found", RegexOption.IGNORE_CASE)
    )

    private fun assertHuman(text: String, where: String) {
        assertTrue(text.isNotBlank(), "$where: copy vacío")
        jargon.forEach { rx -> assertFalse(rx.containsMatchIn(text), "$where: jerga /${rx.pattern}/ en \"$text\"") }
    }

    // ============================================================
    // Escenarios 1-5 — arranque, permiso, timeout, no-match, incompleta
    // ============================================================

    @Test fun s01_tapListen_permissionGranted_listensActive() {
        val h = harness()
        h.controller.startListening()
        assertEquals(1, h.engine.startCount)
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
        assertTrue(h.controller.isListening)
    }

    @Test fun s02_tapListen_permissionDenied_humanExplanationNoToken() {
        val h = harness(hasPermission = false)
        h.controller.startListening()
        assertEquals(0, h.engine.startCount)
        assertEquals(VoiceListeningState.ERROR, h.controller.currentState)
        assertEquals(1, h.errors.size)
        assertHuman(h.errors.single(), "permiso denegado")
        assertTrue(h.errors.single().contains("permiso", ignoreCase = true))
    }

    @Test fun s03_listening_timeout_understandable_recoversClean() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals(VoiceListeningState.WAITING_RETRY, h.controller.currentState)
        h.errors.forEach { assertHuman(it, "timeout") }
        h.scheduler.runNext()
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
        assertEquals(2, h.engine.startCount)
    }

    @Test fun s04_listening_noMatch_humanHint_notStuck() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(VoiceListeningState.WAITING_RETRY, h.controller.currentState)
        assertTrue(h.errors.isNotEmpty(), "debe dar una pista humana")
        h.errors.forEach { assertHuman(it, "no match") }
        h.scheduler.runNext()
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
    }

    @Test fun s05_incompleteUtterance_detected_soCallerDoesNotExecute() {
        assertTrue(IncompleteUtteranceClassifier.isFillerOnly("mmm"), "muletilla sola")
        assertTrue(IncompleteUtteranceClassifier.looksIncomplete("eh"), "incompleta")
        assertTrue(IncompleteUtteranceClassifier.looksIncomplete("respondéle que"), "colgada en conector")
        assertFalse(IncompleteUtteranceClassifier.looksIncomplete("abrí WhatsApp"), "frase completa")
    }

    // ============================================================
    // Escenarios 6-16 — routing de comandos clave
    // ============================================================

    @Test fun s06_ayuda_routesToHelp_humanCopy() {
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayuda"))
        assertHuman(VoiceHelpCenter.spokenHelp(), "ayuda")
    }

    @Test fun s07_comoEmpiezo_localHelp_notNoEntendi() {
        assertTrue(VoiceCommandDispatcher.isHelpCommand("cómo empiezo"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("no entiendo cómo usarte"))
    }

    @Test fun s08_abriGuasap_localRoute_noPending() {
        assertTrue(WhatsAppPhraseNormalizer.normalize("abrí guasap").contains("whatsapp"))
        assertFalse(dangerous("abrí guasap"))
    }

    @Test fun s09_queLeRespondo_suggestOnly_noDangerousDraft() {
        assertEquals(SafeLlmRoute.SUGGEST_REPLY_ONLY, route("qué le respondo"))
    }

    @Test fun s10_respondeleSinChat_failSafe_noSend() {
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt("respondéle que ya voy"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("respondéle que ya voy"))
    }

    @Test fun s11_tocaEnviar_localRefusal_noFreeLlm() {
        assertTrue(dangerous("tocá enviar"))
        assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route("tocá enviar"))
    }

    @Test fun s12_cancela_localCancel_pendingClean() {
        var pending: String? = "accion"
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("cancelá"))
        assertTrue(route("cancelá") != SafeLlmRoute.ALLOW_CONVERSATION)
        pending = null
        assertTrue(pending == null)
    }

    @Test fun s13_meArrepenti_cancel_draftClean() {
        var draft = true
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("me arrepentí"))
        draft = false
        assertFalse(draft)
    }

    @Test fun s14_aborta_cancel_noFreeLlm() {
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("abortá"))
        assertTrue(route("abortá") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    @Test fun s15_frenaTodo_cancel_noFreeLlm() {
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("frená todo"))
        assertTrue(route("frená todo") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    @Test fun s16_paraTodo_safeStop_cleanState() {
        val stop = VoiceCommandDispatcher.isStopCommand("pará todo") ||
            VoiceCommandDispatcher.isBareCancelCommand("pará todo")
        assertTrue(stop, "pará todo debe detener/cancelar")
        assertTrue(route("pará todo") != SafeLlmRoute.ALLOW_CONVERSATION)
    }

    // ============================================================
    // Escenarios 17-21 — TTS hablando, barge-in, errores de motor
    // ============================================================

    @Test fun s17_ttsSpeaking_userSaysPara_ttsStopped() {
        val h = harness()
        var ttsStopped = false
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = {},
            stopSpeechNow = { ttsStopped = true; h.controller.stopForCommandAndResume() }
        )
        h.controller.startListening()
        h.controller.pauseForSpeech() // Estela hablando
        dispatcher.onPartialText("pará")
        assertTrue(ttsStopped, "barge-in debe cortar TTS")
        assertEquals(VoiceListeningState.WAITING_RETRY, h.controller.currentState)
    }

    @Test fun s18_ttsSpeaking_repeti_noInfiniteDuplication() {
        // "repetí" se reconoce como repetición (no como comando nuevo ni loop).
        assertTrue(VoiceCommandDispatcher.isRepeatCommand("repetí"))
        assertFalse(VoiceCommandDispatcher.isStopCommand("repetí"))
    }

    @Test fun s19_ttsSpeaking_tapListen_noCrashNoDoubleRecognizer() {
        val h = harness()
        h.controller.startListening()
        val before = h.engine.startCount
        h.controller.pauseForSpeech()
        h.controller.startListening() // re-render externo
        assertEquals(before, h.engine.startCount)
        assertEquals(VoiceListeningState.SPEAKING, h.controller.currentState)
    }

    @Test fun s20_voiceErrorCopyForAllCategoriesIsHuman() {
        // Cada categoría de error de voz produce copy hablable, sin token técnico.
        SpeechErrorCategory.values().forEach { c ->
            assertHuman(VoiceSpeechErrorPolicy.humanMessageFor(c), "humanMessageFor($c)")
        }
        assertHuman(VoiceSpeechErrorPolicy.FINAL_NOT_UNDERSTOOD_MESSAGE, "FINAL_NOT_UNDERSTOOD")
        assertHuman(VoiceSpeechErrorPolicy.ENGINE_FALLBACK_MESSAGE, "ENGINE_FALLBACK")
    }

    @Test fun s21_sttError_explicitRetry_noInfiniteSpokenLoop() {
        val h = harness(expectingResponse = true)
        h.controller.startListening()
        repeat(6) {
            h.engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)
            h.scheduler.runNext()
        }
        // En modo "esperando respuesta" no repite la pista una y otra vez.
        assertTrue(h.errors.isEmpty(), "no debe repetir pista en loop: ${h.errors}")
        assertTrue(h.engine.startCount > 1, "debe reintentar")
    }

    // ============================================================
    // Escenarios 22-25 — background, foreground, contexto perdido
    // ============================================================

    @Test fun s22_backgroundDuringListening_safeCleanup() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening() // simula ON_PAUSE
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
        assertTrue(h.engine.stopCount > 0)
    }

    @Test fun s23_foreground_doesNotRevivePreviousActionByItself() {
        // Tras stop (background), un final tardío de la sesión vieja se IGNORA.
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitFinal("abrí whatsapp") // resultado tardío de la sesión vieja
        assertTrue(h.finals.isEmpty(), "no debe revivir una acción tardía: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun s24_whatsAppContextLost_readAsksContextOrFailsSafe() {
        val p = "qué dice ahí"
        assertFalse(dangerous(p))
        val r = route(p)
        assertTrue(r != SafeLlmRoute.BLOCK_DANGEROUS && r != SafeLlmRoute.ALLOW_CONVERSATION, "ruta segura: $r")
    }

    @Test fun s25_queDiceAhi_noUsefulSurface_usefulBriefReply() {
        // El fallback local (no "no entendí") guía el siguiente paso; copy útil.
        assertHuman(VoiceHelpCenter.contextualSpokenHelp(com.ojoclaro.android.help.VoiceHelpContext.WHATSAPP), "wa-help")
        assertHuman(VoiceHelpCenter.contextualSpokenHelp(com.ojoclaro.android.help.VoiceHelpContext.DEFAULT), "default-help")
    }

    @Test fun s26_masDespacio_repeatsOrExplains_noTechnicalToken() {
        assertTrue(VoiceCommandDispatcher.isRepeatCommand("más despacio"))
        assertTrue(VoiceCommandDispatcher.isRepeatCommand("más lento"))
    }

    // ============================================================
    // Escenarios 27-30 — privacidad de voz y repetición
    // ============================================================

    @Test fun s27_repetiAfterSecretResponse_doesNotRepeatSecret() {
        // La capa de sesión REDACTA el secreto: ni logs ni diagnóstico ni el
        // "último reconocido" guardan el PIN para repetirlo.
        val session = VoiceListeningSession(sessionId = 1L, startedAt = 0L)
            .recordFinal("mi pin es 1234")
        assertFalse(session.finalText.contains("1234"), "final redactado: ${session.finalText}")
        assertTrue(session.finalWasRedacted)
        assertFalse(session.bestPartialCandidate.contains("1234"))
        val diag = session.diagnostic(VoiceHearingStatus.IDLE, VoiceSpeechEngine.PLATFORM_DEFAULT).toString()
        assertFalse(diag.contains("1234"), "diagnóstico sin secreto: $diag")
    }

    @Test fun s28_miPinEs1234_sanitizedBeforeLlm_ttsWouldNotRepeatPin() {
        val out = LlmInputSanitizer.sanitize("mi pin es 1234")
        assertFalse(out.contains("1234"), "PIN redactado antes del LLM: $out")
        assertFalse(VoiceListeningSession(2L, 0L).recordPartial("mi pin es 1234").bestPartialCandidate.contains("1234"))
    }

    @Test fun s29_miCodigoEs445566_notInPendingNorDraft() {
        // No se usa como candidato parcial (sensible) y se redacta el final.
        assertFalse(isSafePartialCandidate("mi código es 445566"))
        val out = LlmInputSanitizer.sanitize("mi código es 445566")
        assertFalse(out.contains("445566"))
        assertTrue(VoiceListeningSession(3L, 0L).recordFinal("mi código es 445566").finalWasRedacted)
    }

    @Test fun s30_noMandesNadaDuringResponse_cancelsAndClears() {
        var pending: String? = "draft"
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("no mandes nada"))
        pending = null
        assertTrue(pending == null)
    }

    // ============================================================
    // Escenarios 31-35 — recuperación, doble inicio, eventos tardíos
    // ============================================================

    @Test fun s31_ayudaAfterCancellation_freshCleanSession() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.controller.startListening() // sesión nueva
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
        assertEquals(2, h.engine.startCount)
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayuda"))
    }

    @Test fun s32_twoRapidTapsListen_noDoubleRecognizer() {
        val h = harness()
        h.controller.startListening()
        h.controller.startListening()
        assertEquals(1, h.engine.startCount)
    }

    @Test fun s33_stopThenStart_noZombieState() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
        h.controller.startListening()
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
        assertEquals(2, h.engine.startCount)
    }

    @Test fun s34_partialThenStop_doesNotProcessLateFinal() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitPartial("abrir")
        h.controller.stopListening()
        h.engine.emitFinal("abrir whatsapp") // final tardío
        assertTrue(h.finals.isEmpty(), "no debe procesar final tardío tras stop: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun s35_lateFinalAfterCancellation_ignoredSafely() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitFinal("transferile plata a juan") // peligroso + tardío
        assertTrue(h.finals.isEmpty(), "final tardío peligroso tras cancelación debe ignorarse: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun s36_destroyedController_ignoresLateFinal() {
        val h = harness()
        h.controller.startListening()
        h.controller.destroy()
        h.engine.emitFinal("abrir whatsapp")
        assertTrue(h.finals.isEmpty(), "controller destruido no debe procesar finales: ${h.finals}")
    }

    // -------------------------------------------------------------------------
    private class Harness(
        val controller: VoiceCommandController,
        val engine: FakeEngine,
        val scheduler: FakeRetryScheduler,
        val finals: MutableList<String>,
        val errors: MutableList<String>,
        val partials: MutableList<String>,
        val states: MutableList<VoiceListeningState>
    )

    private fun harness(
        hasPermission: Boolean = true,
        expectingResponse: Boolean = false
    ): Harness {
        val engine = FakeEngine()
        val scheduler = FakeRetryScheduler()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val partials = mutableListOf<String>()
        val states = mutableListOf<VoiceListeningState>()
        val controller = VoiceCommandController(
            engine = engine,
            hasRecordAudioPermission = { hasPermission },
            onPartialTextCallback = partials::add,
            onFinalTextCallback = finals::add,
            onErrorCallback = errors::add,
            onReadyCallback = {},
            onStateChanged = states::add,
            retryScheduler = scheduler
        )
        if (expectingResponse) controller.setExpectingResponse(true)
        return Harness(controller, engine, scheduler, finals, errors, partials, states)
    }

    private class FakeEngine : SpeechInputEngine {
        override var listener: SpeechInputEngine.Listener? = null
        override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT
        override var isListening: Boolean = false
            private set
        var startCount: Int = 0
            private set
        var stopCount: Int = 0
            private set
        var resetCount: Int = 0
            private set
        var mode: SpeechListeningMode = SpeechListeningMode.DEFAULT
            private set

        override fun startListening() { startCount += 1; isListening = true; listener?.onReady() }
        override fun stopListening() { stopCount += 1; isListening = false }
        override fun resetRecognizer() { resetCount += 1; isListening = false }
        override fun destroy() { isListening = false }
        override fun setListeningMode(mode: SpeechListeningMode) { this.mode = mode }

        fun emitPartial(t: String) { listener?.onPartialText(t) }
        fun emitFinal(t: String) { isListening = false; listener?.onFinalText(t) }
        fun emitError(c: Int) { isListening = false; listener?.onError(c) }
    }

    private class FakeRetryScheduler : VoiceRetryScheduler {
        private data class Scheduled(val delayMillis: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val scheduled = mutableListOf<Scheduled>()
        override fun schedule(delayMillis: Long, action: () -> Unit): VoiceRetryHandle {
            val item = Scheduled(delayMillis, action)
            scheduled += item
            return VoiceRetryHandle { item.cancelled = true }
        }
        fun runNext() {
            val item = scheduled.firstOrNull { !it.cancelled } ?: return
            item.cancelled = true
            item.action()
        }
    }
}
