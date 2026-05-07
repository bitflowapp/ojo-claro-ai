package com.ojoclaro.android.privacy

import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.memory.UserMemory
import com.ojoclaro.android.patterns.FrequentPattern
import com.ojoclaro.android.risk.RiskType
import com.ojoclaro.android.risk.RiskWarning

/**
 * Reglas centrales de privacidad y seguridad de Ojo Claro.
 *
 * Todas las funciones son puras y testeables. No persiste nada.
 * Es la única fuente de verdad sobre qué se puede leer, qué se puede
 * enviar al backend y qué se debe descartar.
 */
object PrivacyGuard {

    /**
     * Recorta texto destinado a TTS para no leer minutos enteros.
     */
    fun sanitizeForSpeech(text: String): String =
        text.take(MAX_SPOKEN_CHARS).trim()

    /**
     * Recorta texto leído del AccessibilityService antes de emitirlo por voz.
     * Mantiene el contenido íntegro hasta el límite. NO redacta contenido propio
     * del usuario (un mensaje de WhatsApp pertenece a la conversación que el
     * usuario está mirando) — la decisión de leerlo ya la tomó el usuario.
     */
    fun sanitizeScreenText(text: String): String =
        text.take(MAX_SCREEN_TEXT_CHARS).trim()

    /**
     * Decide si un nodo de Accessibility se puede leer.
     * Reglas: visible, no password, con contenido.
     */
    fun isSafeToRead(text: String, isPassword: Boolean, isVisible: Boolean): Boolean =
        !isPassword && isVisible && text.isNotBlank()

    /**
     * Devuelve true sólo si todas las condiciones para enviar al cloud están dadas.
     * Por defecto safetyMode bloquea imágenes (la imagen puede contener una pantalla
     * de banco, una contraseña, etc.). El usuario puede desactivar safetyMode
     * conscientemente, pero hoy ningún flujo lo hace.
     */
    fun canSendToCloud(allowCloud: Boolean, safetyMode: Boolean, hasImage: Boolean): Boolean {
        if (!allowCloud) return false
        if (safetyMode && hasImage) return false
        return true
    }

    fun canStoreMemory(memory: UserMemory): Boolean =
        MemoryPolicy.canStore(memory)

    fun canStorePattern(pattern: FrequentPattern): Boolean {
        if (!pattern.userApprovedForSuggestions) return false
        if (MemoryPolicy.containsProhibitedContent(pattern.normalizedCommand)) return false
        if (privatePatternTokens.any { pattern.normalizedCommand.contains(it, ignoreCase = true) }) {
            return false
        }
        return true
    }

    fun canReadAloud(text: String, riskWarnings: List<RiskWarning>): Boolean {
        if (text.isBlank()) return false
        return riskWarnings.none { shouldRequireStrongConsent(it.type) }
    }

    fun shouldRequireStrongConsent(riskType: RiskType): Boolean = when (riskType) {
        RiskType.BANKING_SCREEN,
        RiskType.PASSWORD_FIELD,
        RiskType.VERIFICATION_CODE -> true
        RiskType.MONEY_REQUEST,
        RiskType.PERSONAL_DATA_REQUEST,
        RiskType.URGENT_MESSAGE,
        RiskType.UNKNOWN_SENSITIVE -> false
    }

    fun redactVerificationCodes(text: String): String {
        val normalized = MemoryPolicy.normalize(text)
        if (!verificationKeywordRegex.containsMatchIn(normalized)) return text
        return text.replace(verificationDigitsRegex, VERIFICATION_REDACTED)
    }

    fun redactPasswords(text: String): String =
        text.lineSequence()
            .map { line ->
                if (passwordAssignmentRegex.containsMatchIn(MemoryPolicy.normalize(line))) {
                    PASSWORD_REDACTED
                } else {
                    line
                }
            }
            .joinToString("\n")

    fun redactCardLikeNumbers(text: String): String =
        cardCandidateRegex.replace(text) { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19) CARD_REDACTED else match.value
        }

    fun containsSensitiveFinancialData(text: String): Boolean {
        val normalized = MemoryPolicy.normalize(text)
        if (MemoryPolicy.containsCardLikeNumber(text)) return true
        return financialSensitiveRegex.containsMatchIn(normalized)
    }

    /**
     * Quita líneas que parecen contraseñas o tokens de un texto (ej. de OCR).
     * Encadena las redacciones más estrictas: contraseñas, códigos de
     * verificación, tarjetas, y por último secretos de aspecto largo
     * (heurística: cadenas largas sin espacios con dígitos y letras).
     */
    fun redactSensitiveText(text: String): String {
        val withoutPasswords = redactPasswords(text)
        val withoutCodes = redactVerificationCodes(withoutPasswords)
        val withoutCards = redactCardLikeNumbers(withoutCodes)
        return withoutCards.lineSequence()
            .map { line -> if (looksLikeSecret(line)) REDACTED_PLACEHOLDER else line }
            .joinToString("\n")
    }

    /**
     * El "limpiador" para datos temporales. Hoy no persistimos nada,
     * así que es un no-op, pero sirve como hook único para limpiar buffers
     * cuando se agreguen caches futuros (por ejemplo cache de respuestas IA).
     */
    fun cleanTemporaryData() {
        // intencionalmente vacío: ningún buffer persistente hoy.
        // mantener este método como punto único hace explícito el contrato.
    }

    /**
     * Revisa que un mensaje a enviar a WhatsApp no incluya texto sospechoso
     * de venir de una pantalla privada (ej. saldos, tokens, contraseñas, tarjetas).
     * Devuelve true sólo si se puede preparar tal cual.
     *
     * No detecta todo, pero bloquea los casos obvios y suficientemente comunes
     * para que un usuario no envíe accidentalmente algo crítico.
     */
    fun isSafeMessagePayload(message: String): Boolean {
        if (message.isBlank()) return false
        if (message.length > MAX_MESSAGE_CHARS) return false

        val normalized = MemoryPolicy.normalize(message)
        if (passwordAssignmentRegex.containsMatchIn(normalized)) return false
        if (verificationKeywordRegex.containsMatchIn(normalized) &&
            verificationDigitsRegex.containsMatchIn(message)
        ) return false
        if (MemoryPolicy.containsCardLikeNumber(message)) return false
        if (financialSensitiveRegex.containsMatchIn(normalized)) return false
        return true
    }

    private fun looksLikeSecret(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < MIN_SECRET_LENGTH) return false
        if (trimmed.contains(' ')) return false
        val digits = trimmed.count { it.isDigit() }
        val letters = trimmed.count { it.isLetter() }
        return digits > 0 && letters > 0
    }

    const val NO_AUTO_SEND_GUARANTEE =
        "Ojo Claro nunca envía mensajes automáticamente. El usuario siempre confirma."

    const val NO_STORE_GUARANTEE =
        "Ojo Claro no guarda OCR, capturas de pantalla ni conversaciones."

    const val NO_PASSWORD_GUARANTEE =
        "Ojo Claro nunca lee ni transmite campos de contraseña."

    const val NO_BACKGROUND_LISTENING_GUARANTEE =
        "Ojo Claro solo lee la pantalla cuando el usuario lo pide explícitamente."

    private const val MAX_SPOKEN_CHARS = 1_200
    private const val MAX_SCREEN_TEXT_CHARS = 2_000
    private const val MAX_MESSAGE_CHARS = 1_000
    private const val MIN_SECRET_LENGTH = 16
    private const val REDACTED_PLACEHOLDER = "[contenido sensible omitido]"
    private const val VERIFICATION_REDACTED = "[código omitido]"
    private const val PASSWORD_REDACTED = "[contraseña: contenido omitido]"
    private const val CARD_REDACTED = "[número de tarjeta omitido]"

    private val privatePatternTokens = listOf(
        "message:",
        "chat:",
        "ocr:",
        "screen_text:"
    )
    private val verificationKeywordRegex = Regex("\\b(?:codigo|cod|verificacion|2fa|otp)\\b")
    private val verificationDigitsRegex = Regex("\\b\\d{4,8}\\b")
    private val passwordAssignmentRegex =
        Regex("\\b(?:mi\\s+)?(?:contrasena|password|clave|pin)\\s*(?:es|:|=)\\s*\\S+")
    private val cardCandidateRegex = Regex("(?:\\d[ -]?){13,19}")
    private val financialSensitiveRegex =
        Regex("\\b(?:saldo|cbu|cvu|cuenta\\s+bancaria|home\\s*banking|tarjeta|debito|credito)\\b")
}
