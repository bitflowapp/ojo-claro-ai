package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskType
import com.ojoclaro.android.risk.RiskWarning
import java.util.Locale

/**
 * Builder puro que transforma un [ScreenSnapshot] crudo (proveniente del
 * AccessibilityService) en un [StructuredScreenSnapshot] sanitizado y
 * clasificado.
 *
 * Sin Android. Sin coroutines. Sin estado interno. Testeable directo.
 *
 * Reglas:
 *  - El sanitizer pasa primero: ninguna línea con password/código/tarjeta
 *    sale de acá en crudo.
 *  - La detección de riesgo combina [RiskDetector.detectFromVisibleText] +
 *    [RiskDetector.detectFromPackageName]. Los warnings se deduplican por
 *    tipo y se ordenan por severidad.
 *  - Los buttons y editableFields se toman de los `ScreenElement` ya
 *    mapeados por [AccessibilityNodeMapper]. Sus labels se vuelven a pasar
 *    por el sanitizer línea a línea — si el label entero matchea un
 *    placeholder, se descarta (no queremos botones con label "[CONTRASEÑA
 *    OCULTA]").
 *  - El focusedLabel intenta tomar el primer elemento marcado como editable
 *    o heading. Es heurística — no es la fuente de verdad de "qué tiene
 *    foco realmente". Para el v1 alcanza.
 *  - Las señales se calculan en función de package + warnings + elementos.
 */
class StructuredScreenSnapshotBuilder(
    private val riskDetector: RiskDetector = RiskDetector(),
    private val sanitizer: (String) -> String = { ScreenTextSanitizer.sanitizeLine(it) }
) {

    /**
     * @param snapshot snapshot crudo. Si es null, devolvemos un structured vacío
     *  marcado como limited.
     * @param capturedAtMillis se preserva del snapshot si está; el caller puede
     *  pasarlo por separado para snapshots sintéticos.
     */
    fun build(
        snapshot: ScreenSnapshot?,
        capturedAtMillis: Long = snapshot?.capturedAtMillis ?: 0L
    ): StructuredScreenSnapshot {
        if (snapshot == null) return StructuredScreenSnapshot.empty(capturedAtMillis)

        val packageName = snapshot.packageName?.takeIf { it.isNotBlank() }
        val appLabel = appLabelFor(packageName)

        // Los placeholders SE MANTIENEN en redactedTextLines: que el usuario
        // sepa que algo se ocultó (en lugar de no ver nada y pensar que la
        // pantalla está vacía). Sí los filtramos en buttons/editableFields,
        // donde un label "[CONTRASEÑA OCULTA]" sería basura.
        val redactedTextLines = ScreenTextSanitizer.sanitizeText(snapshot.text)

        val buttons = snapshot.elements
            .asSequence()
            .filter { it.isInteractive && it.role == ScreenElementRole.BUTTON }
            .map { sanitizer(it.label) }
            .filter { it.isNotBlank() && !isPlaceholderOnly(it) }
            .distinct()
            .take(MAX_BUTTONS)
            .toList()

        val editableFields = snapshot.elements
            .asSequence()
            .filter {
                it.role == ScreenElementRole.EDIT_TEXT && !it.isPassword
            }
            .map { sanitizer(it.label) }
            .filter { it.isNotBlank() && !isPlaceholderOnly(it) }
            .distinct()
            .take(MAX_FIELDS)
            .toList()

        val focusedLabel = pickFocusedLabel(snapshot)?.let(sanitizer)
            ?.takeIf { it.isNotBlank() && !isPlaceholderOnly(it) }

        val packageWarnings = riskDetector.detectFromPackageName(packageName)
        val textWarnings = if (snapshot.hasText) {
            riskDetector.detectFromVisibleText(snapshot.text)
        } else {
            emptyList()
        }
        val warnings = mergeWarnings(packageWarnings + textWarnings)

        val signals = classify(snapshot, warnings, packageName)

        val isLimited = snapshot.text.isBlank() &&
            buttons.isEmpty() &&
            editableFields.isEmpty()

        return StructuredScreenSnapshot(
            packageName = packageName,
            appLabel = appLabel,
            capturedAtMillis = capturedAtMillis,
            redactedTextLines = redactedTextLines.take(MAX_TEXT_LINES),
            buttons = buttons,
            editableFields = editableFields,
            focusedLabel = focusedLabel,
            totalNodes = snapshot.elements.size,
            signals = signals,
            warnings = warnings,
            isLimited = isLimited
        )
    }

    private fun pickFocusedLabel(snapshot: ScreenSnapshot): String? {
        val heading = snapshot.elements.firstOrNull { it.role == ScreenElementRole.HEADING }
        if (heading != null && heading.label.isNotBlank()) return heading.label
        val firstEditable = snapshot.elements.firstOrNull {
            it.role == ScreenElementRole.EDIT_TEXT && !it.isPassword
        }
        if (firstEditable != null && firstEditable.label.isNotBlank()) return firstEditable.label
        return null
    }

    private fun classify(
        snapshot: ScreenSnapshot,
        warnings: List<RiskWarning>,
        packageName: String?
    ): ScreenSignals {
        val hasPasswordField = snapshot.elements.any { it.isPassword } ||
            warnings.any { it.type == RiskType.PASSWORD_FIELD }

        val isBankingApp = warnings.any { it.type == RiskType.BANKING_SCREEN }
        val hasPaymentOrTransferSignals = warnings.any { it.type == RiskType.MONEY_REQUEST }
        val hasVerificationCode = warnings.any { it.type == RiskType.VERIFICATION_CODE }
        val hasPersonalDataRequest = warnings.any { it.type == RiskType.PERSONAL_DATA_REQUEST }

        val isMessagingApp = isMessagingPackage(packageName)
        val hasFormFields = snapshot.elements.count { it.role == ScreenElementRole.EDIT_TEXT } >= 1
        val hasScrollableContent = snapshot.elements.size >= SCROLLABLE_NODE_HINT_THRESHOLD

        return ScreenSignals(
            hasPasswordField = hasPasswordField,
            hasPaymentOrTransferSignals = hasPaymentOrTransferSignals,
            isBankingApp = isBankingApp,
            isMessagingApp = isMessagingApp,
            hasScrollableContent = hasScrollableContent,
            hasVerificationCode = hasVerificationCode,
            hasPersonalDataRequest = hasPersonalDataRequest,
            hasFormFields = hasFormFields
        )
    }

    private fun mergeWarnings(input: List<RiskWarning>): List<RiskWarning> =
        input.distinctBy { it.type }.sortedByDescending { it.severity }

    private fun isPlaceholderOnly(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == ScreenTextSanitizer.PASSWORD_PLACEHOLDER ||
            trimmed == ScreenTextSanitizer.CODE_PLACEHOLDER ||
            trimmed == ScreenTextSanitizer.SENSITIVE_NUMBER_PLACEHOLDER ||
            trimmed == ScreenTextSanitizer.PRIVATE_DATA_PLACEHOLDER
    }

    private fun appLabelFor(packageName: String?): String? {
        val key = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        return when {
            "whatsapp" in key -> "WhatsApp"
            "ojoclaro" in key || "ojo_claro" in key -> "Estela"
            "settings" in key || "ajustes" in key -> "Ajustes"
            "google.android.apps.maps" in key -> "Maps"
            "mercadopago" in key -> "Mercado Pago"
            "galicia" in key -> "Banco Galicia"
            "bbva" in key -> "BBVA"
            "santander" in key -> "Santander"
            "brubank" in key -> "Brubank"
            else -> null
        }
    }

    private fun isMessagingPackage(packageName: String?): Boolean {
        val key = packageName?.lowercase(Locale.ROOT) ?: return false
        return MESSAGING_PACKAGE_TOKENS.any { it in key }
    }

    companion object {
        private const val MAX_TEXT_LINES = 24
        private const val MAX_BUTTONS = 10
        private const val MAX_FIELDS = 6
        private const val SCROLLABLE_NODE_HINT_THRESHOLD = 12

        private val MESSAGING_PACKAGE_TOKENS = listOf(
            "whatsapp",
            "messenger",
            "telegram",
            "signal",
            "android.mms",
            "messaging"
        )
    }
}
