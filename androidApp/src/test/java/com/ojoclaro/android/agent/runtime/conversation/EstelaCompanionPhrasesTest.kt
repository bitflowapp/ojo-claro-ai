package com.ojoclaro.android.agent.runtime.conversation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * V1.3 — modo conversacional humano.
 *
 * Contrato:
 *  - Las frases de compañía tienen respuesta cálida, corta y hablable.
 *  - Ningún comando real (visión/GPS/rutas/WhatsApp) cae en compañía.
 *  - El routing del servicio evalúa compañía DESPUÉS de los comandos
 *    locales y ANTES del fallback.
 */
class EstelaCompanionPhrasesTest {

    @Test
    fun companionPhrasesGetWarmResponses() {
        listOf(
            "Estela", "Hola", "¿Estás ahí?",
            "No sé qué hacer", "Qué hago ahora",
            "No entiendo",
            "Qué podés hacer", "Ayudame", "¿Podés ayudarme?", "ayuda",
            "Gracias", "Sos muy útil",
            "Cómo estás",
            "Charlemos", "Hablame", "Acompañame",
            "Estoy nervioso", "Tengo miedo",
            "Explicame"
        ).forEach { phrase ->
            assertNotNull(
                EstelaCompanionPhrases.respond(phrase),
                "Debería tener respuesta de compañía: \"$phrase\""
            )
        }
    }

    @Test
    fun responsesAreShortWarmAndSpeakable() {
        val coldWords = listOf("error", "comando", "no reconocido", "inválido", "sistema")
        listOf(
            "Estela", "No sé qué hacer", "No entiendo", "Ayudame", "Gracias",
            "Cómo estás", "Charlemos", "Estoy nervioso", "Explicame"
        ).forEach { phrase ->
            val response = EstelaCompanionPhrases.respond(phrase)!!
            assertTrue(
                response.length in 10..230,
                "Respuesta hablable (10-230 chars) para \"$phrase\": ${response.length}"
            )
            coldWords.forEach { cold ->
                assertTrue(
                    !response.lowercase().contains(cold),
                    "Sin palabras frías (\"$cold\") en: $response"
                )
            }
        }
    }

    @Test
    fun realCommandsNeverFallIntoCompanionLayer() {
        listOf(
            "Describí lo que tengo enfrente",
            "Dónde estoy",
            "Estoy perdido",
            "Llevame a la farmacia",
            "Leé la pantalla",
            "Mandale a Juan que estoy llegando",
            "Enviá el mensaje",
            "Reproducí el audio",
            "Cancelar ruta",
            "¿Es seguro cruzar?",
            "Ayudame a mandar un mensaje a Juan",
            "callate"
        ).forEach { phrase ->
            assertEquals(
                null,
                EstelaCompanionPhrases.respond(phrase),
                "NO es frase de compañía: \"$phrase\""
            )
        }
    }

    @Test
    fun capabilitiesAnswerNeverInventsFeatures() {
        val capabilities = EstelaCompanionPhrases.respond("qué podés hacer")!!
        // Solo capacidades reales del piloto.
        assertTrue(capabilities.contains("describir"))
        assertTrue(capabilities.contains("pantalla"))
        assertTrue(capabilities.contains("rutas"))
        assertTrue(capabilities.contains("confirmación"))
        // Nada de promesas inexistentes.
        listOf("música", "llamadas", "recordatorio", "alarma").forEach { fake ->
            assertTrue(!capabilities.lowercase().contains(fake))
        }
    }

    @Test
    fun serviceRoutesCompanionAfterLocalCommandsBeforeFallback() {
        val service =
            File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()
        val outdoorIdx = service.indexOf("handleOutdoorCommand(text)")
        val whatsappIdx = service.indexOf("handleWhatsAppVoiceSendCommand(text)")
        val companionIdx = service.indexOf("EstelaCompanionPhrases.respond(text)")
        val fallbackIdx = service.indexOf("fallbackReason=no_local_match")
        assertTrue(outdoorIdx in 1 until companionIdx, "outdoor antes que compañía")
        assertTrue(whatsappIdx in 1 until companionIdx, "whatsapp seguro antes que compañía")
        assertTrue(companionIdx in 1 until fallbackIdx, "compañía antes que el fallback")
    }

    @Test
    fun bareRepeatUsesShortMemoryWhenNoRouteActive() {
        val service =
            File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()
        assertTrue(service.contains("lastSpokenResponse"), "memoria corta de última respuesta")
        assertTrue(
            service.contains("REPEAT_PREFIX = \"Te repito: \""),
            "la repetición no debe pisar la memoria corta"
        )
        assertTrue(
            service.contains("Todavía no dije nada"),
            "respuesta honesta cuando no hay nada para repetir"
        )
    }
}
