package com.ojoclaro.android.agent.core.screen

import java.text.Normalizer

/**
 * V1.10.3 — nota hablada cuando el resumen de pantalla menciona acciones
 * sensibles de transporte/pago (pedir viaje, confirmar, pagar, tarjeta...).
 *
 * Estela puede LEER esas opciones (la persona necesita saber que existen),
 * pero la nota deja explícito el límite: jamás las toca por el usuario.
 * Puro Kotlin, sin Android: 100% testeable.
 */
object ScreenActionSafetyNote {

    const val NOTE: String =
        "Aclaración: veo opciones sensibles y no voy a tocar botones de " +
            "pedido, confirmación o pago por vos."

    private val SENSITIVE_ACTION_TOKENS = setOf(
        "confirmar", "pagar", "pago", "tarjeta", "solicitar"
    )

    private val SENSITIVE_ACTION_PHRASES = listOf(
        "pedir viaje", "pedir ahora", "pedir un viaje",
        "aceptar tarifa", "aceptar viaje", "aceptar precio",
        "confirma el viaje", "request ride", "confirm"
    )

    /** True si el texto (resumen hablado) menciona una acción sensible. */
    fun mentionsSensitiveAction(spokenText: String): Boolean {
        val folded = fold(spokenText)
        if (folded.isBlank()) return false
        if (SENSITIVE_ACTION_PHRASES.any { folded.contains(it) }) return true
        return folded.split(' ').any { it in SENSITIVE_ACTION_TOKENS }
    }

    /** Devuelve el texto con la nota agregada solo si hace falta (una vez). */
    fun appendIfSensitive(spokenText: String): String =
        if (mentionsSensitiveAction(spokenText) && !spokenText.contains(NOTE)) {
            "$spokenText $NOTE"
        } else {
            spokenText
        }

    private fun fold(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
