package com.ojoclaro.android.accessibility

/**
 * V1.2 — resultados de las operaciones de envío seguro y audio de WhatsApp
 * vía AccessibilityService. Datos planos, sin nodos vivos.
 *
 * Contrato de seguridad del envío:
 *  - Leer el borrador y tocar enviar SOLO funciona con WhatsApp en primer
 *    plano.
 *  - El toque de enviar exige que el campo de texto coincida EXACTAMENTE
 *    (espacios normalizados) con lo que Estela leyó en voz alta. Si el campo
 *    cambió, no se envía.
 *  - Nunca se loguea el contenido del borrador: solo longitudes y buckets.
 */
sealed class WhatsAppDraftReadResult {
    data class Draft(val text: String) : WhatsAppDraftReadResult()
    data object EmptyDraft : WhatsAppDraftReadResult()
    data object NotInWhatsApp : WhatsAppDraftReadResult()
    data object NoEntryField : WhatsAppDraftReadResult()
    data object ServiceUnavailable : WhatsAppDraftReadResult()
}

/**
 * Resultado de escribir un borrador en el campo de texto de WhatsApp con
 * ACTION_SET_TEXT (compose desde voz). Escribir NO envía: enviar exige la
 * doble confirmación y el toque verificado de [WhatsAppSendTapResult].
 */
sealed class WhatsAppDraftSetResult {
    data object SetOk : WhatsAppDraftSetResult()
    data object NotInWhatsApp : WhatsAppDraftSetResult()
    data object NoEntryField : WhatsAppDraftSetResult()
    data object SetFailed : WhatsAppDraftSetResult()
    data object MismatchAfterSet : WhatsAppDraftSetResult()
    data object ServiceUnavailable : WhatsAppDraftSetResult()
}

sealed class WhatsAppSendTapResult {
    data object Sent : WhatsAppSendTapResult()
    data object NotInWhatsApp : WhatsAppSendTapResult()

    /** El campo no coincide con lo confirmado: NUNCA se envía en este caso. */
    data object FieldMismatch : WhatsAppSendTapResult()
    data object NoSendButton : WhatsAppSendTapResult()
    data object ClickFailed : WhatsAppSendTapResult()
    data object ServiceUnavailable : WhatsAppSendTapResult()
}

sealed class WhatsAppAudioPlayResult {
    data object Playing : WhatsAppAudioPlayResult()
    data object NoAudioVisible : WhatsAppAudioPlayResult()
    data object NotInWhatsApp : WhatsAppAudioPlayResult()
    data object ClickFailed : WhatsAppAudioPlayResult()
    data object ServiceUnavailable : WhatsAppAudioPlayResult()
}

/**
 * V1.11 — videollamada de WhatsApp. Contrato: detectar jamás toca; el toque
 * SOLO llega tras confirmación hablada explícita y re-verifica paquete y
 * botón en el instante del click (pantalla cambiada = no se toca).
 */
sealed class WhatsAppVideoCallCheck {
    data object Present : WhatsAppVideoCallCheck()
    data object NoButton : WhatsAppVideoCallCheck()
    data object NotInWhatsApp : WhatsAppVideoCallCheck()
    data object ServiceUnavailable : WhatsAppVideoCallCheck()
}

sealed class WhatsAppVideoCallTapResult {
    data object Tapped : WhatsAppVideoCallTapResult()
    data object NoButton : WhatsAppVideoCallTapResult()
    data object NotInWhatsApp : WhatsAppVideoCallTapResult()
    data object ClickFailed : WhatsAppVideoCallTapResult()
    data object ServiceUnavailable : WhatsAppVideoCallTapResult()
}
