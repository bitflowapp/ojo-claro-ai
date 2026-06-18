package com.ojoclaro.android.accessibility

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityOverlayContractTest {

    @Test
    fun accessibilityServiceOwnsPersistentOverlayInsteadOfOpeningMainActivity() {
        val source = mainSource("accessibility/OjoClaroAccessibilityService.kt")

        assertFalse(
            source.contains("MainActivity"),
            "El AccessibilityService no debe abrir MainActivity para acciones del overlay."
        )
        assertTrue(source.contains("TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(source.contains("Leer pantalla"))
        assertTrue(source.contains("Hablar"))
        assertTrue(source.contains("ScreenUnderstandingUseCase"))
        assertTrue(source.contains("GlobalAssistantService.startOverlayVoice"))
    }

    @Test
    fun accessibilityServiceRequestsInteractiveWindowsAndFiltersOverlayWindows() {
        val serviceXml = File("src/main/res/xml/ojo_claro_accessibility_service.xml").readText()
        val source = mainSource("accessibility/OjoClaroAccessibilityService.kt")

        assertTrue(serviceXml.contains("flagRetrieveInteractiveWindows"))
        assertTrue(source.contains("REAL_EXTERNAL_ACCESSIBILITY_WINDOW"))
        assertTrue(source.contains("TYPE_APPLICATION"))
        assertTrue(source.contains("TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(source.contains("ownPackageSelected"))
        assertTrue(source.contains("overlayWindowSelected"))
    }

    @Test
    fun overlayVoiceEntrypointDoesNotCreateSecondApplicationOverlay() {
        val service = mainSource("global/GlobalAssistantService.kt")
        val mode = mainSource("global/GlobalAssistantMode.kt")

        assertTrue(mode.contains("ACTION_OVERLAY_VOICE_ENTRYPOINT"))
        assertTrue(service.contains("startOverlayVoice("))
        assertTrue(service.contains("appOverlayEnabled = false"))
        assertTrue(service.contains("accessibilityOverlayVoiceSingleShot = true"))
        assertTrue(service.contains("finalState=IDLE"))
    }

    @Test
    fun expandedOverlayOffersCloseActionDistinctFromSilence() {
        val source = mainSource("accessibility/OjoClaroAccessibilityService.kt")

        // El panel ofrece una acción explícita de cerrar/contraer.
        assertTrue(
            source.contains("\"Cerrar\""),
            "El panel expandido debe ofrecer una accion clara de Cerrar/Contraer."
        )
        assertTrue(
            source.contains("collapseOverlayPanel"),
            "Cerrar debe contraer el panel via collapseOverlayPanel."
        )

        // Cerrar solo colapsa: no detiene TTS ni pide silencio (eso es Callar).
        val collapseBody = source.substringAfter("private fun collapseOverlayPanel()")
            .substringBefore("private fun ")
        assertFalse(
            collapseBody.contains("speechController.stop"),
            "Cerrar no debe detener el TTS (eso es responsabilidad de Callar)."
        )
        assertFalse(
            collapseBody.contains("requestSilence"),
            "Cerrar no debe pedir silencio al GlobalAssistantService."
        )

        // Callar sigue siendo quien detiene el habla.
        val silenceBody = source.substringAfter("private fun silenceFromOverlay()")
            .substringBefore("private fun ")
        assertTrue(
            silenceBody.contains("speechController.stop"),
            "Callar debe seguir deteniendo el TTS."
        )
    }

    private fun mainSource(path: String): String =
        File("src/main/java/com/ojoclaro/android/$path").readText()
}
