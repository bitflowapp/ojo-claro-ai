package com.ojoclaro.android.agent.diagnostics

object RobotCapabilityMatrix {
    fun defaultStaticMatrix(): List<CapabilityStatus> = listOf(
        CapabilityStatus(
            capability = RobotCapability.VOICE_INPUT,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "HomeScreen conecta VoiceCommandController con AndroidSpeechInputEngine/SpeechRecognizer; requiere RECORD_AUDIO y prueba en dispositivo.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.TEXT_TO_SPEECH,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "SpeechController usa TextToSpeech con QUEUE_FLUSH, dedupe y generation guards; depende del motor TTS instalado.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.ACCESSIBILITY_SCREEN_READING,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "AccessibilityScreenReader lee solo texto visible desde OjoClaroAccessibilityService cuando el usuario activa el servicio.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.OCR_READING,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "TextScanScreen y TextRecognitionAnalyzer usan camara/ML Kit local; falta validar permiso, foco y resultados en Android fisico.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.SCREEN_UNDERSTANDING,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "ScreenUnderstandingUseCase y DeterministicScreenSummarizer estan cableados; dependen de snapshot real de Accesibilidad.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.OPEN_APP,
            status = CapabilityReadiness.PARTIAL,
            reason = "El flujo legacy abre apps principales conocidas como WhatsApp/Telefono/Maps; apertura generica por nombre queda parcial.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.OPEN_DIALER,
            status = CapabilityReadiness.READY_STATIC,
            reason = "PhoneActionExecutor prepara Intent.ACTION_DIAL y no requiere ni usa CALL_PHONE; el usuario toca llamar manualmente.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.PREPARE_WHATSAPP_MESSAGE,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "WRITE_MESSAGE confirmado delega a ComposeWhatsAppMessage y WhatsAppIntentHelper.composeMessage con Intent.ACTION_SEND; prepara texto para revision humana y no envia automaticamente.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.CONTACT_LOOKUP,
            status = CapabilityReadiness.PARTIAL,
            reason = "MemoryContactResolver resuelve memoria local, emergencia y numeros dictados; no hay lectura real de agenda con READ_CONTACTS.",
            requiresRuntimeDevice = false
        ),
        CapabilityStatus(
            capability = RobotCapability.CONTACT_INSERT_REQUEST,
            status = CapabilityReadiness.NOT_IMPLEMENTED,
            reason = "No hay flujo ACTION_INSERT/ContactsContract para pedir agendar contacto; debe agregarse con confirmacion humana en una fase futura.",
            requiresRuntimeDevice = false
        ),
        CapabilityStatus(
            capability = RobotCapability.WHATSAPP_VISIBLE_CHAT_DETECTION,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "WhatsAppScreenDetector, WhatsAppChatListDetector y WhatsAppVisibleChatsReader usan solo pantalla visible por Accesibilidad; no leen bases ni historial.",
            requiresRuntimeDevice = true
        ),
        CapabilityStatus(
            capability = RobotCapability.SITUATION_BRAIN_CONTEXT,
            status = CapabilityReadiness.READY_STATIC,
            reason = "SituationRuntimeMemory conserva ActiveGoal, PendingAction y recentTurns solo en RAM; SituationBrainFeatureFlag sigue apagado por defecto.",
            requiresRuntimeDevice = false
        ),
        CapabilityStatus(
            capability = RobotCapability.SITUATION_BRAIN_CONFIRMATION,
            status = CapabilityReadiness.READY_STATIC,
            reason = "SituationDecisionApplier y SituationConfirmedActionAdapter soportan confirmaciones para OPEN_APP, CALL_CONTACT y WRITE_MESSAGE seguro.",
            requiresRuntimeDevice = false
        ),
        CapabilityStatus(
            capability = RobotCapability.HARD_CANCEL,
            status = CapabilityReadiness.NEEDS_RUNTIME_QA,
            reason = "VoiceCommandDispatcher y SituationBrain reconocen callate/cancelacion; HomeViewModel corta TTS y limpia memoria segun la ruta activa.",
            requiresRuntimeDevice = true
        )
    )
}
