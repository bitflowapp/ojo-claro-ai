package com.ojoclaro.android.consent

/**
 * Frases canónicas para hablarle al usuario sobre acciones sensibles.
 *
 * Reglas:
 * - Frases cortas, español natural, no técnico.
 * - No culpar al usuario.
 * - Decir qué hacemos y qué NO hacemos.
 * - Nunca repetir el mismo aviso en loop.
 */
object ConsentPhrases {

    const val READ_VISIBLE_MESSAGE =
        "Puedo leer el texto visible de la pantalla. No lo guardo ni lo envío. Confirmá para continuar."

    const val COMPOSE_MESSAGE_TEMPLATE =
        "Preparo un mensaje para %s: %s. No lo envío automáticamente. Confirmá para continuar."

    const val OPEN_EXTERNAL_APP_TEMPLATE =
        "Abro %s. La acción final la hacés vos."

    const val SAVE_MEMORY_GENERIC =
        "Preparo guardar esto. Confirmá para guardar."

    const val MEMORY_SAVED =
        "Guardado. Lo voy a recordar."

    const val MEMORY_SAVE_CANCELLED =
        "Cancelado. No guardé nada."

    const val MEMORY_LIST_HEADER =
        "Esto es lo que recuerdo de forma segura."

    const val MEMORY_EMPTY =
        "Todavía no guardé preferencias."

    const val CLEAR_MEMORY_CONFIRM =
        "Preparo borrar mi memoria local. Confirmá para continuar."

    const val MEMORY_CLEARED =
        "Borré mi memoria local."

    const val MEMORY_CLEAR_CANCELLED =
        "Cancelado. No borré nada."

    const val MEMORY_DELETE_CANCELLED =
        "Cancelado. No olvidé nada."

    const val READ_BANKING_SCREEN =
        "No voy a leer contraseñas, códigos ni datos bancarios. Hacelo desde la app correspondiente."

    const val READ_PASSWORD_FIELD_REJECTED =
        "No voy a leer contraseñas, códigos ni datos bancarios."

    const val PROTECTED_APP_REJECTED =
        "Esta app protege su contenido. No voy a intentar saltar esa protección."

    const val EXPIRED_ACTION =
        "La acción pendiente venció. Volvé a pedirla cuando quieras."

    const val NO_PENDING_CONFIRMATION =
        "No tengo acciones pendientes para confirmar."

    const val NO_PENDING_CANCELLATION =
        "No tengo acciones pendientes para cancelar."

    const val ACTION_CANCELLED =
        "Cancelado. No ejecuto nada."

    const val STRONG_CONFIRMATION_NOT_AVAILABLE =
        "No puedo confirmar acciones tan sensibles. Hacelo desde la app correspondiente."

    const val CONFIRM_REPROMPT =
        "Para avanzar, necesito que digas confirmar. También podés decir cancelar."

    const val CALL_CONTACT_CONFIRM_TEMPLATE =
        "Abro el marcador con el número de %s. La llamada la hacés vos. Confirmá para continuar."

    const val SAVE_CONTACT_CONFIRM_TEMPLATE =
        "Preparo el contacto %s. Confirmá para guardar."

    const val SAVE_CONTACT_PHONE_CONFIRM_TEMPLATE =
        "Preparo el número para %s. Confirmá para guardar."

    const val DELETE_CONTACT_CONFIRM_TEMPLATE =
        "Preparo borrar el contacto %s de mi memoria local. Confirmá para continuar."

    const val RIDE_APP_OPEN_DISCLAIMER_TEMPLATE =
        "Abro %s. No voy a pedir el viaje automáticamente. Te dejo en la pantalla para revisar."

    const val NAVIGATE_TO_DESTINATION_CONFIRM_TEMPLATE =
        "Preparo navegación hacia %s. Confirmá para continuar."

    fun composeMessage(contact: String, message: String): String {
        val safeContact = contact.cleanForSpeech(maxChars = 80)
        val safeMessage = message.cleanForSpeech(maxChars = 220)
        return COMPOSE_MESSAGE_TEMPLATE.format(safeContact, safeMessage)
    }

    fun openExternalApp(appLabel: String): String =
        OPEN_EXTERNAL_APP_TEMPLATE.format(appLabel.cleanForSpeech(maxChars = 80))

    fun saveMemory(summary: String): String =
        "Preparo guardar que ${summary.cleanForSpeech(maxChars = 220)}. Confirmá para guardar."

    fun deleteMemory(label: String): String =
        "Preparo olvidar ${label.cleanForSpeech(maxChars = 120)}. Confirmá para continuar."

    fun callContactConfirm(contact: String): String =
        CALL_CONTACT_CONFIRM_TEMPLATE.format(contact.cleanForSpeech(maxChars = 80))

    fun saveContactConfirm(contact: String): String =
        SAVE_CONTACT_CONFIRM_TEMPLATE.format(contact.cleanForSpeech(maxChars = 80))

    fun saveContactPhoneConfirm(contact: String): String =
        SAVE_CONTACT_PHONE_CONFIRM_TEMPLATE.format(contact.cleanForSpeech(maxChars = 80))

    fun deleteContactConfirm(contact: String): String =
        DELETE_CONTACT_CONFIRM_TEMPLATE.format(contact.cleanForSpeech(maxChars = 80))

    fun rideAppOpenDisclaimer(appLabel: String): String =
        RIDE_APP_OPEN_DISCLAIMER_TEMPLATE.format(appLabel.cleanForSpeech(maxChars = 60))

    fun navigateToDestinationConfirm(destination: String): String =
        NAVIGATE_TO_DESTINATION_CONFIRM_TEMPLATE.format(destination.cleanForSpeech(maxChars = 120))

    private fun String.cleanForSpeech(maxChars: Int): String {
        val cleaned = replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned

        return cleaned
            .take(maxChars)
            .trimEnd()
            .trimEnd('.', ',', ';', ':')
            .plus("…")
    }
}
