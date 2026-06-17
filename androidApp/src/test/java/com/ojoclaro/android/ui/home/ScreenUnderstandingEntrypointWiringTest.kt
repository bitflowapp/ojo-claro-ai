package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenUnderstandingEntrypointWiringTest {

    @Test
    fun normalReadScreenButtonUsesDirectScreenUnderstandingNotVoicePipeline() {
        val homeScreen = mainSource("HomeScreen.kt")
        val viewModel = mainSource("HomeViewModel.kt")

        assertFalse(
            homeScreen.contains("viewModel.submitVoiceText(\"leer la pantalla\")"),
            "El boton normal Leer pantalla no debe simular voz ni entrar al pipeline general."
        )
        assertTrue(
            homeScreen.contains("viewModel.readScreenFromQuickAction()"),
            "El boton normal debe invocar el entrypoint productivo dedicado."
        )
        assertTrue(
            viewModel.contains("fun readScreenFromQuickAction()") &&
                viewModel.contains("entryPoint = ScreenUnderstandingEntryPoint.NORMAL_BUTTON"),
            "El entrypoint del boton normal debe delegar a executeScreenUnderstanding como NORMAL_BUTTON."
        )
    }

    @Test
    fun normalVoiceScreenCommandRunsDirectBeforeFallbackIntentAndAgentTask() {
        val viewModel = mainSource("HomeViewModel.kt")
        val directVoice = "tryExecuteDirectScreenUnderstanding(cleanText, ScreenUnderstandingEntryPoint.NORMAL_VOICE)"
        val directIndex = viewModel.indexOf(directVoice)
        val agentTaskIndex = viewModel.indexOf("handleAgentTaskCommandIfNeeded(cleanText")
        val intentIndex = viewModel.indexOf("handleEstelaIntentRuntimeIfNeeded(cleanText")

        assertTrue(directIndex >= 0, "La voz normal debe probar la ruta directa de pantalla.")
        assertTrue(agentTaskIndex > directIndex, "La voz de pantalla debe correr antes de AgentTaskOrchestrator.")
        assertTrue(intentIndex > directIndex, "La voz de pantalla debe correr antes de /intent.")
        assertTrue(
            viewModel.contains("fun executeScreenUnderstanding("),
            "La implementacion productiva comun debe existir con ese nombre."
        )
    }

    @Test
    fun debugPanelBroadcastAndDebugTextUseSameExecuteScreenUnderstanding() {
        val homeScreen = mainSource("HomeScreen.kt")
        val viewModel = mainSource("HomeViewModel.kt")

        assertTrue(
            homeScreen.contains("viewModel.runScreenReadDiagnostic(ScreenUnderstandingEntryPoint.DEBUG_PANEL)"),
            "El boton debug debe entrar al diagnostico comun como DEBUG_PANEL."
        )
        assertTrue(
            homeScreen.contains("viewModel.runScreenReadDiagnostic(ScreenUnderstandingEntryPoint.DEBUG_BROADCAST)"),
            "El broadcast DEBUG_RUN_SCREEN_DIAGNOSTIC debe entrar al diagnostico comun como DEBUG_BROADCAST."
        )
        assertTrue(
            viewModel.contains("ScreenUnderstandingEntryPoint.DEBUG_SUBMIT_TEXT"),
            "DEBUG_SUBMIT_TEXT con 'lee la pantalla' debe saltar a la misma ruta directa."
        )
        assertTrue(
            viewModel.contains("fun runScreenReadDiagnostic(") &&
                viewModel.contains("executeScreenUnderstanding("),
            "Los entrypoints debug deben delegar en executeScreenUnderstanding."
        )
    }

    @Test
    fun canonicalReadScreenPhraseClassifiesAndSharesSnapshotSource() {
        assertTrue(ScreenQueryPhrases.classify(SCREEN_READ_DIAGNOSTIC_PHRASE) != null)
        assertEquals(
            SCREEN_READ_DIAGNOSTIC_ROUTE,
            ScreenUnderstandingEntryPoint.DEBUG_PANEL.routeLabel
        )
        assertEquals(
            SCREEN_READ_DIAGNOSTIC_ROUTE,
            ScreenUnderstandingEntryPoint.DEBUG_BROADCAST.routeLabel
        )
        assertEquals("SCREEN_UNDERSTANDING_DIRECT_NORMAL_BUTTON", ScreenUnderstandingEntryPoint.NORMAL_BUTTON.routeLabel)
        assertEquals("SCREEN_UNDERSTANDING_DIRECT_NORMAL_VOICE", ScreenUnderstandingEntryPoint.NORMAL_VOICE.routeLabel)
        assertEquals("REAL_ACCESSIBILITY_SNAPSHOT", SCREEN_READ_DIAGNOSTIC_SOURCE)
    }

    private fun mainSource(fileName: String): String =
        File("src/main/java/com/ojoclaro/android/ui/home/$fileName").readText()
}
