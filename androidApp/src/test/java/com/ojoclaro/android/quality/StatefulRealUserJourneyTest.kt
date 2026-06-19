package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.EstelaCompanionPhrases
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
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MISIÓN — Máquina de estados de un usuario NO VIDENTE real: SECUENCIAS completas
 * (no frases sueltas) con cambios de idea, arrepentimientos, interrupciones,
 * preguntas conceptuales en medio de una acción, contexto perdido y alias
 * imperfectos. Camina cada sesión paso a paso sobre los clasificadores puros
 * (espejo de GlobalAssistantService) + un modelo de estado pending/draft, y
 * asevera los invariantes de seguridad y recuperación a lo largo de TODA la
 * secuencia. El estado real (pending/draft de GAS) se valida además en device.
 *
 * PURO/determinista. Peor caso: WhatsApp SIEMPRE activo.
 */
class StatefulRealUserJourneyTest {

    // ---- espejo exacto de buildSafeLlmSignals().looksDangerous + detectores ----
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

    private enum class Kind { HELP, REPEAT, OPEN, READ, SEARCH, REPLY_HELP, DRAFT, CONCEPTUAL, CANCEL, DANGEROUS, CAPABILITY, PII, SAFE }

    private data class Step(val phrase: String, val kind: Kind)
    private infix fun String.isA(k: Kind) = Step(this, k)

    /** Modelo de estado de sesión: lo que un usuario percibiría como "pendiente". */
    private class ModelState { var pending: String? = null }

    /** Asevera el invariante por tipo de paso y actualiza el estado modelado. */
    private fun assertStep(step: Step, state: ModelState, where: String) {
        val p = step.phrase
        // Invariante GLOBAL de seguridad: una acción peligrosa que no sea pregunta
        // conceptual ni ayuda-de-respuesta JAMÁS termina en charla libre o sugerencia.
        if (dangerousDetected(p) && !SafeLlmPhrases.isSafeQuestion(p) && !SafeLlmPhrases.isReplyHelp(p)) {
            assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "$where: danger must BLOCK: \"$p\"")
        }
        when (step.kind) {
            Kind.HELP -> assertTrue(VoiceCommandDispatcher.isHelpCommand(p), "$where: HELP: \"$p\"")
            Kind.REPEAT -> assertTrue(VoiceCommandDispatcher.isRepeatCommand(p), "$where: REPEAT: \"$p\"")
            Kind.OPEN -> assertTrue(
                WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"), "$where: OPEN→wa: \"$p\""
            )
            Kind.READ, Kind.SEARCH, Kind.SAFE ->
                assertFalse(dangerousDetected(p), "$where: read/search/safe must NOT be dangerous: \"$p\"")
            Kind.REPLY_HELP -> {
                assertTrue(SafeLlmPhrases.isReplyHelp(p), "$where: REPLY_HELP: \"$p\"")
                assertTrue(route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY, "$where: reply-help suggest-only: \"$p\"")
            }
            Kind.DRAFT -> {
                assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p), "$where: DRAFT attempt: \"$p\"")
                assertFalse(SafeLlmPhrases.isReplyHelp(p), "$where: DRAFT is not reply-help: \"$p\"")
                state.pending = "draft"
            }
            Kind.CONCEPTUAL -> {
                assertTrue(SafeLlmPhrases.isSafeQuestion(p), "$where: CONCEPTUAL: \"$p\"")
                assertTrue(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "$where: conceptual converses: \"$p\"")
            }
            Kind.CANCEL -> {
                assertTrue(cancelDetected(p), "$where: CANCEL recognized: \"$p\"")
                assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "$where: cancel never free-LLM: \"$p\"")
                state.pending = null // cancelación limpia estado, aun tras una frase peligrosa
            }
            Kind.DANGEROUS -> {
                assertTrue(dangerousDetected(p), "$where: DANGEROUS detected: \"$p\"")
                assertFalse(SafeLlmPhrases.isSafeQuestion(p), "$where: danger not safe-question: \"$p\"")
                // El imperativo peligroso se bloquea: no crea pending propio.
            }
            Kind.CAPABILITY -> assertTrue(
                EstelaCompanionPhrases.respond(p) != null, "$where: CAPABILITY answered: \"$p\""
            )
            Kind.PII -> {
                val out = LlmInputSanitizer.sanitize(p)
                listOf("1234", "445566", "azul123", "9988", "2991234567").forEach { s ->
                    if (p.contains(s)) assertFalse(out.contains(s), "$where: PII redacted \"$s\": \"$p\"→\"$out\"")
                }
            }
        }
    }

    private fun runSession(label: String, steps: List<Step>) {
        val state = ModelState()
        steps.forEachIndexed { i, s -> assertStep(s, state, "$label#${i + 1}") }
        // Recuperación: una sesión que TERMINA en cancelación no deja pending.
        if (steps.last().kind == Kind.CANCEL) {
            assertTrue(state.pending == null, "$label: pending residual after final cancel")
        }
    }

    // ================== FASE 1 — 25 contratos de sesión ==================
    @Test
    fun statefulSessions_holdSafetyAndRecovery() {
        val sessions = listOf(
            "S1" to listOf("ayuda" isA Kind.HELP, "cómo empiezo" isA Kind.HELP, "abrí guasap" isA Kind.OPEN,
                "leé los chats" isA Kind.READ, "cancelá" isA Kind.CANCEL),
            "S2" to listOf("abrí WhatsApp" isA Kind.OPEN, "qué le respondo" isA Kind.REPLY_HELP,
                "respondéle que ya voy" isA Kind.DRAFT, "no, no mandes nada" isA Kind.CANCEL, "cancelá" isA Kind.CANCEL),
            "S3" to listOf("abrí guasap" isA Kind.OPEN, "buscá a CONTACTO_A" isA Kind.SEARCH,
                "ayuda" isA Kind.HELP, "repetí" isA Kind.REPEAT),
            "S4" to listOf("qué dice ahí" isA Kind.READ, "no entendí" isA Kind.REPEAT,
                "más despacio" isA Kind.REPEAT, "repetí" isA Kind.REPEAT),
            "S5" to listOf("respondéle que ya voy" isA Kind.DRAFT, "tocá enviar" isA Kind.DANGEROUS,
                "me arrepentí" isA Kind.CANCEL, "qué puedo hacer" isA Kind.HELP),
            "S6" to listOf("leé los chats" isA Kind.READ, "mandá audio" isA Kind.DANGEROUS,
                "cancelá" isA Kind.CANCEL, "qué le escribo" isA Kind.REPLY_HELP),
            "S7" to listOf("abrí WhatsApp" isA Kind.OPEN, "qué es una transferencia" isA Kind.CONCEPTUAL,
                "transferile plata" isA Kind.DANGEROUS, "cancelá" isA Kind.CANCEL),
            "S8" to listOf("abrí WhatsApp" isA Kind.OPEN, "cómo se bloquea a alguien" isA Kind.CONCEPTUAL,
                "bloquealo" isA Kind.DANGEROUS, "no hagas nada" isA Kind.CANCEL),
            "S9" to listOf("abrí guasap" isA Kind.OPEN, "leé los mensajes" isA Kind.READ,
                "qué dice ahí" isA Kind.READ),
            "S10" to listOf("ayuda" isA Kind.HELP, "abrí wasat" isA Kind.OPEN, "buscá a Ana Prueba" isA Kind.SEARCH,
                "pará todo" isA Kind.CANCEL, "ayuda" isA Kind.HELP),
            "S11" to listOf("respondéle que ya voy" isA Kind.DRAFT, "abortá" isA Kind.CANCEL,
                "respondéle que llego tarde" isA Kind.DRAFT, "frená todo" isA Kind.CANCEL),
            "S12" to listOf("qué le escribo" isA Kind.REPLY_HELP, "cómo le digo" isA Kind.REPLY_HELP,
                "respondéle que estoy ocupado" isA Kind.DRAFT, "no, pará" isA Kind.CANCEL),
            "S13" to listOf("leé los chats del guasap" isA Kind.READ,
                "porfa buscá a CONTACTO_A en wasa" isA Kind.SEARCH, "cancelá todo" isA Kind.CANCEL),
            "S14" to listOf("mi pin es 1234" isA Kind.PII, "qué le escribo" isA Kind.REPLY_HELP, "cancelá" isA Kind.CANCEL),
            "S15" to listOf("qué puedo hacer con WhatsApp" isA Kind.CAPABILITY, "tocá el avioncito" isA Kind.DANGEROUS,
                "no mandes nada" isA Kind.CANCEL),
            "S16" to listOf("abrime el wsp" isA Kind.OPEN, "a ver qué dice ahí" isA Kind.READ,
                "repetí" isA Kind.REPEAT, "dejalo" isA Kind.CANCEL),
            "S17" to listOf("buscá a CONTACTO_A en guasap" isA Kind.SEARCH, "tocá el botón verde" isA Kind.DANGEROUS,
                "me arrepentí" isA Kind.CANCEL),
            "S18" to listOf("qué es un audio de WhatsApp" isA Kind.CONCEPTUAL, "mandá audio" isA Kind.DANGEROUS,
                "cancelá" isA Kind.CANCEL),
            "S19" to listOf("cómo cancelo una respuesta" isA Kind.CONCEPTUAL, "respondéle que ya voy" isA Kind.DRAFT,
                "cancelá" isA Kind.CANCEL),
            "S20" to listOf("no entiendo cómo usarte" isA Kind.HELP, "ayuda" isA Kind.HELP, "abrí WhatsApp" isA Kind.OPEN),
            "S21" to listOf("abrí guasap" isA Kind.OPEN, "cómo le digo que estoy ocupado" isA Kind.REPLY_HELP,
                "prepará borrador" isA Kind.SAFE, "no mandes nada" isA Kind.CANCEL),
            "S22" to listOf("leé eso" isA Kind.READ, "no, no era eso" isA Kind.SAFE,
                "repetí" isA Kind.REPEAT, "ayuda" isA Kind.HELP),
            "S23" to listOf("abrí WhatsApp" isA Kind.OPEN, "videollamada" isA Kind.DANGEROUS,
                "no hagas nada" isA Kind.CANCEL, "qué puedo hacer" isA Kind.HELP),
            "S24" to listOf("qué dice ahí" isA Kind.READ, "pagale" isA Kind.DANGEROUS,
                "abortá" isA Kind.CANCEL, "qué le respondo" isA Kind.REPLY_HELP),
            "S25" to listOf("abrí guasap" isA Kind.OPEN, "cancelá" isA Kind.CANCEL,
                "abrí guasap" isA Kind.OPEN, "leé los chats" isA Kind.READ)
        )
        sessions.forEach { (label, steps) -> runSession(label, steps) }
        assertTrue(sessions.size >= 25, "need >= 25 session contracts, was ${sessions.size}")
    }

    // ================== FASE 3 — caos determinista (semilla fija) ==================
    private val POOLS: Map<Kind, List<String>> = mapOf(
        Kind.HELP to listOf("ayuda", "ayudame", "qué puedo hacer", "qué puedo decirte", "cómo empiezo",
            "por dónde empiezo", "quiero que me ayudes", "no entiendo cómo usarte"),
        Kind.REPEAT to listOf("repetí", "repetilo", "más despacio", "más lento", "no entendí", "otra vez", "qué dijiste"),
        Kind.OPEN to listOf("abrí guasap", "abrí WhatsApp", "abrime el wsp", "abrí wasat", "andá a guasap", "abrí el wasa"),
        Kind.READ to listOf("leé los chats", "qué dice ahí", "leé los mensajes", "qué ves", "leé eso", "leé la pantalla"),
        Kind.SEARCH to listOf("buscá a CONTACTO_A", "buscá a Ana Prueba en guasap", "encontrá el chat de José Demo",
            "buscá a Luz Test en wsp", "abrí el chat de CONTACTO_B"),
        Kind.REPLY_HELP to listOf("qué le respondo", "qué le escribo", "cómo le digo", "qué le contesto",
            "ayudame a responder", "qué le puedo poner"),
        Kind.DRAFT to listOf("respondéle que ya voy", "contestale que estoy ocupado", "decile que llego tarde",
            "escribile que después le hablo"),
        Kind.CONCEPTUAL to listOf("qué es una transferencia", "cómo funciona una videollamada", "qué es un audio de WhatsApp",
            "cómo se bloquea a alguien", "cómo cancelo una respuesta", "qué pasa si toco enviar", "qué es whatsapp"),
        Kind.CANCEL to listOf("cancelá", "cancelá todo", "no mandes nada", "me arrepentí", "abortá", "frená todo",
            "pará todo", "dejalo", "olvidate", "no hagas nada", "no, pará", "no toques nada"),
        Kind.DANGEROUS to listOf("tocá enviar", "mandalo", "tocá el avioncito", "tocá el botón verde", "apretá el avión de papel",
            "mandá audio", "llamalo", "videollamada", "reenviá esto", "borrá el chat", "bloquealo", "pagale",
            "transferile plata", "mandá una foto", "compartí mi ubicación"),
        Kind.PII to listOf("mi pin es 1234", "mi código es 445566", "mi clave es azul123", "mi clave 9988"),
        // SAFE = muletillas / fragmentos sin verbo mutante. NO incluye "respondéle"
        // (verbo de respuesta: dispara el marcador 'respond' por diseño).
        Kind.SAFE to listOf("che estela", "porfa", "eh", "mmm", "a ver", "eso", "ahí", "el coso", "después",
            "abrí", "leé", "buscá", "no era eso")
    )

    @Test
    fun deterministicChaos_neverBreaksSafetyInvariants() {
        val seed = 0xE57E1A2026L
        val rnd = Random(seed)
        val kinds = POOLS.keys.toList()
        val sessionCount = 220
        var steps = 0
        repeat(sessionCount) { sIdx ->
            val state = ModelState()
            val len = 3 + rnd.nextInt(4) // 3..6 pasos
            val seq = (0 until len).map {
                val k = kinds[rnd.nextInt(kinds.size)]
                Step(POOLS.getValue(k)[rnd.nextInt(POOLS.getValue(k).size)], k)
            }
            seq.forEachIndexed { i, s ->
                assertStep(s, state, "CHAOS[seed=$seed sess=$sIdx step=${i + 1}]")
                steps++
            }
        }
        assertTrue(sessionCount in 150..250, "chaos battery must be 150..250 sequences, was $sessionCount")
        assertTrue(steps >= 600, "chaos battery should exercise many steps, was $steps")
    }

    // Regresión: el sustantivo "borrador" (= draft) NO es un borrado peligroso;
    // solo el VERBO "borrá/borrar..." lo es.
    @Test
    fun draftNounIsNotADangerousDelete() {
        listOf("prepará borrador", "preparame un borrador", "leeme el borrador",
            "qué dice el borrador", "escribí un borrador").forEach { p ->
            assertFalse(WhatsAppCriticalGuard.isCritical(p), "draft noun must NOT be critical: \"$p\"")
            assertFalse(dangerousDetected(p), "draft noun must NOT be dangerous: \"$p\"")
        }
        // El verbo de borrado real SIGUE siendo peligroso.
        listOf("borrá el chat", "borra la conversación", "borrá ese mensaje", "eliminá el chat").forEach { p ->
            assertTrue(WhatsAppCriticalGuard.isCritical(p), "delete verb must be critical: \"$p\"")
        }
    }

    // Invariante transversal: una cancelación SIEMPRE limpia un draft previo,
    // incluso intercalada con una frase peligrosa (que nunca ejecuta).
    @Test
    fun cancelAlwaysClearsDraftEvenAfterDanger() {
        listOf(
            listOf("respondéle que ya voy" isA Kind.DRAFT, "tocá enviar" isA Kind.DANGEROUS, "cancelá" isA Kind.CANCEL),
            listOf("respondéle que llego" isA Kind.DRAFT, "mandá audio" isA Kind.DANGEROUS, "abortá" isA Kind.CANCEL),
            listOf("contestale que sí" isA Kind.DRAFT, "pagale" isA Kind.DANGEROUS, "no mandes nada" isA Kind.CANCEL)
        ).forEachIndexed { i, seq ->
            val state = ModelState()
            seq.forEach { assertStep(it, state, "DANGER-CANCEL#${i + 1}") }
            assertTrue(state.pending == null, "draft must be cleared after danger+cancel #${i + 1}")
        }
    }
}
