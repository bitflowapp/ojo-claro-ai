package com.ojoclaro.android.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppNotificationQueryPhrasesTest {

    @Test
    fun matchesTargetQueries() {
        listOf(
            "¿Tengo mensajes nuevos de WhatsApp?",
            "tengo mensajes nuevos de whatsapp",
            "¿Quién me escribió?",
            "quien me escribio",
            "Leeme las últimas notificaciones de WhatsApp.",
            "leeme las notificaciones de whatsapp",
            "Qué llegó de WhatsApp",
            "que llego de whatsapp",
            "Leeme mensajes nuevos",
            "Hay mensajes nuevos?",
            "hay mensajes nuevos",
            "tenés mensajes recientes?"
        ).forEach {
            assertTrue(WhatsAppNotificationQueryPhrases.isNotificationQuery(it), "query: $it")
        }
    }

    @Test
    fun doesNotStealVisibleScreenReadPhrases() {
        // Lectura de la pantalla VISIBLE / lista de chats: su propia ruta.
        listOf(
            "leeme los mensajes",
            "leé los chats",
            "qué mensajes me llegaron",
            "leeme este chat",
            "qué dice la pantalla"
        ).forEach {
            assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery(it), "not-query: $it")
        }
    }

    @Test
    fun doesNotStealComposeOrActionPhrases() {
        // "quién me escribió" entra; "escribile/mandale a X que ..." NO (compose).
        listOf(
            "mandale a Juan que tengo mensajes nuevos",
            "escribile a Ana que hay mensajes nuevos",
            "abrí WhatsApp",
            "abrí el chat de Marco",
            "respondele que ya salgo",
            "llamá a mamá"
        ).forEach {
            assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery(it), "action: $it")
        }
    }

    @Test
    fun ambiguousNotificationWordNeedsWhatsAppOrReadVerb() {
        // "notificacion" sola, sin WhatsApp ni verbo de lectura, no matchea.
        assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery("activá las notificaciones"))
        assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery("silenciá las notificaciones"))
        // Con verbo de lectura sí.
        assertTrue(WhatsAppNotificationQueryPhrases.isNotificationQuery("leeme las notificaciones"))
    }

    @Test
    fun blankIsNotQuery() {
        assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery(""))
        assertFalse(WhatsAppNotificationQueryPhrases.isNotificationQuery("   "))
    }
}
