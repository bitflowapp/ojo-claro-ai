package com.ojoclaro.android.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingConfirmationBannerTest {

    @Test
    fun `pending state shows banner with confirmation text`() {
        val state = PendingConfirmationViewState.from(
            HomeUiState(
                hasPendingConfirmation = true,
                pendingConfirmationText = "Para preparar el WhatsApp, decí: confirmar.",
                lastAgentBridgeMessage = "Esta acción requiere confirmación."
            )
        )

        assertTrue(state.visible)
        assertEquals("Para preparar el WhatsApp, decí: confirmar.", state.message)
        assertEquals(PendingConfirmationViewState.DEFAULT_HINT, state.hint)
    }

    @Test
    fun `non pending state hides banner`() {
        val state = PendingConfirmationViewState.from(
            HomeUiState(
                hasPendingConfirmation = false,
                pendingConfirmationText = "No se debe mostrar"
            )
        )

        assertFalse(state.visible)
        assertEquals("", state.message)
        assertEquals("", state.hint)
    }

    @Test
    fun `pending state can fall back to last bridge message`() {
        val state = PendingConfirmationViewState.from(
            HomeUiState(
                hasPendingConfirmation = true,
                pendingConfirmationText = null,
                lastAgentBridgeMessage = "Esta acción requiere confirmación."
            )
        )

        assertTrue(state.visible)
        assertEquals("Esta acción requiere confirmación.", state.message)
    }

    @Test
    fun `button phrases use same textual path as voice`() {
        assertEquals("confirmar", CONFIRM_BUTTON_VOICE_PHRASE)
        assertEquals("cancelar", CANCEL_BUTTON_VOICE_PHRASE)
    }
}
