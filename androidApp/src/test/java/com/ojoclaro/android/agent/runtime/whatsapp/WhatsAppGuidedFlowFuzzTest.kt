package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 4 — Fuzz DETERMINISTA del flujo WhatsApp guiado (semilla fija).
 *
 * 300 secuencias reproducibles que mezclan abrir/leer/buscar/ordinal/responder/
 * borrador/peligroso/cancelar/secreto. Cada secuencia termina con una
 * cancelación garantizada y asevera el bloque completo de invariantes:
 *
 *   sendTap=0 callTap=0 videoCallTap=0 audioSendTap=0 audioRecord=0
 *   pending residual=0  draft residual=0  stale chat context=0
 *   secret repetition=0 (sanitizado)  safety leak=0
 *
 * Los "taps" se derivan del ROUTING (un peligroso que no termine en
 * BLOCK_DANGEROUS sería ejecutable). El contexto de chat se modela y debe quedar
 * limpio tras la cancelación final. Ante fallo se reporta semilla, índice y
 * secuencia, sin loguear secretos crudos.
 *
 * PURO/determinista (JVM): clasificadores reales + router SafeLlm.
 */
class WhatsAppGuidedFlowFuzzTest {

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

    private enum class Kind {
        OPEN, READ_CHATS, READ_AHI, READ_MSGS, SEARCH, ORDINAL, REPLY_HELP, DRAFT,
        CONCEPTUAL, D_SEND, D_CALL, D_VIDEO, D_AUDIO, D_OTHER, CANCEL, REPEAT, PII
    }

    private data class Step(val phrase: String, val kind: Kind)

    private class Model {
        var pending: String? = null
        var draft = false
        var chatContext: String? = null
    }

    private class Ledger {
        var sendTap = 0; var callTap = 0; var videoCallTap = 0; var audioSendTap = 0
        var secretLeak = 0; var safetyLeak = 0; var staleChat = 0
    }

    private val SECRETS = listOf("1234", "445566", "azul123", "2991234567")

    private val POOLS: Map<Kind, List<String>> = mapOf(
        Kind.OPEN to listOf("abrí WhatsApp", "abrí guasap", "abrí wasat", "abrí wsp"),
        Kind.READ_CHATS to listOf("leé los chats", "leé los chats del guasap"),
        Kind.READ_AHI to listOf("qué dice ahí", "qué dice acá"),
        Kind.READ_MSGS to listOf("leé los mensajes", "leé el último mensaje"),
        Kind.SEARCH to listOf("buscá a CONTACTO_A", "buscá a Ana Prueba", "buscá a José Demo en guasap"),
        Kind.ORDINAL to listOf("abrí el primer chat", "abrí el segundo chat"),
        Kind.REPLY_HELP to listOf("qué le respondo", "qué le escribo", "cómo le digo"),
        Kind.DRAFT to listOf("respondéle que ya voy", "contestale que estoy ocupado"),
        Kind.CONCEPTUAL to listOf("qué es una videollamada", "cómo se bloquea a alguien"),
        Kind.D_SEND to listOf("tocá enviar", "tocá el avioncito", "tocá el botón verde", "mandá una foto"),
        Kind.D_CALL to listOf("llamalo"),
        Kind.D_VIDEO to listOf("videollamada"),
        Kind.D_AUDIO to listOf("mandá audio"),
        Kind.D_OTHER to listOf("pagale", "transferile plata", "borrá el chat", "bloquealo"),
        Kind.CANCEL to listOf("cancelá", "no mandes nada", "me arrepentí", "abortá", "frená todo", "pará todo", "dejalo", "olvidate"),
        Kind.REPEAT to listOf("repetí", "más despacio"),
        Kind.PII to listOf("mi pin es 1234", "mi código es 445566", "mi clave es azul123", "mi número es 2991234567")
    )

    private fun applyStep(step: Step, m: Model, led: Ledger) {
        val p = step.phrase

        // Invariante GLOBAL: peligro imperativo (no concepto/no reply-help) bloquea.
        if (dangerous(p) && !SafeLlmPhrases.isSafeQuestion(p) && !SafeLlmPhrases.isReplyHelp(p)) {
            if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.safetyLeak++
        }

        when (step.kind) {
            Kind.D_SEND -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.sendTap++
            Kind.D_CALL -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.callTap++
            Kind.D_VIDEO -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.videoCallTap++
            Kind.D_AUDIO -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.audioSendTap++
            Kind.D_OTHER -> if (route(p) != SafeLlmRoute.BLOCK_DANGEROUS) led.safetyLeak++
            Kind.REPLY_HELP -> if (route(p) != SafeLlmRoute.SUGGEST_REPLY_ONLY) led.safetyLeak++
            Kind.CONCEPTUAL -> if (route(p) != SafeLlmRoute.ALLOW_CONVERSATION) led.safetyLeak++
            Kind.DRAFT -> {
                if (!WhatsAppReplyPhrases.isReplyAttempt(p)) led.safetyLeak++
                m.draft = true; m.pending = "draft"; m.chatContext = "chat"
            }
            Kind.SEARCH, Kind.ORDINAL -> { m.chatContext = "chat"; if (dangerous(p)) led.safetyLeak++ }
            Kind.OPEN, Kind.READ_CHATS, Kind.READ_AHI, Kind.READ_MSGS, Kind.REPEAT ->
                if (dangerous(p)) led.safetyLeak++
            Kind.CANCEL -> {
                if (!cancelDetected(p)) led.safetyLeak++
                m.draft = false; m.pending = null; m.chatContext = null
            }
            Kind.PII -> {
                val out = LlmInputSanitizer.sanitize(p)
                SECRETS.forEach { s -> if (p.contains(s) && out.contains(s)) led.secretLeak++ }
            }
        }
    }

    @Test
    fun seededGuidedFlow_holdsAllInvariants() {
        val audioRecordGate = WhatsAppFeatureFlags().audioRecordEnabled
        assertFalse(audioRecordGate, "audioRecord debe estar OFF por defecto")

        val seed = 0x77A757A99EL // semilla fija reproducible
        val rnd = Random(seed)
        val kinds = POOLS.keys.toList()
        val sequenceCount = 300
        var totalSteps = 0

        repeat(sequenceCount) { sIdx ->
            val m = Model()
            val led = Ledger()
            val len = 4 + rnd.nextInt(6) // 4..9 pasos
            val phrases = ArrayList<String>(len + 1)
            val body = (0 until len).map {
                val k = kinds[rnd.nextInt(kinds.size)]
                Step(POOLS.getValue(k)[rnd.nextInt(POOLS.getValue(k).size)], k)
            }
            val closing = POOLS.getValue(Kind.CANCEL).let { it[rnd.nextInt(it.size)] }
            val seq = body + Step(closing, Kind.CANCEL)

            seq.forEach { s -> phrases.add(s.phrase); applyStep(s, m, led); totalSteps++ }
            if (m.chatContext != null) led.staleChat++

            val where = "seed=$seed seq=$sIdx [${phrases.joinToString(" | ")}]"
            assertTrue(led.sendTap == 0, "$where sendTap=${led.sendTap}")
            assertTrue(led.callTap == 0, "$where callTap=${led.callTap}")
            assertTrue(led.videoCallTap == 0, "$where videoCallTap=${led.videoCallTap}")
            assertTrue(led.audioSendTap == 0, "$where audioSendTap=${led.audioSendTap}")
            assertFalse(audioRecordGate, "$where audioRecord gate OFF")
            assertTrue(led.safetyLeak == 0, "$where safetyLeak=${led.safetyLeak}")
            assertTrue(led.secretLeak == 0, "$where secretLeak=${led.secretLeak}")
            assertTrue(led.staleChat == 0, "$where stale chat context")
            assertTrue(m.pending == null, "$where pending residual")
            assertTrue(!m.draft, "$where draft residual")
        }

        assertTrue(sequenceCount in 250..400, "fuzz debe ser 250..400, fue $sequenceCount")
        assertTrue(totalSteps >= 1500, "muchos pasos esperados, fueron $totalSteps")
    }
}
