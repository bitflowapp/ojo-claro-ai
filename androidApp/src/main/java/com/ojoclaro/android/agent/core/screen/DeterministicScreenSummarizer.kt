package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskType

/**
 * Resumidor determinista (sin LLM) de pantallas.
 *
 * Reglas:
 *  - Si la pantalla es hot zone (banca o contraseña), NO se lee el texto:
 *    solo se dice una advertencia.
 *  - Si hay riesgo medio (códigos de verificación), se lee con redacción.
 *  - El texto se recorta con PrivacyGuard.sanitizeScreenText y se pasan filtros
 *    redactSensitiveText.
 *  - El resumen NUNCA afirma información que no está en el snapshot.
 *  - El resumen NUNCA persiste el snapshot.
 */
class DeterministicScreenSummarizer(
    private val riskDetector: RiskDetector = RiskDetector()
) {

    fun summarize(snapshot: ScreenSnapshot?, mode: ScreenSummaryMode): ScreenSummary {
        if (snapshot == null || (!snapshot.hasText && !snapshot.hasElements)) {
            return ScreenSummary(
                mode = mode,
                spokenText = "No tengo lectura de esta pantalla. Tocá donde quieras o pedime algo más.",
                risk = ScreenRiskAssessment.SAFE,
                isLimited = true
            )
        }

        val risk = assessRisk(snapshot)

        // Excepción: WHAT_CAN_I_DO no lee texto de pantalla, solo lista las
        // acciones interactivas seguras (botones/links). Es accesibilidad
        // pura. Solo bloqueamos si la pantalla es claramente bancaria.
        if (!risk.allowedToReadAloud && !(mode == ScreenSummaryMode.WHAT_CAN_I_DO && !risk.isBanking)) {
            return ScreenSummary(
                mode = mode,
                spokenText = hotZoneAdvisory(risk),
                risk = risk,
                isLimited = true
            )
        }

        val sanitizedText = PrivacyGuard.sanitizeScreenText(
            PrivacyGuard.redactSensitiveText(snapshot.text)
        )

        val text = when (mode) {
            ScreenSummaryMode.SHORT -> shortSummary(snapshot, sanitizedText)
            ScreenSummaryMode.DETAILED -> detailedSummary(snapshot, sanitizedText)
            ScreenSummaryMode.WHERE_AM_I -> whereAmI(snapshot)
            ScreenSummaryMode.WHAT_CAN_I_DO -> whatCanIDo(snapshot)
            ScreenSummaryMode.IMPORTANT -> importantOnly(sanitizedText, snapshot)
        }

        return ScreenSummary(
            mode = mode,
            spokenText = text,
            risk = risk,
            isLimited = false
        )
    }

    fun assessRisk(snapshot: ScreenSnapshot): ScreenRiskAssessment {
        val packageWarnings = riskDetector.detectFromPackageName(snapshot.packageName)
        val textWarnings = if (snapshot.hasText) {
            riskDetector.detectFromVisibleText(snapshot.text)
        } else {
            emptyList()
        }
        val allWarnings = (packageWarnings + textWarnings).distinctBy { it.type }

        val isBanking = allWarnings.any { it.type == RiskType.BANKING_SCREEN }
        val containsPasswordField = allWarnings.any { it.type == RiskType.PASSWORD_FIELD } ||
            snapshot.elements.any { it.isPassword }
        val containsVerificationCode = allWarnings.any { it.type == RiskType.VERIFICATION_CODE }
        val allowedToReadAloud = PrivacyGuard.canReadAloud(snapshot.text, allWarnings) &&
            !isBanking &&
            !containsPasswordField

        return ScreenRiskAssessment(
            warnings = allWarnings,
            isBanking = isBanking,
            containsPasswordField = containsPasswordField,
            containsVerificationCode = containsVerificationCode,
            allowedToReadAloud = allowedToReadAloud
        )
    }

    private fun hotZoneAdvisory(risk: ScreenRiskAssessment): String = when {
        risk.isBanking -> "Esta pantalla puede contener datos bancarios. Por seguridad no la leo."
        risk.containsPasswordField -> "Veo un campo de contraseña. Por seguridad no lo leo."
        else -> "Esta pantalla parece sensible. No la leo sin tu confirmación."
    }

    private fun shortSummary(snapshot: ScreenSnapshot, sanitizedText: String): String {
        val productSummary = productShortSummary(snapshot)
        if (productSummary != null) return productSummary

        val heading = snapshot.elements.firstOrNull { it.role == ScreenElementRole.HEADING }?.label
        if (!heading.isNullOrBlank()) {
            return "Estás en: ${heading.take(MAX_HEADING_CHARS)}."
        }
        val firstLine = sanitizedText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_HEADING_CHARS)
        return when {
            !firstLine.isNullOrBlank() -> "La pantalla dice: $firstLine."
            else -> "No encontré un título claro."
        }
    }

    private fun productShortSummary(snapshot: ScreenSnapshot): String? {
        if (isWhatsAppLike(snapshot) && snapshot.elements.none { it.role == ScreenElementRole.HEADING }) {
            return "App detectada: WhatsApp. Puedo listar chats visibles o guiarte, pero no leo mensajes completos."
        }

        val appLabel = appLabelForSpeech(snapshot.packageName)
        val heading = snapshot.elements.firstOrNull { it.role == ScreenElementRole.HEADING }?.label
            ?.take(MAX_HEADING_CHARS)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val actions = topActions(snapshot)
        val primaryAction = actions.firstOrNull()

        if (appLabel.isNullOrBlank() && heading.isNullOrBlank() && primaryAction.isNullOrBlank()) {
            return null
        }

        val importantItems = (listOfNotNull(heading) + actions)
            .distinct()
            .take(MAX_IMPORTANT_ITEMS)
        val parts = mutableListOf<String>()
        if (!appLabel.isNullOrBlank()) parts += "App detectada: $appLabel"
        if (!heading.isNullOrBlank()) parts += "Pantalla: $heading"
        parts += if (!primaryAction.isNullOrBlank()) {
            "Acción principal posible: $primaryAction"
        } else {
            "Acción principal posible: no detecté una acción clara"
        }
        if (importantItems.isNotEmpty()) {
            parts += "Elementos importantes: ${importantItems.joinToString(", ")}"
        }
        return parts.joinToString(". ") + "."
    }

    private fun detailedSummary(snapshot: ScreenSnapshot, sanitizedText: String): String {
        val builder = StringBuilder()
        val heading = snapshot.elements.firstOrNull { it.role == ScreenElementRole.HEADING }?.label
        if (!heading.isNullOrBlank()) {
            builder.append("Estás en: ").append(heading.take(MAX_HEADING_CHARS)).append(". ")
        }
        val excerpt = sanitizedText.take(MAX_DETAIL_CHARS).trim()
        if (excerpt.isNotBlank()) {
            builder.append("Resumen: ").append(excerpt)
            if (sanitizedText.length > MAX_DETAIL_CHARS) builder.append("…")
            builder.append(". ")
        }
        val actions = topActions(snapshot)
        if (actions.isNotEmpty()) {
            builder.append("Acciones disponibles: ").append(actions.joinToString(", ")).append(".")
        }
        return builder.toString().trim().ifBlank {
            "No hay texto útil para resumir."
        }
    }

    private fun whereAmI(snapshot: ScreenSnapshot): String {
        val heading = snapshot.elements.firstOrNull { it.role == ScreenElementRole.HEADING }?.label
        val pkg = snapshot.packageName
        val parts = mutableListOf<String>()
        if (!heading.isNullOrBlank()) {
            parts += "Pantalla: ${heading.take(MAX_HEADING_CHARS)}"
        }
        if (!pkg.isNullOrBlank()) {
            parts += "App: ${pkg.take(MAX_PACKAGE_CHARS)}"
        }
        val buttons = snapshot.elements.count {
            it.role == ScreenElementRole.BUTTON && it.isInteractive
        }
        val fields = snapshot.elements.count {
            it.role == ScreenElementRole.EDIT_TEXT && !it.isPassword
        }
        val checkboxes = snapshot.elements.count {
            it.role == ScreenElementRole.CHECKBOX && it.isInteractive
        }
        if (buttons > 0) parts += "$buttons ${if (buttons == 1) "botón" else "botones"}"
        if (fields > 0) parts += "$fields ${if (fields == 1) "campo" else "campos"}"
        if (checkboxes > 0) parts += "$checkboxes ${if (checkboxes == 1) "opción" else "opciones"}"
        return if (parts.isEmpty()) {
            "No tengo suficiente información sobre dónde estás."
        } else {
            parts.joinToString(". ") + "."
        }
    }

    private fun whatCanIDo(snapshot: ScreenSnapshot): String {
        val actions = topActions(snapshot)
        return if (actions.isEmpty()) {
            "No detecté acciones claras en esta pantalla."
        } else {
            "Podés: " + actions.joinToString(", ") + "."
        }
    }

    private fun importantOnly(sanitizedText: String, snapshot: ScreenSnapshot): String {
        // Priorizamos headings primero, después acciones principales (botones,
        // links, edit fields, checkboxes interactivos). Nunca password.
        val headings = snapshot.elements
            .filter { it.role == ScreenElementRole.HEADING }
            .map { it.label.take(MAX_HEADING_CHARS).trim() }
            .filter { it.isNotBlank() }
        val actions = topActions(snapshot)
        val merged = (headings + actions)
            .distinct()
            .take(MAX_ACTIONS)
            .joinToString(". ")
        if (merged.isNotBlank()) return "$merged."
        return sanitizedText.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_HEADING_CHARS)
            ?.plus(".")
            ?: "No detecté nada destacado."
    }

    private fun topActions(snapshot: ScreenSnapshot): List<String> {
        return snapshot.elements
            .filter {
                it.isInteractive &&
                    !it.isPassword &&
                    (it.role == ScreenElementRole.BUTTON ||
                        it.role == ScreenElementRole.LINK ||
                        it.role == ScreenElementRole.EDIT_TEXT ||
                        it.role == ScreenElementRole.CHECKBOX)
            }
            .map { it.label.take(MAX_HEADING_CHARS).trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ACTIONS)
    }

    private fun appLabelForSpeech(packageName: String?): String? {
        val key = packageName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            "whatsapp" in key -> "WhatsApp"
            "ojoclaro" in key || "ojo_claro" in key -> "Ojo Claro"
            "settings" in key || "ajustes" in key -> "Ajustes"
            else -> "app actual"
        }
    }

    private fun isWhatsAppLike(snapshot: ScreenSnapshot): Boolean =
        snapshot.packageName?.lowercase()?.contains("whatsapp") == true

    companion object {
        private const val MAX_HEADING_CHARS = 80
        private const val MAX_PACKAGE_CHARS = 60
        private const val MAX_DETAIL_CHARS = 400
        private const val MAX_ACTIONS = 3
        private const val MAX_IMPORTANT_ITEMS = 3
    }
}
