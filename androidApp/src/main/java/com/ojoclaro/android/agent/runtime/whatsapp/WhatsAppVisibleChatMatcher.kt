package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer

class WhatsAppVisibleChatMatcher {

    fun findBest(
        targetName: String,
        elements: List<ScreenElement>
    ): WhatsAppVisibleChatMatch? {
        val normalizedTarget = normalizeName(targetName)
        if (normalizedTarget.isBlank()) return null

        return elements
            .asSequence()
            .take(MAX_ELEMENTS_TO_SCAN)
            .mapNotNull { element -> candidateFor(targetName, normalizedTarget, element) }
            .filter { it.score >= MIN_CLEAR_SCORE }
            .maxWithOrNull(compareBy<WhatsAppVisibleChatMatch> { it.score }.thenByDescending { it.displayName.length })
    }

    private fun candidateFor(
        targetName: String,
        normalizedTarget: String,
        element: ScreenElement
    ): WhatsAppVisibleChatMatch? {
        if (!isEligibleChatElement(element)) return null
        val label = element.label.trim()
        val score = matchScore(label, normalizedTarget)
        if (score <= 0) return null
        return WhatsAppVisibleChatMatch(
            requestedName = targetName.trim(),
            displayName = displayNameFor(label = label, requestedName = targetName),
            matchedLabel = label,
            score = score
        )
    }

    private fun isEligibleChatElement(element: ScreenElement): Boolean {
        if (element.isPassword) return false
        if (element.label.isBlank()) return false
        if (element.label.length > MAX_LABEL_CHARS) return false
        if (isSensitiveActionLabel(element.label)) return false

        return when (element.role) {
            ScreenElementRole.TEXT,
            ScreenElementRole.LIST_ITEM -> true
            ScreenElementRole.BUTTON -> element.isInteractive
            else -> false
        }
    }

    private fun displayNameFor(label: String, requestedName: String): String {
        val cleanRequested = requestedName.trim().replace(Regex("\\s+"), " ")
        val normalizedLabel = normalizeName(label)
        val normalizedRequested = normalizeName(cleanRequested)
        return if (normalizedLabel.contains(normalizedRequested)) {
            cleanRequested
        } else {
            label.take(WhatsAppVisibleChat.MAX_NAME_LENGTH)
        }
    }

    companion object {
        const val MAX_ELEMENTS_TO_SCAN: Int = 64
        const val MAX_LABEL_CHARS: Int = 90
        const val MIN_CLEAR_SCORE: Int = 70

        fun normalizeName(text: String): String =
            TextMatchNormalizer.normalize(text)
                .replace(Regex("[·•|/\\\\]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun matchesTargetLabel(label: String, targetName: String): Boolean {
            val normalizedTarget = normalizeName(targetName)
            if (normalizedTarget.isBlank()) return false
            return matchScore(label, normalizedTarget) >= MIN_CLEAR_SCORE
        }

        fun isSensitiveActionLabel(label: String): Boolean {
            val normalized = normalizeName(label)
            if (normalized.isBlank()) return false
            return SENSITIVE_EXACT.any { normalized == it } ||
                SENSITIVE_CONTAINS.any { normalized.contains(it) }
        }

        private fun matchScore(label: String, normalizedTarget: String): Int {
            val normalizedLabel = normalizeName(label)
            if (normalizedLabel.isBlank()) return 0
            if (isSensitiveActionLabel(normalizedLabel)) return 0

            return when {
                normalizedLabel == normalizedTarget -> 100
                normalizedLabel.contains(normalizedTarget) -> 94
                normalizedTarget.contains(normalizedLabel) && normalizedLabel.length >= 5 -> 84
                tokenInitialsMatch(normalizedLabel, normalizedTarget) -> 78
                tokenOverlapScore(normalizedLabel, normalizedTarget) >= 70 -> tokenOverlapScore(
                    normalizedLabel,
                    normalizedTarget
                )
                else -> 0
            }
        }

        private fun tokenInitialsMatch(normalizedLabel: String, normalizedTarget: String): Boolean {
            val labelTokens = normalizedLabel.split(" ").filter { it.isNotBlank() }
            val targetTokens = normalizedTarget.split(" ").filter { it.isNotBlank() }
            if (labelTokens.size < 2 || targetTokens.size < 2) return false
            if (labelTokens.first() != targetTokens.first()) return false
            return labelTokens.drop(1).zip(targetTokens.drop(1)).all { (label, target) ->
                label == target || (label.length == 1 && target.startsWith(label))
            }
        }

        private fun tokenOverlapScore(normalizedLabel: String, normalizedTarget: String): Int {
            val labelTokens = normalizedLabel.split(" ").filter { it.length >= 2 }.toSet()
            val targetTokens = normalizedTarget.split(" ").filter { it.length >= 2 }.toSet()
            if (labelTokens.isEmpty() || targetTokens.isEmpty()) return 0
            val common = labelTokens.intersect(targetTokens).size
            val required = targetTokens.size
            return ((common.toFloat() / required.toFloat()) * 100).toInt()
        }

        private val SENSITIVE_EXACT = setOf(
            "enviar",
            "send",
            "llamar",
            "call",
            "borrar",
            "delete",
            "eliminar",
            "archivar",
            "archive",
            "pagar",
            "pay"
        )

        private val SENSITIVE_CONTAINS = setOf(
            "enviar",
            "send",
            "llamar",
            "call",
            "borrar",
            "delete",
            "eliminar",
            "archivar",
            "archive",
            "pagar",
            "pay",
            "transferir",
            "transfer",
            "bloquear",
            "block",
            "reportar",
            "report",
            "silenciar",
            "mute",
            "adjuntar",
            "attach",
            "microfono",
            "microphone",
            "camara",
            "camera"
        )
    }
}

data class WhatsAppVisibleChatMatch(
    val requestedName: String,
    val displayName: String,
    val matchedLabel: String,
    val score: Int
)

sealed class VisibleChatOpenResult {
    data class Opened(val displayName: String) : VisibleChatOpenResult()
    data class NotInWhatsApp(val packageName: String?) : VisibleChatOpenResult()
    data class NoMatch(val targetName: String) : VisibleChatOpenResult()
    data class Unsafe(val displayName: String?, val reason: String) : VisibleChatOpenResult()
    data class Failed(val displayName: String?, val reason: String) : VisibleChatOpenResult()
}
