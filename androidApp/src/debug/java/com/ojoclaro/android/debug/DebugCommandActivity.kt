package com.ojoclaro.android.debug

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.global.GlobalAssistantService
import com.ojoclaro.android.logging.SafeLog
import com.ojoclaro.android.memory.RelationshipContactStore
import java.util.Locale

/**
 * Harness de QA SOLO-DEBUG para probar el ruteo conversacional sin depender de
 * voz real. Inyecta un comando de texto por el MISMO pipeline real de la voz
 * (GlobalAssistantService.handleRecognizedText vía ACTION_DEBUG_VOICE_TEXT), con
 * la app en foreground para que cámara/ubicación puedan arrancar sus foreground
 * services (regla de Android 12+/14).
 *
 * Uso:
 *   adb shell am start -n com.ojoclaro.android/.debug.DebugCommandActivity \
 *     --es command "describir entorno"
 *   (opcional) --ei keepAliveMs 4000
 *
 * Contrato de seguridad del harness (hardening conversacional):
 *  - NUNCA enruta WhatsApp/Instagram, envíos, llamadas, pagos, compras ni
 *    viajes: un denylist conservador bloquea esas frases y loguea
 *    DEBUG_COMMAND_ERROR reason=blocked_unsafe_for_harness.
 *  - Existe solo en builds debug (este archivo vive en src/debug). El receptor
 *    real (ACTION_DEBUG_VOICE_TEXT) también está guardado por BuildConfig.DEBUG.
 */
class DebugCommandActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrarse sobre el lockscreen y encender la pantalla: así la Activity
        // queda VISIBLE/RESUMED y los foreground services de cámara/GPS pueden
        // arrancar (regla Android 12+/14), igual que una app de cámara desde el
        // candado. Pantalla encendida mientras corre el comando.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val command = (intent?.getStringExtra(EXTRA_COMMAND)
            ?: intent?.getStringExtra("cmd")
            ?: "").trim().take(MAX_COMMAND_LENGTH)
        val keepAliveMs = intent?.getIntExtra(EXTRA_KEEP_ALIVE_MS, DEFAULT_KEEP_ALIVE_MS)
            ?: DEFAULT_KEEP_ALIVE_MS

        showStatus("Estela debug\n\n\"$command\"")

        if (!BuildConfig.DEBUG) {
            // Defensa redundante: jamás operar fuera de debug.
            log("DEBUG_COMMAND_ERROR reason=not_debug_build")
            finishAfter(keepAliveMs)
            return
        }

        // Trusted Contacts (QA): sembrar/olvidar una relación SIN pasar por la
        // voz ni por el LLM. El número viene de la PANTALLA VISIBLE de WhatsApp
        // (default: no se tipea ningún número) o, sólo para QA con sintéticos, de
        // un extra. Escribe únicamente en las SharedPreferences privadas del
        // paquete; jamás loguea el número entero (sólo longitud + últimos 4).
        intent?.getStringExtra(EXTRA_FORGET_RELATIONSHIP)?.trim()?.lowercase()?.let { forgetKey ->
            if (forgetKey.isNotBlank()) {
                handleForgetRelationship(forgetKey)
                finishAfter(keepAliveMs)
                return
            }
        }
        intent?.getStringExtra(EXTRA_SEED_RELATIONSHIP)?.trim()?.lowercase()?.let { seedKey ->
            if (seedKey.isNotBlank()) {
                handleSeedRelationship(seedKey)
                finishAfter(keepAliveMs)
                return
            }
        }

        if (command.isBlank()) {
            log("DEBUG_COMMAND_ERROR reason=empty_command")
            showStatus("Estela debug\n\nFALTA --es command \"...\"")
            finishAfter(keepAliveMs)
            return
        }

        SafeLog.security(
            "debug_command_received",
            "len" to SafeLog.textLen(command),
            "hash" to SafeLog.shortHash(command),
            "route" to "harness_received",
            "keepAliveMs" to keepAliveMs
        )

        if (isUnsafeForHarness(command)) {
            // El harness conversacional jamás dispara mensajería/llamadas/pagos/viajes.
            SafeLog.security(
                "debug_command_blocked",
                "reason" to "unsafe_for_harness",
                "len" to SafeLog.textLen(command),
                "hash" to SafeLog.shortHash(command),
                "route" to "harness_blocked"
            )
            showStatus("Estela debug\n\nBLOQUEADO (no permitido en harness)\n\"$command\"")
            finishAfter(keepAliveMs)
            return
        }

        dispatchToRealPipeline(command)
        finishAfter(keepAliveMs)
    }

    /** Inyecta el texto por el ruteo REAL de la voz (mismo que usa el overlay). */
    private fun dispatchToRealPipeline(command: String) {
        try {
            val serviceIntent = Intent(this, GlobalAssistantService::class.java).apply {
                action = GlobalAssistantService.ACTION_DEBUG_VOICE_TEXT
                putExtra(GlobalAssistantService.EXTRA_AGENT_GOAL, command)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            log("DEBUG_COMMAND_DISPATCHED handler=GlobalAssistantService action=ACTION_DEBUG_VOICE_TEXT")
            log("DEBUG_COMMAND_RESULT result=dispatched note=ver_logs_EstelaBackground_para_ruteo")
        } catch (t: Throwable) {
            log("DEBUG_COMMAND_ERROR reason=dispatch_exception detail=${t.javaClass.simpleName}")
        }
    }

    /**
     * Siembra una relación ("pareja"/"qa"/...) con un contacto confiable LOCAL.
     * Número desde la pantalla visible de WhatsApp (default, sin tipear) o, sólo
     * para QA, desde --es seed_phone (usar SINTÉTICOS). Log redactado.
     */
    private fun handleSeedRelationship(key: String) {
        val explicit = intent?.getStringExtra(EXTRA_SEED_PHONE)?.trim()
        val fromVisible = explicit.isNullOrBlank()
        val phone = if (!explicit.isNullOrBlank()) {
            explicit
        } else {
            runCatching { OjoClaroAccessibilityService.readVisibleWhatsAppPhoneNumber() }.getOrNull()
        }
        if (phone.isNullOrBlank()) {
            log("DEBUG_SEED_RELATIONSHIP result=no_number key=$key fromVisible=$fromVisible")
            showStatus("Estela debug\n\nSin número para \"$key\".\nAbrí el chat (número visible) o pasá --es seed_phone")
            return
        }
        val label = intent?.getStringExtra(EXTRA_SEED_LABEL)?.trim().takeUnless { it.isNullOrBlank() }
            ?: "contacto $key"
        val linked = RelationshipContactStore(this)
            .link(key, label = label, phoneE164 = phone, source = "debug_seed")
        if (linked == null) {
            log("DEBUG_SEED_RELATIONSHIP result=invalid_number key=$key fromVisible=$fromVisible")
            showStatus("Estela debug\n\nNúmero inválido para \"$key\"")
            return
        }
        log("DEBUG_SEED_RELATIONSHIP result=linked fromVisible=$fromVisible ${linked.redactedForLog()}")
        showStatus("Estela debug\n\nGuardado \"$key\" terminado en ${linked.phoneEnding}")
    }

    private fun handleForgetRelationship(key: String) {
        RelationshipContactStore(this).forget(key)
        log("DEBUG_SEED_RELATIONSHIP result=forgot key=$key")
        showStatus("Estela debug\n\nOlvidado \"$key\"")
    }

    private fun finishAfter(delayMs: Int) {
        mainHandler.postDelayed({ if (!isFinishing) finish() }, delayMs.toLong().coerceIn(500L, 15_000L))
    }

    private fun showStatus(text: String) {
        val view = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(48, 48, 48, 48)
        }
        setContentView(view)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    /**
     * Denylist conservador del harness. Sprint WhatsApp: el CONTROL de WhatsApp
     * (abrir/leer chats/abrir chat/leer mensajes/scroll/responder-borrador/
     * cancelar/volver/diagnóstico) está PERMITIDO porque el envío real está
     * gateado por la doble confirmación del pipeline (jamás envía sin confirmar).
     * Siguen BLOQUEADAS las categorías prohibidas del sprint: Instagram,
     * llamadas/videollamadas, audios, fotos/archivos/stickers, pagos/banco,
     * Uber/viajes, compras.
     */
    private fun isUnsafeForHarness(command: String): Boolean {
        // Las preguntas conceptuales financieras no ejecutan pagos y deben poder
        // atravesar el pipeline real durante QA. Los imperativos siguen bloqueados.
        if (SafeLlmPhrases.isSafeQuestion(command) && PaymentGuidePhrases.classify(command) == null) {
            return false
        }
        val normalized = command.lowercase(Locale.ROOT)
        return UNSAFE_MARKERS.any { normalized.contains(it) }
    }

    companion object {
        private const val TAG = "EstelaDebugCmd"
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_KEEP_ALIVE_MS = "keepAliveMs"
        private const val DEFAULT_KEEP_ALIVE_MS = 3_000
        private const val MAX_COMMAND_LENGTH = 200

        // Trusted Contacts (QA, sólo debug): sembrar/olvidar una relación local.
        private const val EXTRA_SEED_RELATIONSHIP = "seed_relationship"
        private const val EXTRA_FORGET_RELATIONSHIP = "forget_relationship"
        private const val EXTRA_SEED_PHONE = "seed_phone"
        private const val EXTRA_SEED_LABEL = "seed_label"

        private val UNSAFE_MARKERS = listOf(
            // Otras apps fuera de alcance.
            "instagram", "insta ", "telegram", "messenger",
            // Llamadas / videollamadas (prohibido).
            "llamar", "llamá", "llama", "llamada", "llamalo", "llamala",
            "videollamada", "video llamada",
            // Audios / notas de voz (prohibido).
            "audio", "nota de voz", "grabar", "grabame", "mensaje de voz",
            // Fotos / archivos / stickers (prohibido).
            "foto", "fotos", "imagen", "imagenes", "archivo", "documento",
            "sticker", "stickers", "gif",
            // Pagos / banco (prohibido).
            "pagar", "pago", "pagale", "transferir", "transferencia", "tarjeta",
            "plata", "dinero", "mercado pago",
            // Compras (prohibido).
            "comprar", "comprame",
            // Transporte (prohibido en este sprint).
            "uber", "didi", "cabify", "taxi", "remis", "pedime un viaje"
        )
    }
}
