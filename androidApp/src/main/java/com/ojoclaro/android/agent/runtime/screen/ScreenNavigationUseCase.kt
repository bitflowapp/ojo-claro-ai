package com.ojoclaro.android.agent.runtime.screen

/**
 * Resultado de intentar un scroll sobre el contenedor visible.
 *
 *  - SCROLLED: se encontró un contenedor scrollable visible y se desplazó.
 *  - NO_TARGET: no hay contenedor scrollable visible (no hay nada que mover).
 *  - UNAVAILABLE: el servicio de accesibilidad no está disponible o sin raíz.
 */
enum class ScreenScrollOutcome {
    SCROLLED,
    NO_TARGET,
    UNAVAILABLE
}

/**
 * Acciones de navegación seguras sobre la pantalla activa.
 *
 * Capa de borde inyectable: la implementación real ([AndroidScreenNavigationActions])
 * delega en el AccessibilityService; los tests usan un fake puro. Por diseño,
 * esta interfaz SOLO expone movimientos no destructivos (scroll, back). No hay
 * método para enviar, borrar, llamar ni compartir.
 */
interface ScreenNavigationActions {
    fun scroll(forward: Boolean): ScreenScrollOutcome
    fun goBack(): Boolean
}

/**
 * Resultado verbal del use case de navegación. El ViewModel solo lo traduce a
 * voz; no interpreta acciones.
 */
sealed class ScreenNavigationResult {
    object NotANavigationCommand : ScreenNavigationResult()
    data class NeedsAccessibilityService(val spokenText: String) : ScreenNavigationResult()
    data class Scrolled(val spokenText: String) : ScreenNavigationResult()
    data class NothingToScroll(val spokenText: String) : ScreenNavigationResult()
    data class WentBack(val spokenText: String) : ScreenNavigationResult()
    data class Failed(val spokenText: String) : ScreenNavigationResult()
}

/**
 * Use case de navegación segura (Fase 2A).
 *
 * Reglas hard:
 *  - Solo scroll forward/backward y back global. Nunca clicks sensibles.
 *  - Si Accesibilidad no está activa, guía a activarla. No inventa que se movió.
 *  - Si no hay contenedor scrollable, lo dice con honestidad (no afirma scroll).
 *  - No persiste, no usa red, no usa LLM.
 */
class ScreenNavigationUseCase(
    private val actions: ScreenNavigationActions,
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    fun handle(rawText: String): ScreenNavigationResult {
        val command = ScreenNavigationCommandParser.parse(rawText)
            ?: return ScreenNavigationResult.NotANavigationCommand

        if (!isAccessibilityReady()) {
            return ScreenNavigationResult.NeedsAccessibilityService(NEEDS_ACCESSIBILITY_TEXT)
        }

        return when (command) {
            ScreenNavigationCommand.ScrollDown -> scroll(forward = true)
            ScreenNavigationCommand.ScrollUp -> scroll(forward = false)
            ScreenNavigationCommand.Back -> back()
        }
    }

    private fun scroll(forward: Boolean): ScreenNavigationResult {
        val outcome = runCatching { actions.scroll(forward) }
            .getOrDefault(ScreenScrollOutcome.UNAVAILABLE)
        return when (outcome) {
            ScreenScrollOutcome.SCROLLED ->
                ScreenNavigationResult.Scrolled(if (forward) SCROLLED_DOWN_TEXT else SCROLLED_UP_TEXT)
            ScreenScrollOutcome.NO_TARGET ->
                ScreenNavigationResult.NothingToScroll(NOTHING_TO_SCROLL_TEXT)
            ScreenScrollOutcome.UNAVAILABLE ->
                ScreenNavigationResult.NeedsAccessibilityService(NEEDS_ACCESSIBILITY_TEXT)
        }
    }

    private fun back(): ScreenNavigationResult {
        val ok = runCatching { actions.goBack() }.getOrDefault(false)
        return if (ok) {
            ScreenNavigationResult.WentBack(WENT_BACK_TEXT)
        } else {
            ScreenNavigationResult.Failed(BACK_FAILED_TEXT)
        }
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para moverme por la pantalla necesito el servicio de Accesibilidad activo. " +
                "Activá Estela en Ajustes de Accesibilidad."
        const val SCROLLED_DOWN_TEXT: String = "Listo, bajé un poco."
        const val SCROLLED_UP_TEXT: String = "Listo, subí un poco."
        const val NOTHING_TO_SCROLL_TEXT: String =
            "No encontré nada para desplazar en esta pantalla."
        const val WENT_BACK_TEXT: String = "Volví a la pantalla anterior."
        const val BACK_FAILED_TEXT: String =
            "No pude volver atrás. Probá tocar el botón de atrás del teléfono."
    }
}
