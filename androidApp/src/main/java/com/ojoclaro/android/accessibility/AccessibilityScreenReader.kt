package com.ojoclaro.android.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.ojoclaro.android.external.CommandResult
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskWarning

object AccessibilityScreenReader {

    fun readVisibleScreen(context: Context): CommandResult {
        if (!isServiceEnabled(context)) {
            return CommandResult.Failed(
                spokenText = "Para leer esta pantalla, activá Estela en Accesibilidad. Solo leo cuando vos me lo pedís.",
                recoverable = true
            )
        }

        if (!OjoClaroAccessibilityService.isConnected()) {
            return CommandResult.Failed(
                spokenText = "Estela se está activando. Esperá un segundo y probá de nuevo.",
                recoverable = true
            )
        }

        val visibleText = try {
            OjoClaroAccessibilityService.readVisibleText()
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        } catch (_: Exception) {
            return CommandResult.Failed(
                spokenText = "No pude leer esta pantalla ahora. Probá de nuevo en unos segundos.",
                recoverable = true
            )
        }

        if (visibleText.isBlank()) {
            return CommandResult.Failed(
                spokenText = "No encontré texto visible en esta pantalla. Probá abrir un chat o una pantalla con texto.",
                recoverable = true
            )
        }

        return CommandResult.Success(
            spokenText = riskAwareScreenText(visibleText)
        )
    }

    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(
            context,
            OjoClaroAccessibilityService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }

    const val permissionExplanation =
        "Solo leo texto visible cuando vos me lo pedís. No guardo mensajes ni envío nada. Y nunca leo contraseñas."

    private fun riskAwareScreenText(visibleText: String): String {
        val redactedText = redactForSpeech(visibleText)
        val safeText = PrivacyGuard.sanitizeScreenText(redactedText)

        val warnings = riskDetector.detectFromVisibleText(visibleText)

        if (warnings.isEmpty()) {
            return "La pantalla dice: $safeText"
        }

        val warningText = buildRiskWarningText(warnings)

        if (!PrivacyGuard.canReadAloud(visibleText, warnings)) {
            return warningText
        }

        return "$warningText La pantalla dice: $safeText"
    }

    private fun buildRiskWarningText(warnings: List<RiskWarning>): String {
        val details = warnings
            .distinctBy { it.type }
            .take(MAX_WARNINGS_TO_SPEAK)
            .joinToString(" ") { it.spokenText }

        return "Antes de responder, te aviso: este texto puede ser sensible. $details".trim()
    }

    private fun redactForSpeech(text: String): String =
        PrivacyGuard.redactCardLikeNumbers(
            PrivacyGuard.redactVerificationCodes(
                PrivacyGuard.redactPasswords(text)
            )
        )

    private const val MAX_WARNINGS_TO_SPEAK = 2

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val riskDetector = RiskDetector()
}
