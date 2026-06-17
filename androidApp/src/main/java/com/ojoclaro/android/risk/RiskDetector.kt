package com.ojoclaro.android.risk

import java.text.Normalizer
import java.util.Locale

class RiskDetector {

    fun detectFromVisibleText(text: String): List<RiskWarning> = detectFromText(text)

    fun detectFromOcrText(text: String): List<RiskWarning> = detectFromText(text)

    fun detectFromCommand(command: String): List<RiskWarning> = detectFromText(command)

    fun detectFromPackageName(packageName: String?): List<RiskWarning> {
        val normalized = normalize(packageName.orEmpty())
        if (normalized.isBlank()) return emptyList()

        return when {
            bankingPackageTokens.any(normalized::contains) -> listOf(warningFor(RiskType.BANKING_SCREEN))
            passwordPackageTokens.any(normalized::contains) -> listOf(warningFor(RiskType.PASSWORD_FIELD))
            else -> emptyList()
        }
    }

    private fun detectFromText(text: String): List<RiskWarning> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()

        val warnings = linkedMapOf<RiskType, RiskWarning>()

        if (moneyRegex.containsMatchIn(normalized)) {
            warnings[RiskType.MONEY_REQUEST] = warningFor(RiskType.MONEY_REQUEST)
        }
        if (bankingRegex.containsMatchIn(normalized)) {
            warnings[RiskType.BANKING_SCREEN] = warningFor(RiskType.BANKING_SCREEN)
        }
        if (passwordRegex.containsMatchIn(normalized)) {
            warnings[RiskType.PASSWORD_FIELD] = warningFor(RiskType.PASSWORD_FIELD)
        }
        if (verificationRegex.containsMatchIn(normalized)) {
            warnings[RiskType.VERIFICATION_CODE] = warningFor(RiskType.VERIFICATION_CODE)
        }
        if (personalDataRegex.containsMatchIn(normalized)) {
            warnings[RiskType.PERSONAL_DATA_REQUEST] = warningFor(RiskType.PERSONAL_DATA_REQUEST)
        }
        if (urgentRegex.containsMatchIn(normalized)) {
            warnings[RiskType.URGENT_MESSAGE] = warningFor(RiskType.URGENT_MESSAGE)
        }

        return warnings.values.toList()
    }

    private fun warningFor(type: RiskType): RiskWarning = when (type) {
        RiskType.MONEY_REQUEST -> RiskWarning(
            type = type,
            spokenText = "Este texto habla de dinero o transferencia. Revisalo con cuidado antes de responder.",
            severity = 2,
            requiresConfirmation = false
        )
        RiskType.BANKING_SCREEN -> RiskWarning(
            type = type,
            spokenText = "Esta pantalla puede contener datos privados. Para continuar, usá la seguridad del teléfono.",
            severity = 3,
            requiresConfirmation = true
        )
        RiskType.PASSWORD_FIELD -> RiskWarning(
            type = type,
            spokenText = "No puedo leer campos de contraseña. Eso es por seguridad.",
            severity = 3,
            requiresConfirmation = true
        )
        RiskType.VERIFICATION_CODE -> RiskWarning(
            type = type,
            spokenText = "Esto parece un código de verificación. No lo voy a leer en voz alta sin confirmación.",
            severity = 3,
            requiresConfirmation = true
        )
        RiskType.PERSONAL_DATA_REQUEST -> RiskWarning(
            type = type,
            spokenText = "Este texto puede pedir datos personales. Revisalo con cuidado antes de responder.",
            severity = 2,
            requiresConfirmation = false
        )
        RiskType.URGENT_MESSAGE -> RiskWarning(
            type = type,
            spokenText = "Este mensaje parece urgente y pide una acción. Confirmá antes de responder.",
            severity = 2,
            requiresConfirmation = true
        )
        RiskType.UNKNOWN_SENSITIVE -> RiskWarning(
            type = type,
            spokenText = "Antes de responder, te aviso: este texto puede ser sensible.",
            severity = 2,
            requiresConfirmation = false
        )
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private val diacriticRegex = Regex("\\p{Mn}+")
        private val moneyRegex =
            Regex("\\b(?:dinero|transferencia|transferir|plata|pesos|pago|pagar|deposito|alias|cbu|cvu|mercado\\s*pago)\\b|\\$")
        private val bankingRegex =
            Regex("\\b(?:banco|bancaria|home\\s*banking|saldo|cuenta\\s+bancaria|cbu|cvu|tarjeta|debito|credito)\\b")
        private val passwordRegex =
            Regex("\\b(?:contrasena|password|clave|pin)\\b")
        private val verificationRegex =
            Regex("\\b(?:codigo|cod|verificacion|2fa|otp)\\b.{0,24}\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.{0,24}\\b(?:codigo|cod|verificacion|2fa|otp)\\b")
        private val personalDataRegex =
            Regex("\\b(?:dni|documento|pasaporte|direccion|telefono|mail|correo|cuil|cuit)\\b")
        private val urgentRegex =
            Regex("\\b(?:urgente|ahora|ya mismo|inmediatamente|ultimo aviso|cuenta bloqueada|si no respondes|vence hoy)\\b")
        private val bankingPackageTokens = listOf(
            "bank",
            "banco",
            "brubank",
            "galicia",
            "bbva",
            "santander",
            "mercadopago",
            "uala"
        )
        private val passwordPackageTokens = listOf(
            "password",
            "bitwarden",
            "keepass",
            "lastpass",
            "authenticator"
        )
    }
}
