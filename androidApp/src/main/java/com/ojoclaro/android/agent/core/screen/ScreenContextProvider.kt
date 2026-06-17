package com.ojoclaro.android.agent.core.screen

/**
 * Abstracción que entrega un ScreenSnapshot al agent-core.
 *
 * La capa Android implementa esta interfaz consultando al AccessibilityService.
 * El agent-core trabaja contra la interfaz para mantenerse puro/testeable y
 * para no acoplar la lógica de planning a frameworks de Android.
 */
fun interface ScreenContextProvider {
    fun current(): ScreenSnapshot?
}

/**
 * Implementación inerte: el caller la usa cuando todavía no hay AccessibilityService
 * conectado o cuando el feature flag de "leer pantalla" está apagado.
 */
object DisabledScreenContextProvider : ScreenContextProvider {
    override fun current(): ScreenSnapshot? = null
}
