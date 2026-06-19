package com.ojoclaro.android.voice

/**
 * Texto hablable para una categoría de error de voz. Evita exponer el token
 * técnico (p. ej. "NO_MATCH", "SPEECH_TIMEOUT") a una persona no vidente, que de
 * otro modo escucharía jerga sin sentido en el aviso de voz del Home.
 *
 * Frases cortas, en español rioplatense, sin punto final (el llamador agrega la
 * puntuación). `when` exhaustivo: un error nuevo obliga a darle un aviso claro.
 */
fun SpeechErrorCategory.humanLabel(): String =
    when (this) {
        SpeechErrorCategory.NO_MATCH -> "No te escuché bien"
        SpeechErrorCategory.SPEECH_TIMEOUT -> "No llegué a escucharte"
        SpeechErrorCategory.RECOGNIZER_BUSY -> "Todavía estoy con lo anterior, esperá un segundo"
        SpeechErrorCategory.NETWORK -> "Tengo problemas de conexión"
        SpeechErrorCategory.CLIENT -> "Tuve un problema al escuchar"
        SpeechErrorCategory.INSUFFICIENT_PERMISSIONS -> "Necesito permiso del micrófono"
        SpeechErrorCategory.TOO_MANY_REQUESTS -> "Probá de nuevo en un momento"
        SpeechErrorCategory.SERVICE_DISCONNECTED -> "Se cortó el reconocimiento de voz, probá otra vez"
        SpeechErrorCategory.LANGUAGE_UNAVAILABLE -> "El idioma de voz no está disponible"
        SpeechErrorCategory.SERVICE_UNAVAILABLE -> "El reconocimiento de voz no está disponible ahora"
        SpeechErrorCategory.UNKNOWN -> "Tuve un problema al escucharte"
    }

/**
 * Convierte un token de categoría (el nombre del enum, p. ej. "NO_MATCH") en un
 * aviso hablable. Si el token no se reconoce, devuelve un aviso genérico seguro;
 * nunca devuelve el token crudo, así la UI jamás muestra jerga técnica.
 */
fun voiceErrorCategoryHumanLabel(token: String): String {
    val match = SpeechErrorCategory.values()
        .firstOrNull { it.name.equals(token.trim(), ignoreCase = true) }
    return (match ?: SpeechErrorCategory.UNKNOWN).humanLabel()
}
