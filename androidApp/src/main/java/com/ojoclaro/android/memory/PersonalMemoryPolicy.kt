package com.ojoclaro.android.memory

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector

object PersonalMemoryPolicy {
    private val blockedTokens = listOf(
        "contrasena",
        "password",
        "clave",
        "pin",
        "tarjeta",
        "banco",
        "cbu",
        "cvu",
        "api key",
        "token",
        "secreto",
        "codigo de verificacion",
        "codigo",
        "dni",
        "documento"
    )

    fun canStore(memory: PersonalAgentMemory): Boolean {
        if (!memory.userApproved) return false
        if (memory.label.isBlank() || memory.value.isBlank()) return false
        if (memory.label.length > 80 || memory.value.length > 240) return false
        if (memory.type !in PersonalMemoryType.entries) return false

        val combined = "${memory.label}\n${memory.value}"
        if (blockedTokens.any { combined.contains(it, ignoreCase = true) }) return false
        if (PrivacyGuard.containsSensitiveFinancialData(combined)) return false
        if (RiskDetector().detectFromCommand(combined).isNotEmpty()) return false
        if (memory.type == PersonalMemoryType.SAFE_PHONE && !looksLikePhone(memory.value)) return false
        return true
    }

    fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

    fun safeLabel(memory: PersonalAgentMemory): String = when (memory.type) {
        PersonalMemoryType.CONTACT -> memory.label
        PersonalMemoryType.SAFE_PHONE -> memory.label
        PersonalMemoryType.PLACE -> memory.label
        PersonalMemoryType.ROUTINE -> memory.label
        PersonalMemoryType.PREFERENCE -> memory.label
        PersonalMemoryType.PENDING_TASK -> memory.label
        PersonalMemoryType.MESSAGE_STYLE -> memory.label
    }

    fun safeSummary(memory: PersonalAgentMemory): String = when (memory.type) {
        PersonalMemoryType.CONTACT -> "${memory.label} es contacto."
        PersonalMemoryType.SAFE_PHONE -> "Numero seguro para ${memory.label}."
        PersonalMemoryType.PLACE -> "Lugar guardado: ${memory.label}."
        PersonalMemoryType.ROUTINE -> "Rutina: ${memory.value}."
        PersonalMemoryType.PREFERENCE -> "Preferencia: ${memory.value}."
        PersonalMemoryType.PENDING_TASK -> "Pendiente: ${memory.value}."
        PersonalMemoryType.MESSAGE_STYLE -> "Estilo: ${memory.value}."
    }

    private fun looksLikePhone(text: String): Boolean =
        Regex("^\\+?\\d[\\d\\s-]{5,}\\d$").matches(text.trim())
}
