package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import java.text.Normalizer

/**
 * Detector determinista de "¿estamos en WhatsApp?" y "¿en qué parte?"
 *
 * Reglas:
 *  - No lee mensajes. Solo inspecciona la estructura del snapshot: packageName,
 *    roles de elementos, labels CORTOS (acotados por el mapper a 80 chars) que
 *    coinciden con strings de UI de WhatsApp (Mensaje, Cámara, Enviar, etc.).
 *  - No deja salir labels privados — el WhatsAppScreenState solo tiene booleans.
 *  - Confianza HIGH solo si el packageName matchea. Sin packageName la confianza
 *    queda en MEDIUM o LOW según la fuerza de las señales.
 */
class WhatsAppScreenDetector {

    fun detect(snapshot: ScreenSnapshot?): WhatsAppScreenState {
        if (snapshot == null) return WhatsAppScreenState.UNKNOWN
        if (!snapshot.hasText && !snapshot.hasElements && snapshot.packageName.isNullOrBlank()) {
            return WhatsAppScreenState.UNKNOWN
        }

        val packageNameMatched = packageNameLooksLikeWhatsApp(snapshot.packageName)

        // Señales por etiqueta. Buscamos coincidencias seguras y fijas.
        val hasMessageField = snapshot.elements.any { isMessageField(it) }
        val hasCameraButton = snapshot.elements.any { hasLabelMatching(it, CAMERA_LABELS) && isInteractive(it) }
        val hasAttachButton = snapshot.elements.any { hasLabelMatching(it, ATTACH_LABELS) && isInteractive(it) }
        val hasSendButton = snapshot.elements.any { hasLabelMatching(it, SEND_LABELS) && isInteractive(it) }
        val hasMicrophoneButton = snapshot.elements.any {
            hasLabelMatching(it, MICROPHONE_LABELS) && isInteractive(it)
        }
        val hasBackButton = snapshot.elements.any { hasLabelMatching(it, BACK_LABELS) && isInteractive(it) }

        // Pistas textuales (no labels de elementos) por si el árbol de Accessibility
        // no clasifica bien — usa el texto bruto. Lo usamos solo para boostear la
        // confianza cuando NO tenemos packageName, no para inferir botones nuevos.
        val textHasWhatsAppHints = snapshot.text.isNotBlank() &&
            WHATSAPP_TEXT_HINTS.any { it in normalize(snapshot.text) }

        val structuralSignalCount = listOf(
            hasMessageField,
            hasCameraButton,
            hasAttachButton,
            hasSendButton,
            hasMicrophoneButton
        ).count { it }

        // En un chat real de WhatsApp esperamos al menos el campo de mensaje
        // + uno o dos botones cercanos (cámara/adjuntar/mic). Si solo hay
        // botones sin campo (lista de chats), NO consideramos isInChat = true.
        val isInChat = hasMessageField && structuralSignalCount >= 2

        // Tener un packageName que NO coincide con WhatsApp es evidencia NEGATIVA
        // clara: sabemos en qué app estamos y no es WhatsApp. Confianza LOW
        // explícita, no UNKNOWN.
        val knownNonWhatsAppPackage = !snapshot.packageName.isNullOrBlank() && !packageNameMatched

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

    private fun isMessageField(element: ScreenElement): Boolean {
        if (element.isPassword) return false
        if (element.role != ScreenElementRole.EDIT_TEXT) return false
        // El composer de WhatsApp normalmente tiene hint "Mensaje" / "Message" /
        // "Escribe un mensaje". Si hay otro edit field (búsqueda) lo descartamos.
        val normalized = normalize(element.label)
        if (SEARCH_LABELS.any { normalized.contains(it) }) return false
        return MESSAGE_FIELD_LABELS.any { normalized.contains(it) } ||
            normalized.isBlank() // edit field sin label en WhatsApp suele ser el composer
    }

    private fun hasLabelMatching(element: ScreenElement, tokens: Set<String>): Boolean {
        if (element.isPassword) return false
        if (element.label.isBlank()) return false
        val normalized = normalize(element.label)
        return tokens.any { normalized.contains(it) }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        /** Packages oficiales de WhatsApp y WhatsApp Business. */
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
