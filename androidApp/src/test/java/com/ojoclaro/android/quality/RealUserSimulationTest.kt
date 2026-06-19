package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.ConversationShortMemory
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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MISIÓN PESADA — simulación de persona NO VIDENTE real hablándole a Estela con
 * muletillas, dudas, frases partidas, miedo a equivocarse y pedidos de ayuda.
 * Codifica el CONTRATO de seguridad/UX (lo que DEBE pasar) sobre los clasificadores
 * puros — el mismo espejo que usa GlobalAssistantService. PURO/determinista.
 *
 * NO ejercita la cadena de handlers de GAS (orden), solo las clasificaciones que
 * gobiernan la seguridad. Las acciones reales (tap/envío) están gateadas por flags
 * + único call-site y se validan en device.
 */
class RealUserSimulationTest {

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

    private fun route(p: String, waActive: Boolean = true): SafeLlmRoute =
        SafeLlmFallbackPolicy.decide(
            SafeLlmSignals(
                conversational = false,
                whatsAppActive = waActive,
                namesWhatsApp = waActive,
                looksDangerous = dangerousDetected(p),
                looksLikeMessageContent = false,
                looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
                wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
                looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
            )
        )

    // ---------- FASE 1: persona confundida pide ayuda ----------
    @Test
    fun fase1_helpRequestsAreRecognizedLocally() {
        // Una persona que no sabe usar la app pide ayuda de muchas formas. Debe caer
        // en ayuda LOCAL clara, no en "no entendí" ni en el LLM libre.
        listOf(
            "ayuda", "ayudame", "qué podés hacer", "qué puedo decirte",
            "no entiendo cómo usarte", "no entiendo cómo se usa",
            "no sé cómo usarte", "no sé usar esto", "cómo te uso", "cómo se usa esto"
        ).forEach { p ->
            assertTrue(
                VoiceCommandDispatcher.isHelpCommand(p),
                "help request must be recognized locally: \"$p\""
            )
        }
    }

    @Test
    fun fase1_aliasesAndFillersRouteToWhatsApp() {
        listOf("abrí guasap", "abrime el wasa", "abrí wasat", "che estela abrí guasap",
            "porfa abrime WhatsApp", "leé los chats del guasap").forEach { p ->
            assertTrue(
                WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"),
                "alias/filler must canonicalize to whatsapp: \"$p\""
            )
        }
    }

    @Test
    fun fase1_panicCancellationsAreLocalNeverLlm() {
        listOf("pará", "pará todo", "frená todo", "abortá", "me arrepentí",
            "no hagas nada", "olvidate", "dejalo", "cancelá todo", "no, cancelá").forEach { p ->
            assertTrue(cancelDetected(p), "panic cancel must be recognized locally: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "cancel must not go to free LLM: \"$p\"")
        }
    }

    // ---------- FASE 2: leer/responder sin enviar ----------
    @Test
    fun fase2_replyHelpSuggestsOnly() {
        listOf("qué le respondo", "qué le escribo", "cómo le digo", "ayudame a responder")
            .forEach { p ->
                assertTrue(SafeLlmPhrases.isReplyHelp(p), "reply-help: \"$p\"")
                assertTrue(route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY, "must suggest-only: \"$p\"")
            }
    }

    @Test
    fun fase2_replyComposeIsADraftNotReplyHelpNorSend() {
        // "respondéle que ya voy" = borrador (el handler de draft lo toma ANTES del
        // critical guard). Acá garantizamos que NO se confunde con reply-help (que
        // sugeriría) y que SÍ se extrae como intento de respuesta (borrador, no envío).
        listOf("respondéle que ya voy", "contestale que estoy ocupado",
            "decile que después le hablo").forEach { p ->
            assertFalse(SafeLlmPhrases.isReplyHelp(p), "compose-with-text is NOT reply-help: \"$p\"")
            assertTrue(WhatsAppReplyPhrases.isReplyAttempt(p), "must be a draft attempt: \"$p\"")
        }
    }

    @Test
    fun fase2_tapSendIsDangerous() {
        assertTrue(dangerousDetected("tocá enviar"), "tocá enviar must be dangerous")
        assertTrue(route("tocá enviar") == SafeLlmRoute.BLOCK_DANGEROUS, "tocá enviar must block")
    }

    // ---------- FASE 3: comandos partidos / fragmentos ----------
    @Test
    fun fase3_fragmentsAreSafeNeverThrowNeverFakeDanger() {
        val frags = listOf("abrí", "abrí gua", "leé", "leé los", "respondéle", "respondéle que",
            "buscá", "buscá a", "tocá", "cancelá")
        frags.forEach { p ->
            dangerousDetected(p); cancelDetected(p); route(p)
            WhatsAppPhraseNormalizer.normalize(p)
        }
        // Fragmentos sin verbo mutante no deben fingir peligro.
        listOf("abrí", "abrí gua", "leé", "leé los", "buscá", "buscá a").forEach { p ->
            assertFalse(dangerousDetected(p), "harmless fragment must not be dangerous: \"$p\"")
        }
        // "tocá" solo (sin objeto) no debe ser bloqueado como envío.
        assertFalse(dangerousDetected("tocá"), "bare 'tocá' must not be dangerous")
    }

    // ---------- FASE 4: usuario ansioso intenta forzar ----------
    @Test
    fun fase4_anxiousForcingIsAlwaysBlocked() {
        listOf(
            "dale mandalo", "tocá el verde", "tocá el avioncito", "apretá el botón de enviar",
            "sí, sí, mandalo", "por esta vez mandalo", "hacelo igual no pasa nada mandalo",
            "no pasa nada, mandalo", "mandale un audio", "llamalo", "hacé videollamada",
            "reenviá esto", "borrá el chat", "bloquealo", "pagale", "transferile plata",
            "hacé una transferencia"
        ).forEach { p ->
            assertTrue(dangerousDetected(p), "anxious-forcing must be dangerous: \"$p\"")
            assertFalse(SafeLlmPhrases.isSafeQuestion(p), "forcing must not be safe-question: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.BLOCK_DANGEROUS, "forcing must block: \"$p\"")
        }
    }

    @Test
    fun fase4_bareYesNeverConfirmsSend() {
        listOf("sí", "sí sí", "dale", "ok", "obvio", "claro").forEach { p ->
            assertFalse(WhatsAppVoiceSendPhrases.isConfirmSend(p), "bare yes must not confirm: \"$p\"")
            assertFalse(WhatsAppReplyPhrases.isStrongSendConfirm(p), "bare yes must not strong-confirm: \"$p\"")
        }
    }

    // ---------- FASE 5: conceptual vs acción ----------
    @Test
    fun fase5_conceptualQuestionsConverseInBothContexts() {
        val conceptual = listOf(
            "qué es una transferencia", "cómo funciona una transferencia",
            "qué significa transferencia", "cómo se manda un mensaje por WhatsApp",
            "cómo se bloquea a alguien en WhatsApp", "cómo funciona una videollamada",
            "qué es un audio de WhatsApp", "qué pasa si toco enviar"
        )
        conceptual.forEach { p ->
            assertTrue(SafeLlmPhrases.isSafeQuestion(p), "conceptual must be safe-question: \"$p\"")
            // Conversa con o sin WhatsApp activo (la pregunta-concepto rescata el egress).
            assertTrue(route(p, waActive = true) == SafeLlmRoute.ALLOW_CONVERSATION, "wa-active converse: \"$p\"")
            assertTrue(route(p, waActive = false) == SafeLlmRoute.ALLOW_CONVERSATION, "no-wa converse: \"$p\"")
        }
    }

    // ---------- FASE 6: privacidad / PII ----------
    @Test
    fun fase6_dictatedSecretsAreRedactedBeforeLlm() {
        // El VALOR sensible nunca debe salir crudo. Conservamos la palabra clave.
        val cases = listOf(
            "mi pin es 1234", "mi clave es 9988", "mi código es 445566",
            "la contraseña es azul123", "usá este código 1234", "el pin 1234",
            "mi clave 9988"
        )
        cases.forEach { raw ->
            val out = LlmInputSanitizer.sanitize(raw)
            // El número/secreto dictado no debe sobrevivir literal.
            listOf("1234", "9988", "445566", "azul123").forEach { secret ->
                if (raw.contains(secret)) {
                    assertFalse(
                        out.contains(secret),
                        "dictated secret \"$secret\" must be redacted: \"$raw\" -> \"$out\""
                    )
                }
            }
        }
        // Teléfono/DNI largos también se redactan (corrida de dígitos).
        listOf("mi dni es 12345678", "mi teléfono es 2991234567").forEach { raw ->
            val out = LlmInputSanitizer.sanitize(raw)
            assertTrue(out.contains("[número]") || out.contains("[dato]"), "long id redacted: \"$raw\" -> \"$out\"")
        }
    }

    @Test
    fun fase6_normalTextWithKeywordsIsNotOverRedacted() {
        // No romper texto legítimo que menciona la palabra sin dictar un valor.
        listOf("qué es un pin", "la clave musical", "cómo cambio mi clave",
            "código postal 1234").forEach { raw ->
            val out = LlmInputSanitizer.sanitize(raw)
            assertFalse(out.contains("[dato]"), "must not over-redact normal text: \"$raw\" -> \"$out\"")
        }
    }

    // ---------- CYCLE 2: lecturas, búsquedas y bordes de cancelación ----------
    @Test
    fun reads_and_searches_areNeverDangerous() {
        // Verbos de LECTURA / BÚSQUEDA jamás se clasifican como acción peligrosa.
        listOf(
            "leé los chats", "qué chats hay", "qué dice ahí", "leé eso", "qué ves",
            "describime lo que hay", "leé los mensajes",
            "buscá a CONTACTO_A en guasap", "encontrá el chat de CONTACTO_A en wsp",
            "abrí el chat de Ana Prueba", "abrí el chat de José Demo"
        ).forEach { p ->
            assertFalse(dangerousDetected(p), "read/search must not be dangerous: \"$p\"")
            assertTrue(
                route(p) != SafeLlmRoute.BLOCK_DANGEROUS,
                "read/search must not block-dangerous (got ${route(p)}): \"$p\""
            )
        }
    }

    @Test
    fun search_aliasesCanonicalize() {
        listOf("buscá a CONTACTO_A en guasap", "encontrá el chat de CONTACTO_A en wsp")
            .forEach { p ->
                assertTrue(
                    WhatsAppPhraseNormalizer.normalize(p).contains("whatsapp"),
                    "search alias must canonicalize: \"$p\""
                )
            }
    }

    @Test
    fun panic_noPara_isRecognizedAsCancel() {
        // "no pará" / "no, pará" = "no, frená" → intención de cancelar (dirección segura).
        listOf("no pará", "no, pará").forEach { p ->
            assertTrue(cancelDetected(p), "panic 'no pará' must be a cancel: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "must not go to free LLM: \"$p\"")
        }
        // "no pares" = "no dejes de" (negación) → NO debe cancelar (como "no canceles").
        assertFalse(cancelDetected("no pares"), "negation 'no pares' must NOT cancel")
    }

    // ---------- PRE-PILOTO: onboarding del primer minuto ----------
    @Test
    fun prepilot_onboardingPleasReachLocalHelp() {
        // Primer minuto de una persona no vidente: "cómo empiezo" / "quiero que me
        // ayudes" caen a la ayuda LOCAL clara, no a no-match/LLM.
        listOf("cómo empiezo", "por dónde empiezo", "quiero que me ayudes",
            "necesito que me ayudes").forEach { p ->
            assertTrue(
                VoiceCommandDispatcher.isHelpCommand(p),
                "onboarding plea must reach local help: \"$p\""
            )
        }
    }

    @Test
    fun prepilot_whatsappCapabilityQuestionIsAnswered() {
        // "qué puedo hacer con WhatsApp" → respuesta de capacidades WA-aware (compañía
        // local), no dead-end. Cubre alias (guasap/wsp normalizan a whatsapp).
        listOf("qué puedo hacer con WhatsApp", "qué puedo hacer con el guasap",
            "qué se puede hacer con WhatsApp").forEach { p ->
            assertTrue(
                EstelaCompanionPhrases.respond(p) != null,
                "WhatsApp capability question must be answered locally: \"$p\""
            )
        }
    }

    @Test
    fun prepilot_tooFastRequestRepeatsInsteadOfDeadEnd() {
        // No hay control de velocidad (sería feature nueva). Lo honesto: "más
        // despacio" / "hablás muy rápido" → REPETIR lo último (o "todavía no dije
        // nada"), nunca un "no entendí" vacío.
        listOf("más despacio", "más lento", "hablás muy rápido", "vas muy rápido")
            .forEach { p ->
                assertTrue(
                    VoiceCommandDispatcher.isRepeatCommand(p),
                    "speed plea must route to repeat (graceful): \"$p\""
                )
                assertFalse(dangerousDetected(p), "speed plea must not be dangerous: \"$p\"")
            }
    }

    // ---------- PRE-PILOTO: la memoria corta NUNCA guarda secretos ----------
    @Test
    fun prepilot_shortMemoryNeverStoresDictatedSecrets() {
        val mem = ConversationShortMemory()
        listOf("mi pin es 1234", "mi clave es azul123", "mi número es 2991234567",
            "mi cvv es 123", "mi contraseña es secreta99", "estoy nervioso").forEach {
            mem.recordUser(it)
        }
        val snap = mem.snapshot().joinToString(" ")
        listOf("1234", "azul123", "2991234567", "secreta99").forEach { secret ->
            assertFalse(snap.contains(secret), "short memory must not store secret \"$secret\": [$snap]")
        }
    }

    @Test
    fun conceptual_howToCancel_converses() {
        // Pregunta CONCEPTUAL de cómo cancelar/no enviar → conversa, no ejecuta.
        listOf("cómo cancelo una respuesta", "cómo hago para que no mandes nada",
            "cómo cancelo un envío").forEach { p ->
            assertTrue(SafeLlmPhrases.isSafeQuestion(p), "how-to-cancel must be safe-question: \"$p\"")
            assertTrue(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "must converse: \"$p\"")
        }
    }
}
