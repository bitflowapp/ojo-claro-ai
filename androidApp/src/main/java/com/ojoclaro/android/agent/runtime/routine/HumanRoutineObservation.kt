package com.ojoclaro.android.agent.runtime.routine

/**
 * Observación atómica de una acción que el sistema podría querer aprender.
 *
 * NUNCA debe contener:
 *  - texto de mensajes;
 *  - contenido OCR;
 *  - capturas de pantalla;
 *  - chats privados completos;
 *  - claves, tokens, codigos.
 *
 * El campo [labelHint] debe ser corto y describir QUÉ tipo de acción es,
 * no su contenido. Ejemplos válidos:
 *  - kind = "compose_message_to_contact", labelHint = "mensaje a Marco"
 *  - kind = "open_maps_to_alias", labelHint = "ir a casa"
 *
 * Ejemplos NO válidos:
 *  - labelHint = "te llamo en un rato" (contenido del mensaje)
 *  - labelHint = "saldo bancario 1.250.000"
 */
data class HumanRoutineObservation(
    val kind: String,
    val labelHint: String,
    val observedAtMillis: Long
) {
    init {
        require(kind.isNotBlank()) { "kind must not be blank" }
        require(labelHint.isNotBlank()) { "labelHint must not be blank" }
        require(kind.length <= MAX_KIND_LENGTH)
        require(labelHint.length <= MAX_LABEL_HINT_LENGTH)
    }

    companion object {
        const val MAX_KIND_LENGTH: Int = 80
        const val MAX_LABEL_HINT_LENGTH: Int = 80
    }
}
