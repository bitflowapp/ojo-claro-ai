package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopMetric

/**
 * Deterministic detector for "are we in WhatsApp?" and "which WhatsApp area?".
 *
 * Rules:
 *  - reads only app package, roles, and short UI labels already capped by the mapper;
 *  - never exposes private labels or chat content in the returned state;
 *  - HIGH confidence requires a matching package name;
 *  - scans a bounded number of elements and normalizes each label at most once.
 */
class WhatsAppScreenDetector {

    fun detect(snapshot: ScreenSnapshot?): WhatsAppScreenState =
        RobotLoopInstrumentation.measure(RobotLoopMetric.WHATSAPP_DETECTOR) {
            detectInternal(snapshot)
        }

    private fun detectInternal(snapshot: ScreenSnapshot?): WhatsAppScreenState {
        if (snapshot == null) return WhatsAppScreenState.UNKNOWN
        if (!snapshot.hasText && !snapshot.hasElements && snapshot.packageName.isNullOrBlank()) {
            return WhatsAppScreenState.UNKNOWN
        }

        val packageNameMatched = packageNameLooksLikeWhatsApp(snapshot.packageName)
        val knownNonWhatsAppPackage = !snapshot.packageName.isNullOrBlank() && !packageNameMatched

        var hasMessageField = false
        var hasCameraButton = false
        var hasAttachButton = false
        var hasSendButton = false
        var hasMicrophoneButton = false
        var hasBackButton = false

        var scanned = 0
        for (element in snapshot.elements) {
            if (scanned >= MAX_ELEMENTS_TO_SCAN) break
            scanned += 1
            if (element.isPassword) continue

            val normalizedLabel = normalize(element.label)
            val interactive = isInteractive(element)

            if (!hasMessageField && isMessageField(element, normalizedLabel)) {
                hasMessageField = true
            }
            if (interactive) {
                if (!hasCameraButton && hasLabelMatching(normalizedLabel, CAMERA_LABELS)) {
                    hasCameraButton = true
                }
                if (!hasAttachButton && hasLabelMatching(normalizedLabel, ATTACH_LABELS)) {
                    hasAttachButton = true
                }
                if (!hasSendButton && hasLabelMatching(normalizedLabel, SEND_LABELS)) {
                    hasSendButton = true
                }
                if (!hasMicrophoneButton && hasLabelMatching(normalizedLabel, MICROPHONE_LABELS)) {
                    hasMicrophoneButton = true
                }
                if (!hasBackButton && hasLabelMatching(normalizedLabel, BACK_LABELS)) {
                    hasBackButton = true
                }
            }

            if (
                hasMessageField &&
                hasCameraButton &&
                hasAttachButton &&
                hasSendButton &&
                hasMicrophoneButton &&
                hasBackButton
            ) {
                break
            }
        }

        // Raw text hints only boost confidence when the package is unknown. A known
        // non-WhatsApp package is negative evidence and should not be overridden by text.
        val textHasWhatsAppHints = !knownNonWhatsAppPackage &&
            !packageNameMatched &&
            snapshot.text.isNotBlank() &&
            WHATSAPP_TEXT_HINTS.any { it in normalize(snapshot.text.take(MAX_TEXT_HINT_CHARS)) }

        val structuralSignalCount = listOf(
            hasMessageField,
            hasCameraButton,
            hasAttachButton,
            hasSendButton,
            hasMicrophoneButton
        ).count { it }

        val isInChat = hasMessageField && structuralSignalCount >= 2

        val confidence: WhatsAppDetectionConfidence = when {
            packageNameMatched && structuralSignalCount >= 2 -> WhatsAppDetectionConfidence.HIGH
            packageNameMatched -> WhatsAppDetectionConfidence.HIGH
            structuralSignalCount >= 3 -> WhatsAppDetectionConfidence.MEDIUM
            structuralSignalCount >= 1 && textHasWhatsAppHints -> WhatsAppDetectionConfidence.MEDIUM
            structuralSignalCount >= 1 || textHasWhatsAppHints -> WhatsAppDetectionConfidence.LOW
            knownNonWhatsAppPackage -> WhatsAppDetectionConfidence.LOW
            else -> WhatsAppDetectionConfidence.UNKNOWN
        }

        val isOpen = packageNameMatched ||
            confidence == WhatsAppDetectionConfidence.MEDIUM ||
            confidence == WhatsAppDetectionConfidence.HIGH

        return WhatsAppScreenState(
            isOpen = isOpen,
            isInChat = isInChat && isOpen,
            hasMessageField = hasMessageField,
            hasCameraButton = hasCameraButton,
            hasAttachButton = hasAttachButton,
            hasSendButton = hasSendButton,
            hasMicrophoneButton = hasMicrophoneButton,
            hasBackButton = hasBackButton,
            confidence = confidence,
            packageNameMatched = packageNameMatched
        )
    }

    private fun packageNameLooksLikeWhatsApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        return KNOWN_PACKAGES.any { lower == it } || lower.contains("whatsapp")
    }

    private fun isInteractive(element: ScreenElement): Boolean =
        element.isInteractive && !element.isPassword

    private fun isMessageField(element: ScreenElement, normalized: String): Boolean {
        if (element.role != ScreenElementRole.EDIT_TEXT) return false
        if (SEARCH_LABELS.any { normalized.contains(it) }) return false
        return MESSAGE_FIELD_LABELS.any { normalized.contains(it) } ||
            normalized.isBlank()
    }

    private fun hasLabelMatching(normalized: String, tokens: Set<String>): Boolean {
        if (normalized.isBlank()) return false
        return tokens.any { normalized.contains(it) }
    }

    private fun normalize(text: String): String = TextMatchNormalizer.normalize(text)

    companion object {
        const val MAX_ELEMENTS_TO_SCAN: Int = 32
        private const val MAX_TEXT_HINT_CHARS: Int = 500

        val KNOWN_PACKAGES: Set<String> = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )

        private val MESSAGE_FIELD_LABELS: Set<String> = setOf(
            "mensaje",
            "message",
            "escribe un mensaje",
            "type a message",
            "writeamessage",
            "mensaje de texto"
        )

        private val SEARCH_LABELS: Set<String> = setOf(
            "buscar",
            "search"
        )

        private val CAMERA_LABELS: Set<String> = setOf(
            "camara",
            "camera"
        )

        private val ATTACH_LABELS: Set<String> = setOf(
            "adjuntar",
            "adjuntos",
            "attach",
            "clip",
            "abrir adjuntos"
        )

        private val SEND_LABELS: Set<String> = setOf(
            "enviar",
            "send"
        )

        private val MICROPHONE_LABELS: Set<String> = setOf(
            "microfono",
            "microphone",
            "audio",
            "manten para grabar",
            "hold to record",
            "mensaje de voz"
        )

        private val BACK_LABELS: Set<String> = setOf(
            "volver",
            "back",
            "navegar hacia arriba",
            "navigate up",
            "atras"
        )

        private val WHATSAPP_TEXT_HINTS: Set<String> = setOf(
            "whatsapp",
            "estado",
            "comunidades",
            "llamadas"
        )
    }
}
