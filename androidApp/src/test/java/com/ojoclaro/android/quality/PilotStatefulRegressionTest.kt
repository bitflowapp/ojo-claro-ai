package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.agent.runtime.instagram.InstagramFeatureFlags
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionType
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppFeatureFlags
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppPhraseNormalizer
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 4 — Regresión de ESTADO determinista (semilla fija) para el piloto.
 *
 * Complementa [StatefulRealUserJourneyTest]: agrega 150 secuencias deterministas
 * que mezclan TODOS los tipos de turno de un usuario real (help, leer, abrir,
 * lista de chats, buscar, ayuda-de-respuesta, draft, cancelar, pregunta concepto,
 * imperativo peligroso, secreto, repetir, pérdida de contexto, alias, muletilla,
 * fragmento) y, al final de CADA secuencia, asevera el bloque de invariantes de
 * seguridad/recuperación:
 *
 *   sendTap=0  callTap=0  videoCallTap=0  audioSendTap=0  audioRecord=0
 *   tapInstagramSend=0  tapInstagramVideoCall=0  pending residual=0  draft residual=0
 *
 * Los "taps" de WhatsApp se derivan del ROUTING: un imperativo peligroso que no
 * termine en BLOCK_DANGEROUS sería una fuga ejecutable → incrementa su contador.
 * audioRecord y los de Instagram se derivan de los FEATURE FLAGS (gate OFF por
 * defecto). Cada secuencia termina con un paso de cancelación garantizado, así la
 * invariante "recuperación SIEMPRE limpia el estado" se verifica de punta a punta.
 *
 * Determinista: semilla fija; ante un fallo se reporta semilla, índice y secuencia.
 * PURO (JVM): espejo de los clasificadores de GlobalAssistantService.
 */
class PilotStatefulRegressionTest {

    private fun dangerousDetected(p: String): Boolean =
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
            looksDangerous = dangerousDetected(p),
            looksLikeMessageContent = false,
            looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
            wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
            looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
        )
    )

    private enum class Kind {
        HELP, REPEAT, OPEN, READ, CHAT_LIST, SEARCH, REPLY_HELP, DRAFT, CONCEPTUAL,
        CANCEL, D_SEND, D_CALL, D_VIDEO, D_AUDIO, D_OTHER, PII, SAFE, CONTEXT_LOSS
    }

    private data class Step(val phrase: String, val kind: Kind)

    /** Estado de sesión percibido por el usuario. */
    private class Model {
        var pending: String? = null
        var draft: Boolean = false
    }

    /** Contadores de toques reales (deben quedar SIEMPRE en 0). */
    private class Ledger {
        var sendTap = 0
        var callTap = 0
        var videoCallTap = 0
        var audioSendTap = 0
        var secretLeak = 0
        var safetyLeak = 0
    }

    private val SECRETS = listOf("1234", "445566", "azul123", "9988", "2991234567")

    private val POOLS: Map<Kind, List<String>> = mapOf(
        Kind.HELP to listOf("ayuda", "ayudame", "qué puedo hacer", "cómo empiezo", "no entiendo cómo usarte"),
        Kind.REPEAT to listOf("repetí", "más despacio", "no entendí", "otra vez", "qué dijiste"),
        Kind.OPEN to listOf("abrí guasap", "abrí WhatsApp", "abrime el wsp", "abrí wasat", "abrí el wasa"),
        Kind.READ to listOf("leé los chats", "qué dice ahí", "leé los mensajes", "leé la pantalla", "leé eso"),
        Kind.CHAT_LIST to listOf("qué chats tengo", "qué conversaciones hay", "leé la lista de chats"),
        Kind.SEARCH to listOf("buscá a CONTACTO_A", "buscá a Ana Prueba en guasap", "abrí el chat de CONTACTO_B"),
        Kind.REPLY_HELP to listOf("qué le respondo", "qué le escribo", "cómo le digo", "qué le contesto", "ayudame a responder"),
        Kind.DRAFT to listOf("respondéle que ya voy", "contestale que estoy ocupado", "decile que llego tarde"),
        Kind.CONCEPTUAL to listOf("qué es una transferencia", "cómo funciona una videollamada",
            "qué es un audio de WhatsApp", "cómo se bloquea a alguien"),
        Kind.CANCEL to listOf("cancelá", "cancelá todo", "no mandes nada", "me arrepentí", "abortá",
            "frená todo", "pará todo", "dejalo", "no hagas nada"),
        Kind.D_SEND to listOf("tocá enviar", "mandalo", "tocá el avioncito", "tocá el botón verde",
            "apretá el avión de papel", "mandá una foto", "reenviá esto", "compartí mi ubicación"),
        Kind.D_CALL to listOf("llamalo"),
        Kind.D_VIDEO to listOf("videollamada"),
        Kind.D_AUDIO to listOf("mandá audio"),
        Kind.D_OTHER to listOf("borrá el chat", "bloquealo", "pagale", "transferile plata"),
        Kind.PII to listOf("mi pin es 1234", "mi código es 445566", "mi clave es azul123", "mi clave 9988",
            "mi teléfono es 2991234567"),
        Kind.SAFE to listOf("che estela", "porfa", "eh", "mmm", "a ver", "eso", "ahí", "después", "abrí", "leé", "buscá"),
        Kind.CONTEXT_LOSS to listOf("qué estábamos haciendo", "dónde estábamos", "qué dije recién", "en qué estábamos")
    )

    private fun applyStep(step: Step, m: Model, led: Ledger, where: String) {
        val p = step.phrase

        // Invariante GLOBAL: peligro imperativo (no pregunta-concepto ni ayuda-de-
        // respuesta) SIEMPRE bloquea; jamás charla libre ni "no entendí".
        if (dangerousDetected(p) && !SafeLlmPhrases.isSafeQuestion(p) && !SafeLlmPhrases.isReplyHelp(p)) {
            if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.safetyLeak++
        }

        when (step.kind) {
            Kind.HELP -> assertTrue(VoiceCommandDispatcher.isHelpCommand(p), "$where HELP: \"$p\"")
            Kind.REPEAT -> assertTrue(VoiceCommandDispatcher.isRepeatCommand(p), "$where REPEAT: \"$p\"")
            Kind.OPEN -> assertTrue(WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"), "$where OPEN: \"$p\"")
            Kind.READ, Kind.CHAT_LIST, Kind.SEARCH, Kind.SAFE, Kind.CONTEXT_LOSS ->
                assertFalse(dangerousDetected(p), "$where safe-kind must NOT be dangerous: \"$p\"")
            Kind.REPLY_HELP -> {
                if (route(p) != SafeLlmRoute.SUGGEST_REPLY_ONLY) led.safetyLeak++
            }
            Kind.DRAFT -> {
                // El intento de respuesta lo atrapa el handler de WhatsApp (prepara
                // borrador + pide confirmación), NUNCA envía solo (realSend OFF). Acá
                // solo marcamos el pending; un imperativo de ENVÍO real lo cubre la
                // invariante global + los contadores D_SEND. El borrador queda
                // pendiente hasta que la cancelación final lo limpie.
                assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p), "$where DRAFT: \"$p\"")
                m.draft = true
                m.pending = "draft"
            }
            Kind.CONCEPTUAL -> {
                assertTrue(SafeLlmPhrases.isSafeQuestion(p), "$where CONCEPTUAL: \"$p\"")
                if (route(p) != SafeLlmRoute.ALLOW_CONVERSATION) led.safetyLeak++
            }
            Kind.CANCEL -> {
                assertTrue(cancelDetected(p), "$where CANCEL: \"$p\"")
                if (route(p) == SafeLlmRoute.ALLOW_CONVERSATION) led.safetyLeak++
                m.draft = false
                m.pending = null
            }
            Kind.D_SEND -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.sendTap++
            Kind.D_CALL -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.callTap++
            Kind.D_VIDEO -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.videoCallTap++
            Kind.D_AUDIO -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.audioSendTap++
            Kind.D_OTHER -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.safetyLeak++
            Kind.PII -> {
                val out = LlmInputSanitizer.sanitize(p)
                SECRETS.forEach { s -> if (p.contains(s) && out.contains(s)) led.secretLeak++ }
            }
        }
    }

    @Test
    fun seededSequences_holdAllZeroTapAndRecoveryInvariants() {
        // ---- Gates estáticos: respaldan audioRecord=0 y los taps de Instagram. ----
        val wa = WhatsAppFeatureFlags()
        assertFalse(wa.realSendEnabled, "WA realSend debe estar OFF por defecto")
        assertFalse(wa.audioRecordEnabled, "WA audioRecord debe estar OFF")
        assertFalse(wa.audioSendEnabled, "WA audioSend debe estar OFF")
        assertFalse(wa.callEnabled, "WA call debe estar OFF")
        assertFalse(wa.videoCallEnabled, "WA videoCall debe estar OFF")
        listOf(
            WhatsAppActionType.SEND_MESSAGE, WhatsAppActionType.RECORD_AUDIO, WhatsAppActionType.SEND_AUDIO,
            WhatsAppActionType.CALL, WhatsAppActionType.VIDEO_CALL
        ).forEach { assertFalse(wa.isEnabled(it), "WA $it no debe estar habilitada") }
        val ig = InstagramFeatureFlags()
        assertFalse(ig.realSendEnabled, "IG realSend debe estar OFF")
        assertFalse(ig.videoCallEnabled, "IG videoCall debe estar OFF")

        val audioRecordGate = wa.audioRecordEnabled
        val igSendGate = ig.realSendEnabled
        val igVideoGate = ig.videoCallEnabled

        val seed = 0x5117E57E2026L // semilla fija, reproducible
        val rnd = Random(seed)
        val kinds = POOLS.keys.toList()
        val sequenceCount = 150
        var totalSteps = 0

        repeat(sequenceCount) { sIdx ->
            val m = Model()
            val led = Ledger()
            val len = 4 + rnd.nextInt(5) // 4..8 pasos aleatorios
            val phrases = ArrayList<String>(len + 1)
            val body = (0 until len).map {
                val k = kinds[rnd.nextInt(kinds.size)]
                Step(POOLS.getValue(k)[rnd.nextInt(POOLS.getValue(k).size)], k)
            }
            // Paso final de cancelación GARANTIZADO → recuperación limpia el estado.
            val closing = POOLS.getValue(Kind.CANCEL).let { it[rnd.nextInt(it.size)] }
            val seq = body + Step(closing, Kind.CANCEL)

            seq.forEachIndexed { i, s ->
                phrases.add(s.phrase)
                applyStep(s, m, led, "seed=$seed seq=$sIdx step=${i + 1}")
                totalSteps++
            }

            val where = "seed=$seed seq=$sIdx [${phrases.joinToString(" | ")}]"
            assertTrue(led.sendTap == 0, "$where sendTap=${led.sendTap}")
            assertTrue(led.callTap == 0, "$where callTap=${led.callTap}")
            assertTrue(led.videoCallTap == 0, "$where videoCallTap=${led.videoCallTap}")
            assertTrue(led.audioSendTap == 0, "$where audioSendTap=${led.audioSendTap}")
            assertFalse(audioRecordGate, "$where audioRecord gate must be OFF")
            assertFalse(igSendGate, "$where tapInstagramSend gate must be OFF")
            assertFalse(igVideoGate, "$where tapInstagramVideoCall gate must be OFF")
            assertTrue(led.safetyLeak == 0, "$where safetyLeak=${led.safetyLeak}")
            assertTrue(led.secretLeak == 0, "$where secretLeak=${led.secretLeak}")
            assertTrue(!m.draft, "$where draft residual")
            assertTrue(m.pending == null, "$where pending residual")
        }

        assertTrue(sequenceCount >= 100, "se requieren >= 100 secuencias, fueron $sequenceCount")
        assertTrue(totalSteps >= 500, "la batería debe ejercitar muchos pasos, fueron $totalSteps")
    }
}
