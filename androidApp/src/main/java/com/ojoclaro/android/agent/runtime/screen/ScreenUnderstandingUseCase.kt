package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.DeterministicScreenSummarizer
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.core.screen.ScreenRiskAssessment
import com.ojoclaro.android.agent.core.screen.ScreenSummaryMode

/**
 * Pieza de glue del Agent Runtime v1.
 *
 * Responde a las consultas de pantalla del usuario:
 *  - "qué hay en pantalla" / "resumí la pantalla"           → SHORT
 *  - "dónde estoy"                                          → WHERE_AM_I
 *  - "qué puedo hacer acá"                                  → WHAT_CAN_I_DO
 *  - "leeme lo importante"                                  → IMPORTANT
 *  - "contame los detalles de la pantalla"                  → DETAILED
 *
 * Reglas no negociables:
 *  - Si el texto no es una consulta de pantalla, devuelve NotAScreenCommand
 *    y no consume el input. El caller debe seguir su flujo normal.
 *  - Si el servicio de Accesibilidad no está activo, devuelve un mensaje
 *    útil pidiendo activarlo. No falla en silencio.
 *  - Si la pantalla es bancaria/de contraseña/sensible según DeterministicScreenSummarizer,
 *    no se lee el contenido. Solo se dice una advertencia genérica.
 *  - NUNCA envía el snapshot a LLM, NUNCA lo persiste.
 */
class ScreenUnderstandingUseCase(
    private val provider: ScreenContextProvider,
    private val summarizer: DeterministicScreenSummarizer = DeterministicScreenSummarizer(),
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    fun handle(rawText: String): ScreenUnderstandingResult {
        val mode = ScreenQueryPhrases.classify(rawText)
            ?: return ScreenUnderstandingResult.NotAScreenCommand

        if (!isAccessibilityReady()) {
            return ScreenUnderstandingResult.NeedsAccessibilityService(
                spokenText = NEEDS_ACCESSIBILITY_TEXT
            )
        }

        val snapshot = try {
            provider.current()
        } catch (_: Throwable) {
            null
        }

        val summary = summarizer.summarize(snapshot, mode)
        return ScreenUnderstandingResult.Spoken(
            mode = mode,
            spokenText = summary.spokenText,
            isLimited = summary.isLimited,
            risk = summary.risk
        )
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para que pueda leer la pantalla, activá Ojo Claro en Ajustes de Accesibilidad. " +
                "Solo leo cuando vos me lo pedís y nunca leo contraseñas."
    }
}

/**
 * Resultado de procesar una consulta de pantalla.
 *
 * - NotAScreenCommand: el texto no era una consulta de pantalla. El caller
 *   debe ignorar este resultado y seguir su flujo normal.
 * - NeedsAccessibilityService: el comando se entendió, pero el servicio no
 *   está activo. El caller debe hablar el mensaje y pedir permiso.
 * - Spoken: el comando se procesó y hay un texto para hablar.
 */
sealed class ScreenUnderstandingResult {
    object NotAScreenCommand : ScreenUnderstandingResult()

    data class NeedsAccessibilityService(
        val spokenText: String
    ) : ScreenUnderstandingResult()

    data class Spoken(
        val mode: ScreenSummaryMode,
        val spokenText: String,
        val isLimited: Boolean,
        val risk: ScreenRiskAssessment
    ) : ScreenUnderstandingResult() {
        val isSafeToReadAloud: Boolean
            get() = risk.allowedToReadAloud
    }
}
