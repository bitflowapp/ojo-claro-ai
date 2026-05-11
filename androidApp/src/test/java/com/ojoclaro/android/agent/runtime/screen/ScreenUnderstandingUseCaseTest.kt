package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.core.screen.ScreenSummaryMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenUnderstandingUseCaseTest {

    private fun useCase(
        snapshot: ScreenSnapshot? = null,
        isReady: Boolean = true,
        throwOnRead: Boolean = false
    ): ScreenUnderstandingUseCase {
        val provider = ScreenContextProvider {
            if (throwOnRead) error("boom")
            snapshot
        }
        return ScreenUnderstandingUseCase(
            provider = provider,
            isAccessibilityReady = { isReady }
        )
    }

    @Test
    fun unrelatedTextIsNotAScreenCommand() {
        val result = useCase().handle("abrí WhatsApp")
        assertEquals(ScreenUnderstandingResult.NotAScreenCommand, result)
    }

    @Test
    fun repeatLastIsNotAScreenCommand() {
        val result = useCase().handle("repetí")
        assertEquals(ScreenUnderstandingResult.NotAScreenCommand, result)
    }

    @Test
    fun helpIsNotAScreenCommand() {
        // El comando general "qué puedo decir" / "qué puedo hacer" sin "acá"
        // no debe ser tomado por screen understanding.
        assertEquals(
            ScreenUnderstandingResult.NotAScreenCommand,
            useCase().handle("qué puedo hacer")
        )
        assertEquals(
            ScreenUnderstandingResult.NotAScreenCommand,
            useCase().handle("qué puedo decir")
        )
    }

    @Test
    fun affirmativeNoiseIsNotAScreenCommand() {
        listOf("sí", "ok", "dale", "uh", "eh").forEach { phrase ->
            assertEquals(
                ScreenUnderstandingResult.NotAScreenCommand,
                useCase().handle(phrase),
                "phrase '$phrase' should not trigger screen understanding"
            )
        }
    }

    @Test
    fun screenCommandWithoutAccessibilityServiceReturnsNeedsService() {
        val result = useCase(isReady = false).handle("qué hay en pantalla")
        assertTrue(result is ScreenUnderstandingResult.NeedsAccessibilityService)
        val needs = result as ScreenUnderstandingResult.NeedsAccessibilityService
        assertTrue(needs.spokenText.contains("Accesibilidad", ignoreCase = true))
        assertTrue(needs.spokenText.contains("nunca leo contraseñas", ignoreCase = true))
    }

    @Test
    fun emptySnapshotReturnsHonestLimitedMessage() {
        val result = useCase(snapshot = null, isReady = true)
            .handle("resumí la pantalla")
        assertTrue(result is ScreenUnderstandingResult.Spoken)
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertTrue(spoken.isLimited)
        assertTrue(spoken.spokenText.contains("No tengo lectura", ignoreCase = true))
    }

    @Test
    fun normalScreenIsSummarizedWithoutInventingContent() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "Lista de tareas. Comprar pan. Llamar al doctor.",
            elements = listOf(
                ScreenElement("Lista de tareas", ScreenElementRole.HEADING, isInteractive = false)
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("resumí la pantalla")
        assertTrue(result is ScreenUnderstandingResult.Spoken)
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertEquals(ScreenSummaryMode.SHORT, spoken.mode)
        assertTrue(spoken.spokenText.contains("Lista de tareas"))
        assertFalse(
            spoken.spokenText.contains("WhatsApp"),
            "no debe inventar apps que no están en pantalla"
        )
        assertFalse(spoken.isLimited)
        assertTrue(spoken.isSafeToReadAloud)
    }

    @Test
    fun bankingScreenIsBlockedAndNotRead() {
        val snapshot = ScreenSnapshot(
            packageName = "com.bbva.app",
            text = "Saldo de tu cuenta bancaria 1.250.000 pesos. Tarjeta de crédito.",
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("resumí la pantalla")
        assertTrue(result is ScreenUnderstandingResult.Spoken)
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertFalse(spoken.isSafeToReadAloud)
        assertTrue(spoken.spokenText.contains("banc", ignoreCase = true))
        assertFalse(
            spoken.spokenText.contains("1.250.000"),
            "nunca debe leer el saldo"
        )
        assertFalse(
            spoken.spokenText.contains("Saldo de tu cuenta"),
            "nunca debe leer el contenido literal de pantalla bancaria"
        )
    }

    @Test
    fun passwordFieldDoesNotLeakValueOnShortMode() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.login",
            text = "Ingresá tu contraseña",
            elements = listOf(
                ScreenElement(
                    label = "Contraseña",
                    role = ScreenElementRole.EDIT_FIELD,
                    isInteractive = true,
                    isPassword = true
                )
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("resumí la pantalla")
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertFalse(spoken.isSafeToReadAloud)
        assertTrue(spoken.spokenText.contains("contraseña", ignoreCase = true))
        // Aviso, no lectura.
        assertTrue(spoken.spokenText.contains("no la leo", ignoreCase = true) ||
            spoken.spokenText.contains("no lo leo", ignoreCase = true))
    }

    @Test
    fun whatCanIDoListsInteractiveActionsWhenPresent() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "x",
            elements = listOf(
                ScreenElement("Guardar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Cancelar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Texto inerte", ScreenElementRole.TEXT, isInteractive = false)
            ),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("qué puedo hacer acá")
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, spoken.mode)
        assertTrue(spoken.spokenText.contains("Guardar"))
        assertTrue(spoken.spokenText.contains("Cancelar"))
        assertFalse(spoken.spokenText.contains("Texto inerte"))
    }

    @Test
    fun whereAmIDescribesPackageEvenWithoutHeading() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            text = "algo de texto",
            elements = emptyList(),
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("dónde estoy")
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertEquals(ScreenSummaryMode.WHERE_AM_I, spoken.mode)
        assertTrue(spoken.spokenText.contains("com.example.app"))
    }

    @Test
    fun importantOnlyDoesNotInventContent() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            text = "Alerta crítica. Tu vuelo despega en 30 minutos.",
            capturedAtMillis = 0L
        )
        val result = useCase(snapshot = snapshot, isReady = true)
            .handle("leeme lo importante")
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertEquals(ScreenSummaryMode.IMPORTANT, spoken.mode)
        assertTrue(spoken.spokenText.isNotBlank())
        assertFalse(
            spoken.spokenText.contains("WhatsApp"),
            "no debe inventar apps que no están en la pantalla"
        )
    }

    @Test
    fun providerThrowingFallsBackToEmptySnapshot() {
        val result = useCase(throwOnRead = true, isReady = true)
            .handle("resumí la pantalla")
        assertTrue(result is ScreenUnderstandingResult.Spoken)
        val spoken = result as ScreenUnderstandingResult.Spoken
        assertTrue(spoken.isLimited)
    }

    @Test
    fun whatsAppCommandIsNeverConsumedAsScreenCommand() {
        // Importante: la integración del Agent Runtime no puede comerse el comando
        // de WhatsApp ni los de Maps/Phone. La clasificación es estricta.
        listOf(
            "abrí WhatsApp",
            "mandale un mensaje a Sofi",
            "llamá a Sofi",
            "abrí Maps",
            "navegá a casa"
        ).forEach { text ->
            val result = useCase().handle(text)
            assertEquals(
                ScreenUnderstandingResult.NotAScreenCommand,
                result,
                "comando '$text' nunca debe ser tomado como consulta de pantalla"
            )
        }
    }

    @Test
    fun stopAndCancelCommandsAreNeverScreenCommands() {
        listOf("callar", "callate", "silencio", "cancelar", "para").forEach { text ->
            assertEquals(
                ScreenUnderstandingResult.NotAScreenCommand,
                useCase().handle(text)
            )
        }
    }
}
