package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * WhatsApp Anxiety Hardening — decisión PURA del flujo de respuesta con DOBLE
 * confirmación (WA-5). Encapsula la máquina de estados de confirmación para que
 * [com.ojoclaro.android.global.GlobalAssistantService] sea una cáscara fina y
 * la lógica quede testeable sin instrumentar el servicio.
 *
 * Contrato de seguridad (idéntico al histórico del piloto):
 *  - "repetí" re-dice el prompt y NO toca WhatsApp (gana primero).
 *  - cualquier cancelación gana sobre confirmar: la dirección segura es no enviar.
 *  - paso 1 ("¿querés enviarlo?"): un sí/dale/ok/correcto AVANZA (no envía).
 *  - paso 2 ("¿lo mando ahora?"): SOLO confirmación FUERTE envía; un
 *    "sí"/"dale"/"ok"/"bueno"/"ajá"/"mmm"/"puede ser" a secas NUNCA envía.
 *  - en DRY-RUN, aun con confirmación fuerte, jamás se toca enviar.
 *
 * El resolver no habla, no loguea y no muta estado: solo devuelve la decisión.
 */
object WhatsAppReplyConfirmationResolver {

    /** Paso interno de envío real (V1.2 set-text ya tipeado): pide "enviá". */
    const val STEP_SEND_DRAFT: Int = 3

    enum class Outcome {
        /** "repetí": re-decir el prompt actual; no toca WhatsApp. */
        REPEAT_PROMPT,

        /** Cancelación: borrar el borrador propio y no enviar. */
        CANCELLED,

        /** Paso 1 confirmado: avanzar a la confirmación fuerte (no envía). */
        ADVANCED_TO_SEND,

        /** Paso 1 sin confirmación clara: volver a pedir sí/cancelar. */
        REPROMPT_STEP_1,

        /** Paso 2 con afirmación DÉBIL ("sí"/"dale"/"ajá"): jamás envía. */
        WEAK_CONFIRMATION_BLOCKED,

        /** Paso 2 con confirmación fuerte pero en DRY-RUN: no se toca enviar. */
        BLOCKED_DRY_RUN,

        /** Paso 2 con confirmación fuerte, dry-run apagado: el envío real
         *  todavía no está habilitado en este modo (no se toca enviar). */
        SEND_REAL_NOT_ENABLED,

        /** Paso 2 sin confirmación reconocible: volver a pedir mandalo/cancelar. */
        REPROMPT_STEP_2
    }

    /**
     * Afirmaciones DÉBILES que jamás alcanzan para enviar en el paso 2. Coincide
     * con la lista del contrato: "sí, dale, ok, bueno, ajá, mmm, puede ser".
     */
    private val WEAK_AFFIRMATIVES = setOf(
        "si", "sip", "sii", "dale", "dale si", "ok", "okey", "oka", "okay",
        "bueno", "bueno si", "esta", "ya", "ahi va",
        "aja", "aha", "ajam", "mmm", "mm", "mjm", "eh", "ehh",
        "puede ser", "tal vez", "talvez", "capaz", "supongo", "creo que si",
        "mas o menos", "puede", "y bueno"
    )

    /**
     * @param awaitingStep 1 = "¿querés enviarlo?"; 2 = "¿lo mando ahora?";
     *   [STEP_SEND_DRAFT] = borrador V1.2 ya tipeado esperando "enviá".
     */
    fun resolve(awaitingStep: Int, rawText: String, dryRun: Boolean): Outcome {
        // 1) Repetir gana: re-dice el prompt sin tocar nada.
        if (VoiceCommandDispatcher.isRepeatCommand(rawText)) return Outcome.REPEAT_PROMPT
        // 2) Cancelar gana sobre confirmar.
        if (WhatsAppReplyPhrases.isCancel(rawText)) return Outcome.CANCELLED

        return if (awaitingStep <= 1) {
            if (WhatsAppReplyPhrases.isConfirmStep1(rawText)) Outcome.ADVANCED_TO_SEND
            else Outcome.REPROMPT_STEP_1
        } else {
            when {
                WhatsAppReplyPhrases.isStrongSendConfirm(rawText) ->
                    if (dryRun) Outcome.BLOCKED_DRY_RUN else Outcome.SEND_REAL_NOT_ENABLED
                isWeakAffirmative(rawText) -> Outcome.WEAK_CONFIRMATION_BLOCKED
                else -> Outcome.REPROMPT_STEP_2
            }
        }
    }

    /**
     * Afirmación débil que NUNCA debe enviar en el paso fuerte. Reusa el gate
     * canónico del normalizador ("dale"/"sí"/"ok"/"ajá" jamás confirman) y suma
     * los titubeos ("puede ser", "tal vez", "capaz") propios del contrato.
     */
    fun isWeakAffirmative(rawText: String): Boolean {
        if (VoicePhraseNormalizer.isNeverConfirm(rawText)) return true
        if (VoicePhraseNormalizer.isAffirmativeNoise(rawText)) return true
        return norm(rawText) in WEAK_AFFIRMATIVES
    }

    private fun norm(text: String): String =
        VoicePhraseNormalizer.normalizeForParser(text)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
