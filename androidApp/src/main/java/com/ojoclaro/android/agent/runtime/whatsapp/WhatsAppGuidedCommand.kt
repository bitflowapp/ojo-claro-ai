package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Comandos guiados que el usuario puede pedir mientras está en WhatsApp.
 *
 * Importante: ninguno de estos comandos ejecuta una acción. Todos resultan en
 * guía verbal — Ojo Claro nunca toca botones, envía mensajes ni interactúa
 * con la app por su cuenta.
 */
sealed class WhatsAppGuidedCommand {
    /** "¿estoy en WhatsApp?" / "¿este es WhatsApp?" */
    object AmIInWhatsApp : WhatsAppGuidedCommand()

    /** "¿qué puedo hacer en este chat?" / "qué opciones tengo acá" */
    object WhatCanIDoHere : WhatsAppGuidedCommand()

    /** "¿cómo mando una foto?" */
    object HowDoISendPhoto : WhatsAppGuidedCommand()

    /** "¿cómo mando ubicación?" */
    object HowDoISendLocation : WhatsAppGuidedCommand()

    /** "¿cómo le mando un mensaje?" */
    object HowDoISendMessage : WhatsAppGuidedCommand()
}
