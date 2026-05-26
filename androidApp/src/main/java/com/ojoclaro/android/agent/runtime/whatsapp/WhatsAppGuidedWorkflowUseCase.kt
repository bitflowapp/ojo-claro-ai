package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.performance.RobotLoopBlockReason
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

/**
 * Use case del WhatsApp Guided Workflow v1.
 *
 * Reglas hard:
 *  - Verbal-only. No tap, no escritura, no envío.
 *  - Plantillas FIJAS de respuesta. Nunca incluye contenido del chat.
 *  - No persiste snapshots ni guarda historial.
 *  - Si no es un comando guiado de WhatsApp, devuelve NotAWhatsAppCommand
 *    y el caller debe seguir su flujo normal (REPEAT_LAST, ScreenUnderstanding,
 *    orchestrator de WhatsApp seguro, etc.).
 *  - No usa LLM, no llama a la red.
 */
class WhatsAppGuidedWorkflowUseCase(
    private val provider: ScreenContextProvider,
    private val detector: WhatsAppScreenDetector = WhatsAppScreenDetector(),
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    fun handle(rawText: String): WhatsAppGuidedResponse {
        val start = System.nanoTime()
        val isCommand = WhatsAppGuidedPhrases.classify(rawText) != null
        val accessibilityOff = isCommand && !isAccessibilityReady()
        var result: WhatsAppGuidedResponse? = null
        try {
            result = handleInternal(rawText)
            return result
        } finally {
            val elapsedNanos = (System.nanoTime() - start).coerceAtLeast(0L)
            val guidance = result as? WhatsAppGuidedResponse.Guidance
            RobotLoopInstrumentation.recordElapsedNanos(
                metric = RobotLoopMetric.WHATSAPP_GUIDED_WORKFLOW,
                elapsedNanos = elapsedNanos
            )
            RobotLoopInstrumentation.recordSafeLog(
                RobotLoopSafeLogEvent(
                    stage = RobotLoopLogStage.WHATSAPP_GUIDED_WORKFLOW,
                    result = whatsAppGuidedLogResult(result, accessibilityOff),
                    durationMillis = elapsedNanos / 1_000_000L,
                    whatsappDetected = guidance?.state?.isOpen,
                    chatOpen = guidance?.state?.isInChat,
                    blocked = accessibilityOff,
                    blockReason = if (accessibilityOff) {
                        RobotLoopBlockReason.ACCESSIBILITY_OFF
                    } else {
                        RobotLoopBlockReason.NONE
                    }
                )
            )
        }
    }

    private fun handleInternal(rawText: String): WhatsAppGuidedResponse {
        val command = WhatsAppGuidedPhrases.classify(rawText)
            ?: return WhatsAppGuidedResponse.NotAWhatsAppCommand

        if (!isAccessibilityReady()) {
            return WhatsAppGuidedResponse.NotInWhatsApp(
                spokenText = NEEDS_ACCESSIBILITY_TEXT
            )
        }

        val snapshot = runCatching { provider.current() }.getOrNull()
        val state = detector.detect(snapshot)

        if (state.isUnknown) {
            return WhatsAppGuidedResponse.StateNotConfident(
                spokenText = "No puedo confirmar que estés en WhatsApp. Abrí el chat y decime otra vez."
            )
        }

        if (!state.isOpen) {
            return WhatsAppGuidedResponse.NotInWhatsApp(
                spokenText = "No estás en WhatsApp. Abrílo primero y volvé a pedirme."
            )
        }

        val spokenText = when (command) {
            WhatsAppGuidedCommand.AmIInWhatsApp -> amIInWhatsAppGuidance(state)
            WhatsAppGuidedCommand.WhatCanIDoHere -> whatCanIDoGuidance(state)
            WhatsAppGuidedCommand.HowDoISendPhoto -> howDoISendPhotoGuidance(state)
            WhatsAppGuidedCommand.HowDoISendLocation -> howDoISendLocationGuidance(state)
            WhatsAppGuidedCommand.HowDoISendMessage -> howDoISendMessageGuidance(state)
        }

        return WhatsAppGuidedResponse.Guidance(
            command = command,
            state = state,
            spokenText = spokenText
        )
    }

    private fun whatsAppGuidedLogResult(
        result: WhatsAppGuidedResponse?,
        accessibilityOff: Boolean
    ): RobotLoopLogResult {
        if (accessibilityOff) return RobotLoopLogResult.ACCESSIBILITY_OFF
        return when (result) {
            WhatsAppGuidedResponse.NotAWhatsAppCommand -> RobotLoopLogResult.NOT_A_COMMAND
            is WhatsAppGuidedResponse.NotInWhatsApp -> RobotLoopLogResult.NOT_IN_WHATSAPP
            is WhatsAppGuidedResponse.StateNotConfident -> RobotLoopLogResult.STATE_NOT_CONFIDENT
            is WhatsAppGuidedResponse.Guidance -> RobotLoopLogResult.OK
            null -> RobotLoopLogResult.STATE_NOT_CONFIDENT
        }
    }

    private fun amIInWhatsAppGuidance(state: WhatsAppScreenState): String {
        if (state.confidence == WhatsAppDetectionConfidence.HIGH) {
            return if (state.isInChat) {
                "Sí, estás en WhatsApp y veo un chat abierto."
            } else {
                "Sí, estás en WhatsApp. Todavía no veo un chat abierto."
            }
        }
        // MEDIUM o LOW: no afirmamos en duro
        return "Parece que estás en WhatsApp, pero no puedo confirmarlo del todo. Abrí un chat para empezar."
    }

    private fun whatCanIDoGuidance(state: WhatsAppScreenState): String {
        if (!state.isInChat) {
            return "Estás en WhatsApp pero no veo un chat abierto. Abrí un chat para que pueda guiarte."
        }
        val parts = mutableListOf<String>()
        if (state.hasMessageField) parts += "Veo el campo de mensaje, está en la parte inferior"
        if (state.hasCameraButton) parts += "veo un botón de cámara"
        if (state.hasAttachButton) parts += "veo un botón de adjuntar"
        if (state.hasMicrophoneButton) parts += "veo un botón de micrófono"
        if (state.hasSendButton) parts += "veo un botón de enviar"
        if (state.hasBackButton) parts += "veo un botón para volver"
        if (parts.isEmpty()) {
            return "Estás en un chat de WhatsApp, pero no detecté claramente los controles. " +
                "Probá tocar abajo de la pantalla."
        }
        return parts.joinToString(separator = ". ") +
            ". Yo no toco la app por vos: te guío para que toques vos."
    }

    private fun howDoISendPhotoGuidance(state: WhatsAppScreenState): String {
        if (!state.isInChat) {
            return "Abrí primero el chat al que querés mandar la foto, después decime: cómo mando una foto."
        }
        val intro = "Para mandar una foto: "
        val body = when {
            state.hasCameraButton -> "buscá el botón de cámara al lado del campo de mensaje. " +
                "Tocá dos veces para abrir la cámara, sacá la foto y después tocá enviar."
            state.hasAttachButton -> "tocá el botón de adjuntar al lado del campo de mensaje. " +
                "Después elegí Galería o Cámara, seleccioná la foto y tocá enviar."
            else -> "buscá un ícono de cámara o de clip cerca del campo de mensaje. " +
                "No detecté claramente cuál es, así que probá los íconos a la derecha del campo."
        }
        return intro + body + " Yo nunca envío la foto por vos."
    }

    private fun howDoISendLocationGuidance(state: WhatsAppScreenState): String {
        if (!state.isInChat) {
            return "Abrí primero el chat al que querés mandar la ubicación, " +
                "después decime: cómo mando ubicación."
        }
        val intro = "Para mandar tu ubicación en WhatsApp: "
        val body = if (state.hasAttachButton) {
            "tocá el botón de adjuntar al lado del campo de mensaje, después elegí Ubicación y compartí. "
        } else {
            "buscá el ícono de clip o adjuntar cerca del campo de mensaje, después elegí Ubicación. "
        }
        return intro + body + "Yo no envío la ubicación por mi cuenta."
    }

    private fun howDoISendMessageGuidance(state: WhatsAppScreenState): String {
        if (!state.isInChat) {
            return "Abrí primero el chat al que querés escribir, después decime cómo mandar el mensaje."
        }
        if (!state.hasMessageField) {
            return "No veo el campo de mensaje. Asegurate de estar adentro del chat y volvé a pedirme."
        }
        val sendHint = if (state.hasSendButton) {
            " Después tocá el botón enviar."
        } else {
            " Después buscá el botón enviar abajo a la derecha."
        }
        return "Tocá dos veces sobre el campo de mensaje y dictá lo que querés escribir." +
            sendHint +
            " Yo nunca envío el mensaje por vos."
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para que pueda guiarte en WhatsApp necesito el servicio de Accesibilidad activo. " +
                "Activá Estela en Ajustes de Accesibilidad."
    }
}
