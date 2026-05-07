package com.ojoclaro.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ojoclaro.android.ui.OjoClaroApp
import com.ojoclaro.android.voice.OjoClaroIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Punto de entrada de Ojo Claro.
 *
 * Recibe dos clases de intents:
 *  - MAIN/LAUNCHER: arranque normal desde el ícono o el launcher.
 *  - ACTION_START_LISTENING: viene del Quick Settings tile o del botón flotante
 *    de Accesibilidad. Significa "el usuario quiere arrancar a hablar ya".
 *
 * Cuando llega un intent de modo escucha, exponemos un trigger creciente vía
 * `listeningTriggers`. El HomeScreen lo observa y dispara el voice loop con la
 * app visible. El intent NUNCA arranca el micrófono por sí solo.
 */
class MainActivity : ComponentActivity() {

    private val listeningTriggers = MutableStateFlow(0L)
    private val stopSpeechTriggers = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)

        setContent {
            OjoClaroApp(
                listeningTriggers = listeningTriggers.asStateFlow(),
                stopSpeechTriggers = stopSpeechTriggers.asStateFlow()
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val action = intent?.action
        val startListeningExtra =
            intent?.getBooleanExtra(OjoClaroIntents.EXTRA_START_LISTENING, false) == true
        if (OjoClaroIntents.isListeningRequest(action, startListeningExtra)) {
            // Cada disparo aumenta el trigger para que la UI re-reaccione aún si
            // el mismo intent llega dos veces (por ejemplo, tile tocado dos veces).
            listeningTriggers.value = System.currentTimeMillis()
        }
        val stopSpeakingExtra =
            intent?.getBooleanExtra(OjoClaroIntents.EXTRA_STOP_SPEAKING, false) == true
        if (OjoClaroIntents.isStopSpeakingRequest(action, stopSpeakingExtra)) {
            stopSpeechTriggers.value = System.currentTimeMillis()
        }
    }

    val listeningTriggersForTest: StateFlow<Long>
        get() = listeningTriggers.asStateFlow()

    val stopSpeechTriggersForTest: StateFlow<Long>
        get() = stopSpeechTriggers.asStateFlow()
}
