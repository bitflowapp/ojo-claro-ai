package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner

/**
 * Punto puro y testeable para elegir la dependencia opcional del bridge.
 *
 * Si el runtime graph no está instalado, devuelve null y el HomeViewModel
 * conserva el flujo legacy. Si existe, pasa el controller; el controller
 * decide por flags si maneja o devuelve FallbackToLegacy.
 */
internal fun selectAgentBridgeDispatchControllerForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): AgentBridgeDispatchController? =
    owner.currentGraph()?.dispatchController
