package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * V2.2 runtime wiring — parser + narrador PUROS que conectan [ScreenReasoner] y
 * [ContactResolver] al routing real de GlobalAssistantService.
 *
 * Alcance de ESTE sprint: PERCEPCIÓN (qué veo / a quién le mando) y NAVEGACIÓN
 * (abrir un chat por nombre). NO prepara ni envía mensajes: el envío queda para
 * un sprint posterior, una vez que percepción/navegación pasen en el Moto.
 *
 * Componentes PUROS (sin Android): un adapter en el servicio llena los nodos
 * desde OjoClaroAccessibilityService.readVisibleNodeSummaries() y este código
 * solo decide intención y texto a hablar.
 *
 * Reglas duras que respeta el cableado:
 *  - jamás envía un mensaje real ("sí"/"dale"/"ok" no disparan nada acá).
 *  - ante ambigüedad de contacto, pregunta; nunca auto-elige.
 *  - si no puede verificar el chat/contacto, NO abre a ciegas: explica.
 *  - el número de fallback de un alias autorizado abre el chat (wa.me) pero
 *    NUNCA escribe ni envía.
 */
sealed class ScreenIntent {
    /** "qué personas aparecen" — leer contactos/chats visibles. */
    object WhoIsVisible : ScreenIntent()

    /** "qué chat estoy viendo" — decir dónde está parado el usuario. */
    object WhichChat : ScreenIntent()

    /** "a quién le estoy por mandar esto" — destinatario del mensaje en preparación. */
    object WhoAmISending : ScreenIntent()

    /** "abrí el chat de X [en WhatsApp/Instagram]". */
    data class OpenChat(val rawName: String, val app: TargetApp) : ScreenIntent()
}

/**
 * Reconoce SOLO las frases que hoy caen a no_local_match y la apertura de chat
 * por nombre ("abrí el chat de X"), que el parser de compose NO captura. No pisa
 * a ScreenQueryPhrases ("qué estoy viendo", "qué aparece en pantalla", etc.):
 * esas frases siguen yendo a su ruta existente.
 */
object ScreenIntelligencePhrases {

    private val WHO_IS_VISIBLE = setOf(
        "que personas aparecen", "quienes aparecen", "que personas hay",
        "que contactos aparecen", "que contactos hay", "quien aparece",
        "que personas ves", "que contactos ves", "a quienes ves",
        "que gente aparece", "que personas figuran"
    )

    private val WHICH_CHAT = setOf(
        "que chat estoy viendo", "en que chat estoy", "que chat es este",
        "que chat estoy mirando", "en que chat estoy parado",
        "que conversacion estoy viendo", "en que conversacion estoy",
        "que chat tengo abierto", "que chat hay abierto"
    )

    private val WHO_AM_I_SENDING = setOf(
        "a quien le estoy por mandar esto", "a quien le mando esto",
        "a quien le estoy mandando", "a quien le estoy escribiendo",
        "a quien le mando", "a quien le estoy por escribir",
        "a quien se lo mando", "a quien le voy a mandar esto",
        "a quien le estoy por mandar", "a quien le estoy por enviar esto"
    )

    // "abrí el chat de X" — verbos de APERTURA que WhatsAppSmartComposeParser NO
    // captura (sus VERBS son mandar/enviar/escribir/decir/avisar). Exige el
    // sustantivo "chat/conversación" para no robarle "abrí WhatsApp" a su ruta.
    private val OPEN_CHAT = Regex(
        "^(?:abri|abrime|abrila|abrir|entra|entrame|entrar|mostrame|mostra|llevame|pasame) " +
            "(?:el |la |un |una |mi )?(?:chat|conversacion|charla|conversa) " +
            "(?:de |con |del |de la |a )?(.+)$"
    )

    private val APP_WHATSAPP = Regex("\\b(?:en |por |de |del )?whats?app(?:\\s+business)?\\b")
    private val APP_INSTAGRAM = Regex("\\b(?:en |por |de |del )?(?:instagram|insta|ig)\\b")

    fun parse(rawText: String): ScreenIntent? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        when (key) {
            in WHO_IS_VISIBLE -> return ScreenIntent.WhoIsVisible
            in WHICH_CHAT -> return ScreenIntent.WhichChat
            in WHO_AM_I_SENDING -> return ScreenIntent.WhoAmISending
        }
        val match = OPEN_CHAT.find(key) ?: return null
        var name = match.groupValues[1].trim()
        // Detectar la app si el usuario la nombró, y sacarla del nombre.
        val app = when {
            APP_INSTAGRAM.containsMatchIn(name) -> TargetApp.INSTAGRAM
            APP_WHATSAPP.containsMatchIn(name) -> TargetApp.WHATSAPP
            else -> TargetApp.UNKNOWN
        }
        name = name
            .replace(APP_INSTAGRAM, " ")
            .replace(APP_WHATSAPP, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("de ").removePrefix("con ").trim()
        if (name.isBlank()) return null
        return ScreenIntent.OpenChat(name.take(60), app)
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Alias locales para QA. El phoneFallback es un placeholder SINTÉTICO (no un
 * número real): se usa SOLO para ABRIR el chat por wa.me cuando el contacto no
 * aparece en la lista visible; nunca para escribir ni enviar. Un número real
 * va por config runtime / memoria de confianza, nunca hardcodeado en producción.
 */
object ScreenIntelligenceAliases {
    val WHATSAPP: List<ContactAlias> = listOf(
        ContactAlias(
            alias = "marco",
            canonicalName = "Marco Luna",
            app = TargetApp.WHATSAPP,
            phoneFallback = null,
        ),
    )
}

/** De dónde salió la decisión de app activa (para logs/diagnóstico). */
enum class ActiveAppSource { RAW_PACKAGE, RAW_PACKAGE_OTHER, IG_MARKERS, EXTERNAL_APP, WA_NODE_MARKERS, NONE }

data class ActiveAppResolution(
    val app: ActiveApp,
    val source: ActiveAppSource,
)

/**
 * Resuelve la app activa para Screen Intelligence combinando señales ESTABLES,
 * no solo `readActivePackageName()` (que justo tras un cambio de app o con el
 * overlay de Estela al frente puede venir vacío/del propio paquete).
 *
 * Prioridad (snapshot actual manda sobre estado viejo; NUNCA forzar IG/WA si el
 * snapshot dice claramente OTRA app):
 *  1. rawPackage confiable y == Instagram/WhatsApp → esa app.
 *  2. rawPackage es OTRO paquete concreto (no vacío, no el nuestro) → OTHER
 *     (no forzar: launcher/ajustes/otra app real al frente).
 *  3. rawPackage poco confiable (vacío o el overlay de Estela) → señales 2as:
 *     a. marcadores de Instagram (instagramScreenCheck del router de tareas) → INSTAGRAM.
 *     b. externalApp == WhatsApp (handoff) → WHATSAPP.
 *     c. marcadores de WhatsApp en nodos visibles → WHATSAPP.
 *  4. nada → OTHER.
 *
 * Conflicto rawPackage vs nodos: gana rawPackage (el dueño de la ventana es
 * autoritativo); los marcadores de nodos solo se usan si rawPackage no sirve.
 */
object ActiveAppResolver {

    private const val DEFAULT_OWN_PACKAGE = "com.ojoclaro.android"

    fun resolve(
        rawPackage: String?,
        externalIsWhatsApp: Boolean,
        inInstagramByMarkers: Boolean,
        nodes: List<ReasonerNode>,
        ownPackage: String = DEFAULT_OWN_PACKAGE,
    ): ActiveAppResolution {
        val pkg = rawPackage.orEmpty().lowercase().trim()
        when {
            "com.instagram" in pkg ->
                return ActiveAppResolution(ActiveApp.INSTAGRAM, ActiveAppSource.RAW_PACKAGE)
            pkg == "com.whatsapp.w4b" ->
                return ActiveAppResolution(ActiveApp.WHATSAPP_BUSINESS, ActiveAppSource.RAW_PACKAGE)
            "com.whatsapp" in pkg ->
                return ActiveAppResolution(ActiveApp.WHATSAPP, ActiveAppSource.RAW_PACKAGE)
        }
        // Si hay un paquete concreto de OTRA app (no vacío, no el nuestro), el
        // snapshot manda: no forzamos IG/WA desde estado viejo ni marcadores.
        val unreliable = pkg.isBlank() || pkg == ownPackage.lowercase().trim()
        if (!unreliable) {
            return ActiveAppResolution(ActiveApp.OTHER, ActiveAppSource.RAW_PACKAGE_OTHER)
        }
        // rawPackage no sirve (vacío o el overlay): usar señales secundarias.
        if (inInstagramByMarkers) {
            return ActiveAppResolution(ActiveApp.INSTAGRAM, ActiveAppSource.IG_MARKERS)
        }
        if (externalIsWhatsApp) {
            return ActiveAppResolution(ActiveApp.WHATSAPP, ActiveAppSource.EXTERNAL_APP)
        }
        if (hasWhatsAppNodeMarkers(nodes)) {
            return ActiveAppResolution(ActiveApp.WHATSAPP, ActiveAppSource.WA_NODE_MARKERS)
        }
        return ActiveAppResolution(ActiveApp.OTHER, ActiveAppSource.NONE)
    }

    private fun hasWhatsAppNodeMarkers(nodes: List<ReasonerNode>): Boolean =
        nodes.any { n ->
            val label = (n.text ?: n.contentDescription ?: n.hint ?: "").lowercase()
            "whatsapp" in label
        }
}

/**
 * Convierte un [ScreenModel] (y el estado de un mensaje en preparación) en texto
 * listo para hablar. Sin confianza suficiente, lo dice en vez de inventar.
 */
object ScreenIntelligenceNarrator {

    fun lowConfidence(): String = "No pude reconocer con seguridad la pantalla actual."

    fun whoIsVisible(model: ScreenModel): String {
        if (model.activeApp == ActiveApp.OTHER &&
            (model.confidence == Confidence.LOW || model.confidence == Confidence.UNKNOWN)
        ) {
            return lowConfidence()
        }
        val names = model.visibleContacts
            .map { it.primaryText.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
        if (names.isEmpty()) {
            return "No veo contactos ni chats en esta pantalla."
        }
        val noun = if (names.size == 1) "contacto" else "contactos"
        val more = if (model.visibleContacts.size > names.size) ", y hay más abajo" else ""
        return "Veo ${names.size} $noun: ${names.joinToString(", ")}$more."
    }

    fun whichChat(model: ScreenModel): String {
        if (model.confidence == Confidence.LOW || model.confidence == Confidence.UNKNOWN) {
            return "${lowConfidence()} ${model.explanation}".trim()
        }
        return when (model.screenType) {
            ScreenType.CONVERSATION ->
                if (model.currentChatTitle != null) "Estás en el chat con ${model.currentChatTitle}."
                else "Estás dentro de un chat, pero no pude leer con seguridad el nombre."
            else -> "No estás dentro de un chat puntual. ${model.explanation}"
        }
    }

    fun describe(model: ScreenModel): String {
        if (model.confidence == Confidence.LOW || model.confidence == Confidence.UNKNOWN) {
            return "${lowConfidence()} ${model.explanation}".trim()
        }
        return model.explanation
    }

    fun whoAmISending(recipient: String?): String =
        if (recipient.isNullOrBlank()) {
            "Ahora mismo no tengo ningún mensaje preparado para enviar."
        } else {
            "Tendrías un mensaje en preparación para $recipient. " +
                "De todos modos, no voy a enviar nada sin tu confirmación explícita."
        }
}
