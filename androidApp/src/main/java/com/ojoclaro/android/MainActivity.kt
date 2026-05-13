package com.ojoclaro.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.ojoclaro.android.llm.SafeAiFallbackAndroidLogcat
import com.ojoclaro.android.performance.RobotLoopAndroidLogcat
import com.ojoclaro.android.ui.OjoClaroApp
import com.ojoclaro.android.voice.OjoClaroIntents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val DEBUG_SUBMIT_TEXT_MAX_CHARS: Int = 500

internal fun sanitizeDebugSubmitText(rawText: String): String {
    val bounded = rawText.take(DEBUG_SUBMIT_TEXT_MAX_CHARS * 2)
    return bounded
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(DEBUG_SUBMIT_TEXT_MAX_CHARS)
}

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
    private val debugTextSubmissions = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4
    )
    private var debugSubmitTextReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RobotLoopAndroidLogcat.install(enabled = BuildConfig.DEBUG)
        SafeAiFallbackAndroidLogcat.install(enabled = BuildConfig.DEBUG)
        registerDebugSubmitTextReceiver()
        consumeIntent(intent)

        setContent {
            OjoClaroApp(
                listeningTriggers = listeningTriggers.asStateFlow(),
                stopSpeechTriggers = stopSpeechTriggers.asStateFlow(),
                debugTextSubmissions = debugTextSubmissions.asSharedFlow()
            )
        }
    }

    override fun onDestroy() {
        debugSubmitTextReceiver?.let { unregisterReceiver(it) }
        debugSubmitTextReceiver = null
        super.onDestroy()
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

    private fun registerDebugSubmitTextReceiver() {
        if (!BuildConfig.DEBUG || debugSubmitTextReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DEBUG_SUBMIT_TEXT_ACTION) return
                val text = sanitizeDebugSubmitText(intent.getStringExtra(DEBUG_SUBMIT_TEXT_EXTRA).orEmpty())
                if (text.isBlank()) return
                debugTextSubmissions.tryEmit(text)
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(DEBUG_SUBMIT_TEXT_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        debugSubmitTextReceiver = receiver
    }

    val listeningTriggersForTest: StateFlow<Long>
        get() = listeningTriggers.asStateFlow()

    val stopSpeechTriggersForTest: StateFlow<Long>
        get() = stopSpeechTriggers.asStateFlow()

    val debugTextSubmissionsForTest: SharedFlow<String>
        get() = debugTextSubmissions.asSharedFlow()

    companion object {
        const val DEBUG_SUBMIT_TEXT_ACTION = "com.ojoclaro.DEBUG_SUBMIT_TEXT"
        const val DEBUG_SUBMIT_TEXT_EXTRA = "text"
    }
}
