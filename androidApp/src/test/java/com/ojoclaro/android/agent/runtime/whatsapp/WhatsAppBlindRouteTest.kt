package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WhatsApp Blind-First — clasificación de intenciones de NAVEGACIÓN por voz.
 *
 * Garantías cubiertas:
 *  - abrir el chat de un contacto nombrado por voz, sin tocar la pantalla;
 *  - responder la última notificación;
 *  - NO robar las rutas existentes (compose-con-mensaje, "abrí el chat de X",
 *    responder al chat abierto, navegación "mandame a casa", lecturas).
 */
class WhatsAppBlindRouteTest {

    private fun openQuery(phrase: String): String? =
        (WhatsAppBlindRoute.parse(phrase) as? WhatsAppBlindIntent.OpenContactChat)?.contactQuery

    // --- Abrir chat por contacto -----------------------------------------------

    @Test
    fun openContactChatRecognizesAppWithContactForms() {
        val cases = mapOf(
            "abrí WhatsApp con Juan" to "juan",
            "abrime WhatsApp para Juan" to "juan",
            "andá a WhatsApp con la doctora" to "la doctora",
            "abrí WhatsApp con José" to "jose",
            "quiero abrir WhatsApp con mi hermana" to "mi hermana"
        )
        cases.forEach { (phrase, expected) ->
            assertEquals(expected, openQuery(phrase), "abrir-con de \"$phrase\"")
        }
    }

    @Test
    fun openContactChatRecognizesMessageVerbsWithoutText() {
        val cases = mapOf(
            "escribile a Juan" to "juan",
            "mandale un WhatsApp a Juan" to "juan",
            "respondé a Juan" to "juan",
            "contestale a Marcos" to "marcos",
            "avisale a Walter" to "walter",
            "mandale un mensaje a la abuela" to "la abuela"
        )
        cases.forEach { (phrase, expected) ->
            assertEquals(expected, openQuery(phrase), "verbo-sin-texto de \"$phrase\"")
        }
    }

    @Test
    fun openContactQueryIsNormalizedLowercaseNoAccents() {
        // contactQuery es una clave de búsqueda, no texto para mostrar.
        assertEquals("jose", openQuery("escribile a José"))
    }

    // --- Responder la última notificación --------------------------------------

    @Test
    fun replyToLastNotificationRecognized() {
        val phrases = listOf(
            "respondé el último WhatsApp",
            "contestá el último mensaje",
            "respondé el último",
            "respondé a quien me escribió",
            "contestá al que me escribió",
            "respondé el mensaje nuevo"
        )
        phrases.forEach { phrase ->
            assertEquals(
                WhatsAppBlindIntent.ReplyToLastNotification,
                WhatsAppBlindRoute.parse(phrase),
                "responder-último de \"$phrase\""
            )
        }
    }

    // --- NO robar rutas existentes ---------------------------------------------

    @Test
    fun composeWithMessageDefersToSmartCompose() {
        // "... que ..." y ":" son compose-con-texto: no son abrir.
        listOf(
            "decile a Juan que estoy llegando",
            "mandale a Walter que llego en diez",
            "escribile a mi novia que ya salí",
            "Escribile a Juan: estoy llegando",
            "mandá este mensaje a Juan: ya salí",
            "mandale a Marco con el texto estoy llegando"
        ).forEach { phrase ->
            assertNull(WhatsAppBlindRoute.parse(phrase), "compose no robado: \"$phrase\"")
        }
    }

    @Test
    fun openChatByNameNounDefersToScreenIntelligence() {
        // El sustantivo "chat"/"conversación" lo cubre ScreenIntelligence.
        listOf(
            "abrí el chat de Juan",
            "abrí chat con Juan",
            "mostrame la conversación de Marco"
        ).forEach { phrase ->
            assertNull(WhatsAppBlindRoute.parse(phrase), "open-chat-noun no robado: \"$phrase\"")
        }
    }

    @Test
    fun genericOpenAndNavigationNotClaimed() {
        listOf(
            "abrí WhatsApp",
            "abrí WhatsApp business",
            "mandame a casa",
            "llevame a la farmacia",
            "respondé estoy llegando",
            "respondé que ya salí"
        ).forEach { phrase ->
            assertNull(WhatsAppBlindRoute.parse(phrase), "no debería reclamar: \"$phrase\"")
        }
    }

    @Test
    fun notificationReadQueriesNotClaimed() {
        // Estas son consultas de lectura (WhatsAppNotificationQueryPhrases).
        listOf(
            "quién me escribió",
            "tengo mensajes nuevos",
            "leeme las notificaciones de WhatsApp"
        ).forEach { phrase ->
            assertNull(WhatsAppBlindRoute.parse(phrase), "read-query no robado: \"$phrase\"")
        }
    }

    @Test
    fun blankAndJunkReturnsNull() {
        listOf("", "   ", "abrí WhatsApp con", "escribile a").forEach { phrase ->
            assertNull(WhatsAppBlindRoute.parse(phrase), "junk: \"$phrase\"")
        }
    }

    @Test
    fun openContactQueryNeverExceedsCap() {
        // Frase larga sin separador de mensaje: si parece un nombre, se acota;
        // si tiene demasiados tokens, no se reclama (lo maneja otra ruta).
        val parsed = WhatsAppBlindRoute.parse("escribile a uno dos tres cuatro cinco")
        assertNull(parsed, "más de 4 tokens no es un nombre de contacto")
    }

    @Test
    fun intentTypesAreDisjoint() {
        val open = WhatsAppBlindRoute.parse("abrí WhatsApp con Juan")
        val reply = WhatsAppBlindRoute.parse("respondé el último WhatsApp")
        assertTrue(open is WhatsAppBlindIntent.OpenContactChat)
        assertTrue(reply is WhatsAppBlindIntent.ReplyToLastNotification)
    }
}
