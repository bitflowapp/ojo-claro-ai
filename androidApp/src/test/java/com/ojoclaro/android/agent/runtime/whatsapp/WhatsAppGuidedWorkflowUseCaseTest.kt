package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppGuidedWorkflowUseCaseTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.clear()
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
    }

    private fun useCase(
        snapshot: ScreenSnapshot? = null,
        isReady: Boolean = true,
        throwOnRead: Boolean = false
    ): WhatsAppGuidedWorkflowUseCase {
        val provider = ScreenContextProvider {
            if (throwOnRead) error("boom")
            snapshot
        }
        return WhatsAppGuidedWorkflowUseCase(
            provider = provider,
            isAccessibilityReady = { isReady }
        )
    }

    private fun chatSnapshot(
        packageName: String? = "com.whatsapp",
        extraElements: List<ScreenElement> = emptyList()
    ) = ScreenSnapshot(
        packageName = packageName,
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Micrófono", ScreenElementRole.BUTTON, isInteractive = true)
        ) + extraElements,
        capturedAtMillis = 0L
    )

    @Test
    fun unrelatedTextReturnsNotAWhatsAppCommand() {
        val result = useCase(snapshot = chatSnapshot()).handle("abrí WhatsApp")
        assertEquals(WhatsAppGuidedResponse.NotAWhatsAppCommand, result)
    }

    @Test
    fun repeatLastIsNotConsumed() {
        val result = useCase(snapshot = chatSnapshot()).handle("repetí")
        assertEquals(WhatsAppGuidedResponse.NotAWhatsAppCommand, result)
    }

    @Test
    fun screenUnderstandingCommandsAreNotConsumed() {
        listOf(
            "qué hay en pantalla",
            "resumí la pantalla",
            "dónde estoy",
            "leeme lo importante",
            "qué puedo hacer acá"
        ).forEach { phrase ->
            val result = useCase(snapshot = chatSnapshot()).handle(phrase)
            assertEquals(
                WhatsAppGuidedResponse.NotAWhatsAppCommand,
                result,
                "phrase '$phrase' should not be consumed by WhatsApp guided workflow"
            )
        }
    }

    @Test
    fun accessibilityServiceOffReturnsNotInWhatsAppMessage() {
        val result = useCase(snapshot = null, isReady = false).handle("¿estoy en WhatsApp?")
        assertTrue(result is WhatsAppGuidedResponse.NotInWhatsApp)
        val r = result as WhatsAppGuidedResponse.NotInWhatsApp
        assertTrue(r.spokenText.contains("Accesibilidad", ignoreCase = true))
    }

    @Test
    fun nullSnapshotReturnsStateNotConfident() {
        val result = useCase(snapshot = null, isReady = true).handle("¿estoy en WhatsApp?")
        assertTrue(result is WhatsAppGuidedResponse.StateNotConfident)
        val r = result as WhatsAppGuidedResponse.StateNotConfident
        assertTrue(r.spokenText.contains("No puedo confirmar", ignoreCase = true))
        assertTrue(r.spokenText.contains("WhatsApp", ignoreCase = true))
    }

    @Test
    fun notInWhatsAppAsksToOpenIt() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "Lista de tareas",
            elements = emptyList(),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot).handle("¿estoy en WhatsApp?")
        assertTrue(result is WhatsAppGuidedResponse.NotInWhatsApp)
        val r = result as WhatsAppGuidedResponse.NotInWhatsApp
        assertTrue(r.spokenText.contains("WhatsApp"))
        assertTrue(r.spokenText.contains("Abrílo", ignoreCase = true) ||
            r.spokenText.contains("abri", ignoreCase = true))
    }

    @Test
    fun amIInWhatsAppHighConfidenceWithChatConfirmsBoth() {
        val result = useCase(snapshot = chatSnapshot()).handle("¿estoy en WhatsApp?")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertEquals(WhatsAppGuidedCommand.AmIInWhatsApp, r.command)
        assertTrue(r.spokenText.contains("Sí", ignoreCase = true))
        assertTrue(r.spokenText.contains("chat", ignoreCase = true))
    }

    @Test
    fun whatCanIDoListsDetectedControls() {
        val result = useCase(snapshot = chatSnapshot()).handle("qué puedo hacer en este chat")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertTrue(r.spokenText.contains("campo de mensaje", ignoreCase = true))
        assertTrue(r.spokenText.contains("cámara", ignoreCase = true))
        assertTrue(r.spokenText.contains("enviar", ignoreCase = true))
        assertTrue(r.spokenText.contains("no toco la app", ignoreCase = true))
    }

    @Test
    fun howDoISendPhotoReturnsGuidanceNotAction() {
        val result = useCase(snapshot = chatSnapshot()).handle("¿cómo mando una foto?")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertEquals(WhatsAppGuidedCommand.HowDoISendPhoto, r.command)
        // Es GUÍA: explica cómo, no afirma que envió.
        assertTrue(r.spokenText.contains("cámara", ignoreCase = true) ||
            r.spokenText.contains("adjuntar", ignoreCase = true))
        assertTrue(r.spokenText.contains("nunca envío", ignoreCase = true))
        assertFalse(
            r.spokenText.contains("foto enviada", ignoreCase = true),
            "no debe afirmar que la foto fue enviada"
        )
    }

    @Test
    fun howDoISendLocationReturnsGuidanceNotAction() {
        val result = useCase(snapshot = chatSnapshot()).handle("¿cómo mando ubicación?")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertEquals(WhatsAppGuidedCommand.HowDoISendLocation, r.command)
        assertTrue(r.spokenText.contains("adjuntar", ignoreCase = true) ||
            r.spokenText.contains("clip", ignoreCase = true))
        assertTrue(r.spokenText.contains("Ubicación", ignoreCase = true))
        assertFalse(
            r.spokenText.contains("ubicación enviada", ignoreCase = true),
            "no debe afirmar que la ubicación fue enviada"
        )
    }

    @Test
    fun howDoISendMessageReturnsGuidanceNotAction() {
        val result = useCase(snapshot = chatSnapshot()).handle("¿cómo le mando un mensaje?")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertTrue(r.spokenText.contains("campo de mensaje", ignoreCase = true))
        assertTrue(r.spokenText.contains("dictá", ignoreCase = true))
        assertTrue(r.spokenText.contains("nunca envío", ignoreCase = true))
        assertFalse(
            r.spokenText.contains("mensaje enviado", ignoreCase = true),
            "no debe afirmar que el mensaje fue enviado"
        )
    }

    @Test
    fun howDoISendPhotoWithoutChatAsksToOpenChat() {
        // package matchea pero NO hay chat abierto (lista de chats sin composer)
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "x",
            elements = listOf(
                ScreenElement("Sofi", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Familia", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot).handle("¿cómo mando una foto?")
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
        val r = result as WhatsAppGuidedResponse.Guidance
        assertTrue(r.spokenText.contains("Abrí primero el chat", ignoreCase = true))
    }

    @Test
    fun guidanceTextNeverContainsChatContent() {
        // El snapshot trae texto con "contenido privado de un mensaje".
        // La guía generada NUNCA debe quotearlo: las plantillas son fijas.
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Sofi: estoy llegando tarde, fijate la dirección",
            elements = listOf(
                ScreenElement(
                    label = "Sofi: estoy llegando tarde",
                    role = ScreenElementRole.TEXT,
                    isInteractive = false
                ),
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val commandsToProbe = listOf(
            "¿estoy en WhatsApp?",
            "qué puedo hacer en este chat",
            "cómo mando una foto",
            "cómo mando ubicación",
            "cómo le mando un mensaje"
        )
        commandsToProbe.forEach { phrase ->
            val result = useCase(snapshot = snapshot).handle(phrase)
            val text = when (result) {
                is WhatsAppGuidedResponse.Guidance -> result.spokenText
                is WhatsAppGuidedResponse.NotInWhatsApp -> result.spokenText
                is WhatsAppGuidedResponse.StateNotConfident -> result.spokenText
                WhatsAppGuidedResponse.NotAWhatsAppCommand -> ""
            }
            assertFalse(
                text.contains("estoy llegando", ignoreCase = true),
                "guidance for '$phrase' leaked private message content"
            )
            assertFalse(
                text.contains("Sofi:"),
                "guidance for '$phrase' leaked sender name from chat"
            )
            assertFalse(
                text.contains("dirección", ignoreCase = true),
                "guidance for '$phrase' leaked chat content"
            )
        }
    }

    @Test
    fun whatsAppGuidedSafeLogUsesBooleansNotContent() {
        RobotLoopInstrumentation.clear()
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Sofi: estoy llegando tarde, fijate la direccion",
            elements = listOf(
                ScreenElement("Sofi: estoy llegando tarde", ScreenElementRole.TEXT, isInteractive = false),
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Camara", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )

        useCase(snapshot = snapshot).handle("estoy en WhatsApp")

        val logs = RobotLoopInstrumentation.safeLogSnapshot().joinToString("\n")
        assertTrue(logs.contains("whatsappDetected=true") || logs.contains("whatsapp=true"))
        assertTrue(logs.contains("chatOpen=true"))
        assertFalse(logs.contains("Sofi", ignoreCase = true))
        assertFalse(logs.contains("estoy llegando", ignoreCase = true))
        assertFalse(logs.contains("direccion", ignoreCase = true))
    }

    @Test
    fun whenInChatWithoutCameraGivesAttachFallback() {
        // No hay botón cámara directo, pero sí adjuntar.
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "x",
            elements = listOf(
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot).handle("¿cómo mando una foto?")
        val text = (result as WhatsAppGuidedResponse.Guidance).spokenText
        assertTrue(text.contains("adjuntar", ignoreCase = true))
    }

    @Test
    fun providerThrowingFallsBackToStateNotConfident() {
        val result = useCase(throwOnRead = true).handle("¿estoy en WhatsApp?")
        assertTrue(result is WhatsAppGuidedResponse.StateNotConfident)
    }

    @Test
    fun legacyWhatsAppActionPhrasesAreNeverConsumed() {
        // El integration spec exige que el guided workflow NO se trague comandos
        // del flujo seguro de WhatsApp existente. Si los consumiera, romperíamos
        // el orquestador legacy (compose/open chat/etc).
        listOf(
            "abrí WhatsApp",
            "abri whatsapp",
            "mandale a Marco",
            "mandale un mensaje a Marco",
            "mandale un whatsapp a Marco",
            "abrí el chat de Marco",
            "llamá a Marco"
        ).forEach { phrase ->
            val result = useCase(snapshot = chatSnapshot()).handle(phrase)
            assertEquals(
                WhatsAppGuidedResponse.NotAWhatsAppCommand,
                result,
                "legacy WhatsApp phrase '$phrase' debe seguir su flujo normal, no ser consumida por el guided workflow"
            )
        }
    }

    @Test
    fun controlPhrasesAreNeverConsumed() {
        // Stop / cancel / confirm / help: nunca deben caer en guided workflow.
        listOf(
            "callate",
            "callar",
            "cancelar",
            "confirmar",
            "ayuda",
            "qué puedo hacer",
            "qué puedo decir"
        ).forEach { phrase ->
            val result = useCase(snapshot = chatSnapshot()).handle(phrase)
            assertEquals(
                WhatsAppGuidedResponse.NotAWhatsAppCommand,
                result,
                "control phrase '$phrase' should never be consumed"
            )
        }
    }

    @Test
    fun mediumConfidenceWithoutPackageStillReturnsGuidance() {
        // packageName ausente pero señales estructurales fuertes.
        val snapshot = ScreenSnapshot(
            packageName = null,
            text = "x",
            elements = listOf(
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot).handle("¿cómo le mando un mensaje?")
        // Aunque sea MEDIUM, el state.isOpen = true → entra en Guidance.
        assertTrue(result is WhatsAppGuidedResponse.Guidance)
    }
}
