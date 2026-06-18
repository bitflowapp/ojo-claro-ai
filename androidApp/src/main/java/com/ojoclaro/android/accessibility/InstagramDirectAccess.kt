package com.ojoclaro.android.accessibility

/**
 * V1.12 — resultados tipados del acceso por accesibilidad a Instagram Direct.
 *
 * Mismo contrato que WhatsApp:
 *  - detectar JAMÁS toca;
 *  - tocar re-verifica paquete y botón EN el momento del toque;
 *  - el contenido de mensajes/borradores nunca viaja en logs (solo longitudes).
 *
 * Instagram es single-activity (InstagramMainActivity para feed, inbox y
 * chat): la detección de pantalla se hace por marcadores de UI (ids y
 * etiquetas accesibles), nunca por el nombre de la activity.
 */
enum class InstagramScreenState {
    NOT_IN_INSTAGRAM,
    FEED_OR_HOME,
    INBOX,
    THREAD,
    UNKNOWN
}

/**
 * Estado de pantalla + título del chat abierto (solo para hablar; no se
 * loguea). [threadSubtitle] trae el handle visible del header (V1.12.1:
 * "so_roomero") para resolver pedidos por username desde el thread.
 */
data class InstagramScreenCheck(
    val state: InstagramScreenState,
    val threadTitle: String? = null,
    val threadSubtitle: String? = null
)

/** Resultado de tocar el tab "Mensaje" (Direct) en la barra inferior. */
sealed class InstagramTapOutcome {
    object Tapped : InstagramTapOutcome()
    object NotFound : InstagramTapOutcome()
    object NotInInstagram : InstagramTapOutcome()
    object ClickFailed : InstagramTapOutcome()
    object ServiceUnavailable : InstagramTapOutcome()
}

/** Resultado de abrir un chat del inbox por nombre visible. */
sealed class InstagramChatOpenResult {
    data class Opened(val matchedLabel: String) : InstagramChatOpenResult()
    data class NoMatch(val query: String) : InstagramChatOpenResult()
    object NotInInbox : InstagramChatOpenResult()
    object NotInInstagram : InstagramChatOpenResult()
    data class Unsafe(val reason: String) : InstagramChatOpenResult()
    object ClickFailed : InstagramChatOpenResult()
    object ServiceUnavailable : InstagramChatOpenResult()
}

/** Resultado de escribir el borrador en el composer (ACTION_SET_TEXT). */
sealed class InstagramDraftSetResult {
    object SetOk : InstagramDraftSetResult()
    object MismatchAfterSet : InstagramDraftSetResult()
    object NoComposer : InstagramDraftSetResult()
    object NotInThread : InstagramDraftSetResult()
    object NotInInstagram : InstagramDraftSetResult()
    object SetFailed : InstagramDraftSetResult()
    object ServiceUnavailable : InstagramDraftSetResult()
}

/** Resultado de tocar ENVIAR (solo si el campo coincide con lo confirmado). */
sealed class InstagramSendTapResult {
    object Sent : InstagramSendTapResult()
    object FieldMismatch : InstagramSendTapResult()
    object NoSendButton : InstagramSendTapResult()
    object NoComposer : InstagramSendTapResult()
    object NotInInstagram : InstagramSendTapResult()
    object ClickFailed : InstagramSendTapResult()
    object ServiceUnavailable : InstagramSendTapResult()
}

/** ¿Hay botón de videollamada visible en el chat abierto? */
sealed class InstagramVideoCallCheck {
    object Present : InstagramVideoCallCheck()
    object NoButton : InstagramVideoCallCheck()
    object NotInThread : InstagramVideoCallCheck()
    object NotInInstagram : InstagramVideoCallCheck()
    object ServiceUnavailable : InstagramVideoCallCheck()
}

/** Resultado del TOQUE de videollamada (solo tras confirmación hablada). */
sealed class InstagramVideoCallTapResult {
    object Tapped : InstagramVideoCallTapResult()
    object NoButton : InstagramVideoCallTapResult()
    object NotInThread : InstagramVideoCallTapResult()
    object NotInInstagram : InstagramVideoCallTapResult()
    object ClickFailed : InstagramVideoCallTapResult()
    object ServiceUnavailable : InstagramVideoCallTapResult()
}

/**
 * Detección del botón de mensaje de voz del composer. SOLO informativo para
 * la guía hablada: grabar exige mantener presionado (gesto continuo) y la
 * automatización de gestos está prohibida por contrato — no existe ninguna
 * función que lo toque.
 */
data class InstagramAudioButtonInfo(
    val present: Boolean,
    val label: String? = null
)

/**
 * V1.12.1 — corte de llamada de Instagram para LIMPIEZA DE QA (el
 * KEYCODE_ENDCALL no corta llamadas de Instagram). Jamás lo dispara una
 * frase de usuario: solo el broadcast de debug. Guardas duras: paquete
 * exacto, pantalla de llamada real y botón FUERTE de colgar; si no hay
 * botón accesible, no toca nada (el corte queda humano/manual).
 */
sealed class InstagramEndCallTapResult {
    object Tapped : InstagramEndCallTapResult()
    object NoButton : InstagramEndCallTapResult()
    object NotInCallScreen : InstagramEndCallTapResult()
    object NotInInstagram : InstagramEndCallTapResult()
    object ClickFailed : InstagramEndCallTapResult()
    object ServiceUnavailable : InstagramEndCallTapResult()
}
