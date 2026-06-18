package com.ojoclaro.android.agent.runtime.conversation

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases

/**
 * V1.7 — memoria conversacional corta, SOLO en runtime (nunca persistida).
 *
 * Guarda los últimos turnos usuario/Estela para que la charla tenga hilo
 * ("¿Qué probamos?" después de "Estoy nervioso"). Los turnos con pinta de
 * credencial/datos bancarios no se guardan jamás, y las coordenadas exactas
 * tampoco (la ubicación hablada usa calle/precisión, nunca lat/lon).
 *
 * V1.9 — además deriva señales útiles para el LLM conversacional:
 *  - ánimo reciente del usuario (nervioso/cansado/confundido/etc.);
 *  - últimos eventos de capacidad ("inició una ruta", "falló la conexión")
 *    anotados por el servicio con [noteContext].
 * Todo vive en memoria y muere con el proceso.
 */
class ConversationShortMemory(private val maxTurns: Int = 5) {

    private val turns = ArrayDeque<String>()
    private val contextNotes = ArrayDeque<String>()

    @Synchronized
    fun recordUser(text: String) = record("Usuario: ", text)

    @Synchronized
    fun recordAssistant(text: String) = record("Estela: ", text)

    @Synchronized
    fun snapshot(): List<String> = turns.toList()

    /**
     * Snapshot + una línea sintética de contexto (ánimo/eventos) al final,
     * para que el backend la reciba aunque recorte a los últimos 5 ítems.
     */
    @Synchronized
    fun snapshotWithContext(): List<String> {
        val line = contextLine()
        return if (line == null) turns.toList() else turns.toList() + line
    }

    /** Evento de capacidad reciente ("inició una ruta a pie"). Máx 3, sin datos. */
    @Synchronized
    fun noteContext(event: String) {
        val clean = event.trim().take(60)
        if (clean.isBlank()) return
        if (contextNotes.lastOrNull() == clean) return
        contextNotes.addLast(clean)
        while (contextNotes.size > MAX_CONTEXT_NOTES) contextNotes.removeFirst()
    }

    @Synchronized
    fun clear() {
        turns.clear()
        contextNotes.clear()
    }

    internal fun contextLine(): String? {
        val mood = moodHint()
        val notes = contextNotes.toList()
        if (mood == null && notes.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (mood != null) parts += "ánimo reciente del usuario: $mood"
        if (notes.isNotEmpty()) parts += "últimos eventos: ${notes.joinToString(", ")}"
        return "Contexto: " + parts.joinToString("; ")
    }

    /**
     * Señal de ánimo derivada de los turnos del usuario. Heurística simple y
     * honesta: si no hay marca clara, null (el LLM no recibe inventos).
     */
    internal fun moodHint(): String? {
        val joined = turns
            .filter { it.startsWith("Usuario: ") }
            .joinToString(" ")
            .lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u')
        return when {
            listOf("nervios", "ansios").any { joined.contains(it) } -> "nervioso"
            listOf("miedo", "asustad").any { joined.contains(it) } -> "con miedo"
            listOf("cansad", "agotad").any { joined.contains(it) } -> "cansado"
            joined.contains("vergüenza") || joined.contains("verguenza") -> "con vergüenza"
            listOf("confundid", "no se que hacer", "no entiendo").any { joined.contains(it) } ->
                "confundido"
            joined.contains("salio mal") || joined.contains("me equivoque") ->
                "frustrado por un intento fallido"
            joined.contains("perdid") -> "se siente perdido"
            else -> null
        }
    }

    private fun record(prefix: String, text: String) {
        val clean = text.trim().take(MAX_TURN_CHARS)
        if (clean.isBlank()) return
        // Los secretos no entran a la memoria, ni siquiera truncados.
        if (WhatsAppVoiceSendPhrases.looksSensitive(clean)) return
        // Coordenadas exactas tampoco: un decimal de 4+ dígitos es lat/lon.
        if (COORDINATE_PATTERN.containsMatchIn(clean)) return
        turns.addLast(prefix + clean)
        while (turns.size > maxTurns) turns.removeFirst()
    }

    private companion object {
        const val MAX_TURN_CHARS = 140
        const val MAX_CONTEXT_NOTES = 3
        val COORDINATE_PATTERN = Regex("-?\\d{1,3}\\.\\d{4,}")
    }
}
