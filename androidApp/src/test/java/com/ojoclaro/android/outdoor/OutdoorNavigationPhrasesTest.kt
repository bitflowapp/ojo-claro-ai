package com.ojoclaro.android.outdoor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * V1.2 — GPS y rutas por voz para el piloto rider.
 *
 * Contrato:
 *  - Las frases naturales de ubicación producen WhereAmI.
 *  - Las frases naturales de ruta producen NavigateTo con el destino limpio.
 *  - Los comandos durante ruta (cuánto falta / repetir / próximo paso /
 *    cancelar ruta) parsean a su comando.
 *  - Ninguna frase de WhatsApp o de seguridad vial se convierte en ruta.
 */
class OutdoorNavigationPhrasesTest {

    @Test
    fun locationPhrasesParseAsWhereAmI() {
        listOf(
            "Dónde estoy",
            "dónde estoy?",
            "Decime mi ubicación",
            "Cuál es mi ubicación",
            "Ubicame",
            "Estoy perdido",
            "estoy perdida",
            "me perdí",
            "Dónde me encuentro"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.WhereAmI>(
                OutdoorPhrases.parse(phrase),
                "Debería ser WhereAmI: \"$phrase\""
            )
        }
    }

    @Test
    fun routePhrasesParseWithCleanDestination() {
        val cases = mapOf(
            "Llevame a la farmacia" to "la farmacia",
            "Llevame al hospital" to "hospital",
            "Llevame a una plaza" to "una plaza",
            "Llevame a un supermercado" to "un supermercado",
            "Llevame a un kiosco" to "un kiosco",
            "Cómo llego a la farmacia" to "la farmacia",
            "Ruta a la farmacia" to "la farmacia",
            "Quiero ir a una farmacia" to "una farmacia",
            "Tengo que ir a San Martín 500" to "san martin 500",
            "Llevame a San Martín 500" to "san martin 500",
            "Cómo llego a San Martín 500" to "san martin 500",
            "Cómo llego a San Martín y Roca" to "san martin y roca",
            "Qué tengo que hacer para llegar a San Martín 500" to "san martin 500",
            "ruta hasta la estación" to "la estacion"
        )
        cases.forEach { (phrase, destination) ->
            val command = OutdoorPhrases.parse(phrase)
            assertIs<OutdoorPhrases.Command.NavigateTo>(
                command,
                "Debería ser NavigateTo: \"$phrase\""
            )
            assertEquals(destination, command.destination, "Destino de \"$phrase\"")
        }
    }

    @Test
    fun duringRouteCommandsParse() {
        assertIs<OutdoorPhrases.Command.HowFar>(OutdoorPhrases.parse("Cuánto falta"))
        assertIs<OutdoorPhrases.Command.HowFar>(OutdoorPhrases.parse("cuánto queda"))
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(OutdoorPhrases.parse("Repetí"))
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(
            OutdoorPhrases.parse("Repetí la indicación")
        )
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(
            OutdoorPhrases.parse("Cuál es el próximo paso")
        )
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(OutdoorPhrases.parse("próximo paso"))
        assertIs<OutdoorPhrases.Command.CancelNavigation>(OutdoorPhrases.parse("Cancelar ruta"))
        assertIs<OutdoorPhrases.Command.CancelNavigation>(OutdoorPhrases.parse("Terminá la ruta"))
        assertIs<OutdoorPhrases.Command.CancelNavigation>(
            OutdoorPhrases.parse("Cancelar navegación")
        )
    }

    @Test
    fun whatsAppAndScreenPhrasesAreNeverRoutes() {
        listOf(
            "Abrí WhatsApp",
            "Leé el chat",
            "Mirá el primer mensaje",
            "Mandale mensaje a San Martín",
            "mandale a juan que estoy llegando",
            "escribile a maria que ya salgo",
            "leeme los mensajes"
        ).forEach { phrase ->
            val parsed = OutdoorPhrases.parse(phrase)
            assertEquals(
                null,
                parsed,
                "NUNCA debería ser comando outdoor: \"$phrase\" (parseó $parsed)"
            )
        }
    }

    @Test
    fun safetyQuestionsNeverBecomeRoutesNorAdvanceOrders() {
        listOf(
            "¿Es seguro cruzar?",
            "puedo cruzar ahora",
            "¿está libre el camino para ir a la farmacia?"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.SafetyQuery>(
                OutdoorPhrases.parse(phrase),
                "Debería ser SafetyQuery (nunca ruta ni avance): \"$phrase\""
            )
        }
    }
}
