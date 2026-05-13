package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class VoiceCommandCorrectionType {
    NO_CORRECTION,
    AUTO_CORRECTION,
    CONFIRMATION_REQUIRED,
    REJECTED_SENSITIVE
}

enum class VoiceCommandCorrectionConfidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class VoiceCommandTargetIntent(
    val canonicalText: String,
    val spokenLabel: String
) {
    NONE("", ""),
    OPEN_WHATSAPP("abrir WhatsApp", "abrir WhatsApp"),
    READ_VISIBLE_SCREEN("que hay en pantalla", "que hay en pantalla"),
    WHAT_CAN_I_DO("que puedo hacer aca", "que puedo hacer aca"),
    READ_VISIBLE_CHATS("que chats ves", "que chats ves"),
    REPEAT_LAST("repeti", "repetir"),
    RESET_FLOW("resetear", "resetear"),
    STOP_SPEAKING("callate", "callate"),
    PAUSE_ROBOT("pausar robot", "pausar robot"),
    ENABLE_ROBOT("encender robot", "encender robot");

    val isLowRiskExecutable: Boolean
        get() = this != NONE
}

data class VoiceCommandCorrectionResult(
    val originalText: String,
    val correctedText: String,
    val correctionType: VoiceCommandCorrectionType,
    val confidence: VoiceCommandCorrectionConfidence,
    val requiresConfirmation: Boolean,
    val targetIntent: VoiceCommandTargetIntent
) {
    val shouldAutoExecute: Boolean
        get() = correctionType == VoiceCommandCorrectionType.AUTO_CORRECTION &&
            confidence == VoiceCommandCorrectionConfidence.HIGH &&
            !requiresConfirmation &&
            targetIntent.isLowRiskExecutable

    val canBeConfirmedSafely: Boolean
        get() = correctionType == VoiceCommandCorrectionType.CONFIRMATION_REQUIRED &&
            requiresConfirmation &&
            targetIntent.isLowRiskExecutable

    fun confirmationPrompt(): String =
        "¿Quisiste decir ${targetIntent.spokenLabel}?"
}

data class PendingVoiceCommandCorrection(
    val correction: VoiceCommandCorrectionResult,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

enum class VoiceCommandConfirmationResponse {
    CONFIRM,
    CANCEL,
    NONE
}

object VoiceCommandCorrection {

    private const val MAX_CORRECTION_INPUT_CHARS: Int = 240
    private const val MEDIUM_THRESHOLD: Double = 0.74
    private const val HIGH_THRESHOLD: Double = 0.88
    const val CONFIRMATION_TTL_MILLIS: Long = 20_000L

    fun correct(rawText: String): VoiceCommandCorrectionResult {
        val original = rawText
            .take(MAX_CORRECTION_INPUT_CHARS * 2)
            .trim()
            .take(MAX_CORRECTION_INPUT_CHARS)
        if (original.isBlank()) {
            return noCorrection(original)
        }

        val key = normalizeForLookup(original)
        if (key.isBlank()) {
            return noCorrection(original)
        }

        if (containsSensitiveOrIrreversibleAction(key)) {
            return VoiceCommandCorrectionResult(
                originalText = original,
                correctedText = original,
                correctionType = VoiceCommandCorrectionType.REJECTED_SENSITIVE,
                confidence = VoiceCommandCorrectionConfidence.LOW,
                requiresConfirmation = false,
                targetIntent = VoiceCommandTargetIntent.NONE
            )
        }

        strongWhatsAppOpenMatch(original, key)?.let { return it }

        val best = commandSpecs
            .asSequence()
            .mapNotNull { spec -> spec.match(key) }
            .maxWithOrNull(
                compareBy<CommandMatch> { it.score }
                    .thenBy { it.spec.priority }
            )
            ?: return noCorrection(original)

        return when {
            best.score >= HIGH_THRESHOLD || best.exact -> corrected(
                original = original,
                target = best.spec.target,
                confidence = VoiceCommandCorrectionConfidence.HIGH,
                requiresConfirmation = false
            )
            best.score >= MEDIUM_THRESHOLD -> corrected(
                original = original,
                target = best.spec.target,
                confidence = VoiceCommandCorrectionConfidence.MEDIUM,
                requiresConfirmation = true
            )
            else -> noCorrection(original)
        }
    }

    fun confirmationResponse(rawText: String): VoiceCommandConfirmationResponse =
        when (normalizeForLookup(rawText)) {
            "si",
            "confirmar",
            "confirmo",
            "correcto",
            "dale",
            "esta bien" -> VoiceCommandConfirmationResponse.CONFIRM
            "no",
            "cancelar",
            "cancela",
            "nada",
            "anular" -> VoiceCommandConfirmationResponse.CANCEL
            else -> VoiceCommandConfirmationResponse.NONE
        }

    fun isKnownRecognizerNoise(rawText: String): Boolean {
        val key = normalizeForLookup(rawText)
        if (key.isBlank()) return false
        if (key in knownRecognizerNoise) return true

        val tokens = key.split(' ').filter { it.isNotBlank() }
        return tokens.size <= 2 && tokens.any { it in wakeWordNoise }
    }

    private fun strongWhatsAppOpenMatch(
        original: String,
        key: String
    ): VoiceCommandCorrectionResult? {
        val match = openWhatsAppLooseRegex.matchEntire(key) ?: return null
        val targetPart = match.groupValues[1]
            .replace(Regex("^(?:el|la)\\s+"), "")
            .trim()
        if (targetPart.isBlank()) return null

        if (targetPart in highConfidenceWhatsAppTargets) {
            return corrected(
                original = original,
                target = VoiceCommandTargetIntent.OPEN_WHATSAPP,
                confidence = VoiceCommandCorrectionConfidence.HIGH,
                requiresConfirmation = false
            )
        }

        val bestAliasScore = whatsappTargetAliases.maxOf { similarity(targetPart, it) }
        if (bestAliasScore >= HIGH_THRESHOLD) {
            return corrected(
                original = original,
                target = VoiceCommandTargetIntent.OPEN_WHATSAPP,
                confidence = VoiceCommandCorrectionConfidence.HIGH,
                requiresConfirmation = false
            )
        }
        if (bestAliasScore >= MEDIUM_THRESHOLD) {
            return corrected(
                original = original,
                target = VoiceCommandTargetIntent.OPEN_WHATSAPP,
                confidence = VoiceCommandCorrectionConfidence.MEDIUM,
                requiresConfirmation = true
            )
        }
        return null
    }

    private fun corrected(
        original: String,
        target: VoiceCommandTargetIntent,
        confidence: VoiceCommandCorrectionConfidence,
        requiresConfirmation: Boolean
    ): VoiceCommandCorrectionResult =
        VoiceCommandCorrectionResult(
            originalText = original,
            correctedText = target.canonicalText,
            correctionType = if (requiresConfirmation) {
                VoiceCommandCorrectionType.CONFIRMATION_REQUIRED
            } else {
                VoiceCommandCorrectionType.AUTO_CORRECTION
            },
            confidence = confidence,
            requiresConfirmation = requiresConfirmation,
            targetIntent = target
        )

    private fun noCorrection(original: String): VoiceCommandCorrectionResult =
        VoiceCommandCorrectionResult(
            originalText = original,
            correctedText = original,
            correctionType = VoiceCommandCorrectionType.NO_CORRECTION,
            confidence = VoiceCommandCorrectionConfidence.LOW,
            requiresConfirmation = false,
            targetIntent = VoiceCommandTargetIntent.NONE
        )

    private fun CommandSpec.match(key: String): CommandMatch? {
        if (key in phrases) {
            return CommandMatch(this, score = 1.0, exact = true)
        }
        if (key.length < minFuzzyChars) return null

        val score = phrases.maxOf { phrase -> similarity(key, phrase) }
        return CommandMatch(this, score = score, exact = false)
            .takeIf { score >= MEDIUM_THRESHOLD }
    }

    private fun containsSensitiveOrIrreversibleAction(key: String): Boolean =
        sensitiveActionRegex.containsMatchIn(key)

    private fun normalizeForLookup(rawText: String): String {
        val parserReady = VoicePhraseNormalizer.normalizeForParser(rawText)
        val lower = parserReady.lowercase(Locale("es", "AR"))
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0

        val edit = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length).toDouble()
        val tokens = tokenSimilarity(a, b)
        return max(edit, tokens)
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return intersection / union
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                )
            }
            val temp = previous
            previous = current
            current = temp
        }
        return previous[b.length]
    }

    private data class CommandSpec(
        val target: VoiceCommandTargetIntent,
        val priority: Int,
        val minFuzzyChars: Int = 5,
        val phrases: Set<String>
    )

    private data class CommandMatch(
        val spec: CommandSpec,
        val score: Double,
        val exact: Boolean
    )

    private val diacriticRegex = Regex("\\p{Mn}+")

    private val openWhatsAppLooseRegex =
        Regex("^(?:abri|abrir|abre|abreme)\\s+(.+)$")

    private val whatsappTargetAliases = setOf(
        "whatsapp",
        "whats app",
        "wp",
        "wsp",
        "wpp",
        "wasap",
        "guasap",
        "watsap",
        "whasap",
        "wasa"
    )

    private val highConfidenceWhatsAppTargets = whatsappTargetAliases + setOf(
        "ure max",
        "uremax",
        "ure mas",
        "ure mas principal"
    )

    private val wakeWordNoise = setOf(
        "android",
        "aurelio",
        "siri",
        "alexa",
        "cortana"
    )

    private val knownRecognizerNoise = setOf(
        "android",
        "si aurelio",
        "cancion de marco antonio"
    )

    private val commandSpecs: List<CommandSpec> = listOf(
        CommandSpec(
            target = VoiceCommandTargetIntent.OPEN_WHATSAPP,
            priority = 100,
            phrases = setOf(
                "abrir whatsapp",
                "abri whatsapp",
                "abre whatsapp",
                "abreme whatsapp",
                "abrir wp",
                "abri wp",
                "abrir wpp",
                "abri wpp",
                "abrir wasap",
                "abri wasap",
                "abrir guasap",
                "abri guasap",
                "abrir wasa",
                "abri wasa",
                "abrir ure max",
                "abri ure max"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.READ_VISIBLE_SCREEN,
            priority = 90,
            phrases = setOf(
                "que hay en pantalla",
                "que hay en la pantalla",
                "que dice la pantalla",
                "leer pantalla",
                "leeme la pantalla"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.WHAT_CAN_I_DO,
            priority = 85,
            phrases = setOf(
                "que puedo hacer aca",
                "que puedo hacer aqui",
                "que puedo hacer",
                "que opciones tengo",
                "que botones hay"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.READ_VISIBLE_CHATS,
            priority = 80,
            phrases = setOf(
                "que chats ves",
                "que chat ves",
                "que chats hay",
                "leeme los chats",
                "que conversaciones hay"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.REPEAT_LAST,
            priority = 70,
            phrases = setOf(
                "repeti",
                "repetir",
                "repite",
                "repetilo",
                "que dijiste"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.RESET_FLOW,
            priority = 65,
            phrases = setOf(
                "resetear",
                "resetea",
                "resetear flujo",
                "volver al inicio",
                "limpiar estado"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.STOP_SPEAKING,
            priority = 60,
            phrases = setOf(
                "callate",
                "callar",
                "silencio",
                "deja de hablar",
                "para"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.PAUSE_ROBOT,
            priority = 50,
            phrases = setOf(
                "pausar robot",
                "pausa robot",
                "desactivar robot",
                "apagar robot"
            )
        ),
        CommandSpec(
            target = VoiceCommandTargetIntent.ENABLE_ROBOT,
            priority = 45,
            phrases = setOf(
                "encender robot",
                "prender robot",
                "activar robot",
                "segui escuchando"
            )
        )
    )

    private val sensitiveActionRegex = Regex(
        "\\b(?:" +
            "mandale|mandar|manda|enviar|envia|enviale|escribir|escribile|decir|decile|" +
            "mensaje|ubicacion|foto|llamar|llama|llamada|" +
            "banco|bancaria|bancario|pago|pagar|transferir|transferencia|plata|" +
            "clave|contrasena|password|otp|codigo|pin|tarjeta|cbu|cvu" +
            ")\\b"
    )
}
