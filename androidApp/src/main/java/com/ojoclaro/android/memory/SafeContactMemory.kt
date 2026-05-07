package com.ojoclaro.android.memory

object SafeContactMemory {
    const val CONTACT_PREFIX = "contact:"
    const val PHONE_PREFIX = "phone:"

    fun contactValue(displayName: String): String =
        "$CONTACT_PREFIX${displayName.cleanContactField()}"

    fun phoneValue(phoneNumber: String): String? =
        normalizePhoneNumber(phoneNumber)?.let { "$PHONE_PREFIX$it" }

    fun extractPhoneNumber(value: String): String? {
        val cleanValue = value.trim()
        if (cleanValue.startsWith(PHONE_PREFIX, ignoreCase = true)) {
            return normalizePhoneNumber(cleanValue.substringAfter(':'))
        }

        return phoneCandidateRegex.findAll(cleanValue)
            .mapNotNull { normalizePhoneNumber(it.value) }
            .firstOrNull()
    }

    fun normalizePhoneNumber(phoneNumber: String): String? {
        val trimmed = phoneNumber.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.count { it == '+' } > 1) return null
        if ('+' in trimmed && !trimmed.trimStart().startsWith("+")) return null
        if (trimmed.any { it !in allowedPhoneChars }) return null

        val digits = trimmed.filter(Char::isDigit)
        if (digits.length !in MIN_CONTACT_PHONE_DIGITS..MAX_CONTACT_PHONE_DIGITS) return null

        return if (trimmed.trimStart().startsWith("+")) "+$digits" else digits
    }

    fun isContactType(type: MemoryType): Boolean =
        type == MemoryType.TRUSTED_CONTACT || type == MemoryType.EMERGENCY_CONTACT

    fun contactText(value: String): String =
        value.trim()
            .removePrefix(CONTACT_PREFIX)
            .removePrefix(PHONE_PREFIX)
            .cleanContactField()

    private fun String.cleanContactField(): String =
        trim()
            .trim('.', ',', ';', ':')
            .replace(Regex("\\s+"), " ")

    private val allowedPhoneChars: Set<Char> =
        ('0'..'9').toSet() + setOf('+', ' ', '-', '(', ')')

    private val phoneCandidateRegex = Regex("\\+?\\d(?:[\\s\\-()]?\\d){5,14}")

    private const val MIN_CONTACT_PHONE_DIGITS = 6
    private const val MAX_CONTACT_PHONE_DIGITS = 15
}
