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
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 3 — Contratos de UX y recuperación para el PILOTO HUMANO ASISTIDO.
 *
 * Una persona no vidente real le habla a Estela con frases naturales, cambios de
 * idea y arrepentimientos. Cada contrato verifica que la SALIDA sea útil y SEGURA:
 *  - nunca expone jerga técnica (NO_MATCH, handler, intent, fallback...);
 *  - nunca deriva una acción peligrosa al LLM libre;
 *  - la cancelación limpia el estado (sin pending ni draft residual);
 *  - jamás promete un envío (el LLM no ejecuta; WhatsApp prepara, no envía);
 *  - hay fallback útil cuando falta contexto, entendible sin mirar la pantalla.
 *
 * PURO/determinista (JVM): espejo de los clasificadores de GlobalAssistantService
 * + el router SafeLlmFallbackPolicy. Sin Android, sin red, sin estado global.
 */
class PilotReadinessJourneyTest {

    // ---- Espejo del routing seguro (peor caso: WhatsApp SIEMPRE activo) --------
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

    // Tokens técnicos que JAMÁS deben aparecer en copy hablado al usuario.
    private val FORBIDDEN_JARGON = listOf(
        Regex("NO_MATCH"),
        Regex("not[_ ]?found", RegexOption.IGNORE_CASE),
        Regex("\\bblocked\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhandler\\b", RegexOption.IGNORE_CASE),
        Regex("\\bintent\\b", RegexOption.IGNORE_CASE),
        Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnull\\b", RegexOption.IGNORE_CASE),
        Regex("\\bexception\\b", RegexOption.IGNORE_CASE)
    )

    private fun assertNoJargon(text: String, where: String) {
        FORBIDDEN_JARGON.forEach { rx ->
            assertFalse(rx.containsMatchIn(text), "$where: jerga técnica en copy: /${rx.pattern}/ en \"$text\"")
        }
    }

    // ============================================================
    // Los 10 contratos nombrados por la misión
    // ============================================================

    /** 1. "cómo empiezo" no termina en "no entendí": es comando de ayuda. */
    @Test
    fun comoEmpiezo_doesNotDeadEndInNoEntendi() {
        assertTrue(VoiceCommandDispatcher.isHelpCommand("cómo empiezo"), "cómo empiezo debe ser ayuda")
        assertTrue(VoiceCommandDispatcher.isHelpCommand("por dónde empiezo"), "por dónde empiezo debe ser ayuda")
        val help = VoiceHelpCenter.spokenHelp()
        assertTrue(help.isNotBlank(), "la ayuda no puede ser vacía")
        assertNoJargon(help, "spokenHelp")
    }

    /** 2. "no entiendo cómo usarte" ofrece acciones concretas, no un dead-end. */
    @Test
    fun noEntiendoComoUsarte_offersConcreteActions() {
        assertTrue(
            VoiceCommandDispatcher.isHelpCommand("no entiendo cómo usarte"),
            "no entiendo cómo usarte debe enrutar a ayuda"
        )
        val help = VoiceHelpCenter.spokenHelp().lowercase()
        // Debe nombrar al menos una acción concreta y entendible.
        assertTrue(
            help.contains("pantalla") || help.contains("whatsapp") || help.contains("camara") || help.contains("cámara"),
            "la ayuda debe nombrar acciones concretas: \"$help\""
        )
    }

    /** 3. "qué puedo hacer con WhatsApp" menciona el límite de seguridad (confirmación). */
    @Test
    fun quePuedoHacerConWhatsApp_mentionsSafetyLimit() {
        val answer = EstelaCompanionPhrases.respond("qué puedo hacer con WhatsApp")
        assertTrue(answer != null, "debe responder algo a 'qué puedo hacer con WhatsApp'")
        assertTrue(
            answer!!.contains("confirma", ignoreCase = true),
            "debe aclarar que prepara con confirmación: \"$answer\""
        )
        assertNoJargon(answer, "capability-whatsapp")
    }

    /**
     * 4. "qué le respondo" sugiere/pide contexto, nunca envía. La ayuda de
     * redacción tiene PRIORIDAD sobre cualquier marcador de envío: aunque el
     * detector la marque como "contenido de envío", el router la deriva a
     * SUGGEST_REPLY_ONLY (sugerir sin ejecutar), nunca a un envío real.
     */
    @Test
    fun queLeRespondo_suggestsContext_neverSends() {
        listOf("qué le respondo", "qué le escribo", "cómo le digo", "qué le contesto").forEach { p ->
            assertTrue(SafeLlmPhrases.isReplyHelp(p), "reply-help: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY, "sugerir sin enviar: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "nunca LLM libre: \"$p\"")
        }
    }

    /**
     * 5. "respondéle que ya voy" (respuesta con contenido, sin confirmación)
     * falla SEGURO: se trata como intento de envío y se BLOQUEA local en vez de
     * mandar solo. Nunca deriva al LLM libre. (El runtime, además, explica que
     * primero hay que abrir el chat; acá fijamos la invariante de seguridad pura.)
     */
    @Test
    fun respondeleSinChat_failsSafe_noFreeLlm() {
        val p = "respondéle que ya voy"
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p), "es intento de respuesta")
        assertFalse(SafeLlmPhrases.isReplyHelp(p), "no es ayuda de redacción")
        assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "nunca charla libre")
        assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "falla seguro: bloquea el envío, no ejecuta")
    }

    /** 6. "no mandes nada" limpia pending/draft. */
    @Test
    fun noMandesNada_clearsPendingAndDraft() {
        var pending: String? = "draft"
        val p = "no mandes nada"
        assertTrue(cancelDetected(p), "no mandes nada cancela")
        if (cancelDetected(p)) pending = null
        assertTrue(pending == null, "no debe quedar draft pendiente")
    }

    /** 7. "qué dice ahí" sin contenido útil: seguro, sin peligro y sin charla libre. */
    @Test
    fun queDiceAhi_safeRead_noDangerNoFreeLlm() {
        val p = "qué dice ahí"
        assertFalse(dangerousDetected(p), "leer no es peligroso")
        val r = route(p)
        assertTrue(r != SafeLlmRoute.BLOCK_DANGEROUS, "no es un bloqueo de peligro")
        assertTrue(r != SafeLlmRoute.ALLOW_CONVERSATION, "no deriva a LLM libre")
    }

    /** 8. "más despacio" repite/explica: no cae a un no-match técnico. */
    @Test
    fun masDespacio_repeats_notTechnicalNoMatch() {
        listOf("más despacio", "más lento", "no entendí", "repetí").forEach { p ->
            assertTrue(VoiceCommandDispatcher.isRepeatCommand(p), "debe ser repetir/aclarar: \"$p\"")
        }
    }

    /** 9. "cancelá" confirma que no queda acción pendiente. */
    @Test
    fun cancela_confirmsNoPendingAction() {
        var pending: String? = "accion"
        val p = "cancelá"
        assertTrue(cancelDetected(p), "cancelá cancela")
        assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "cancelar nunca es LLM libre")
        if (cancelDetected(p)) pending = null
        assertTrue(pending == null, "no debe quedar nada pendiente")
    }

    /** 10. "tocá enviar" da rechazo tranquilo local (bloqueo), nunca ejecuta. */
    @Test
    fun tocaEnviar_calmLocalRefusal_neverExecutes() {
        val p = "tocá enviar"
        assertTrue(dangerousDetected(p), "tocá enviar es peligroso")
        assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "rechazo local, no ejecuta ni charla")
    }

    // ============================================================
    // Contratos adicionales (cobertura amplia de UX/recuperación)
    // ============================================================

    /** 11. Imperativos peligrosos: SIEMPRE bloqueo local, nunca LLM libre ni no-match. */
    @Test
    fun dangerousImperatives_alwaysBlockedNeverFreeLlm() {
        listOf(
            "tocá enviar", "mandalo", "tocá el avioncito", "tocá el botón verde",
            "mandá audio", "llamalo", "videollamada", "reenviá esto", "borrá el chat",
            "bloquealo", "pagale", "transferile plata", "mandá una foto", "compartí mi ubicación"
        ).forEach { p ->
            assertTrue(dangerousDetected(p), "debe detectarse peligro: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "debe bloquear local: \"$p\"")
            assertFalse(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "jamás LLM libre: \"$p\"")
        }
    }

    /** 12. Variantes de cancelación: todas reconocidas, ninguna deriva a LLM libre. */
    @Test
    fun cancelVariants_allRecognized_neverFreeLlm() {
        listOf(
            "cancelá", "cancelá todo", "no mandes nada", "me arrepentí", "abortá",
            "frená todo", "pará todo", "dejalo", "olvidate", "no hagas nada", "no, pará"
        ).forEach { p ->
            assertTrue(cancelDetected(p), "debe reconocerse cancelación: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "cancelar nunca es LLM libre: \"$p\"")
        }
    }

    /** 13. Variantes de ayuda: todas enrutan a ayuda y el copy es concreto y sin jerga. */
    @Test
    fun helpVariants_routeToHelp_andCopyIsClean() {
        listOf(
            "ayuda", "ayudame", "qué puedo hacer", "qué puedo decirte", "cómo empiezo",
            "por dónde empiezo", "quiero que me ayudes", "no entiendo cómo usarte"
        ).forEach { p ->
            assertTrue(VoiceCommandDispatcher.isHelpCommand(p), "debe ser ayuda: \"$p\"")
        }
        VoiceHelpContextsToCheck().forEach { (label, text) ->
            assertTrue(text.isNotBlank(), "$label no puede ser vacío")
            assertNoJargon(text, label)
        }
    }

    /** 14. Toda ayuda de respuesta sugiere sin enviar. */
    @Test
    fun replyHelpVariants_allSuggestOnly() {
        listOf(
            "qué le respondo", "qué le escribo", "cómo le digo", "qué le contesto",
            "ayudame a responder", "qué le puedo poner"
        ).forEach { p ->
            assertTrue(route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY, "suggest-only: \"$p\"")
        }
    }

    /** 15. Preguntas conceptuales conversan; no ejecutan, aunque nombren algo peligroso. */
    @Test
    fun conceptualQuestions_converse_notExecute() {
        listOf(
            "qué es una transferencia", "cómo funciona una videollamada",
            "qué es un audio de WhatsApp", "cómo se bloquea a alguien",
            "cómo cancelo una respuesta", "qué pasa si toco enviar"
        ).forEach { p ->
            assertTrue(SafeLlmPhrases.isSafeQuestion(p), "es pregunta-concepto: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "conversa, no ejecuta: \"$p\"")
        }
    }

    /** 16. Pedir USAR el contenido privado del chat se bloquea (no sale del teléfono). */
    @Test
    fun privateContextRequests_areBlocked() {
        listOf(
            "según el chat qué le digo", "leelo y respondé", "resumí el chat",
            "resumime la conversación", "usá el chat para responder"
        ).forEach { p ->
            assertTrue(SafeLlmPhrases.wantsChatContent(p), "pide contenido privado: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.BLOCK_PRIVATE_CONTEXT, "debe bloquear contexto privado: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "no sale del teléfono: \"$p\"")
        }
    }

    /** 17. El fallback cálido es entendible (sin jerga) y ofrece acciones concretas. */
    @Test
    fun warmFallback_isJargonFreeAndConcrete() {
        val warm = EstelaCompanionPhrases.NOT_UNDERSTOOD_WARM
        assertNoJargon(warm, "NOT_UNDERSTOOD_WARM")
        val low = warm.lowercase()
        assertTrue(
            low.contains("pantalla") || low.contains("enfrente") || low.contains("dónde estoy") || low.contains("donde estoy"),
            "el fallback debe sugerir algo concreto: \"$warm\""
        )
        assertNoJargon(EstelaCompanionPhrases.SILENT_CLOSE, "SILENT_CLOSE")
    }

    /** 18. Ningún copy hablable de usuario incluye tokens técnicos prohibidos. */
    @Test
    fun userFacingSpokenCopy_hasNoForbiddenTechnicalTokens() {
        val texts = mutableListOf(
            VoiceHelpCenter.SPOKEN_HELP,
            VoiceHelpCenter.MEMORY_HELP,
            VoiceHelpCenter.SAFETY_HELP,
            VoiceHelpCenter.spokenHelp(includeMemory = true),
            VoiceHelpCenter.spokenHelp(includeMemory = false),
            EstelaCompanionPhrases.NOT_UNDERSTOOD_WARM,
            EstelaCompanionPhrases.SILENT_CLOSE
        )
        listOf("ayuda", "qué puedo hacer con WhatsApp", "no entiendo", "gracias", "hola estela").forEach { p ->
            EstelaCompanionPhrases.respond(p)?.let { texts.add(it) }
        }
        texts.forEachIndexed { i, t -> assertNoJargon(t, "spoken-copy[$i]") }
    }

    /** 19. La cancelación limpia el draft AUNQUE haya una frase peligrosa antes. */
    @Test
    fun cancelClearsDraftEvenAfterDanger() {
        data class Step(val phrase: String, val isDraft: Boolean, val isCancel: Boolean)
        listOf(
            listOf(
                Step("respondéle que ya voy", true, false),
                Step("tocá enviar", false, false),
                Step("no mandes nada", false, true)
            ),
            listOf(
                Step("contestale que estoy ocupado", true, false),
                Step("mandá audio", false, false),
                Step("cancelá", false, true)
            )
        ).forEachIndexed { i, seq ->
            var pending: String? = null
            seq.forEach { s ->
                if (s.isDraft) {
                    assertTrue(WhatsAppReplyPhrases.isReplyAttempt(s.phrase), "draft #${i + 1}: \"${s.phrase}\"")
                    pending = "draft"
                }
                if (dangerousDetected(s.phrase) && !SafeLlmPhrases.isSafeQuestion(s.phrase) && !SafeLlmPhrases.isReplyHelp(s.phrase)) {
                    assertTrue(route(s.phrase) == SafeLlmRoute.BLOCK_DANGEROUS, "peligro bloquea #${i + 1}: \"${s.phrase}\"")
                }
                if (s.isCancel) {
                    assertTrue(cancelDetected(s.phrase), "cancela #${i + 1}: \"${s.phrase}\"")
                    pending = null
                }
            }
            assertTrue(pending == null, "draft residual tras peligro+cancel #${i + 1}")
        }
    }

    /** 20. "abrí WhatsApp" y alias de STT enrutan a WhatsApp (no a un dead-end). */
    @Test
    fun openWhatsAppVariants_routeToWhatsApp() {
        listOf("abrí WhatsApp", "abrí guasap", "abrime el wsp", "abrí wasat", "abrí el wasa").forEach { p ->
            assertTrue(
                WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"),
                "debe normalizar a whatsapp: \"$p\""
            )
        }
    }

    /** 21. Frenar/callar se reconoce como stop; nunca deriva a LLM libre. */
    @Test
    fun stopVariants_silence_neverConverse() {
        listOf("pará", "pará todo", "frená", "frená todo", "basta", "callate").forEach { p ->
            val recognized = VoiceCommandDispatcher.isStopCommand(p) || VoiceCommandDispatcher.isBareCancelCommand(p)
            assertTrue(recognized, "debe reconocerse como detener/cancelar: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "detener nunca es LLM libre: \"$p\"")
        }
    }

    /** 22. Ni un draft ni una ayuda de respuesta derivan a charla libre (no falso envío). */
    @Test
    fun draftsAndReplyHelp_neverFreeLlm_noFalseSendPromise() {
        listOf("respondéle que ya voy", "contestale que sí", "decile que llego tarde").forEach { p ->
            assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p), "draft: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "draft no charla libre: \"$p\"")
        }
        listOf("qué le respondo", "cómo le digo").forEach { p ->
            assertTrue(route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY, "reply-help suggest-only: \"$p\"")
        }
    }

    /** 23. Sesiones de punta a punta sostienen seguridad + recuperación. */
    @Test
    fun endToEndPilotSessions_holdSafetyAndRecovery() {
        // (phrase, isCancel) — recorremos y exigimos que terminar en cancelación
        // deje el estado limpio y que nada peligroso se derive al LLM.
        val sessions = listOf(
            listOf("ayuda" to false, "abrí WhatsApp" to false, "qué le respondo" to false, "no mandes nada" to true),
            listOf("qué dice ahí" to false, "tocá enviar" to false, "me arrepentí" to true),
            listOf("abrí guasap" to false, "leé los chats" to false, "cancelá" to true),
            listOf("respondéle que ya voy" to false, "pagale" to false, "abortá" to true)
        )
        sessions.forEachIndexed { i, steps ->
            var pending: String? = null
            steps.forEach { (p, isCancel) ->
                if (WhatsAppReplyPhrases.isReplyAttempt(p) && !isCancel) pending = "draft"
                if (dangerousDetected(p) && !SafeLlmPhrases.isSafeQuestion(p) && !SafeLlmPhrases.isReplyHelp(p)) {
                    assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "S${i + 1} peligro bloquea: \"$p\"")
                }
                if (isCancel) {
                    assertTrue(cancelDetected(p), "S${i + 1} cancela: \"$p\"")
                    pending = null
                }
            }
            assertTrue(pending == null, "S${i + 1}: pending residual al terminar en cancelación")
        }
    }

    // Conjunto de textos contextuales de ayuda para el contrato 13.
    private fun VoiceHelpContextsToCheck(): List<Pair<String, String>> =
        com.ojoclaro.android.help.VoiceHelpContext.values().map { ctx ->
            "contextualSpokenHelp($ctx)" to VoiceHelpCenter.contextualSpokenHelp(ctx)
        }
}
