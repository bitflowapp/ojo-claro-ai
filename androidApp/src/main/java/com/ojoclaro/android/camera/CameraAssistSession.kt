package com.ojoclaro.android.camera

import java.util.Locale

/**
 * V1.13 — estado y políticas PURAS de Camera Assist (sin Android).
 *
 * Decide QUÉ se habla y CUÁNDO, a partir de los textos que el OCR local va
 * emitiendo. Reglas:
 *  - one-shot de texto: se habla el PRIMER texto que llegue antes del
 *    deadline; si no llega nada, el caller anuncia el fallo honesto;
 *  - watch: un texto se habla solo si se mantuvo ESTABLE dos frames
 *    seguidos, no se repitió hace poco, y pasó el gap mínimo desde la
 *    última lectura (el TTS no se pisa a sí mismo);
 *  - presupuesto duro de watch: 2 minutos y se corta solo;
 *  - el contenido JAMÁS se loguea acá: el caller solo loguea longitudes.
 */
class CameraAssistSession(
    private val nowMillis: () -> Long,
    private val sensitivePredicate: (String) -> Boolean = { false }
) {

    enum class State {
        IDLE,
        CAMERA_STARTING,
        CAMERA_READY,
        TEXT_SCAN_ONESHOT,
        SCENE_DESCRIBE_ONESHOT,
        WATCHING_TEXT,
        ERROR_NO_PERMISSION,
        ERROR_CAMERA_UNAVAILABLE
    }

    sealed class OcrDecision {
        /** Hay que hablar este texto (el caller arma el copy y trunca). */
        data class Speak(
            val text: String,
            val sensitive: Boolean,
            val fromWatch: Boolean
        ) : OcrDecision()

        object Ignore : OcrDecision()
    }

    sealed class DeadlineEvent {
        /** One-shot venció sin texto: anunciar fallo honesto. */
        object ScanTimedOutWithoutText : DeadlineEvent()

        /** Watch agotó su presupuesto: avisar y volver a CAMERA_READY. */
        object WatchBudgetExhausted : DeadlineEvent()
    }

    var state: State = State.IDLE
        private set

    private var scanDeadlineAtMillis: Long = 0L
    private var watchEndsAtMillis: Long = 0L

    // Watch: candidato pendiente de estabilidad (2 frames con la misma clave).
    private var watchCandidateKey: String = ""

    // null = todavía no se habló nada: la PRIMERA lectura no espera gap.
    private var lastSpokenAtMillis: Long? = null
    private val recentlySpokenKeys = ArrayDeque<Pair<String, Long>>()

    fun onCameraStarting() {
        state = State.CAMERA_STARTING
    }

    fun onCameraReady() {
        if (state == State.CAMERA_STARTING || state == State.IDLE) {
            state = State.CAMERA_READY
        }
    }

    fun onCameraPermissionMissing() {
        state = State.ERROR_NO_PERMISSION
    }

    fun onCameraUnavailable() {
        state = State.ERROR_CAMERA_UNAVAILABLE
    }

    fun onClosed() {
        state = State.IDLE
        watchCandidateKey = ""
        recentlySpokenKeys.clear()
        scanDeadlineAtMillis = 0L
        watchEndsAtMillis = 0L
        lastSpokenAtMillis = null
    }

    val isActive: Boolean
        get() = state == State.CAMERA_READY || state == State.TEXT_SCAN_ONESHOT ||
            state == State.SCENE_DESCRIBE_ONESHOT || state == State.WATCHING_TEXT ||
            state == State.CAMERA_STARTING

    fun beginTextScan(deadlineMillis: Long = SCAN_DEADLINE_MILLIS) {
        state = State.TEXT_SCAN_ONESHOT
        scanDeadlineAtMillis = nowMillis() + deadlineMillis
    }

    fun beginSceneDescribe() {
        state = State.SCENE_DESCRIBE_ONESHOT
    }

    fun endSceneDescribe() {
        if (state == State.SCENE_DESCRIBE_ONESHOT) state = State.CAMERA_READY
    }

    fun beginWatch(budgetMillis: Long = WATCH_BUDGET_MILLIS) {
        state = State.WATCHING_TEXT
        watchEndsAtMillis = nowMillis() + budgetMillis
        watchCandidateKey = ""
    }

    fun stopWatch() {
        if (state == State.WATCHING_TEXT) state = State.CAMERA_READY
        watchCandidateKey = ""
    }

    /** Texto del OCR local. Decide si se habla, se espera o se ignora. */
    fun onOcrText(rawText: String): OcrDecision {
        val text = rawText.trim()
        if (text.isBlank()) return OcrDecision.Ignore
        val now = nowMillis()
        return when (state) {
            State.TEXT_SCAN_ONESHOT -> {
                state = State.CAMERA_READY
                rememberSpoken(keyOf(text), now)
                OcrDecision.Speak(
                    text = text,
                    sensitive = sensitivePredicate(text),
                    fromWatch = false
                )
            }
            State.WATCHING_TEXT -> {
                val key = keyOf(text)
                when {
                    wasSpokenRecently(key, now) -> OcrDecision.Ignore
                    key != watchCandidateKey -> {
                        // Primer frame con este texto: esperar estabilidad.
                        watchCandidateKey = key
                        OcrDecision.Ignore
                    }
                    lastSpokenAtMillis?.let { now - it < MIN_SPEAK_GAP_MILLIS } == true ->
                        OcrDecision.Ignore
                    else -> {
                        watchCandidateKey = ""
                        rememberSpoken(key, now)
                        OcrDecision.Speak(
                            text = text,
                            sensitive = sensitivePredicate(text),
                            fromWatch = true
                        )
                    }
                }
            }
            else -> OcrDecision.Ignore
        }
    }

    /** El caller la llama periódicamente (o tras delays) para vencimientos. */
    fun checkDeadlines(): DeadlineEvent? {
        val now = nowMillis()
        if (state == State.TEXT_SCAN_ONESHOT && now >= scanDeadlineAtMillis) {
            state = State.CAMERA_READY
            return DeadlineEvent.ScanTimedOutWithoutText
        }
        if (state == State.WATCHING_TEXT && now >= watchEndsAtMillis) {
            state = State.CAMERA_READY
            watchCandidateKey = ""
            return DeadlineEvent.WatchBudgetExhausted
        }
        return null
    }

    private fun rememberSpoken(key: String, now: Long) {
        lastSpokenAtMillis = now
        recentlySpokenKeys.addLast(key to now)
        while (recentlySpokenKeys.size > MAX_RECENT_KEYS) {
            recentlySpokenKeys.removeFirst()
        }
    }

    private fun wasSpokenRecently(key: String, now: Long): Boolean {
        while (recentlySpokenKeys.isNotEmpty() &&
            now - recentlySpokenKeys.first().second > RECENT_KEY_WINDOW_MILLIS
        ) {
            recentlySpokenKeys.removeFirst()
        }
        return recentlySpokenKeys.any { it.first == key }
    }

    private fun keyOf(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    companion object {
        const val SCAN_DEADLINE_MILLIS = 4_500L
        const val WATCH_BUDGET_MILLIS = 120_000L
        const val MIN_SPEAK_GAP_MILLIS = 2_500L
        const val RECENT_KEY_WINDOW_MILLIS = 30_000L
        const val MAX_RECENT_KEYS = 8
    }
}
