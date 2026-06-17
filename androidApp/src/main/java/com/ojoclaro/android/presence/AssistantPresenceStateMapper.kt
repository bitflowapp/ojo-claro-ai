package com.ojoclaro.android.presence

/**
 * V1.14 — mapeo PURO de señales del asistente al estado visual de reposo.
 *
 * Sin Android: solo lógica, totalmente testeable. El [WARNING] NO se resuelve
 * acá: es un destello transitorio que el caller superpone y luego revierte a
 * [resolve] con las señales vigentes (por eso "SPEAKING terminó" vuelve a
 * CAMERA_ACTIVE si la cámara sigue abierta, o a IDLE si no).
 *
 * Prioridad (de más a menos urgente para comunicar): hablar > pensar >
 * escuchar > cámara > reposo. Hablar gana porque es la acción más audible y
 * el usuario necesita saber que Estela está respondiendo.
 */
object AssistantPresenceStateMapper {

    data class Signals(
        val listening: Boolean = false,
        val processing: Boolean = false,
        val speaking: Boolean = false,
        val cameraActive: Boolean = false
    )

    fun resolve(signals: Signals): AssistantVisualState =
        when {
            signals.speaking -> AssistantVisualState.SPEAKING
            signals.processing -> AssistantVisualState.THINKING
            signals.listening -> AssistantVisualState.LISTENING
            signals.cameraActive -> AssistantVisualState.CAMERA_ACTIVE
            else -> AssistantVisualState.IDLE
        }
}
