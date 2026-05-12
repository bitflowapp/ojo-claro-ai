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
        elements: List<ScreenElement> = emptyList()
    ) = ScreenSnapshot(
        packageName = packageName,
        text = text,
        elements = elements,
        capturedAtMillis = 0L
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
}
