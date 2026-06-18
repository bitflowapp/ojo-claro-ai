package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppScreenDetectorTest {

    private val detector = WhatsAppScreenDetector()

    private fun snapshot(
        packageName: String? = null,
        text: String = "",
        elements: List<ScreenElement> = emptyList(),
        activityClassName: String? = null
    ) = ScreenSnapshot(
        packageName = packageName,
        text = text,
        elements = elements,
        capturedAtMillis = 0L,
        activityClassName = activityClassName
    )

    private fun button(label: String) =
        ScreenElement(label = label, role = ScreenElementRole.BUTTON, isInteractive = true)

    private fun messageField(label: String = "") =
        ScreenElement(label = label, role = ScreenElementRole.EDIT_TEXT, isInteractive = true)

    @Test
    fun unknownWhenSnapshotIsNull() {
        val state = detector.detect(null)
        assertTrue(state.isUnknown)
        assertFalse(state.isOpen)
        assertEquals(WhatsAppDetectionConfidence.UNKNOWN, state.confidence)
    }

    @Test
    fun detectsWhatsAppByOfficialPackage() {
        val state = detector.detect(snapshot(packageName = "com.whatsapp"))
        assertTrue(state.isOpen)
        assertTrue(state.packageNameMatched)
        assertEquals(WhatsAppDetectionConfidence.HIGH, state.confidence)
    }

    @Test
    fun detectsWhatsAppBusinessPackage() {
        val state = detector.detect(snapshot(packageName = "com.whatsapp.w4b"))
        assertTrue(state.isOpen)
        assertEquals(WhatsAppDetectionConfidence.HIGH, state.confidence)
    }

    @Test
    fun packageNameWithWhatsAppSubstringMatches() {
        // forks o variantes
        val state = detector.detect(snapshot(packageName = "com.whatsapp.beta"))
        assertTrue(state.isOpen)
    }

    @Test
    fun unrelatedPackageIsNotWhatsApp() {
        val state = detector.detect(snapshot(packageName = "com.telegram.messenger"))
        assertFalse(state.isOpen)
        assertFalse(state.packageNameMatched)
    }

    @Test
    fun detectsChatOpenWithMessageFieldAndButtons() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    messageField("Mensaje"),
                    button("Cámara"),
                    button("Enviar")
                )
            )
        )
        assertTrue(state.isInChat)
        assertTrue(state.hasMessageField)
        assertTrue(state.hasCameraButton)
        assertTrue(state.hasSendButton)
    }

    @Test
    fun chatListWithoutMessageFieldIsNotInChat() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    button("Sofi"),
                    button("Familia"),
                    button("Trabajo")
                )
            )
        )
        assertTrue(state.isOpen)
        assertFalse(state.isInChat)
    }

    @Test
    fun detectsCameraButton() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(button("Cámara"))
            )
        )
        assertTrue(state.hasCameraButton)
    }

    @Test
    fun detectsSendButton() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(button("Enviar"))
            )
        )
        assertTrue(state.hasSendButton)
    }

    @Test
    fun detectsAttachAndMicrophoneAndBack() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    button("Adjuntar"),
                    button("Micrófono"),
                    button("Volver")
                )
            )
        )
        assertTrue(state.hasAttachButton)
        assertTrue(state.hasMicrophoneButton)
        assertTrue(state.hasBackButton)
    }

    @Test
    fun englishLabelsAlsoMatch() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    messageField("Type a message"),
                    button("Camera"),
                    button("Send"),
                    button("Attach"),
                    button("Microphone"),
                    button("Back")
                )
            )
        )
        assertTrue(state.isInChat)
        assertTrue(state.hasMessageField)
        assertTrue(state.hasCameraButton)
        assertTrue(state.hasSendButton)
        assertTrue(state.hasAttachButton)
        assertTrue(state.hasMicrophoneButton)
        assertTrue(state.hasBackButton)
    }

    @Test
    fun passwordElementsAreIgnoredEvenIfLabelLooksLikeButton() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    ScreenElement(
                        label = "Enviar",
                        role = ScreenElementRole.EDIT_TEXT,
                        isInteractive = true,
                        isPassword = true
                    )
                )
            )
        )
        assertFalse(state.hasSendButton)
        assertFalse(state.hasMessageField)
    }

    @Test
    fun searchFieldIsNotMessageField() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    ScreenElement(
                        label = "Buscar",
                        role = ScreenElementRole.EDIT_TEXT,
                        isInteractive = true
                    )
                )
            )
        )
        assertFalse(state.hasMessageField)
    }

    @Test
    fun stateDoesNotExposePrivateMessageContent() {
        // El detector NUNCA debe devolver el texto privado del chat. El estado
        // solo tiene booleans. Esto es una invariante estructural: el data class
        // WhatsAppScreenState no contiene strings de contenido.
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                text = "Sofi: estoy llegando tarde\nVos: dale\n",
                elements = listOf(
                    ScreenElement("Sofi: estoy llegando", ScreenElementRole.TEXT, isInteractive = false),
                    messageField("Mensaje")
                )
            )
        )
        // Si el data class agregara strings de mensajes, este test fallaría:
        val asString = state.toString()
        assertFalse(asString.contains("estoy llegando", ignoreCase = true))
        assertFalse(asString.contains("Sofi:"))
    }

    @Test
    fun structuralSignalsAloneGiveMediumConfidence() {
        val state = detector.detect(
            snapshot(
                packageName = null,
                elements = listOf(
                    messageField("Mensaje"),
                    button("Cámara"),
                    button("Enviar")
                )
            )
        )
        assertEquals(WhatsAppDetectionConfidence.MEDIUM, state.confidence)
        assertFalse(state.packageNameMatched)
        assertTrue(state.isOpen)
    }

    @Test
    fun weakSignalsGiveLowConfidence() {
        val state = detector.detect(
            snapshot(
                packageName = null,
                elements = listOf(button("Cámara"))
            )
        )
        assertEquals(WhatsAppDetectionConfidence.LOW, state.confidence)
        assertFalse(state.isOpen)
    }

    @Test
    fun emptyEditTextWithoutLabelIsTreatedAsComposer() {
        // Algunas variantes de WhatsApp no exponen hint en el composer.
        // Un EDIT_TEXT sin label, en chat con botones, debería contar.
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    messageField(""),
                    button("Cámara"),
                    button("Enviar")
                )
            )
        )
        assertTrue(state.hasMessageField)
        assertTrue(state.isInChat)
    }

    @Test
    fun nonInteractiveButtonLikeLabelIsIgnored() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(
                    ScreenElement(
                        label = "Cámara",
                        role = ScreenElementRole.TEXT,
                        isInteractive = false
                    )
                )
            )
        )
        assertFalse(state.hasCameraButton)
    }

    @Test
    fun detectorDoesNotScanPastElementCap() {
        val beyondCapSignals = List(WhatsAppScreenDetector.MAX_ELEMENTS_TO_SCAN) { index ->
            ScreenElement("Elemento $index", ScreenElementRole.TEXT, isInteractive = false)
        } + listOf(
            messageField("Mensaje"),
            button("Camara"),
            button("Enviar")
        )

        val state = detector.detect(
            snapshot(
                packageName = null,
                elements = beyondCapSignals
            )
        )

        assertEquals(WhatsAppDetectionConfidence.UNKNOWN, state.confidence)
        assertFalse(state.hasMessageField)
        assertFalse(state.hasSendButton)
    }

    // --- Fix del falso negativo de inChat (señal de activity .Conversation) ---

    @Test
    fun conversationActivityWithDraftedFieldIsInChat() {
        // Root cause: con borrador escrito, el label del EDIT_TEXT es el texto
        // tipeado (no "Mensaje") → antes daba inChat=false. Ahora cuenta.
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                activityClassName = "com.whatsapp.Conversation",
                elements = listOf(messageField("estoy llegando"))
            )
        )
        assertTrue(state.isInChat, "Conversation + composer (con borrador) debe ser inChat")
        assertTrue(state.hasMessageField)
        assertTrue(state.signals.contains("activity_conversation"))
        assertTrue(state.signals.contains("message_field"))
    }

    @Test
    fun conversationActivityWithSparseSignalsStillInChat() {
        // Snapshot escaso (solo el botón enviar visible): la activity Conversation
        // + cualquier señal de composer alcanza.
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                activityClassName = "com.whatsapp.Conversation",
                elements = listOf(button("Enviar"))
            )
        )
        assertTrue(state.isInChat)
        assertEquals("activity_conversation+composer", state.reason)
    }

    @Test
    fun chatListActivityIsNotInChat() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                activityClassName = "com.whatsapp.HomeActivity",
                elements = listOf(messageField(""))
            )
        )
        assertTrue(state.isOpen)
        assertFalse(state.isInChat, "la lista de chats no es un chat abierto")
        assertEquals("non_chat_activity", state.reason)
    }

    @Test
    fun loginOrVerifyActivityIsNotInChat() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                activityClassName = "com.whatsapp.registration.VerifyPhoneNumber",
                elements = listOf(messageField(""))
            )
        )
        assertFalse(state.isInChat, "pantalla de verificar número no es un chat")
    }

    @Test
    fun draftedTextStillCountsAsMessageField() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                elements = listOf(messageField("hola que tal todo bien"))
            )
        )
        assertTrue(state.hasMessageField, "un EDIT_TEXT con borrador sigue siendo el composer")
    }

    @Test
    fun signalsAndReasonAreContentFree() {
        val state = detector.detect(
            snapshot(
                packageName = "com.whatsapp",
                activityClassName = "com.whatsapp.Conversation",
                text = "Sofi: nos vemos a las ocho",
                elements = listOf(messageField("dale ahi voy"))
            )
        )
        val dump = (state.signals.joinToString(" ") + " " + state.reason)
        assertFalse(dump.contains("Sofi", ignoreCase = true))
        assertFalse(dump.contains("nos vemos", ignoreCase = true))
        assertFalse(dump.contains("ocho", ignoreCase = true))
        assertFalse(dump.contains("ahi voy", ignoreCase = true))
    }
}
