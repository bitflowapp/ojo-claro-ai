package com.ojoclaro.shared.commands

import com.ojoclaro.shared.model.AppCommand
import com.ojoclaro.shared.model.AppCommandType

class CommandParser {

    fun parse(rawInput: String, locale: String = "es-AR"): AppCommand {
        val normalized = rawInput.lowercase().trim()

        return when {
            normalized.isBlank() -> unknown(rawInput, locale)

            any(normalized, "leer", "leeme", "texto", "cartel", "ticket", "boleta", "factura") ->
                AppCommand(
                    type = AppCommandType.READ_TEXT,
                    originalText = rawInput,
                    locale = locale,
                    requiresCamera = true
                )

            any(normalized, "describir", "que tengo enfrente", "qué tengo enfrente", "que ves", "qué ves", "mirar") ->
                AppCommand(
                    type = AppCommandType.DESCRIBE_SCENE,
                    originalText = rawInput,
                    locale = locale,
                    requiresCamera = true
                )

            any(normalized, "producto", "precio", "vencimiento", "ingredientes", "supermercado") ->
                AppCommand(
                    type = AppCommandType.IDENTIFY_PRODUCT,
                    originalText = rawInput,
                    locale = locale,
                    requiresCamera = true
                )

            any(normalized, "documento", "resumen", "contrato", "papel") ->
                AppCommand(
                    type = AppCommandType.READ_DOCUMENT,
                    originalText = rawInput,
                    locale = locale,
                    requiresCamera = true
                )

            any(normalized, "ayuda", "emergencia", "perdido", "perdida", "auxilio") ->
                AppCommand(
                    type = AppCommandType.EMERGENCY_HELP,
                    originalText = rawInput,
                    locale = locale,
                    requiresLocation = true,
                    highRisk = true
                )

            any(normalized, "donde estoy", "dónde estoy", "ubicacion", "ubicación") ->
                AppCommand(
                    type = AppCommandType.CURRENT_LOCATION,
                    originalText = rawInput,
                    locale = locale,
                    requiresLocation = true
                )

            else -> unknown(rawInput, locale)
        }
    }

    private fun unknown(rawInput: String, locale: String): AppCommand {
        return AppCommand(
            type = AppCommandType.UNKNOWN,
            originalText = rawInput,
            locale = locale
        )
    }

    private fun any(text: String, vararg tokens: String): Boolean {
        return tokens.any { token -> text.contains(token) }
    }
}
