package com.ojoclaro.android.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController

/**
 * Factory testeable del [HomeViewModel].
 *
 * Diseño:
 *  - Recibe el `application` (necesario para `AndroidViewModel`) y un
 *    `agentBridgeDispatch: AgentBridgeDispatchController?` opcional.
 *  - Si `agentBridgeDispatch` es null (caso producción hoy), construye el VM
 *    con el comportamiento legacy intacto.
 *  - Si se pasa el controller, el VM intercepta cada `submitVoiceText` con el
 *    bridge antes del legacy. El controller mismo respeta los flags y devuelve
 *    `FallbackToLegacy` cuando corresponde — así que pasar un controller
 *    nunca rompe nada, sólo agrega capa.
 *
 * Decisión: por defecto, **el caller decide** si pasar o no el controller. El
 * `MainActivity`/`HomeScreen` consulta al `RuntimeGraphOwner` y, si hay graph
 * instalado, pasa `graph.dispatchController`. Si no hay graph (flags off,
 * tests, builds donde nadie llamó `installOnce`), pasa null.
 *
 * Esto preserva la propiedad clave: con flags OFF + sin install, el
 * comportamiento del APK es idéntico al baseline pre-4C.
 */
class HomeViewModelFactory(
    private val application: Application,
    private val agentBridgeDispatch: AgentBridgeDispatchController? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "HomeViewModelFactory only knows how to build HomeViewModel, got ${modelClass.name}"
        }
        return HomeViewModel(
            application = application,
            agentBridgeDispatch = agentBridgeDispatch
        ) as T
    }
}
