package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Memoria conversacional LOCAL y REDACTADA de WhatsApp.
 *
 * Guarda lo justo para responder "¿de qué estábamos hablando?", "¿a quién le
 * estaba escribiendo?", "¿qué le iba a mandar?" — SIN números completos ni
 * contenido crudo de mensajes. Defensa: cualquier corrida de 7+ dígitos en un
 * label/resumen se reemplaza por "[número]".
 *
 * Sólo runtime (no disco, no red). Thread-safe. El reloj entra por parámetro
 * para que sea testeable sin depender de System.currentTimeMillis().
 */
object WhatsAppConversationContext {

    data class Snapshot(
        /** Nombre para mostrar del chat/destino. NUNCA un número. */
        val chatLabelRedacted: String? = null,
        /** Últimos 4 dígitos, o null. Nunca el número completo. */
        val phoneEnding: String? = null,
        val lastInteractionMillis: Long = 0L,
        /** Token corto de intención: "open_chat", "prepare_draft", "reply", ... */
        val lastUserIntent: String? = null,
        /** Acción pendiente: "send_message", "call", "video_call", ... o null. */
        val pendingAction: String? = null,
        /** Resumen hablado por Estela (ya redactado). */
        val lastAssistantSummary: String? = null,
        /** Longitud del último borrador. Nunca el texto. */
        val lastDraftLen: Int = 0,
        /** Cantidad de mensajes leídos. Nunca el contenido. */
        val lastMessageCount: Int = 0
    )

    private val lock = Any()
    private var state: Snapshot? = null

    private const val MAX_LABEL = 60
    private const val MAX_SUMMARY = 160
    private val LONG_DIGITS = Regex("\\d{7,}")

    fun current(): Snapshot? = synchronized(lock) { state }

    fun noteDestination(label: String?, phoneEnding: String?, nowMillis: Long) = mutate(nowMillis) {
        it.copy(
            chatLabelRedacted = redact(label, MAX_LABEL),
            phoneEnding = phoneEnding?.filter(Char::isDigit)?.takeLast(4)?.takeIf { d -> d.isNotBlank() }
        )
    }

    fun noteIntent(intent: String?, nowMillis: Long) = mutate(nowMillis) {
        it.copy(lastUserIntent = intent?.take(40))
    }

    fun notePendingAction(action: String?, nowMillis: Long) = mutate(nowMillis) {
        it.copy(pendingAction = action?.take(40))
    }

    fun noteDraftLen(len: Int, nowMillis: Long) = mutate(nowMillis) {
        it.copy(lastDraftLen = len.coerceAtLeast(0))
    }

    fun noteMessageCount(count: Int, nowMillis: Long) = mutate(nowMillis) {
        it.copy(lastMessageCount = count.coerceAtLeast(0))
    }

    fun noteAssistantSummary(summary: String?, nowMillis: Long) = mutate(nowMillis) {
        it.copy(lastAssistantSummary = redact(summary, MAX_SUMMARY))
    }

    /** Olvida todo el contexto de WhatsApp. */
    fun clear() = synchronized(lock) { state = null }

    /** Recuerdo hablado, corto y seguro. */
    fun spokenRecall(): String {
        val s = current() ?: return "No tengo contexto reciente de WhatsApp."
        val parts = mutableListOf<String>()
        s.chatLabelRedacted?.let { label ->
            val ending = s.phoneEnding?.let { ", terminado en $it" } ?: ""
            parts += "Lo último fue con $label$ending"
        }
        when (s.pendingAction) {
            "send_message" -> parts += "tenías un mensaje sin enviar"
            "call" -> parts += "ibas a hacer una llamada"
            "video_call" -> parts += "ibas a hacer una videollamada"
            "send_audio" -> parts += "ibas a mandar un audio"
        }
        if (s.lastDraftLen > 0 && s.pendingAction == null) {
            parts += "había un borrador escrito"
        }
        if (parts.isEmpty()) return "No tengo contexto reciente de WhatsApp."
        return parts.joinToString(". ").replaceFirstChar { it.uppercase() } + ". No voy a enviar nada sin tu confirmación."
    }

    private inline fun mutate(nowMillis: Long, block: (Snapshot) -> Snapshot) {
        synchronized(lock) {
            state = block(state ?: Snapshot()).copy(lastInteractionMillis = nowMillis)
        }
    }

    private fun redact(text: String?, max: Int): String? {
        val t = text?.replace(Regex("\\s+"), " ")?.trim() ?: return null
        if (t.isBlank()) return null
        return LONG_DIGITS.replace(t, "[número]").take(max)
    }
}
