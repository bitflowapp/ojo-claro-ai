package com.ojoclaro.android.agent.payments

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.11 — pagos, tarjetas y vinculaciones: SOLO GUÍA, jamás automatización.
 *
 * Política dura (FORBIDDEN_AUTOMATIC):
 *  - Estela NUNCA ingresa números de tarjeta, CVV, claves, tokens ni PINs.
 *  - NUNCA confirma pagos, compras ni guarda métodos de pago por su cuenta.
 *  - Puede acompañar leyendo la pantalla paso a paso y diciendo qué se ve.
 */
object PaymentGuidePhrases {

    enum class Kind { LINK_PAYMENT, REGISTER_CARD, SENSITIVE_BLOCK }

    const val GUIDE_PREAMBLE: String =
        "Puedo guiarte paso a paso, pero no voy a ingresar ni guardar " +
            "datos sensibles por vos, y no confirmo pagos."

    const val GUIDE_NEXT_STEP: String =
        "Abrí la app y decime: leé la pantalla, y te voy diciendo qué aparece en cada paso."

    const val SENSITIVE_REFUSAL: String =
        "Eso no lo hago ni con confirmación: claves, tarjetas completas, " +
            "códigos y pagos los ingresás vos. Te acompaño leyendo la pantalla."

    private val LINK_MARKERS = listOf(
        "mercado pago", "mercadopago",
        "vincula mercado pago", "vincular mercado pago", "vincula mercadopago",
        "vincular mercadopago", "conecta mercado pago", "asocia mercado pago",
        "agrega un metodo de pago", "agregar metodo de pago",
        "vincular el pago", "vincula el pago"
    )

    /** Registrar/agregar/cargar un medio de pago: SOLO guía (no es ingreso
     *  de datos sensibles, es navegar a la pantalla correcta). */
    private val CARD_MARKERS = listOf(
        "registra una tarjeta", "registrame una tarjeta", "registrar una tarjeta",
        "registrar tarjeta", "registra tarjeta",
        "agrega una tarjeta", "agregame una tarjeta", "agregar tarjeta",
        "agrega tarjeta", "agregar una tarjeta",
        "carga una tarjeta", "cargar la tarjeta", "cargar tarjeta",
        "poner una tarjeta", "poner tarjeta"
    )

    /**
     * Pedidos de que Estela INGRESE, CONFIRME, PAGUE o TRANSFIERA: rechazo
     * fijo (FORBIDDEN_AUTOMATIC). Frases multi-palabra, matcheadas por
     * contains. El voseo solo reescribe "mandale"→"mandar" antes del fold,
     * así que cubrimos ambas formas donde hace falta.
     */
    private val SENSITIVE_DO_IT_MARKERS = listOf(
        // Ingreso de datos sensibles.
        "pone el cvv", "ingresa el cvv", "poner el cvv", "carga el cvv",
        "pone la clave", "ingresa la clave", "poner la clave",
        "pone el pin", "ingresa el pin", "poner el pin",
        "pone el codigo", "ingresa el codigo", "poner el codigo",
        "codigo de seguridad",
        "pone el numero de la tarjeta", "ingresa el numero de la tarjeta",
        "numero de tarjeta", "numero de la tarjeta",
        // Confirmar / hacer un pago.
        "confirma el pago", "confirmar el pago", "confirma pago",
        "paga vos", "pagalo vos", "paga por mi", "pagalo por mi",
        // Transferir / mandar dinero (manda + mandar por el voseo).
        "transferi", "transferir", "transferencia", "hace la transferencia",
        "hacer la transferencia", "hacer transferencia",
        "manda la plata", "envia la plata", "manda plata", "mandar plata",
        "manda dinero", "mandar dinero", "manda la guita", "manda guita",
        "envia plata", "enviar plata", "enviar dinero", "envia dinero",
        "mandar la plata", "enviar la plata"
    )

    /**
     * Términos financieros de UNA palabra, matcheados con límites de palabra:
     * "pagar"/"pago"/"cvv" jamás deben matchear por substring contra
     * "apagar"/"apago la camara"/"despachar". El \b evita ese falso positivo
     * (crítico: el routing corre pagos ANTES que los comandos de cámara).
     */
    private val SENSITIVE_WORD_MARKERS = listOf(
        "pagar", "paga", "pague", "pago", "pagos",
        "cvv", "transferencia", "transferencias"
    )

    private val SENSITIVE_WORD_REGEX: Regex =
        Regex("\\b(" + SENSITIVE_WORD_MARKERS.joinToString("|") + ")\\b")

    fun classify(rawText: String): Kind? {
        val text = fold(rawText)
        if (text.isBlank()) return null
        // 1) Pedidos explícitos de ingresar/confirmar/pagar/transferir.
        if (SENSITIVE_DO_IT_MARKERS.any { text.contains(it) }) return Kind.SENSITIVE_BLOCK
        // 2) Registrar/agregar tarjeta y 3) vincular medios de pago: SOLO guía.
        //    Van ANTES del catch-all de palabras: "vinculá mercado pago"
        //    contiene la palabra "pago" pero es guía, no bloqueo.
        if (CARD_MARKERS.any { text.contains(it) }) return Kind.REGISTER_CARD
        if (LINK_MARKERS.any { text.contains(it) }) return Kind.LINK_PAYMENT
        // 4) Catch-all financiero por palabra completa ("pagar"/"pago"/"cvv"/
        //    "transferencia"): jamás por substring (no rompe "apagá la cámara").
        if (SENSITIVE_WORD_REGEX.containsMatchIn(text)) return Kind.SENSITIVE_BLOCK
        return null
    }

    fun spokenGuide(kind: Kind): String = when (kind) {
        Kind.LINK_PAYMENT ->
            "$GUIDE_PREAMBLE Para vincular el método de pago: $GUIDE_NEXT_STEP"
        Kind.REGISTER_CARD ->
            "$GUIDE_PREAMBLE Para registrar la tarjeta: $GUIDE_NEXT_STEP " +
                "Los números y el código de seguridad los escribís vos."
        Kind.SENSITIVE_BLOCK -> SENSITIVE_REFUSAL
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
