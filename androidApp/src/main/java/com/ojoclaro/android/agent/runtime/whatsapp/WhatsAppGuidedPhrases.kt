package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer

/**
 * Reconocimiento determinista de comandos guiados de WhatsApp.
 *
 * Reglas:
 *  - Solo reconoce frases muy específicas. Sin LLM. Sin inferencia.
 *  - Devuelve null si no es un comando WhatsApp guiado → el caller sigue
 *    su flujo normal (REPEAT_LAST, ScreenQueryPhrases, orchestrator).
 *  - Es estricto: "abrí WhatsApp" (acción) NO matchea ningún comando guiado.
 *    Tampoco "mandale un mensaje a Sofi" (compose) — ese sigue al orchestrator.
 *  - Tampoco matchea consultas generales de pantalla ("resumí la pantalla").
 */
object WhatsAppGuidedPhrases {

    private val amIInWhatsApp: Set<String> = setOf(
        "estoy en whatsapp",
        "este es whatsapp",
        "esto es whatsapp",
        "estoy adentro de whatsapp"
    )

    private val whatCanIDoHere: Set<String> = setOf(
        "que puedo hacer en este chat",
        "que puedo hacer en el chat",
        "que opciones tengo en este chat",
        "que opciones tengo aca en whatsapp",
        "que puedo hacer en whatsapp aca",
        "que hay en este chat"
    )

    private val howDoISendPhoto: Set<String> = setOf(
        "como mando una foto",
        "como mando foto",
        "como envio una foto",
        "como le mando una foto",
        "como mando una imagen",
        "como mando imagen"
    )

    private val howDoISendLocation: Set<String> = setOf(
        "como mando ubicacion",
        "como mando mi ubicacion",
        "como envio ubicacion",
        "como le mando ubicacion",
        "como comparto ubicacion"
    )

    private val howDoISendMessage: Set<String> = setOf(
        "como le mando un mensaje",
        "como le mando un mensaje en whatsapp",
        "como mando un mensaje en whatsapp",
        "como escribo un mensaje en whatsapp",
        "como respondo en whatsapp",
        "como escribo aca",
        "como contesto aca"
    )

    fun classify(rawText: String): WhatsAppGuidedCommand? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        return when {
            key in amIInWhatsApp -> WhatsAppGuidedCommand.AmIInWhatsApp
            key in whatCanIDoHere -> WhatsAppGuidedCommand.WhatCanIDoHere
            key in howDoISendPhoto -> WhatsAppGuidedCommand.HowDoISendPhoto
            key in howDoISendLocation -> WhatsAppGuidedCommand.HowDoISendLocation
            key in howDoISendMessage -> WhatsAppGuidedCommand.HowDoISendMessage
            else -> null
        }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
