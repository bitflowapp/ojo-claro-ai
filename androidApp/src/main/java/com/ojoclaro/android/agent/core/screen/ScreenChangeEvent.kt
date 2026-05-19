package com.ojoclaro.android.agent.core.screen

/**
 * Paquete 5E — Tipo de cambio detectado entre dos [StructuredScreenSnapshot]s.
 *
 * No es un dato del producto: es el resultado del [ScreenChangeAwarenessEngine]
 * comparando previous vs current. Cada valor mapea a una semántica precisa
 * para no terminar hablando como loro.
 */
enum class ScreenChangeEvent {
    /** No hay nada relevante para anunciar. */
    NONE,

    /** El packageName cambió de forma clara y no se cae en una sub-categoría sensible. */
    APP_CHANGED,

    /**
     * Genérica de pantalla sensible cuando no encaja en password/banking/OTP.
     * Hoy solo se usa como respaldo defensivo.
     */
    SENSITIVE_SCREEN_ENTERED,

    /** Apareció un campo de contraseña que antes no estaba. */
    PASSWORD_SCREEN_ENTERED,

    /** La pantalla actual parece de pago / banca. */
    PAYMENT_OR_BANKING_SCREEN_ENTERED,

    /** Apareció una pantalla de chat / mensajería. */
    CHAT_SCREEN_ENTERED,

    /** Aparecieron campos editables y antes no había. */
    FORM_SCREEN_ENTERED,

    /**
     * Se detectó un diálogo o alerta: botones tipo Aceptar/Cancelar/Permitir
     * que antes no estaban.
     */
    DIALOG_OR_ALERT_APPEARED,

    /** El set de botones importantes cambió mucho (umbral en el engine). */
    IMPORTANT_BUTTONS_CHANGED,

    /** La pantalla quedó vacía (no hay lectura). */
    SCREEN_BECAME_EMPTY,

    /** Reaparece contenido después de un período vacío. */
    SCREEN_RESTORED
}
