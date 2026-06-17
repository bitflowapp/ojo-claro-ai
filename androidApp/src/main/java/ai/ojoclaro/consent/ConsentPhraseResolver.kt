package ai.ojoclaro.consent

import com.ojoclaro.android.consent.ConsentPhrases

/**
 * Resuelve un voice_response_template emitido por el motor de intenciones LLM
 * a texto humano usando las frases canónicas de ConsentPhrases.
 *
 * Contrato:
 *  - Si template es null o blank → null.
 *  - Si el ID es desconocido → null. No inventa texto.
 *  - Si falta un parámetro requerido → null. No produce texto con huecos.
 *  - Para nombres de apps de viaje, normaliza a TitleCase para el habla
 *    (uber → Uber, didi → Didi).
 */
object ConsentPhraseResolver {

    fun resolve(template: String?, params: Map<String, Any?> = emptyMap()): String? {
        if (template.isNullOrBlank()) return null

        return when (template) {
            "READ_VISIBLE_MESSAGE" -> ConsentPhrases.READ_VISIBLE_MESSAGE
            "READ_PASSWORD_FIELD_REJECTED" -> ConsentPhrases.READ_PASSWORD_FIELD_REJECTED
            "READ_BANKING_SCREEN" -> ConsentPhrases.READ_BANKING_SCREEN
            "PROTECTED_APP_REJECTED" -> ConsentPhrases.PROTECTED_APP_REJECTED

            "CALL_CONTACT_CONFIRM" ->
                params.stringOrNull("contact_query")?.let(ConsentPhrases::callContactConfirm)

            "RIDE_APP_OPEN_DISCLAIMER" ->
                params.stringOrNull("preferred_app")
                    ?.titleCaseForSpeech()
                    ?.let(ConsentPhrases::rideAppOpenDisclaimer)

            "NAVIGATE_TO_DESTINATION_CONFIRM" ->
                params.stringOrNull("destination")?.let(ConsentPhrases::navigateToDestinationConfirm)

            "SAVE_MEMORY_GENERIC" -> ConsentPhrases.SAVE_MEMORY_GENERIC
            "CLEAR_MEMORY_CONFIRM" -> ConsentPhrases.CLEAR_MEMORY_CONFIRM

            "SAVE_CONTACT_CONFIRM" ->
                params.stringOrNull("name")?.let(ConsentPhrases::saveContactConfirm)

            "SAVE_CONTACT_PHONE_CONFIRM" ->
                params.stringOrNull("contact_query")?.let(ConsentPhrases::saveContactPhoneConfirm)

            "DELETE_CONTACT_CONFIRM" ->
                params.stringOrNull("contact_query")?.let(ConsentPhrases::deleteContactConfirm)

            "CONFIRM_REPROMPT" -> ConsentPhrases.CONFIRM_REPROMPT
            "EXPIRED_ACTION" -> ConsentPhrases.EXPIRED_ACTION
            "NO_PENDING_CONFIRMATION" -> ConsentPhrases.NO_PENDING_CONFIRMATION
            "ACTION_CANCELLED" -> ConsentPhrases.ACTION_CANCELLED

            else -> null
        }
    }

    private fun Map<String, Any?>.stringOrNull(key: String): String? {
        val raw = this[key]?.toString()?.trim().orEmpty()
        return raw.ifBlank { null }
    }

    private fun String.titleCaseForSpeech(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
