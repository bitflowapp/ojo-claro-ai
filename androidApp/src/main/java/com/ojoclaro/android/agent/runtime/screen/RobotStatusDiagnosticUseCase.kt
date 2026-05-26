package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector
import com.ojoclaro.android.performance.RobotLoopBlockReason
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

class RobotStatusDiagnosticUseCase(
    private val provider: ScreenContextProvider,
    private val whatsAppDetector: WhatsAppScreenDetector = WhatsAppScreenDetector(),
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    fun handle(rawText: String): RobotStatusDiagnosticResult {
        if (!RobotStatusDiagnosticPhrases.isDiagnosticCommand(rawText)) {
            return RobotStatusDiagnosticResult.NotADiagnosticCommand
        }

        val start = System.nanoTime()
        var result: RobotStatusDiagnosticResult = RobotStatusDiagnosticResult.Spoken(
            spokenText = ACCESSIBILITY_OFF_TEXT
        )
        var stats = SafeScreenSnapshotStats(
            packageName = null,
            elementCount = 0,
            buttonCount = 0,
            fieldCount = 0
        )
        var whatsAppDetected = false
        var chatOpen = false
        var blockReason = RobotLoopBlockReason.NONE

        try {
            if (!isAccessibilityReady()) {
                blockReason = RobotLoopBlockReason.ACCESSIBILITY_OFF
                result = RobotStatusDiagnosticResult.Spoken(ACCESSIBILITY_OFF_TEXT)
                return result
            }

            val snapshot = runCatching { provider.current() }.getOrNull()
            stats = snapshot.safeStats()
            val state = whatsAppDetector.detect(snapshot)
            whatsAppDetected = state.isOpen
            chatOpen = state.isInChat
            result = RobotStatusDiagnosticResult.Spoken(
                spokenText = buildDiagnosticText(stats, whatsAppDetected, chatOpen, snapshot == null)
            )
            return result
        } finally {
            val elapsedMillis = (System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000L
            RobotLoopInstrumentation.recordSafeLog(
                RobotLoopSafeLogEvent(
                    stage = RobotLoopLogStage.ROBOT_STATUS_DIAGNOSTIC,
                    result = if (blockReason == RobotLoopBlockReason.ACCESSIBILITY_OFF) {
                        RobotLoopLogResult.ACCESSIBILITY_OFF
                    } else {
                        RobotLoopLogResult.OK
                    },
                    durationMillis = elapsedMillis,
                    packageName = stats.packageName,
                    elementCount = stats.elementCount,
                    buttonCount = stats.buttonCount,
                    fieldCount = stats.fieldCount,
                    whatsappDetected = whatsAppDetected,
                    chatOpen = chatOpen,
                    blocked = blockReason != RobotLoopBlockReason.NONE,
                    blockReason = blockReason
                )
            )
        }
    }

    private fun buildDiagnosticText(
        stats: SafeScreenSnapshotStats,
        whatsAppDetected: Boolean,
        chatOpen: Boolean,
        missingSnapshot: Boolean
    ): String {
        val app = when {
            whatsAppDetected -> "WhatsApp"
            stats.packageName.isNullOrBlank() -> "desconocida"
            else -> stats.packageName
        }
        val status = when {
            whatsAppDetected && chatOpen -> "chat abierto"
            whatsAppDetected -> "WhatsApp sin chat abierto"
            missingSnapshot -> "sin datos"
            else -> "sin WhatsApp detectado"
        }

        return "Accesibilidad activa. " +
            "App detectada: $app. " +
            "Elementos: ${stats.elementCount}. " +
            "Botones: ${stats.buttonCount}. " +
            "Campos: ${stats.fieldCount}. " +
            "Estado: $status."
    }

    companion object {
        const val ACCESSIBILITY_OFF_TEXT: String =
            "Accesibilidad no está activa. Activá Estela en Ajustes de Accesibilidad."
    }
}

object RobotStatusDiagnosticPhrases {
    fun isDiagnosticCommand(text: String): Boolean =
        TextMatchNormalizer.normalize(text) in PHRASES

    private val PHRASES: Set<String> = setOf(
        "diagnostico",
        "estado del robot",
        "estado de ojo claro"
    )
}

sealed class RobotStatusDiagnosticResult {
    object NotADiagnosticCommand : RobotStatusDiagnosticResult()

    data class Spoken(
        val spokenText: String
    ) : RobotStatusDiagnosticResult()
}
