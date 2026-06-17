package com.ojoclaro.android.presence

/**
 * V1.14 — estado visual de la presencia de Estela en el botón flotante.
 *
 * La voz sigue siendo el canal principal: esta capa es puramente estética y
 * de refuerzo. Cada estado trae su [contentDescription] para que TalkBack
 * anuncie qué está haciendo Estela sin depender de ver la animación.
 */
enum class AssistantVisualState(val contentDescription: String) {
    /** Disponible: respiración lenta, color neutro. */
    IDLE("Estela disponible"),

    /** Escuchando al usuario: anillo activo. */
    LISTENING("Estela escuchando"),

    /** Procesando (routing local o backend): órbita suave. */
    THINKING("Estela pensando"),

    /** Hablando por TTS: ondas procedurales. */
    SPEAKING("Estela hablando"),

    /** Cámara de Estela abierta: nunca silenciosa, indicador claro. */
    CAMERA_ACTIVE("Cámara de Estela activa"),

    /** Pagos/claves/permisos/bloqueo: alerta breve "me frené por seguridad". */
    WARNING("Estela necesita atención")
}
