package com.ojoclaro.android.agent.core.memory

import com.ojoclaro.android.agent.core.AgentPreferenceSource
import com.ojoclaro.android.agent.core.AgentUserPreference
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector

/**
 * Política central de escritura/lectura para memoria personal del Agent Core.
 *
 * Reglas:
 *  - Aprendizaje (inferencias) NO se guarda automáticamente. Se devuelve
 *    NeedsConfirmation; el usuario decide.
 *  - Memoria explícita del usuario (USER_EXPLICIT) se guarda, salvo contenido
 *    sensible: contraseñas, tarjetas, bancos, salud, documentos.
 *  - Si el usuario opted-out de aprendizaje (learningOptedIn = false), las
 *    inferencias se descartan en silencio (sin preguntar).
 *  - Una solicitud de borrar memoria SIEMPRE se permite — el usuario manda.
 */
class AgentPersonalMemoryPolicy(
    private val riskDetector: RiskDetector = RiskDetector(),
    private val maxValueLength: Int = DEFAULT_MAX_VALUE_LENGTH
) {

    fun evaluateWrite(
        request: AgentMemoryWriteRequest,
        learningOptedIn: Boolean
    ): AgentMemoryWriteDecision {
        if (request.label.isBlank() || request.value.isBlank()) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "empty",
                spokenText = "No puedo guardar eso sin un nombre y un valor."
            )
        }
        if (request.label.length > MAX_LABEL_LENGTH || request.value.length > maxValueLength) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "too_long",
                spokenText = "Es muy largo para guardar. Hacelo más corto."
            )
        }
        if (containsBlockedTokens(request.label, request.value)) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "sensitive_tokens",
                spokenText = "No guardo contraseñas, bancos, tarjetas ni documentos."
            )
        }
        if (PrivacyGuard.containsSensitiveFinancialData(request.label + "\n" + request.value)) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "financial",
                spokenText = "No guardo datos bancarios ni financieros."
            )
        }
        if (riskDetector.detectFromCommand(request.label + "\n" + request.value).isNotEmpty()) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "risk_detected",
                spokenText = "No guardo eso porque parece sensible."
            )
        }
        return when (request.source) {
            AgentMemoryWriteSource.USER_EXPLICIT -> AgentMemoryWriteDecision.Allowed(
                spokenAck = "Listo. Guardé: ${request.label}."
            )
            AgentMemoryWriteSource.USER_CONFIRMED -> AgentMemoryWriteDecision.Allowed(
                spokenAck = "Listo. Guardé tu preferencia."
            )
            AgentMemoryWriteSource.INFERRED -> {
                if (!learningOptedIn) {
                    AgentMemoryWriteDecision.Rejected(
                        reason = "learning_opt_out",
                        spokenText = "" // silencio: no preguntamos si el usuario opted-out
                    )
                } else {
                    AgentMemoryWriteDecision.NeedsConfirmation(
                        spokenAck = "",
                        confirmationPrompt = "Noté que ${request.label.lowercase()}. " +
                            "¿Querés que lo recuerde? Decí: confirmar."
                    )
                }
            }
        }
    }

    fun evaluateRead(
        key: String,
        currentPreferences: List<AgentUserPreference>
    ): AgentMemoryReadDecision {
        val match = currentPreferences.firstOrNull { it.key == key }
            ?: return AgentMemoryReadDecision.Missing(
                spokenText = "No tengo nada guardado sobre eso."
            )
        if (!match.isApplicable) {
            return AgentMemoryReadDecision.NeedsConfirmation(
                spokenPrompt = "Tengo una sugerencia pendiente para esto. ¿La confirmamos?"
            )
        }
        return AgentMemoryReadDecision.Allowed(value = match.value)
    }

    /**
     * Aprendizaje real: a partir de una observación, decide si se debe proponer
     * guardarla. NO escribe nada. Solo construye el "siguiente paso".
     */
    fun proposeFromObservation(
        observation: AgentLearningObservation,
        learningOptedIn: Boolean
    ): AgentMemoryWriteDecision {
        if (!learningOptedIn) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "learning_opt_out",
                spokenText = ""
            )
        }
        if (observation.occurrenceCount < observation.minimumOccurrencesToPropose) {
            return AgentMemoryWriteDecision.Rejected(
                reason = "not_enough_observations",
                spokenText = ""
            )
        }
        return evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = observation.label,
                value = observation.value,
                source = AgentMemoryWriteSource.INFERRED
            ),
            learningOptedIn = true
        )
    }

    private fun containsBlockedTokens(label: String, value: String): Boolean {
        val combined = (label + "\n" + value).lowercase()
        return BLOCKED_TOKENS.any { combined.contains(it) }
    }

    companion object {
        const val MAX_LABEL_LENGTH = 80
        const val DEFAULT_MAX_VALUE_LENGTH = 240

        private val BLOCKED_TOKENS = listOf(
            "contraseña", "contrasena", "password", "clave", "pin",
            "tarjeta", "credito", "debito", "cbu", "cvu", "alias bancario",
            "banco", "home banking",
            "dni", "documento", "pasaporte",
            "obra social", "hospital", "diagnostico", "medicacion",
            "token", "api key", "codigo de verificacion", "otp"
        )
    }
}

enum class AgentMemoryWriteSource {
    USER_EXPLICIT,
    USER_CONFIRMED,
    INFERRED
}

data class AgentMemoryWriteRequest(
    val label: String,
    val value: String,
    val source: AgentMemoryWriteSource
)

/**
 * Observación que el sistema "ve" repetidas veces y podría sugerir como
 * preferencia. NO se guarda automáticamente.
 */
data class AgentLearningObservation(
    val label: String,
    val value: String,
    val occurrenceCount: Int,
    val minimumOccurrencesToPropose: Int = 3
)

fun AgentMemoryWriteDecision.toPreferenceSource(): AgentPreferenceSource? = when (this) {
    is AgentMemoryWriteDecision.Allowed -> AgentPreferenceSource.USER_EXPLICIT
    is AgentMemoryWriteDecision.NeedsConfirmation -> AgentPreferenceSource.INFERRED_PENDING_CONFIRMATION
    is AgentMemoryWriteDecision.Rejected -> null
}
