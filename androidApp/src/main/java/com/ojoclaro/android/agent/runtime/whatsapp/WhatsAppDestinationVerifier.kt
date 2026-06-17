package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Verificación FUERTE de destino tras abrir un chat por deep link `wa.me`.
 *
 * No alcanza con `inChat=true`: abrir el deep link puede dejar a la persona en
 * OTRO chat (o en la lista) y, para alguien no vidente, escribir ahí sería
 * mandarle el mensaje a quien no es. Por eso, antes de preparar cualquier
 * borrador, se contrasta el destino ESPERADO contra las señales disponibles de
 * WhatsApp y se exige un resultado explícito.
 *
 * Señal fuerte = últimos 4 dígitos visibles en la cabecera (para un contacto no
 * agendado, el título del chat ES su número). Señal secundaria = coincidencia
 * de etiqueta/título (sólo útil cuando la etiqueta esperada es un nombre real).
 *
 * Es lógica PURA: sin Android, sin IO. El llamador recolecta las señales
 * (detector + lectura de cabecera) y bloquea si el resultado no es VERIFIED.
 */
object WhatsAppDestinationVerifier {

    enum class Status {
        /** Destino confirmado por una señal positiva (ending o etiqueta). */
        VERIFIED,

        /** En el chat, con campo de texto, pero SIN ninguna señal para confirmar. */
        UNVERIFIED_NO_SIGNALS,

        /** Hay señal y CONTRADICE el destino esperado (chat equivocado). */
        MISMATCH,

        /** El deep link no dejó a la persona dentro de un chat. */
        NOT_IN_CHAT,

        /** En un chat, pero sin campo de texto (no se puede redactar). */
        NO_ENTRY_FIELD,

        /** WhatsApp nunca llegó al frente (sin snapshot utilizable). */
        TIMEOUT
    }

    data class Result(val status: Status, val signal: String) {
        val isVerified: Boolean get() = status == Status.VERIFIED

        /** Log SIN PII: estado + qué señal lo decidió. */
        fun redactedForLog(): String = "status=$status signal=$signal"
    }

    /**
     * @param expectedEnding  últimos 4 dígitos del contacto esperado (o null).
     * @param inChat          el detector ve un chat abierto.
     * @param hasEntryField   el detector ve el campo de mensaje (composer).
     * @param timedOut        no se obtuvo snapshot (WhatsApp no llegó al frente).
     * @param visibleEnding   últimos 4 dígitos visibles en la cabecera (o null).
     * @param labelMatches    coincidencia de etiqueta esperada vs título visible:
     *                        true=coincide, false=contradice, null=sin señal.
     */
    fun verify(
        expectedEnding: String?,
        inChat: Boolean,
        hasEntryField: Boolean,
        timedOut: Boolean,
        visibleEnding: String?,
        labelMatches: Boolean? = null
    ): Result {
        if (timedOut) return Result(Status.TIMEOUT, "none")
        if (!inChat) return Result(Status.NOT_IN_CHAT, "none")
        if (!hasEntryField) return Result(Status.NO_ENTRY_FIELD, "none")

        // Señal fuerte: últimos 4 dígitos. Si ambos están, deciden sin apelación.
        if (!expectedEnding.isNullOrBlank() && !visibleEnding.isNullOrBlank()) {
            return if (expectedEnding == visibleEnding) {
                Result(Status.VERIFIED, "phone_ending")
            } else {
                Result(Status.MISMATCH, "phone_ending")
            }
        }

        // Señal secundaria: etiqueta/título (sólo si el llamador la pudo comparar).
        return when (labelMatches) {
            true -> Result(Status.VERIFIED, "label")
            false -> Result(Status.MISMATCH, "label")
            null -> Result(Status.UNVERIFIED_NO_SIGNALS, "none")
        }
    }
}
