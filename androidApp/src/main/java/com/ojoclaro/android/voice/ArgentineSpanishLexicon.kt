package com.ojoclaro.android.voice

/**
 * Vocabulario y reglas de español rioplatense que el normalizador y el parser
 * usan para entender voz humana real (no comandos limpios).
 *
 * Reglas duras:
 *  - "dale" / "sí" / "ok" / "ajá" NUNCA confirman acciones sensibles.
 *  - Las muletillas se filtran SOLO si el resto sigue siendo un intento válido
 *    (sin texto restante, se preserva el original para que el caller decida).
 *  - El módulo no agrega features: solo prepara texto para los parsers existentes.
 *  - Música y recordatorios quedan listados como FUTURE_*: el parser actual NO
 *    los ejecuta. Sirven como contrato cuando se implementen.
 */
object ArgentineSpanishLexicon {

    // Tokens que aparecen como muletillas y deben removerse del input.
    // No incluyen verbos ni nombres propios. Probadas en QA física.
    val MULETILLA_TOKENS: Set<String> = setOf(
        "che",
        "eh",
        "eeh",
        "ehh",
        "mmm",
        "mmmm",
        "bueno",
        "tipo",
        "viste",
        "porfa",
        "dale"
    )

    // Frases de varias palabras que también son muletillas y deben removerse
    // como bloque (no como tokens sueltos para no romper "a casa", "o" como
    // palabra real, etc.).
    val MULETILLA_PHRASES: List<String> = listOf(
        "a ver",
        "o sea",
        "por favor",
        "como te decía",
        "como te decia"
    )

    // Reescrituras de voseo / formas reflexivas -> forma canonica previa al parser.
    // Se aplican como regex sobre el texto crudo y NO agregan features nuevas.
    //
    // Cuidado: NO reescribimos "mandame" porque "mandame a casa" semanticamente
    // es navegacion, no compose. "dale" vive en muletillas y nunca en confirmacion.
    val VOSEO_REWRITES: List<Pair<Regex, String>> = listOf(
        Regex("\\babrime\\b", RegexOption.IGNORE_CASE) to "abrir",
        Regex("\\babreme\\b", RegexOption.IGNORE_CASE) to "abrir",
        Regex("\\babr[íi]\\b", RegexOption.IGNORE_CASE) to "abrir",
        Regex("\\babre\\b", RegexOption.IGNORE_CASE) to "abrir",
        Regex("\\bb[úu]scame\\b", RegexOption.IGNORE_CASE) to "buscar",
        Regex("\\bbuscame\\b", RegexOption.IGNORE_CASE) to "buscar",
        Regex("\\bbusc[áa]\\b", RegexOption.IGNORE_CASE) to "buscar",
        Regex("\\bencontr[áa]\\b", RegexOption.IGNORE_CASE) to "encontrar",
        Regex("\\bencuentra\\b", RegexOption.IGNORE_CASE) to "encontrar",
        Regex("\\bmandale\\b", RegexOption.IGNORE_CASE) to "mandar",
        Regex("\\bdecile\\b", RegexOption.IGNORE_CASE) to "decir",
        Regex("\\bescribile\\b", RegexOption.IGNORE_CASE) to "escribir",
        Regex("\\bllamame\\s+a\\b", RegexOption.IGNORE_CASE) to "llamar a",
        Regex("\\bllamame\\b", RegexOption.IGNORE_CASE) to "llamar",
        Regex("\\bllam[áa]\\b", RegexOption.IGNORE_CASE) to "llamar",
        Regex("\\bllama\\b", RegexOption.IGNORE_CASE) to "llamar",
        Regex("\\bllevame\\b", RegexOption.IGNORE_CASE) to "llevar",
        Regex("\\bponeme\\b", RegexOption.IGNORE_CASE) to "poner",
        Regex("\\bavisame\\s+a\\b", RegexOption.IGNORE_CASE) to "avisar a",
        Regex("\\bavisame\\b", RegexOption.IGNORE_CASE) to "avisar",
        Regex("\\brecordame\\b", RegexOption.IGNORE_CASE) to "recordar",
        Regex("\\bfijate\\s+si\\b", RegexOption.IGNORE_CASE) to "revisar si",
        Regex("\\bfijate\\b", RegexOption.IGNORE_CASE) to "revisar",
        Regex("\\bubicame\\b", RegexOption.IGNORE_CASE) to "donde estoy"
    )

    // Aliases válidos de WhatsApp en español argentino. Mismo set que usa
    // CommandRouter (replicado acá para que tests sin Context lo verifiquen).
    val WHATSAPP_ALIASES: Set<String> = setOf(
        "wp",
        "wsp",
        "wpp",
        "wasap",
        "guasap",
        "watsap",
        "whasap",
        "whats app",
        "whatsapp"
    )

    // Sonidos de relleno de 1-3 letras que NUNCA pueden ser un nombre de
    // contacto. Si la frase candidata contiene alguno, se rechaza.
    val NAME_LIKE_NOISE_TOKENS: Set<String> = setOf(
        "uh",
        "eh",
        "ah",
        "oh",
        "mm",
        "ja",
        "ay",
        "uy",
        "ey",
        "em"
    )

    // Patrones de ruido típicos del SpeechRecognizer: el reconocimiento
    // duplica una palabra cuando tiene baja confianza ("sí sí", "este este").
    // Se usan para colapsar el texto antes de parsear.
    val NOISE_REPEAT_PATTERNS: List<Regex> = listOf(
        Regex("^(?:s[íi]\\s+){1,}s[íi]$", RegexOption.IGNORE_CASE),
        Regex("^(?:eh\\s+){1,}eh$", RegexOption.IGNORE_CASE),
        Regex("^(?:dale\\s+){1,}dale$", RegexOption.IGNORE_CASE),
        Regex("^(?:bueno\\s+){1,}bueno$", RegexOption.IGNORE_CASE),
        Regex("^(?:este\\s+){1,}este$", RegexOption.IGNORE_CASE),
        Regex("^(?:no\\s+){1,}no$", RegexOption.IGNORE_CASE)
    )

    // Frases que NUNCA confirman acciones sensibles, aunque el SR las devuelva.
    // El usuario tiene que decir explícitamente "confirmar" / "confirmo" / "aceptar".
    val NEVER_CONFIRM_PHRASES: Set<String> = setOf(
        "si",
        "sí",
        "dale",
        "ok",
        "okey",
        "okay",
        "bueno",
        "aja",
        "ajá",
        "de una",
        "claro",
        "obvio"
    )

    // Únicas frases válidas para confirmar.
    val STRICT_CONFIRM_PHRASES: Set<String> = setOf(
        "confirmar",
        "confirmo",
        "aceptar"
    )

    // Texto reconocido que NO es un comando. Combina NEVER_CONFIRM con repeticiones
    // típicas y muletillas usadas como utterance entera. Si el texto cae acá sin
    // pending real, la app responde "No hay ninguna acción pendiente." en vez de
    // mandarlo al backend (que devolvería el viejo "No pude conectar...").
    val AFFIRMATIVE_NOISE_PHRASES: Set<String> = setOf(
        "si",
        "sí",
        "si si",
        "sí sí",
        "si si si",
        "sí sí sí",
        "dale",
        "dale dale",
        "ok",
        "okey",
        "okay",
        "uh",
        "eh",
        "mm",
        "mhm",
        "ajá",
        "aja",
        "bueno",
        "che",
        "claro"
    )

    // Tokens que actúan como filler antes de un nombre cuando estamos en modo
    // guiado de WhatsApp. "este Marco Antonio" → "Marco Antonio".
    val LEADING_CONTEXT_FILLERS: Set<String> = setOf(
        "este",
        "esto",
        "ese",
        "eso",
        "como",
        "tipo",
        "che"
    )

    // Frases extra para GET_CURRENT_LOCATION en español argentino.
    val EXTRA_CURRENT_LOCATION_PHRASES: Set<String> = setOf(
        "donde ando",
        "ubicame",
        "donde estoy parado",
        "donde estoy parada",
        "decime donde estoy"
    )

    // Frases extra para NAVIGATE_TO_DESTINATION (alias informales). El parser
    // existente ya entiende "llevame al laburo" porque "llevame a (.+)" captura
    // todo después de "a"; lo dejamos acá para documentar el caso.
    val INFORMAL_LOCATION_ALIASES: Set<String> = setOf(
        "el laburo",
        "laburo",
        "lo de mama",
        "lo de mamá",
        "lo de papa",
        "lo de papá",
        "lo de mi vieja",
        "lo de mi viejo"
    )

    // Música — FUTURE: el parser actual NO ejecuta estas acciones. Lista
    // documentada para la fase que conecte MusicActionExecutor.
    val FUTURE_MUSIC_PHRASES: Set<String> = setOf(
        "poneme musica",
        "pone spotify",
        "abri spotify",
        "pasa tema",
        "siguiente tema",
        "pausa la musica",
        "subi el volumen",
        "baja el volumen"
    )

    // Recordatorios — FUTURE: el parser actual NO ejecuta estas acciones.
    val FUTURE_REMINDER_PHRASES: Set<String> = setOf(
        "recordame tomar la medicacion",
        "avisame a las ocho",
        "poneme alarma",
        "despertame a las ocho",
        "todos los lunes avisame"
    )
}
