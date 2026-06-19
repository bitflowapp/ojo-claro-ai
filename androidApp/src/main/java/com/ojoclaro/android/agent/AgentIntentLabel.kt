package com.ojoclaro.android.agent

/**
 * Etiqueta hablable de la acción que Estela entendió, para mostrar en la UI sin
 * exponer el nombre técnico del enum (p. ej. "READ_VISIBLE_SCREEN").
 *
 * Mantiene el mismo espíritu que [com.ojoclaro.android.model.humanLabel] de
 * RobotSessionState: frases cortas, en español rioplatense, sin jerga. Se usa un
 * `when` exhaustivo a propósito: si se agrega un intent nuevo, el compilador
 * obliga a darle una etiqueta entendible para una persona no vidente.
 */
fun AgentIntent.userActionLabel(): String =
    when (this) {
        AgentIntent.HELP -> "Pedir ayuda"
        AgentIntent.STOP_SPEAKING -> "Dejar de hablar"
        AgentIntent.CANCEL -> "Cancelar"
        AgentIntent.CONFIRM -> "Confirmar"
        AgentIntent.OPEN_APP -> "Abrir una app"
        AgentIntent.OPEN_WHATSAPP -> "Abrir WhatsApp"
        AgentIntent.OPEN_WHATSAPP_CHAT -> "Abrir un chat de WhatsApp"
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> "Preparar un mensaje de WhatsApp"
        AgentIntent.READ_VISIBLE_SCREEN -> "Leer la pantalla"
        AgentIntent.READ_OCR_TEXT -> "Leer texto con la cámara"
        AgentIntent.CALL_CONTACT -> "Llamar a un contacto"
        AgentIntent.OPEN_PHONE -> "Abrir el teléfono"
        AgentIntent.OPEN_MAPS -> "Abrir el mapa"
        AgentIntent.GET_CURRENT_LOCATION -> "Saber dónde estás"
        AgentIntent.NAVIGATE_TO_DESTINATION -> "Ir hacia un destino"
        AgentIntent.SAVE_LOCATION_ALIAS -> "Guardar un lugar"
        AgentIntent.LIST_LOCATION_ALIASES -> "Repasar tus lugares guardados"
        AgentIntent.DELETE_LOCATION_ALIAS -> "Borrar un lugar guardado"
        AgentIntent.OPEN_SPOTIFY -> "Abrir Spotify"
        AgentIntent.PLAY_MUSIC -> "Poner música"
        AgentIntent.PAUSE_MUSIC -> "Pausar la música"
        AgentIntent.NEXT_SONG -> "Pasar a la siguiente canción"
        AgentIntent.VOLUME_UP -> "Subir el volumen"
        AgentIntent.VOLUME_DOWN -> "Bajar el volumen"
        AgentIntent.REMEMBER_MEMORY -> "Recordar algo"
        AgentIntent.LIST_MEMORY -> "Repasar lo que recordás"
        AgentIntent.CLEAR_MEMORY -> "Borrar lo que recordás"
        AgentIntent.SAVE_CONTACT -> "Guardar un contacto"
        AgentIntent.SAVE_CONTACT_PHONE -> "Guardar el teléfono de un contacto"
        AgentIntent.LIST_CONTACTS -> "Repasar tus contactos"
        AgentIntent.DELETE_CONTACT -> "Borrar un contacto"
        AgentIntent.CREATE_REMINDER -> "Crear un recordatorio"
        AgentIntent.LIST_REMINDERS -> "Repasar tus recordatorios"
        AgentIntent.CANCEL_REMINDER -> "Cancelar un recordatorio"
        AgentIntent.CREATE_ALARM -> "Poner una alarma"
        AgentIntent.REPEAT_LAST -> "Repetir lo último"
        AgentIntent.UNKNOWN -> "Todavía no estoy segura de qué pediste"
    }
