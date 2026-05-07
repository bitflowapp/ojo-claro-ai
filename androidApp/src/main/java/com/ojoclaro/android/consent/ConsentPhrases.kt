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
        "Voy a leer texto visible de la pantalla. No lo guardo ni lo envío. Confirmá para continuar."

    const val COMPOSE_MESSAGE_TEMPLATE =
        "Voy a preparar un mensaje para %s que dice: %s. No lo envío automáticamente. Confirmá para continuar."

    const val OPEN_EXTERNAL_APP_TEMPLATE =
        "Voy a abrir %s."

    const val SAVE_MEMORY_GENERIC =
        "Voy a recordar esto. Confirmá para guardar."

    const val MEMORY_SAVED =
        "Listo. Lo voy a recordar."

    const val MEMORY_SAVE_CANCELLED =
        "Cancelado. No guardé nada."

    const val MEMORY_LIST_HEADER =
        "Esto es lo que recuerdo de forma segura."

    const val MEMORY_EMPTY =
        "Todavía no guardé preferencias."

    const val CLEAR_MEMORY_CONFIRM =
        "Voy a borrar mi memoria local. Confirmá para continuar."

    const val MEMORY_CLEARED =
        "Listo. Borré mi memoria local."

    const val MEMORY_CLEAR_CANCELLED =
        "Cancelado. No borré nada."

    const val MEMORY_DELETE_CANCELLED =
        "Cancelado. No olvidé nada."

    const val READ_BANKING_SCREEN =
        "Esta pantalla puede tener datos privados. Por ahora, hacelo desde la app correspondiente."

    const val READ_PASSWORD_FIELD_REJECTED =
        "No puedo leer campos de contraseña. Eso es por seguridad."

    const val PROTECTED_APP_REJECTED =
        "Esta app protege su contenido. No voy a intentar saltar esa protección."

    const val EXPIRED_ACTION =
        "La acción pendiente venció. Volvé a pedirla."

    const val NO_PENDING_CONFIRMATION =
        "No hay ninguna acción pendiente para confirmar."

    const val NO_PENDING_CANCELLATION =
        "No hay ninguna acción pendiente para cancelar."

    const val ACTION_CANCELLED =
        "Acción cancelada."

    const val STRONG_CONFIRMATION_NOT_AVAILABLE =
        "Por ahora no puedo confirmar acciones tan sensibles. Hacelo desde la app correspondiente."

    fun composeMessage(contact: String, message: String): String {
        val safeContact = contact.cleanForSpeech(maxChars = 80)
        val safeMessage = message.cleanForSpeech(maxChars = 220)
        return COMPOSE_MESSAGE_TEMPLATE.format(safeContact, safeMessage)
    }

    fun openExternalApp(appLabel: String): String =
        OPEN_EXTERNAL_APP_TEMPLATE.format(appLabel.cleanForSpeech(maxChars = 80))

    fun saveMemory(summary: String): String =
        "Voy a recordar que ${summary.cleanForSpeech(maxChars = 220)}. Confirmá para guardar."

    fun deleteMemory(label: String): String =
        "Voy a olvidar ${label.cleanForSpeech(maxChars = 120)}. Confirmá para continuar."

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
