package com.ojoclaro.android.memory

import java.text.Normalizer
import java.util.Locale
import com.ojoclaro.android.maps.SafeLocationMemory

object MemoryPolicy {

    fun canStore(memory: UserMemory): Boolean {
        if (!memory.userApproved) return false
        if (memory.isSensitive) return false

        val cleanLabel = memory.label.trim()
        val cleanValue = memory.value.trim()

        if (cleanLabel.isBlank() || cleanValue.isBlank()) return false
        if (cleanLabel.length > MAX_MEMORY_LABEL_CHARS) return false
        if (cleanValue.length > MAX_MEMORY_VALUE_CHARS) return false
        if (cleanValue.lineSequence().count() > MAX_MEMORY_LINES) return false
        if (memory.type !in allowedTypes) return false

        if (SafeContactMemory.isContactType(memory.type)) {
            return canStoreSafeContact(cleanLabel, cleanValue)
        }

        if (memory.type == MemoryType.LOCATION_ALIAS) {
            return SafeLocationMemory.isValidAlias(cleanLabel) &&
                SafeLocationMemory.parse(cleanValue) != null
        }

        val combined = "$cleanLabel\n$cleanValue"
        if (containsProhibitedContent(combined)) return false

        return true
    }

    fun containsProhibitedContent(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false

        if (fullPrivateContentTokens.any { normalized.contains(it) }) return true
        if (imageOrAudioTokens.any { normalized.contains(it) }) return true
        if (financialSensitiveTokens.any { normalized.contains(it) }) return true
        if (passwordAssignmentRegex.containsMatchIn(normalized)) return true
        if (tokenAssignmentRegex.containsMatchIn(normalized)) return true
        if (verificationCodeRegex.containsMatchIn(normalized)) return true
        if (containsCardLikeNumber(text)) return true
        if (privateDocumentRegex.containsMatchIn(normalized)) return true
        if (sensitiveLocationRegex.containsMatchIn(normalized)) return true

        return false
    }

    fun containsCardLikeNumber(text: String): Boolean {
        return cardCandidateRegex.findAll(text).any { match ->
            val digits = match.value.filter(Char::isDigit)
            digits.length in 13..19
        }
    }

    fun normalize(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(DIACRITIC_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private val allowedTypes = setOf(
        MemoryType.TRUSTED_CONTACT,
        MemoryType.EMERGENCY_CONTACT,
        MemoryType.LOCATION_ALIAS,
        MemoryType.USER_PREFERENCE,
        MemoryType.SAFETY_RULE,
        MemoryType.FREQUENT_COMMAND,
        MemoryType.APP_SENSITIVITY,
        MemoryType.WARNING_KEYWORD,
        MemoryType.ROUTINE_PATTERN
    )

    private fun canStoreSafeContact(label: String, value: String): Boolean {
        if (containsProhibitedContactContent(label)) return false

        val cleanValue = value.trim()
        return when {
            cleanValue.startsWith(SafeContactMemory.PHONE_PREFIX, ignoreCase = true) -> {
                val rawPhone = cleanValue.substringAfter(':')
                SafeContactMemory.normalizePhoneNumber(rawPhone) != null &&
                    !containsProhibitedContactContent(rawPhone, allowSafePhone = true)
            }
            cleanValue.startsWith(SafeContactMemory.CONTACT_PREFIX, ignoreCase = true) -> {
                val contactText = SafeContactMemory.contactText(cleanValue)
                contactText.isNotBlank() && !containsProhibitedContactContent(contactText)
            }
            else -> {
                cleanValue.isNotBlank() && !containsProhibitedContent("$label\n$cleanValue")
            }
        }
    }

    private fun containsProhibitedContactContent(
        text: String,
        allowSafePhone: Boolean = false
    ): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false

        if (fullPrivateContentTokens.any { normalized.contains(it) }) return true
        if (imageOrAudioTokens.any { normalized.contains(it) }) return true
        if (financialSensitiveTokens.any { normalized.contains(it) }) return true
        if (passwordAssignmentRegex.containsMatchIn(normalized)) return true
        if (tokenAssignmentRegex.containsMatchIn(normalized)) return true
        if (verificationCodeRegex.containsMatchIn(normalized)) return true
        if (!allowSafePhone && containsCardLikeNumber(text)) return true
        if (privateDocumentRegex.containsMatchIn(normalized)) return true
        if (sensitiveLocationRegex.containsMatchIn(normalized)) return true

        return false
    }

    private val fullPrivateContentTokens = listOf(
        "chat completo",
        "conversacion completa",
        "conversacion entera",
        "pantalla completa",
        "captura completa",
        "ocr completo",
        "texto completo de la pantalla",
        "historial completo"
    )

    private val imageOrAudioTokens = listOf(
        "base64",
        "imagen completa",
        "foto completa",
        "audio completo",
        "grabacion completa",
        "archivo de audio"
    )

    private val financialSensitiveTokens = listOf(
        "cbu",
        "cvu",
        "alias bancario",
        "numero de cuenta",
        "nro de cuenta",
        "numero de tarjeta",
        "tarjeta de credito",
        "tarjeta de debito",
        "codigo de seguridad",
        "cvv",
        "mercado pago",
        "home banking"
    )

    private val passwordAssignmentRegex =
        Regex("\\b(?:mi\\s+)?(?:contrasena|password|clave|pin)\\s*(?:es|:|=)\\s*\\S+")

    private val tokenAssignmentRegex =
        Regex("\\b(?:token|api\\s*key|apikey|secret|secreto)\\s*(?:es|:|=)\\s*\\S+")

    private val verificationCodeRegex =
        Regex("\\b(?:codigo|cod|verificacion|2fa|otp)\\b.{0,24}\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.{0,24}\\b(?:codigo|cod|verificacion|2fa|otp)\\b")

    private val cardCandidateRegex =
        Regex("(?:\\d[ -]?){13,19}")

    private val privateDocumentRegex =
        Regex("\\b(?:dni|documento|pasaporte|cuil|cuit)\\b.{0,16}\\b\\d{6,12}\\b")

    private val sensitiveLocationRegex =
        Regex("\\b(?:mi\\s+direccion|vivo\\s+en|mi\\s+casa\\s+queda)\\b")

    private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private const val MAX_MEMORY_LABEL_CHARS = 80
    private const val MAX_MEMORY_VALUE_CHARS = 240
    private const val MAX_MEMORY_LINES = 3
}
