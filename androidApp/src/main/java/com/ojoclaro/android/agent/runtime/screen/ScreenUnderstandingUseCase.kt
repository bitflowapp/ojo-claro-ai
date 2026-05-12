package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.DeterministicScreenSummarizer
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.core.screen.ScreenRiskAssessment
import com.ojoclaro.android.agent.core.screen.ScreenSummaryMode
import com.ojoclaro.android.performance.RobotLoopBlockReason
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

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
        val start = System.nanoTime()
        var stats = SafeScreenSnapshotStats(
            packageName = null,
            elementCount = 0,
            buttonCount = 0,
            fieldCount = 0
        )
        var resultForLog = RobotLoopLogResult.NOT_A_COMMAND
        var blockReason = RobotLoopBlockReason.NONE
        var blocked = false

        try {
            val mode = ScreenQueryPhrases.classify(rawText)
                ?: return ScreenUnderstandingResult.NotAScreenCommand

            if (!isAccessibilityReady()) {
                resultForLog = RobotLoopLogResult.ACCESSIBILITY_OFF
                blockReason = RobotLoopBlockReason.ACCESSIBILITY_OFF
                blocked = true
                return ScreenUnderstandingResult.NeedsAccessibilityService(
                    spokenText = NEEDS_ACCESSIBILITY_TEXT
                )
            }

            val snapshot = try {
                provider.current()
            } catch (_: Throwable) {
                null
            }
            stats = snapshot.safeStats()

            val summary = RobotLoopInstrumentation.measure(RobotLoopMetric.SCREEN_SUMMARIZER) {
                summarizer.summarize(snapshot, mode)
            }
            blocked = !summary.risk.allowedToReadAloud
            blockReason = if (blocked) {
                blockReasonFor(summary.risk)
            } else {
                RobotLoopBlockReason.NONE
            }
            resultForLog = when {
                blocked -> RobotLoopLogResult.BLOCKED_BY_SAFETY
                snapshot == null -> RobotLoopLogResult.NO_SNAPSHOT
                else -> RobotLoopLogResult.OK
            }
            return ScreenUnderstandingResult.Spoken(
                mode = mode,
                spokenText = summary.spokenText,
                isLimited = summary.isLimited,
                risk = summary.risk
            )
        } finally {
            val elapsedNanos = (System.nanoTime() - start).coerceAtLeast(0L)
            RobotLoopInstrumentation.recordElapsedNanos(
                metric = RobotLoopMetric.SCREEN_UNDERSTANDING,
                elapsedNanos = elapsedNanos
            )
            RobotLoopInstrumentation.recordSafeLog(
                RobotLoopSafeLogEvent(
                    stage = RobotLoopLogStage.SCREEN_UNDERSTANDING,
                    result = resultForLog,
                    durationMillis = elapsedNanos / 1_000_000L,
                    packageName = stats.packageName,
                    elementCount = stats.elementCount,
                    buttonCount = stats.buttonCount,
                    fieldCount = stats.fieldCount,
                    blocked = blocked,
                    blockReason = blockReason
                )
            )
        }
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para que pueda leer la pantalla, activá Ojo Claro en Ajustes de Accesibilidad. " +
                "Solo leo cuando vos me lo pedís y nunca leo contraseñas."
    }
}

private fun blockReasonFor(risk: ScreenRiskAssessment): RobotLoopBlockReason = when {
    risk.isBanking -> RobotLoopBlockReason.BANKING_SCREEN
    risk.containsPasswordField -> RobotLoopBlockReason.PASSWORD_FIELD
    risk.containsVerificationCode -> RobotLoopBlockReason.VERIFICATION_CODE
    else -> RobotLoopBlockReason.SENSITIVE_SCREEN
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
