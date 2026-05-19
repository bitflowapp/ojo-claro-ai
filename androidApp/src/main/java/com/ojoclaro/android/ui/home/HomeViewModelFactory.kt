package com.ojoclaro.android.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController
import com.ojoclaro.android.agent.core.screen.ScreenChangeAnnouncement
import com.ojoclaro.android.voice.AgentBridgeVoiceCoordinator
import kotlinx.coroutines.flow.Flow

/**
 * Factory testeable del [HomeViewModel].
 *
 * Diseño:
 *  - Recibe el `application` (necesario para `AndroidViewModel`) y dependencias
 *    opcionales del runtime moderno: `agentBridgeDispatch` y, desde el paquete
 *    5C, `agentBridgeVoiceCoordinator`.
 *  - Si las dependencias son null (caso producción cuando el graph aún no
 *    se instala), construye el VM con el comportamiento legacy intacto.
 *  - Si se pasan, el VM intercepta `submitVoiceText` con el bridge y enruta
 *    las salidas Handled por el coordinador semántico. Ambas piezas respetan
 *    los flags internamente: con `typedConfirmationEnabled` OFF, el bridge
 *    devuelve `FallbackToLegacy` y el coordinador nunca se invoca.
 *
 * Decisión: por defecto, **el caller decide** si pasar o no las dependencias.
 * `HomeScreen` consulta al `RuntimeGraphOwner` y, si hay graph instalado,
 * pasa `graph.dispatchController` y `graph.voiceCoordinator`. Si no hay graph
 * (tests, builds donde nadie llamó `installOnce`), pasa null.
 *
 * Esto preserva la propiedad clave: con flags OFF + sin install, el
 * comportamiento del APK es idéntico al baseline pre-4C.
 */
class HomeViewModelFactory(
    private val application: Application,
    private val agentBridgeDispatch: AgentBridgeDispatchController? = null,
    private val agentBridgeVoiceCoordinator: AgentBridgeVoiceCoordinator? = null,
    private val screenChangeAnnouncements: Flow<ScreenChangeAnnouncement>? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "HomeViewModelFactory only knows how to build HomeViewModel, got ${modelClass.name}"
        }
        return HomeViewModel(
            application = application,
            agentBridgeDispatch = agentBridgeDispatch,
            agentBridgeVoiceCoordinator = agentBridgeVoiceCoordinator,
            screenChangeAnnouncements = screenChangeAnnouncements
        ) as T
    }
}
