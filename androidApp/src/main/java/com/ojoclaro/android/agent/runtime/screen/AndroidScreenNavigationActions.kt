package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService

/**
 * Implementación real de [ScreenNavigationActions] sobre el AccessibilityService
 * de Ojo Claro. Delega en métodos estáticos del servicio que solo ejecutan
 * acciones seguras (scroll sobre contenedores visibles, back global).
 *
 * No mantiene estado. Si el servicio no está conectado, los métodos del servicio
 * devuelven el estado seguro (UNAVAILABLE / false) y el use case lo traduce.
 */
class AndroidScreenNavigationActions : ScreenNavigationActions {

    override fun scroll(forward: Boolean): ScreenScrollOutcome =
        OjoClaroAccessibilityService.scrollVisibleContainer(forward)

    override fun goBack(): Boolean =
        OjoClaroAccessibilityService.performGlobalBack()
}
