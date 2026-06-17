package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * WhatsApp Blind-First — respuestas de voz cortas, tranquilas y accionables para
 * los casos de recuperación de las rutas ciegas (abrir por contacto / responder
 * la última notificación).
 *
 * Principios:
 *  - Cada respuesta es BREVE y dice qué pasó + qué puede hacer la persona.
 *  - Cuando no se preparó nada, se tranquiliza explícitamente ("No escribí nada").
 *  - Puede nombrar al contacto/remitente (el usuario lo pidió), pero NUNCA dice
 *    un número de teléfono ni contenido de chat.
 *
 * Es PURO: sin Android, sin estado, sin IO. Las longitudes/flags para logs las
 * arma el caller (este narrador solo produce texto hablado).
 */
object WhatsAppBlindRouteNarrator {

    private const val NOTHING_WRITTEN = "No escribí nada."

    /** WhatsApp no está instalado. */
    fun notInstalled(): String =
        "No encontré WhatsApp instalado en este teléfono."

    /** El contacto nombrado no está en los contactos de confianza. */
    fun contactNotFound(spokenName: String): String =
        "No tengo a ${name(spokenName)} en tus contactos de confianza. " +
            "Decime el número, otro nombre, o cancelá. $NOTHING_WRITTEN"

    /** Lo dictado parece sensible (credencial, etc.): no se usa como contacto. */
    fun unsafeQuery(): String =
        "Eso parece tener datos sensibles, así que no lo uso como contacto. " +
            "Decime solo el nombre del contacto. $NOTHING_WRITTEN"

    /** Hay varios contactos parecidos: que la persona elija por nombre. */
    fun multipleContacts(names: List<String>): String {
        val shown = names.map { it.trim() }.filter { it.isNotBlank() }.take(3)
        val list = shown.joinToString(", ")
        return if (list.isBlank()) {
            "Encontré varios contactos parecidos. Decime el nombre exacto, o cancelá."
        } else {
            "Encontré varios contactos parecidos: $list. " +
                "Decime el nombre exacto, o cancelá."
        }
    }

    /** Se abrió WhatsApp pero no se pudo confirmar que entró al chat. */
    fun openedButNotInChat(spokenName: String): String =
        "Abrí WhatsApp, pero no pude confirmar que entró al chat de ${name(spokenName)}. " +
            "$NOTHING_WRITTEN Decime el nombre exacto, o cancelá."

    /** El sistema/WhatsApp bloqueó la apertura del chat. */
    fun couldNotOpen(): String =
        "WhatsApp no me dejó abrir ese chat de forma segura. $NOTHING_WRITTEN"

    /**
     * Tras abrir, NO se pudo confirmar de forma fuerte que el chat sea el del
     * destino esperado (sin señal, o el número visible no coincide). Bloqueo
     * seguro: no se escribe ni toca nada. Es la voz de COULD_NOT_CONFIRM_DESTINATION.
     */
    fun couldNotConfirmDestination(): String =
        "Abrí WhatsApp, pero no pude confirmar con seguridad que sea el chat correcto, " +
            "así que no escribí ni toqué nada. Revisá vos en la pantalla, o decime el " +
            "nombre exacto del contacto."

    /** Confirmación de apertura exitosa (best-effort, sin verificación de chat). */
    fun openedChat(spokenName: String): String =
        "Abrí el chat de ${name(spokenName)}. $NOTHING_WRITTEN"

    // --- Responder la última notificación --------------------------------------

    /** No hay notificaciones registradas. */
    fun noNotifications(): String =
        "Por ahora no tengo mensajes nuevos de WhatsApp registrados."

    /** Llegó un WhatsApp pero Android ocultó el remitente/contenido. */
    fun notificationHidden(): String =
        "Te llegó un WhatsApp, pero Android ocultó el contenido. " +
            "Abrí WhatsApp y te leo lo que se pueda."

    /**
     * Te escribió alguien que NO está en contactos de confianza: no podemos
     * abrir su chat solo por voz. Ofrecemos una alternativa segura.
     */
    fun notificationSenderUnresolved(sender: String): String {
        val who = sender.trim().takeIf { it.isNotBlank() }
        val intro = if (who != null) "Te escribió $who. " else "Te llegó un WhatsApp. "
        return intro +
            "No lo tengo en tus contactos de confianza, así que no puedo abrir ese " +
            "chat de forma segura. Abrí WhatsApp y te leo los mensajes, o decime el " +
            "nombre de un contacto guardado."
    }

    private fun name(spoken: String): String =
        spoken.trim().takeIf { it.isNotBlank() } ?: "ese contacto"
}
