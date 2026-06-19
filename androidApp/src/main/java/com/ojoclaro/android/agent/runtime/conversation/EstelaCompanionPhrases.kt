package com.ojoclaro.android.agent.runtime.conversation

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.3 — modo conversacional humano de Estela (capa local, nunca GPT).
 *
 * Reglas:
 *  - Matching EXACTO sobre texto plegado: una frase compuesta ("ayudame a
 *    mandar un mensaje") NUNCA cae acá; sigue su ruta de comando/misión.
 *  - Respuestas cortas (1 a 3 frases), cálidas, hablables por TTS.
 *  - Estela no finge ser humana ni inventa capacidades: solo ofrece lo que
 *    existe (visión, pantalla, ubicación, rutas, WhatsApp con confirmación).
 *  - Esta capa corre DESPUÉS de los comandos locales y ANTES del fallback:
 *    jamás puede robarle una frase a visión/GPS/rutas/WhatsApp.
 */
object EstelaCompanionPhrases {

    private const val CAPABILITIES =
        "Puedo describir lo que tenés enfrente, leer la pantalla, ayudarte " +
            "con rutas y preparar mensajes de WhatsApp con tu confirmación."

    /** Cierre amable cuando la persona no contesta más. */
    const val SILENT_CLOSE = "Está bien, quedo lista cuando me necesites."

    /** Fallback cálido cuando ninguna ruta entendió la frase. */
    const val NOT_UNDERSTOOD_WARM =
        "No llegué a entenderte bien. Probá decir: describí lo que tengo " +
            "enfrente, dónde estoy, o leé la pantalla."

    private val RESPONSES: Map<String, String> = buildMap {
        listOf("estela", "hola estela", "hola", "estas ahi", "estas").forEach {
            put(it, "Acá estoy. Decime qué necesitás.")
        }
        listOf("no se que hacer", "que hago ahora", "que hago", "no se").forEach {
            put(
                it,
                "Dale, vamos paso a paso. Puedo describirte el entorno, " +
                    "leer la pantalla o ayudarte con una ruta."
            )
        }
        listOf("no entiendo", "no te entiendo", "no entendi").forEach {
            put(
                it,
                "No pasa nada. Te lo explico más simple. Podés decirme: " +
                    "describí lo que tengo enfrente, o: leé la pantalla."
            )
        }
        listOf(
            "que podes hacer", "que sabes hacer", "ayuda", "ayudame",
            "podes ayudarme", "me ayudas", "necesito ayuda"
        ).forEach { put(it, CAPABILITIES) }
        listOf("gracias", "muchas gracias", "mil gracias", "sos muy util", "sos genial").forEach {
            put(it, "De nada. Estoy acá para ayudarte.")
        }
        listOf(
            "como estas", "como andas", "todo bien", "que tal",
            // Variantes reales del STT ("cómo esta", tuteo).
            "como esta", "como va"
        ).forEach {
            put(it, "Acá estoy, lista para ayudarte. ¿Qué necesitás?")
        }
        listOf("que puedes hacer", "que sabes").forEach {
            put(it, CAPABILITIES)
        }
        // Pre-piloto: "qué puedo hacer con WhatsApp" (y alias guasap/wsp, que el
        // normalizer pliega a "whatsapp") → capacidades WA-aware, no dead-end. El
        // envío real SIGUE exigiendo tu confirmación; esto solo informa.
        listOf(
            "que puedo hacer con whatsapp", "que puedo hacer con el whatsapp",
            "que puedo hacer en whatsapp", "que puedo hacer en el whatsapp",
            "que se puede hacer con whatsapp", "que se puede hacer en whatsapp"
        ).forEach { put(it, CAPABILITIES) }
        listOf("charlemos", "quiero hablar", "hablame", "acompaname").forEach {
            put(
                it,
                "Acá estoy con vos. Contame qué necesitás. Si querés, te " +
                    "describo el entorno o te leo la pantalla."
            )
        }
        listOf(
            "estoy nervioso", "estoy nerviosa", "tengo miedo",
            "estoy asustado", "estoy asustada"
        ).forEach {
            put(
                it,
                "Tranquilo, estoy con vos. Respirá un momento. Puedo " +
                    "describirte el entorno o revisar tu ubicación si te ayuda."
            )
        }
        listOf("que haces", "que estas haciendo").forEach {
            put(it, "Acá estoy, atenta por si me necesitás. ¿Probamos algo juntos?")
        }
        listOf("me siento perdido", "me siento perdida").forEach {
            put(
                it,
                "Tranquilo, estoy con vos. Si querés saber dónde estás, " +
                    "decime: dónde estoy. O te describo el entorno."
            )
        }
        listOf("acompaname un rato", "quedate conmigo").forEach {
            put(
                it,
                "Acá me quedo con vos. Pedime lo que necesites, " +
                    "cuando lo necesites."
            )
        }
        listOf("me salio mal", "salio mal", "no me salio").forEach {
            put(
                it,
                "No pasa nada, probamos de nuevo. Decime qué querías hacer " +
                    "y lo intentamos juntos."
            )
        }
        listOf("sos re util", "que bueno", "genial").forEach {
            put(it, "Gracias. Estoy para eso.")
        }
        listOf("probemos algo", "quiero mostrarte", "no se si funciona").forEach {
            put(
                it,
                "Dale. Empecemos por algo simple: describí el entorno, " +
                    "dónde estoy, o leé la pantalla."
            )
        }
        listOf("explicame", "explicamelo").forEach {
            put(
                it,
                "Claro. Decime qué querés que te explique: la pantalla, " +
                    "el entorno, o qué cosas puedo hacer por vos."
            )
        }
    }

    /**
     * @return la respuesta cálida si el texto ES exactamente una frase de
     *         compañía; null si no (y la frase sigue su ruta normal).
     */
    fun respond(rawText: String): String? = RESPONSES[fold(rawText)]

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
