package com.ojoclaro.android.outdoor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Piloto V1.1 — comandos naturales de visión por micrófono real.
 *
 * Contrato:
 *  - TODAS las variantes humanas de "describí lo que tengo enfrente/adelante/
 *    delante/frente mío/mi entorno" producen DescribeAhead.
 *  - NINGUNA frase vecina ("mirá el mensaje", "leé la pantalla") activa la
 *    cámara: la cámara exige intención explícita de visión.
 */
class OutdoorDescribeAheadPhrasesTest {

    private val describeVariants = listOf(
        // Variantes pedidas por la misión, tal como las diría una persona.
        "describí lo que tengo adelante",
        "describe lo que tengo adelante",
        "describime lo que tengo adelante",
        "describí lo que tengo enfrente",
        "describime lo que tengo enfrente",
        "qué tengo enfrente",
        "qué hay enfrente",
        "qué hay adelante",
        "qué tengo delante",
        "mirá lo que tengo enfrente",
        "mirá adelante",
        "mirá enfrente",
        "describí mi entorno",
        "describime el entorno",
        "decime qué hay frente mío",
        "qué hay frente a mí",
        "qué tengo frente mío",
        "usá la cámara",
        "activá la cámara y describí",
        "decime qué está viendo la cámara",
        // Equivalencias de la misión.
        "describí lo que tengo delante",
        "qué hay alrededor",
        "describí lo que hay al frente",
        // Frases legacy que ya funcionaban (no deben regresionar).
        "describi lo que tengo adelante",
        "describi adelante",
        "describi el entorno",
        "describime el entorno",
        "que tengo adelante",
        "que hay adelante",
        "mira lo que tengo adelante"
    )

    @Test
    fun allNaturalDescribeVariantsParse() {
        describeVariants.forEach { phrase ->
            assertIs<OutdoorPhrases.Command.DescribeAhead>(
                OutdoorPhrases.parse(phrase),
                "Debería ser DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun alexaLikeDescribeVariantsParse() {
        // Hardening Alexa-like: pedidos de visión explícitos que ANTES caían a
        // fallback ("no entendí"). Deben producir DescribeAhead.
        listOf(
            "qué ves",
            "¿qué ves?",
            "qué estás viendo",
            "mirá",
            "mirá alrededor",
            "describí lo que ves",
            "describime lo que ves",
            "describí el entorno",
            "qué tengo adelante",
            "qué hay adelante",
            "qué hay frente mío",
            "decí lo que ves",
            "contame lo que ves",
            "qué estoy apuntando",
            "a qué estoy apuntando",
            "qué estás apuntando",
            "ayudame a ver",
            "ayudame a ver lo que tengo",
            "mirá por mí",
            "mirá por mis ojos",
            "sé mis ojos"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.DescribeAhead>(
                OutdoorPhrases.parse(phrase),
                "Alexa-like debería ser DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun alexaLikeWhereAmIVariantsParse() {
        // Hardening Alexa-like: variantes de ubicación de la spec.
        listOf(
            "ubicación actual",
            "mi ubicación actual",
            "orientame",
            "ayudame a ubicarme",
            "ayudame a orientarme"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.WhereAmI>(
                OutdoorPhrases.parse(phrase),
                "Alexa-like debería ser WhereAmI: \"$phrase\""
            )
        }
    }

    @Test
    fun robustToCaseAccentsPunctuationAndSpaces() {
        listOf(
            "¿Qué tengo enfrente?",
            "DESCRIBÍ LO QUE TENGO ENFRENTE",
            "Describí lo que tengo enfrente.",
            "describi   lo que   tengo   enfrente",
            "que hay adelante!!",
            "¡Mirá enfrente!",
            "Qué tengo delante",
            "¿qué hay frente a mí?",
            "Usá la cámara, por favor"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.DescribeAhead>(
                OutdoorPhrases.parse(phrase),
                "Debería ser DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun realSttGarbledTranscriptionsParse() {
        // Transcripciones REALES capturadas en el Moto G15 cuando la persona
        // dijo "Describí lo que tengo enfrente" (logcat 2026-06-10):
        // el STT convierte "describí lo" en "primero"/"te escribí lo" y
        // "enfrente" en "frente"/"en frente"/"al frente".
        listOf(
            "primero que tengo frente",
            "primero que tengo frente al frente",
            "te escribí lo que tengo enfrente",
            "te escribí lo que tengo al frente",
            "te escribí lo que tengo en frente",
            "qué tengo en frente"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.DescribeAhead>(
                OutdoorPhrases.parse(phrase),
                "Transcripción STT real debería ser DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun reasonablePartialTranscriptionsStillParse() {
        // El SR a veces corta la frase pero conserva verbo + referencia.
        listOf(
            "describí enfrente",
            "mira enfrente",
            "que tengo enfrente",
            "describime entorno",
            // Mishearing previsible del STT: "descubrí" por "describí",
            // solo válido junto a una referencia espacial.
            "descubrí lo que tengo enfrente",
            "descubrime lo que hay adelante"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.DescribeAhead>(
                OutdoorPhrases.parse(phrase),
                "Debería ser DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun neighborPhrasesNeverTriggerCamera() {
        // Frases vecinas de la misión: ninguna puede producir DescribeAhead.
        listOf(
            "leé la pantalla",
            "abrí whatsapp",
            "mirá el primer mensaje",
            "mirá el mensaje",
            "mirá whatsapp",
            "mirá qué hora es",
            "dónde estoy",
            "llevame al hospital",
            "cuánto falta",
            "cancelar",
            "callate",
            "cerrar",
            "leeme los mensajes",
            "qué hora es",
            "describí el mensaje",
            "la cámara no anda",
            "apagá la cámara",
            "qué tengo que hacer hoy"
        ).forEach { phrase ->
            val parsed = OutdoorPhrases.parse(phrase)
            assertFalse(
                parsed is OutdoorPhrases.Command.DescribeAhead,
                "NUNCA debería activar cámara: \"$phrase\" (parseó $parsed)"
            )
        }
    }

    @Test
    fun describeWithoutSpatialReferenceDoesNotTriggerCamera() {
        // Verbo sin referencia al entorno: ambiguo, no se captura imagen.
        listOf(
            "describí",
            "describime",
            "descubrí el chat",
            "describime el mensaje de mamá"
        ).forEach { phrase ->
            assertEquals(
                null,
                OutdoorPhrases.parse(phrase),
                "Sin referencia espacial no hay DescribeAhead: \"$phrase\""
            )
        }
    }

    @Test
    fun navigateWithContractedArticleParses() {
        // "llevame al X" no matcheaba "llevar a " por el artículo contraído.
        val hospital = OutdoorPhrases.parse("llevame al hospital")
        assertIs<OutdoorPhrases.Command.NavigateTo>(hospital)
        assertEquals("hospital", hospital.destination)

        val kiosco = OutdoorPhrases.parse("quiero ir al kiosco de la esquina")
        assertIs<OutdoorPhrases.Command.NavigateTo>(kiosco)
        assertEquals("kiosco de la esquina", kiosco.destination)

        // La forma histórica sigue intacta.
        val plaza = OutdoorPhrases.parse("llevame a la plaza")
        assertIs<OutdoorPhrases.Command.NavigateTo>(plaza)
        assertEquals("la plaza", plaza.destination)
    }

    @Test
    fun safetyQueriesStillWinOverDescribe() {
        // "¿puedo cruzar?" + referencia espacial sigue siendo SafetyQuery:
        // la seguridad se evalúa antes que la visión.
        assertIs<OutdoorPhrases.Command.SafetyQuery>(
            OutdoorPhrases.parse("¿es seguro cruzar lo que tengo enfrente?")
        )
    }
}
