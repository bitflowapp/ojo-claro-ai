package com.ojoclaro.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner
import com.ojoclaro.android.llm.SafeAiFallbackAndroidLogcat
import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.performance.RobotLoopAndroidLogcat
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.ui.OjoClaroApp
import com.ojoclaro.android.voice.OjoClaroIntents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val DEBUG_SUBMIT_TEXT_MAX_CHARS: Int = 500
internal const val DEBUG_SUBMIT_TEXT_BOUNDARY_CHARS: Int = DEBUG_SUBMIT_TEXT_MAX_CHARS * 2

internal enum class DebugSubmitTextRejectReason(val logCode: String) {
    BLANK("blank"),
    TOO_LONG("too_long"),
    SENSITIVE("sensitive")
}

internal data class DebugSubmitTextDecision(
    val text: String,
    val rejectReason: DebugSubmitTextRejectReason? = null,
    val commandRedacted: Boolean = false
) {
    val accepted: Boolean get() = rejectReason == null && text.isNotBlank()
}

internal fun sanitizeDebugSubmitText(rawText: String): String {
    return debugSubmitTextDecision(rawText).text
}

internal fun debugSubmitTextDecision(rawText: String): DebugSubmitTextDecision {
    val bounded = rawText.take(DEBUG_SUBMIT_TEXT_BOUNDARY_CHARS + 1)
    val clean = bounded
        .replace(Regex("\\s+"), " ")
        .trim()
    if (clean.isBlank()) {
        return DebugSubmitTextDecision(
            text = "",
            rejectReason = DebugSubmitTextRejectReason.BLANK,
            commandRedacted = true
        )
    }
    if (clean.length > DEBUG_SUBMIT_TEXT_MAX_CHARS || rawText.length > DEBUG_SUBMIT_TEXT_BOUNDARY_CHARS) {
        return DebugSubmitTextDecision(
            text = "",
            rejectReason = DebugSubmitTextRejectReason.TOO_LONG,
            commandRedacted = true
        )
    }
    if (debugSubmitTextLooksSensitive(clean)) {
        return DebugSubmitTextDecision(
            text = "",
            rejectReason = DebugSubmitTextRejectReason.SENSITIVE,
            commandRedacted = true
        )
    }
    return DebugSubmitTextDecision(text = clean)
}

internal fun debugSubmitTextLooksSensitive(text: String): Boolean {
    val normalized = MemoryPolicy.normalize(text)
    if (normalized.isBlank()) return false
    if (MemoryPolicy.containsProhibitedContent(text)) return true
    if (PrivacyGuard.containsSensitiveFinancialData(text)) return true
    return DEBUG_SUBMIT_SENSITIVE_TOKENS.any { token -> normalized.contains(token) }
}

private val DEBUG_SUBMIT_SENSITIVE_TOKENS: Set<String> = setOf(
    "banco",
    "pago",
    "pagar",
    "saldo",
    "transferencia",
    "cbu",
    "cvu",
    "alias",
    "tarjeta",
    "clave",
    "contrasena",
    "contraseña",
    "password",
    "pin",
    "otp",
    "codigo",
    "código"
)

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
        // Paquete 4C/5F: instalación idempotente del runtime graph process-scope.
        //
        // En release, el resolver devuelve DISABLED para mantener el
        // comportamiento histórico (sin bridge, sin snapshot estructurado,
        // sin awareness). En debug, activamos `debugSmokeTestFlags()` para
        // que el smoke test físico ejercite las capas modernas:
        //   - typedConfirmationEnabled
        //   - accessibilityRuntimeContextEnabled
        //   - screenChangeAwarenessEnabled
        // El AccessibilityService sigue read-only por contrato; nada de
        // esto ejecuta acciones reales en apps de terceros.
        val smokeFlagsResolver: () -> com.ojoclaro.android.agent.core.AgentCoreFeatureFlags =
            if (BuildConfig.DEBUG) {
                { RuntimeGraphOwner.debugSmokeTestFlags() }
            } else {
                { RuntimeGraphOwner.productionDefaultFlags() }
            }
        RuntimeGraphOwner.INSTANCE.installOnce(flags = smokeFlagsResolver)
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
        RuntimeGraphOwner.INSTANCE.release()
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
                val text = intent.getStringExtra(DEBUG_SUBMIT_TEXT_EXTRA)
                    .orEmpty()
                    .take(DEBUG_SUBMIT_TEXT_BOUNDARY_CHARS + 1)
                if (text.isBlank()) return
                debugTextSubmissions.tryEmit(text)
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(DEBUG_SUBMIT_TEXT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
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
