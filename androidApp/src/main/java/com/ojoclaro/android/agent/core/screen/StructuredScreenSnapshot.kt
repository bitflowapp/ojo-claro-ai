package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskWarning

/**
 * Snapshot estructurado v1 — la pieza que el Agent Core consume cuando el
 * flag `accessibilityRuntimeContextEnabled` está prendido.
 *
 * Diferencias con [ScreenSnapshot] (crudo):
 *  - El texto ya viene redactado en [redactedTextLines]. Quien lea acá no
 *    tiene que volver a aplicar PrivacyGuard.
 *  - Las listas de botones, campos editables y enfocados son extraídas y
 *    estables (label trimmed).
 *  - Las [warnings] están pre-computadas vía RiskDetector.
 *  - Las [signals] clasifican la pantalla en categorías booleanas.
 *  - [isLimited] indica que la pantalla fue restringida (hot zone o vacía).
 *
 * Diseñado como tipo valor inmutable, sin Android, sin coroutines. Apto para
 * cruzar capas (`AgentContext`, repository, UI) sin riesgo.
 */
data class StructuredScreenSnapshot(
    val packageName: String?,
    val appLabel: String?,
    val capturedAtMillis: Long,
    val redactedTextLines: List<String>,
    val buttons: List<String>,
    val editableFields: List<String>,
    val focusedLabel: String?,
    val totalNodes: Int,
    val signals: ScreenSignals,
    val warnings: List<RiskWarning>,
    val isLimited: Boolean
) {
    init {
        require(totalNodes >= 0) { "totalNodes must be non-negative" }
        require(capturedAtMillis >= 0L) { "capturedAtMillis must be non-negative" }
    }

    val isEmpty: Boolean
        get() = redactedTextLines.isEmpty() &&
            buttons.isEmpty() &&
            editableFields.isEmpty() &&
            packageName.isNullOrBlank()

    val hasButtons: Boolean
        get() = buttons.isNotEmpty()

    val hasEditableFields: Boolean
        get() = editableFields.isNotEmpty()

    val shouldBlockGeneralActions: Boolean
        get() = signals.isBankingApp || signals.hasPasswordField

    companion object {
        fun empty(capturedAtMillis: Long): StructuredScreenSnapshot = StructuredScreenSnapshot(
            packageName = null,
            appLabel = null,
            capturedAtMillis = capturedAtMillis,
            redactedTextLines = emptyList(),
            buttons = emptyList(),
            editableFields = emptyList(),
            focusedLabel = null,
            totalNodes = 0,
            signals = ScreenSignals.EMPTY,
            warnings = emptyList(),
            isLimited = true
        )
    }
}
