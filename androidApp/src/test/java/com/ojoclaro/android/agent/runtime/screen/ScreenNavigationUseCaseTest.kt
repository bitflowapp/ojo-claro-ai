package com.ojoclaro.android.agent.runtime.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenNavigationUseCaseTest {

    private class FakeActions(
        var scrollOutcome: ScreenScrollOutcome = ScreenScrollOutcome.SCROLLED,
        var backOk: Boolean = true
    ) : ScreenNavigationActions {
        var scrollCalls = 0
        var goBackCalls = 0
        var lastForward: Boolean? = null

        override fun scroll(forward: Boolean): ScreenScrollOutcome {
            scrollCalls++
            lastForward = forward
            return scrollOutcome
        }

        override fun goBack(): Boolean {
            goBackCalls++
            return backOk
        }
    }

    private fun useCase(actions: FakeActions, ready: Boolean = true) =
        ScreenNavigationUseCase(actions = actions, isAccessibilityReady = { ready })

    @Test
    fun comandoDesconocidoNoEjecutaNada() {
        val actions = FakeActions()
        val result = useCase(actions).handle("abrí WhatsApp")
        assertEquals(ScreenNavigationResult.NotANavigationCommand, result)
        assertEquals(0, actions.scrollCalls)
        assertEquals(0, actions.goBackCalls)
    }

    @Test
    fun accesibilidadApagadaNoEjecutaAccion() {
        val actions = FakeActions()
        val result = useCase(actions, ready = false).handle("bajá")
        assertTrue(result is ScreenNavigationResult.NeedsAccessibilityService)
        assertEquals(0, actions.scrollCalls)
        assertEquals(0, actions.goBackCalls)
    }

    @Test
    fun bajaHaceScrollForward() {
        val actions = FakeActions(scrollOutcome = ScreenScrollOutcome.SCROLLED)
        val result = useCase(actions).handle("bajá")
        assertTrue(result is ScreenNavigationResult.Scrolled)
        assertEquals(1, actions.scrollCalls)
        assertEquals(true, actions.lastForward)
    }

    @Test
    fun subiHaceScrollBackward() {
        val actions = FakeActions(scrollOutcome = ScreenScrollOutcome.SCROLLED)
        val result = useCase(actions).handle("subí")
        assertTrue(result is ScreenNavigationResult.Scrolled)
        assertEquals(false, actions.lastForward)
    }

    @Test
    fun sinContenedorScrollableLoDiceHonestamente() {
        val actions = FakeActions(scrollOutcome = ScreenScrollOutcome.NO_TARGET)
        val result = useCase(actions).handle("bajá")
        assertTrue(result is ScreenNavigationResult.NothingToScroll)
    }

    @Test
    fun scrollUnavailableGuiaAccesibilidad() {
        val actions = FakeActions(scrollOutcome = ScreenScrollOutcome.UNAVAILABLE)
        val result = useCase(actions).handle("bajá")
        assertTrue(result is ScreenNavigationResult.NeedsAccessibilityService)
    }

    @Test
    fun volverEjecutaBack() {
        val actions = FakeActions(backOk = true)
        val result = useCase(actions).handle("volver")
        assertTrue(result is ScreenNavigationResult.WentBack)
        assertEquals(1, actions.goBackCalls)
        assertEquals(0, actions.scrollCalls)
    }

    @Test
    fun backFallidoDevuelveFailed() {
        val actions = FakeActions(backOk = false)
        val result = useCase(actions).handle("volver")
        assertTrue(result is ScreenNavigationResult.Failed)
    }

    @Test
    fun navegacionNuncaTocaNadaSensible() {
        // La interfaz de acciones solo expone scroll/goBack. No hay forma de
        // enviar, borrar, llamar ni compartir desde esta capa: cualquier
        // comando reconocido termina llamando a uno de esos dos métodos.
        val actions = FakeActions()
        useCase(actions).handle("bajá")
        useCase(actions).handle("volver")
        assertTrue(actions.scrollCalls + actions.goBackCalls > 0)
        assertFalse(actions.lastForward == null && actions.goBackCalls == 0)
    }
}
