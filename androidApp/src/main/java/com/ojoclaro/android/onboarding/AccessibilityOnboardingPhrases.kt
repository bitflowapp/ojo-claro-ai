package com.ojoclaro.android.onboarding

import java.text.Normalizer
import java.util.Locale

/**
 * Accessibility Onboarding — clasificador PURO de los comandos de
 * activación/estado/capacidades que Estela debe atender INCLUSO sin
 * Accesibilidad vinculada (solo necesita TTS + abrir Ajustes).
 *
 * Robusto a tildes/voseo. No toca nada; solo clasifica intención.
 */
object AccessibilityOnboardingPhrases {

    private val ACTIVATE = setOf(
        "activar estela", "activa estela", "activame estela", "prende estela", "prendé estela",
        "activar accesibilidad", "activa accesibilidad", "activa la accesibilidad",
        "activar la accesibilidad", "habilitar accesibilidad", "habilitar estela", "encender estela",
        "ayudame a activar estela", "ayudame a activar la accesibilidad",
        "ayudame a activar accesibilidad", "como activo estela", "como activo la accesibilidad",
        "no puedo activar estela", "no puedo activar la accesibilidad", "no puedo activar accesibilidad",
        "necesito activar estela", "quiero activar estela"
    )

    private val RETURN_CONFIRM = setOf(
        "ya active estela", "ya la active", "ya active la accesibilidad", "ya active accesibilidad",
        "ya esta activado", "ya esta activada", "ya quedo activada", "ya quedo activado",
        "ya volvi", "ya esta", "active estela", "lo active"
    )

    private val STATUS = setOf(
        "estado de estela", "como esta estela", "revisa estela", "revisá estela",
        "fijate si quedo activada", "revisa accesibilidad", "chequea estela",
        "estado", "como andas", "estas lista", "estas activa"
    )

    private val WHAT_CAN_I_DO = setOf(
        "que puedo hacer ahora", "que podes hacer ahora", "que se puede hacer ahora",
        "que puedo hacer", "que podes hacer"
    )

    private val WHAT_IS_MISSING = setOf(
        "que falta", "que falta activar", "que me falta", "que falta para que funcione",
        "que necesitas", "que te falta"
    )

    private val WHY_NOT_WORKING = setOf(
        "por que no funciona", "por que no anda", "por que no podes", "por que no lees",
        "por que no leer", "que pasa que no funciona", "no funciona"
    )

    private val WHATSAPP_HELP = setOf(
        "ayuda whatsapp", "ayuda con whatsapp", "ayudame con whatsapp", "ayuda de whatsapp"
    )

    fun isActivateRequest(text: String): Boolean = norm(text) in ACTIVATE
    fun isReturnConfirmation(text: String): Boolean = norm(text) in RETURN_CONFIRM
    fun isStatusRequest(text: String): Boolean = norm(text) in STATUS
    fun isWhatCanIDoNow(text: String): Boolean = norm(text) in WHAT_CAN_I_DO
    fun isWhatIsMissing(text: String): Boolean = norm(text) in WHAT_IS_MISSING
    fun isWhyNotWorking(text: String): Boolean = norm(text) in WHY_NOT_WORKING
    fun isWhatsAppHelp(text: String): Boolean = norm(text) in WHATSAPP_HELP

    private fun norm(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
