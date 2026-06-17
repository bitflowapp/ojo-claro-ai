package com.ojoclaro.android.camera

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.13 — Camera Assist: frases de cámara, OCR y escena.
 *
 * Reglas duras de convivencia:
 *  - JAMÁS roba "leé la pantalla"/"qué aparece"/"qué puedo tocar": esas son
 *    lectura de PANTALLA (accesibilidad) y tienen su ruta V1.10.3. Acá solo
 *    entran frases con marca de CÁMARA/ENTORNO (cartel, hoja, enfrente,
 *    cámara, apuntando, enfocando...) o, con la cámara YA activa, las
 *    variantes cortas ("leé el texto", "qué ves").
 *  - Detectar jamás captura: el parser solo clasifica.
 */
object CameraAssistPhrases {

    enum class Command {
        CAMERA_START,
        CAMERA_STOP,
        CAMERA_PAUSE,
        TEXT_SCAN,
        SCENE_DESCRIBE,
        WATCH_TEXT
    }

    private val CAMERA_START_PHRASES = setOf(
        "activa la camara", "activa camara", "activame la camara",
        "abri la camara", "abrir la camara", "abri la camara de estela",
        "abrir la camara de estela", "prende la camara", "prende camara",
        "encende la camara", "modo camara", "modo vision", "activa vision",
        "activa el modo camara", "activa el modo vision", "usa la camara"
    )

    private val CAMERA_STOP_PHRASES = setOf(
        "cerra la camara", "cerrar la camara", "cierra la camara",
        "cerra camara", "cerrar camara",
        "apaga la camara", "apagar la camara", "apaga camara", "apagar camara",
        "detene la camara", "detener la camara",
        "detene camara", "detener camara",
        "para la camara", "saca la camara", "deja la camara",
        "termina la camara", "desactiva la camara", "salir del modo camara",
        "cerra el modo camara", "apaga vision", "desactiva vision"
    )

    private val CAMERA_PAUSE_PHRASES = setOf(
        "pausa la camara", "pausa camara", "pausar la camara",
        "pausa vision", "pausa la vision", "congela la camara"
    )

    /** Pedidos de LEER TEXTO del entorno con la cámara. */
    private val TEXT_SCAN_PHRASES = setOf(
        // "leé el texto"/"leer texto" SIEMPRE son OCR de cámara, aunque esté
        // inactiva (la abrimos puntual). NUNCA chocan con "leé la pantalla":
        // el match es por igualdad exacta, no por substring.
        "lee el texto", "leer texto", "leeme el texto",
        "lee el texto que tengo enfrente", "lee lo que tengo enfrente",
        "lee lo que tengo adelante", "lee el texto de enfrente",
        "que dice este cartel", "que dice el cartel", "que dice ese cartel",
        "que dice esta hoja", "que dice la hoja", "que dice este papel",
        "que dice el papel", "que dice esta etiqueta", "que dice el paquete",
        "que dice este paquete", "que dice la caja", "que dice esta caja",
        "que dice la pantalla que estoy apuntando",
        "lee la pantalla que estoy apuntando",
        "captura texto", "capturar texto", "escanea texto",
        "escanea el texto", "escanear texto", "escaneame el texto",
        "lee el texto con la camara", "lee con la camara",
        "lee el cartel", "leeme el cartel", "leeme la hoja"
    )

    /** Variantes cortas y ambiguas: SOLO con la cámara ya activa. ("lee el
     *  texto"/"leer texto" subieron a TEXT_SCAN_PHRASES: son OCR siempre.) */
    private val TEXT_SCAN_WHEN_ACTIVE = setOf(
        "leelo", "lee eso", "leeme eso", "lee",
        "que dice", "que dice aca", "que dice ahi"
    )

    /**
     * SOLO variantes que Outdoor DescribeAhead no posee ("apuntando",
     * "enfocando", cámara explícita). "describí"/"describime qué hay
     * enfrente" siguen siendo de Outdoor (V1.x, cero regresión): sin cámara
     * activa esas frases ni llegan acá, y las nuestras delegan en el mismo
     * flujo de visión.
     */
    private val SCENE_DESCRIBE_PHRASES = setOf(
        "describime que estoy apuntando", "describi que estoy apuntando",
        "decime que estoy apuntando", "que estoy apuntando",
        "que estoy enfocando", "estoy enfocando algo decime que ves",
        "mira con la camara", "mira por la camara",
        "decime que hay adelante con la camara",
        "describime que hay enfrente con la camara",
        "que ves con la camara", "describi con la camara"
    )

    /** Cortas y ambiguas: solo con cámara activa (sin cámara, "describí..."
     *  es de Outdoor y "qué ves" no es un comando). */
    private val SCENE_DESCRIBE_WHEN_ACTIVE = setOf(
        "que ves", "que hay", "que hay adelante", "describi", "describime",
        "describi la escena", "describime la escena", "decime que ves",
        "describime que ves", "que es esto que apunto"
    )

    private val WATCH_TEXT_PHRASES = setOf(
        "avisame si aparece texto", "avisar si aparece texto",
        "avisame cuando aparezca texto", "avisar cuando aparezca texto",
        "lee cuando detectes texto", "lee cuando veas texto",
        "quedate mirando si aparece un cartel",
        "quedate mirando si aparece texto",
        "avisame si ves texto", "avisar si ves texto",
        "avisame si aparece un cartel", "avisar si aparece un cartel"
    )

    /** Mientras la cámara está activa, "pará"/"cancelar" también la frenan
     *  (además de las frases explícitas de cierre). */
    private val STOP_WHEN_ACTIVE = setOf(
        "para", "para para", "cancelar", "cancela", "basta", "listo para"
    )

    fun parse(rawText: String, cameraActive: Boolean): Command? {
        val text = fold(rawText)
        if (text.isBlank()) return null
        return when {
            text in CAMERA_STOP_PHRASES -> Command.CAMERA_STOP
            cameraActive && text in STOP_WHEN_ACTIVE -> Command.CAMERA_STOP
            text in CAMERA_PAUSE_PHRASES -> Command.CAMERA_PAUSE
            text in CAMERA_START_PHRASES -> Command.CAMERA_START
            text in WATCH_TEXT_PHRASES -> Command.WATCH_TEXT
            text in TEXT_SCAN_PHRASES -> Command.TEXT_SCAN
            cameraActive && text in TEXT_SCAN_WHEN_ACTIVE -> Command.TEXT_SCAN
            text in SCENE_DESCRIBE_PHRASES -> Command.SCENE_DESCRIBE
            cameraActive && text in SCENE_DESCRIBE_WHEN_ACTIVE -> Command.SCENE_DESCRIBE
            else -> null
        }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
