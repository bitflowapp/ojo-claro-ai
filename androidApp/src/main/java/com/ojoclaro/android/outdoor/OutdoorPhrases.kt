package com.ojoclaro.android.outdoor

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Fast path local de Outdoor Guidance: estas frases NUNCA van a GPT.
 */
object OutdoorPhrases {

    sealed class Command {
        data object WhereAmI : Command()
        data object HowFar : Command()
        data object RepeatInstruction : Command()
        data object CancelNavigation : Command()
        data object DescribeAhead : Command()
        data class NavigateTo(val destination: String) : Command()

        /** V1.9 — recalcular la ruta activa, SOLO a pedido explícito. */
        data object Recalculate : Command()

        /** "¿Es seguro cruzar?" y similares: SIEMPRE respuesta complementaria. */
        data object SafetyQuery : Command()

        /**
         * V1.10.1 — "pedime un uber/remís/taxi": Estela NUNCA pide transporte
         * real. V1.10.2 — ahora ofrece ABRIR la app con confirmación (solo
         * abrir: jamás pedir, confirmar ni pagar un viaje).
         */
        data class TransportQuery(val app: TransportApp) : Command()

        /**
         * V1.10.2 — "abrí maps (con San Martín al 500)": ofrecer abrir Google
         * Maps con el destino cargado, con confirmación. Nunca inicia
         * navegación automática.
         */
        data class OpenMaps(val destination: String?) : Command()
    }

    /** App o medio de transporte mencionado. */
    enum class TransportApp { UBER, CABIFY, DIDI, TAXI_REMIS }

    private val WHERE_AM_I = listOf(
        "donde estoy", "adonde estoy", "en donde estoy", "donde me encuentro",
        "cual es mi ubicacion", "mi ubicacion", "decime mi ubicacion",
        "dime mi ubicacion", "decime donde estoy", "decime mi posicion",
        // V1.2: una persona perdida pide ayuda así, no con "ubicación".
        "estoy perdido", "estoy perdida", "me perdi",
        // Hardening Alexa-like: variantes de la spec que caían a fallback.
        "ubicacion actual", "mi ubicacion actual", "cual es mi ubicacion actual",
        "orientame", "orientarme", "ayudame a ubicarme", "ayudame a ubicar",
        "ayudame a orientarme", "donde me ubico"
    )
    private val HOW_FAR = listOf(
        "cuanto falta", "cuanta distancia falta", "cuanto me falta", "falta mucho",
        "cuanto queda", "cuanto me queda"
    )
    private val REPEAT = listOf(
        "repeti la indicacion", "repetime la indicacion", "decime la proxima indicacion",
        "proxima indicacion", "cual es la proxima indicacion", "repeti la instruccion",
        // "repetí" a secas solo es outdoor con ruta activa: el caller
        // (GlobalAssistantService) lo deja pasar a su ruta histórica si no.
        "repeti", "repetila", "repetir",
        "cual es el proximo paso", "proximo paso",
        "cual es el siguiente paso", "siguiente paso"
    )
    private val CANCEL = listOf(
        "cancelar navegacion", "cancela la navegacion", "cancelar la navegacion",
        "detener navegacion", "deten la navegacion", "parar navegacion",
        "termina la navegacion",
        "cancelar ruta", "cancela la ruta", "cancelar la ruta",
        "termina la ruta", "termina ruta", "terminar la ruta", "terminar ruta"
    )
    // V1.9 — recalcular la ruta a pedido (el aviso de desvío lo sugiere).
    private val RECALCULATE = listOf(
        "recalcula", "recalcular", "recalculala",
        "recalcula la ruta", "recalcular la ruta", "recalcular ruta",
        "recalcula ruta", "podes recalcular", "recalcula el camino",
        "busca otra ruta", "buscar otra ruta", "buscame otra ruta"
    )
    // --- DescribeAhead: intención de visión explícita, nunca matching laxo ---
    //
    // Regla: (verbo de describir/mirar O pregunta "qué tengo/hay/veo")
    //        + referencia espacial explícita (adelante/enfrente/delante/
    //        frente a mí/entorno/alrededor),
    //        O pedido explícito de cámara ("usá la cámara").
    // Sin referencia espacial ni cámara NO hay captura: "mirá el mensaje",
    // "mirá whatsapp", "mirá qué hora es" siguen por sus rutas normales.
    private val DESCRIBE_VERB_TOKENS = setOf(
        "describi", "describe", "describime", "describir",
        // El STT confunde "describí(me)" con "descubrí(me)": mismo intent,
        // solo se acepta junto a una referencia espacial o de cámara.
        "descubri", "descubrime"
    )
    private val LOOK_VERB_TOKENS = setOf("mira", "mirar")
    private val SCENE_QUESTION_MARKERS = listOf(
        "que tengo", "que hay", "que veo", "que se ve"
    )
    private val AHEAD_MARKER_TOKENS = setOf(
        // "frente" suelto está acá porque el STT real transcribe "enfrente"
        // como "frente" / "en frente" / "al frente" (evidencia física Moto G15:
        // "primero que tengo frente"). Solo cuenta junto a un verbo/pregunta
        // de visión, así que no dispara cámara por sí solo.
        "adelante", "enfrente", "delante", "alrededor", "entorno", "frente"
    )
    private val AHEAD_MARKER_PHRASES = listOf(
        "frente a mi", "frente mio", "al frente", "en frente"
    )
    private val CAMERA_ACTION_TOKENS = setOf(
        "usa", "usar", "usala", "activa", "activar", "activala",
        "prende", "prender", "prendela", "encende", "encender"
    )
    private val CAMERA_SEEING_MARKERS = listOf("viendo", "que ve")

    // Hardening Alexa-like: pedidos de visión EXPLÍCITOS que no encajan en la
    // regla "verbo + referencia espacial" pero son inequívocos para una persona
    // no vidente ("¿qué ves?", "ayudame a ver", "mirá por mí"). Coinciden por
    // igualdad exacta (texto ya normalizado), nunca por contains, para no
    // secuestrar frases de pantalla/mensajes ("mirá el mensaje" sigue intacto).
    private val DESCRIBE_EXACT_PHRASES = setOf(
        "que ves", "que estas viendo", "que es lo que ves",
        "describi lo que ves", "describime lo que ves", "deci lo que ves",
        "contame lo que ves", "describi lo que tenes adelante",
        "que estoy apuntando", "que estas apuntando", "a que estoy apuntando",
        "a que le estoy apuntando", "que estoy enfocando",
        "ayudame a ver", "ayudame a ver lo que tengo", "ayuda a ver", "ve por mi",
        "mira por mi", "mira por mis ojos", "mira vos por mi", "se mis ojos"
    )

    private val NAVIGATE_PREFIXES = listOf(
        // El lexicon de voseo canoniza "llevame"→"llevar" ANTES de este parser;
        // cubrimos ambas formas por robustez. Las variantes con "al" son
        // necesarias porque "llevar a " no matchea "llevar al hospital".
        "llevar a ", "llevar hasta ", "llevar al ",
        "llevarme a ", "llevarme hasta ", "llevarme al ",
        "llevame a ", "llevame hasta ", "llevame al ",
        "quiero ir a ", "quiero ir hasta ", "quiero ir al ",
        "como llego a ", "como llego al ", "como llego hasta ",
        "guiame a ", "guiame hasta ", "guiame al ",
        "navegar a ", "navegar al ", "navega a ", "navega al ",
        // V1.2: formas naturales de pedir una ruta.
        "ruta a ", "ruta al ", "ruta hasta ",
        "tengo que ir a ", "tengo que ir al ",
        "como hago para llegar a ", "como hago para llegar al ",
        "que tengo que hacer para llegar a ", "que tengo que hacer para llegar al "
    )
    private val HOW_FAR_TO_PREFIX = "cuanto falta para "

    /**
     * Preguntas de seguridad física: nunca se contestan con autorización.
     * Matching por contains (la gente las formula de muchas maneras).
     */
    private val SAFETY_QUERY_MARKERS = listOf(
        "es seguro cruzar", "es seguro avanzar", "es seguro pasar",
        "puedo cruzar", "puedo avanzar", "puedo pasar",
        "esta libre el camino", "el camino esta libre", "esta despejado",
        "viene algun auto", "viene un auto", "no viene ningun auto",
        "sin usar el baston", "sin usar baston", "sin el baston", "sin baston"
    )

    // V1.10.1 — apps/medios de transporte: por token exacto para no
    // secuestrar frases que solo los mencionan de pasada en otra palabra.
    private val TRANSPORT_TOKENS = setOf("uber", "remis", "taxi", "cabify", "didi")

    // V1.10.2 — "abrí maps (con destino)": token de maps + verbo de abrir/usar.
    // La mención pasiva ("qué aparece en el mapa") no dispara nada.
    private val MAPS_TOKENS = setOf("maps", "mapa", "mapas")
    private val MAPS_OPEN_VERB_TOKENS = setOf(
        "abri", "abrir", "abrime", "abre", "abrila", "abrilo",
        "usa", "usar", "pone", "poner", "poneme"
    )
    private const val MAPS_DESTINATION_CONNECTOR = " con "

    fun parse(rawText: String): Command? {
        val text = normalize(rawText)
        if (text.isBlank()) return null

        // Normalización liviana del texto CRUDO (solo minúsculas/acentos/
        // puntuación): conserva "a ver" y "lo que ves", que la limpieza pesada
        // borra como muletilla. Solo para las frases de visión EXACTAS.
        val rawLight = lightNormalize(rawText)

        if (SAFETY_QUERY_MARKERS.any { text.contains(it) }) return Command.SafetyQuery
        val tokens = text.split(' ')
        tokens.firstOrNull { it in TRANSPORT_TOKENS }?.let { token ->
            return Command.TransportQuery(
                when (token) {
                    "uber" -> TransportApp.UBER
                    "cabify" -> TransportApp.CABIFY
                    "didi" -> TransportApp.DIDI
                    else -> TransportApp.TAXI_REMIS
                }
            )
        }
        if (tokens.any { it in MAPS_TOKENS } && tokens.any { it in MAPS_OPEN_VERB_TOKENS }) {
            val destination = text.substringAfter(MAPS_DESTINATION_CONNECTOR, "")
                .trim()
                .removeSuffix(" por favor").trim()
                .takeIf { it.length >= 3 }
            return Command.OpenMaps(destination)
        }
        if (WHERE_AM_I.any { text == it || text == "$it ahora" }) return Command.WhereAmI
        if (CANCEL.any { text == it }) return Command.CancelNavigation
        if (RECALCULATE.any { text == it }) return Command.Recalculate
        if (REPEAT.any { text == it }) return Command.RepeatInstruction
        if (isDescribeAheadIntent(text, rawLight)) return Command.DescribeAhead
        if (HOW_FAR.any { text == it }) return Command.HowFar
        if (text.startsWith(HOW_FAR_TO_PREFIX)) return Command.HowFar

        NAVIGATE_PREFIXES.forEach { prefix ->
            if (text.startsWith(prefix)) {
                val destination = text.removePrefix(prefix).trim()
                    .removeSuffix(" por favor").trim()
                if (destination.length >= 3) {
                    return Command.NavigateTo(destination)
                }
            }
        }
        return null
    }

    /**
     * La cámara solo se activa con intención explícita de visión:
     * verbo/pregunta de describir + referencia espacial, o pedido directo
     * de cámara. `text` llega ya normalizado (minúsculas, sin tildes,
     * sin puntuación, espacios simples).
     */
    private fun isDescribeAheadIntent(text: String, rawLight: String): Boolean {
        if (text in DESCRIBE_EXACT_PHRASES || rawLight in DESCRIBE_EXACT_PHRASES) return true
        val tokens = text.split(' ')
        val hasDescribeVerb = tokens.any { it in DESCRIBE_VERB_TOKENS }

        // Pedido explícito de cámara: "usá la cámara", "activá la cámara y
        // describí", "decime qué está viendo la cámara". La mención pasiva
        // ("la cámara no anda") no dispara nada.
        if ("camara" in tokens) {
            if (tokens.any { it in CAMERA_ACTION_TOKENS }) return true
            if (CAMERA_SEEING_MARKERS.any { text.contains(it) }) return true
            if (hasDescribeVerb) return true
        }

        val hasAheadMarker = tokens.any { it in AHEAD_MARKER_TOKENS } ||
            AHEAD_MARKER_PHRASES.any { text.contains(it) }
        if (!hasAheadMarker) return false

        return hasDescribeVerb ||
            tokens.any { it in LOOK_VERB_TOKENS } ||
            SCENE_QUESTION_MARKERS.any { text.contains(it) }
    }

    private fun normalize(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Normalización LIVIANA del texto crudo: minúsculas + sin acentos + sin
     * puntuación, SIN reescrituras de voseo ni borrado de muletillas. Necesaria
     * para frases donde el verbo real es una "muletilla" ("ayudame a ver" →
     * la limpieza pesada borra "a ver"). Solo se compara contra sets EXACTOS.
     */
    private fun lightNormalize(rawText: String): String =
        rawText
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
