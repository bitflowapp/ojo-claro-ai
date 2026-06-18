package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Reconocedor del pedido GENÉRICO y context-aware de "leeme WhatsApp".
 *
 * Cubre las frases naturales ambiguas que el usuario dice sin precisar si
 * quiere la lista de chats o los mensajes de un chat:
 *   "lee wp", "leé wp", "leeme WhatsApp", "qué dice WhatsApp",
 *   "qué aparece en WhatsApp", "leeme lo de WhatsApp", ...
 *
 * El caller (HomeViewModel / GlobalAssistantService) resuelve el destino según
 * la pantalla real:
 *   - dentro de un chat  → leer mensajes visibles,
 *   - en la lista        → leer chats visibles,
 *   - fuera de WhatsApp  → ofrecer abrirlo.
 *
 * Es la red de seguridad de la Fase 2A.1: si el texto menciona WhatsApp (o un
 * alias: wp, wsp, guasap, ...) junto con un verbo de lectura, NUNCA debe caer
 * en "no entendí". Las acciones (abrir, mandar, enviar, escribir, llamar,
 * borrar, mandar foto/ubicación) quedan EXCLUIDAS: esas siguen su ruta propia.
 */
object WhatsAppReadAloudPhrases {

    // Verbos/marcadores de acción que NO son lectura. Si aparecen, dejamos pasar
    // el texto a los routers de acción (abrir/mandar/etc.) y no lo tratamos como
    // "leer WhatsApp".
    private val ACTION_REGEX = Regex(
        "\\b(?:abrir|abri|abrime|entrar|entra|anda|andate|mandar|manda|mandale|mandales|" +
            "mandame|mandarle|enviar|envia|enviale|enviame|escribir|escribi|escribile|" +
            "escribirle|decile|decirle|contestar|contesta|contestale|responder|responde|" +
            "respondele|llamar|llama|llamale|borrar|borra|eliminar|elimina|compartir|" +
            "comparti|foto|fotos|imagen|imagenes|audio|grabar|ubicacion)\\b"
    )

    // Marcadores de intención de lectura / consulta de contenido visible.
    private val READ_REGEX = Regex(
        "\\b(?:lee|leer|leeme|leemelo|leelo|leela|repetime)\\b" +
            "|\\bque dice\\b|\\bque dicen\\b|\\bque aparece\\b|\\bque aparecen\\b" +
            "|\\bque hay\\b|\\bque se ve\\b|\\bque ves\\b|\\bque mensajes\\b" +
            "|\\bque chats\\b|\\bque conversaciones\\b|\\bque contactos\\b" +
            "|\\bmostrame\\b|\\bmostra\\b|\\bdecime que\\b|\\bcontame\\b" +
            "|\\bresumi\\b|\\bresumime\\b|\\brevisame\\b|\\bfijate\\b"
    )

    private val WHATSAPP_REGEX = Regex("\\bwhatsapp\\b")

    fun matches(rawText: String): Boolean {
        val key = WhatsAppPhraseNormalizer.normalize(rawText)
        if (key.isBlank()) return false
        if (!WHATSAPP_REGEX.containsMatchIn(key)) return false
        if (ACTION_REGEX.containsMatchIn(key)) return false
        return READ_REGEX.containsMatchIn(key)
    }
}
