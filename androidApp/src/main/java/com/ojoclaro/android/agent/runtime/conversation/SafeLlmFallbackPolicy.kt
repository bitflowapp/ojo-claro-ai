package com.ojoclaro.android.agent.runtime.conversation

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Safe LLM Fallback Router — decide qué hacer con una frase que NINGÚN handler
 * local atendió, en vez de caer a "no entendí". El LLM NUNCA ejecuta: a lo sumo
 * se deriva a conversación (con la entrada sanitizada por el caller), se pide
 * aclaración, se sugiere una respuesta SIN enviar, o se bloquea local.
 *
 * Invariante dura: una acción PELIGROSA imperativa (mutante/irreversible) o un
 * pedido de usar contenido PRIVADO del chat JAMÁS terminan en ALLOW_CONVERSATION.
 *
 * PURO: sin Android, sin estado, sin IO. El caller (GlobalAssistantService) arma
 * las señales con sus clasificadores locales y ejecuta la decisión con sus
 * handlers existentes (que vuelven a validar todo).
 */
enum class SafeLlmRoute {
    /** Q&A / charla segura → conversación libre con el LLM (entrada sanitizada). */
    ALLOW_CONVERSATION,

    /** Frase ambigua con WhatsApp activo → aclarar local, sin LLM. */
    ASK_CLARIFY,

    /** "¿qué le respondo?" → sugerir ideas SIN enviar y pidiendo permiso de contexto. */
    SUGGEST_REPLY_ONLY,

    /** Acción peligrosa imperativa nombrando WhatsApp / con WhatsApp activo → bloqueo. */
    BLOCK_DANGEROUS,

    /** Pide usar/mandar contenido privado del chat → no sale del teléfono sin permiso. */
    BLOCK_PRIVATE_CONTEXT,

    /** No matcheó nada seguro: el caller deja seguir su fallback local (no "no entendí"). */
    NO_MATCH_SAFE_HELP
}

/** Señales (todas locales, sin contenido privado) para decidir la ruta segura. */
data class SafeLlmSignals(
    val conversational: Boolean,
    val whatsAppActive: Boolean,
    val namesWhatsApp: Boolean,
    val looksDangerous: Boolean,
    val looksLikeMessageContent: Boolean,
    val looksLikeReplyHelp: Boolean,
    val wantsChatContent: Boolean,
    val looksLikeSafeQuestion: Boolean
)

object SafeLlmFallbackPolicy {

    fun decide(s: SafeLlmSignals): SafeLlmRoute = when {
        // Pedir resumir/USAR el contenido del chat → no sale afuera sin permiso.
        // Va PRIMERO (prioridad privacidad): una frase que nombra contenido privado
        // ("según el chat qué le digo") es a la vez reply-help; gana el bloqueo de
        // contexto privado en vez de la sugerencia. No depende del foreground.
        s.wantsChatContent -> SafeLlmRoute.BLOCK_PRIVATE_CONTEXT

        // "¿qué le respondo?" es ayuda de redacción, no un envío ni uso de contenido
        // privado. Se sugiere sin ejecutar incluso si el harness tapa el foreground.
        s.looksLikeReplyHelp -> SafeLlmRoute.SUGGEST_REPLY_ONLY

        // Acción peligrosa IMPERATIVA (no una pregunta-concepto) detectada: SIEMPRE
        // negativa LOCAL explícita + alternativa segura, sin importar el contexto.
        // Nunca NO_MATCH genérico ni LLM. La negativa NO ejecuta nada (no toca
        // enviar/llamar/video/audio). Las preguntas-concepto ("qué es una
        // transferencia") quedan fuera por !looksLikeSafeQuestion.
        s.looksDangerous && !s.looksLikeSafeQuestion -> SafeLlmRoute.BLOCK_DANGEROUS

        // Contenido de mensaje ambiguo ("estoy llegando") con WhatsApp activo →
        // aclarar local, nunca LLM (defensa en profundidad: el clarifier ya corrió).
        s.whatsAppActive && s.looksLikeMessageContent -> SafeLlmRoute.ASK_CLARIFY

        // Charla/pregunta segura → conversación. La pregunta-concepto rescata frases
        // que el ConversationGate sobre-bloquea por contener un substring de acción
        // ("por qué los MENSAJEs…", "qué es una TRANSFERencia").
        s.conversational -> SafeLlmRoute.ALLOW_CONVERSATION
        s.looksLikeSafeQuestion -> SafeLlmRoute.ALLOW_CONVERSATION

        else -> SafeLlmRoute.NO_MATCH_SAFE_HELP
    }
}

/**
 * Clasificadores puros de intención conversacional segura. Mismo fold() que los
 * parsers hermanos (voseo + acentos), para comparar contra marcadores estables.
 */
object SafeLlmPhrases {

    // Marcadores de pregunta-concepto / pedido de explicación (Q&A general).
    private val QUESTION_MARKERS = listOf(
        "que es", "que son", "que significa", "que quiere decir", "que diferencia",
        "por que", "para que sirve", "para que se usa", "como funciona", "como se usa",
        // "como se " (impersonal) cubre preguntas conceptuales sobre acciones
        // ("como se bloquea a alguien", "como se manda un mensaje"): son
        // explicaciones, NO imperativos (IMPERATIVE_ACTION_START ya descarta los
        // imperativos antes de mirar estos marcadores).
        "como se hace", "como se ", "como puedo", "como hago para", "cual es", "cuales son",
        "cuando se", "cuando hay que", "explicame", "explica ", "contame que",
        "decime que es", "que pasa si", "se puede ", "es verdad que", "que conviene"
    )

    // Verbos IMPERATIVOS de acción al inicio: descartan pregunta-concepto y ayuda.
    private val IMPERATIVE_ACTION_START = Regex(
        "^(?:abri|abrime|manda|mandale|envia|enviale|escribi|escribile|deci|decile|" +
            "borra|elimina|bloquea|reenvia|reporta|denuncia|archiva|silencia|paga|pagale|" +
            "llama|llamale|compart|mostra|tira|graba|reproduci)\\w*\\b"
    )

    private val REPLY_HELP = listOf(
        "que le respondo", "que le contesto", "que le digo", "que le pongo",
        "que puedo responderle", "que puedo contestarle", "que puedo decirle",
        // Variante sin pronombre ("qué puedo responder/contestar"): misma ayuda
        // de redacción; sin -le un no-vidente igual pide ideas para contestar.
        "que puedo responder", "que puedo contestar",
        "como le respondo", "como le contesto", "ayudame a responder",
        "ayudame a contestar", "dame ideas para responder", "que respondo",
        "que contesto", "ayudame a redactar"
    )

    // "resumí/resumime/resumir el chat | la conversación" (tolerante al voseo).
    private val SUMMARIZE_CHAT = Regex("\\bresum\\w*\\s+(?:el chat|la conversacion)\\b")
    private val WANTS_CHAT_CONTENT = listOf(
        "lee el chat y", "leelo y respond", "leelo y contest", "segun el chat",
        "usa el chat", "usando el chat", "con el texto del chat",
        "que dice y respond", "mandale lo que diga el chat"
    )

    fun isSafeQuestion(rawText: String): Boolean {
        val f = fold(rawText)
        if (f.length < 4) return false
        if (IMPERATIVE_ACTION_START.containsMatchIn(f)) return false
        return QUESTION_MARKERS.any { f.contains(it) }
    }

    fun isReplyHelp(rawText: String): Boolean {
        val f = fold(rawText)
        if (IMPERATIVE_ACTION_START.containsMatchIn(f)) return false
        return REPLY_HELP.any { f.contains(it) }
    }

    fun wantsChatContent(rawText: String): Boolean {
        val f = fold(rawText)
        return SUMMARIZE_CHAT.containsMatchIn(f) || WANTS_CHAT_CONTENT.any { f.contains(it) }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
