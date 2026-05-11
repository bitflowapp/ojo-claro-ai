package com.ojoclaro.android.agent.core.preference

import com.ojoclaro.android.agent.core.AgentPreferenceKeys
import com.ojoclaro.android.agent.core.AgentPreferenceSource
import com.ojoclaro.android.agent.core.AgentUserPreference
import java.text.Normalizer

/**
 * Parser determinista para comandos de preferencia hablada.
 *
 * Reglas:
 *  - Solo reconoce frases explícitas. NO infiere de forma silenciosa.
 *  - "Hablame más corto" / "respuestas cortas" → response.length = short
 *  - "Hablame normal" / "respuestas más largas" → response.length = normal
 *  - "Recordá que X es mi contacto principal" → contact.primary = X
 *  - "No guardes preferencias" / "no aprendas de mí" → learning.optIn = false
 *  - "Sí, aprende de mí" → learning.optIn = true
 *  - "Olvidá eso" / "borrá esa preferencia" → devuelve ForgetRequest
 */
class PreferenceCommandParser(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    fun parse(rawText: String): PreferenceCommandResult {
        val key = normalize(rawText)
        if (key.isBlank()) return PreferenceCommandResult.NotARecognizedPreference

        return when {
            key.contains("no guardes preferencias") ||
                key.contains("no guardes mis preferencias") ||
                key.contains("no aprendas de mi") ||
                key.contains("no aprendas") -> setBoolean(
                AgentPreferenceKeys.LEARNING_OPT_IN, value = false
            )

            (key.contains("aprende de mi") && !key.contains("no aprende")) ||
                key.contains("podes aprender") -> setBoolean(
                AgentPreferenceKeys.LEARNING_OPT_IN, value = true
            )

            key.contains("hablame mas corto") ||
                key.contains("respuestas cortas") ||
                key.contains("respondeme corto") ||
                key.contains("respuestas mas cortas") ||
                key.contains("resumime todo") ||
                key.contains("no me leas todo") -> setString(
                AgentPreferenceKeys.RESPONSE_LENGTH,
                value = "short"
            )

            key.contains("hablame normal") ||
                key.contains("respuestas mas largas") ||
                key.contains("respondeme con mas detalle") -> setString(
                AgentPreferenceKeys.RESPONSE_LENGTH,
                value = "normal"
            )

            key.contains("olvida eso") ||
                key.contains("borra esa preferencia") ||
                key.contains("olvida esa preferencia") -> PreferenceCommandResult.ForgetRequest(
                spokenAck = "Listo. Borré esa preferencia."
            )

            key.startsWith("recorda que ") &&
                (key.contains("contacto principal") ||
                    key.contains("es mi contacto principal") ||
                    key.contains("es mi contacto de confianza")) -> {
                val name = extractContactNameForPrimary(key)
                if (name.isBlank()) {
                    PreferenceCommandResult.NotARecognizedPreference
                } else {
                    setString(
                        key = AgentPreferenceKeys.PRIMARY_CONTACT,
                        value = name
                    )
                }
            }

            else -> PreferenceCommandResult.NotARecognizedPreference
        }
    }

    private fun extractContactNameForPrimary(normalized: String): String {
        // "recorda que sofi es mi contacto principal" → "sofi"
        val match = Regex("recorda que ([\\w ]+?) es mi contacto").find(normalized)
            ?: return ""
        return match.groupValues[1].trim()
    }

    private fun setString(key: String, value: String): PreferenceCommandResult.UpdatePreference =
        PreferenceCommandResult.UpdatePreference(
            preference = AgentUserPreference(
                key = key,
                value = value,
                source = AgentPreferenceSource.USER_EXPLICIT,
                updatedAtMillis = nowProvider()
            ),
            spokenAck = ackFor(key, value)
        )

    private fun setBoolean(key: String, value: Boolean): PreferenceCommandResult.UpdatePreference =
        setString(key = key, value = value.toString())

    private fun ackFor(key: String, value: String): String = when (key) {
        AgentPreferenceKeys.RESPONSE_LENGTH -> when (value) {
            "short" -> "Listo. Voy a usar respuestas cortas."
            "normal" -> "Listo. Vuelvo a respuestas normales."
            else -> "Listo. Actualicé tu preferencia."
        }
        AgentPreferenceKeys.PRIMARY_CONTACT -> "Listo. Recordé a $value como tu contacto principal."
        AgentPreferenceKeys.LEARNING_OPT_IN -> when (value) {
            "true" -> "Listo. Voy a aprender de tus preferencias si surgen."
            "false" -> "Listo. No voy a aprender automáticamente de tus preferencias."
            else -> "Listo. Actualicé tu preferencia."
        }
        else -> "Listo. Actualicé tu preferencia."
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

sealed class PreferenceCommandResult {
    /** El texto no era una orden de preferencia reconocida. Caller sigue normal. */
    object NotARecognizedPreference : PreferenceCommandResult()

    /** Actualizar (o crear) una preferencia. Caller debe persistir y hablar ack. */
    data class UpdatePreference(
        val preference: AgentUserPreference,
        val spokenAck: String
    ) : PreferenceCommandResult()

    /** Olvidar una preferencia. Caller decide cuál borrar. */
    data class ForgetRequest(val spokenAck: String) : PreferenceCommandResult()
}
