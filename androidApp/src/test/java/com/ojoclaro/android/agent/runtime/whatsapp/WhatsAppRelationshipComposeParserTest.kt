package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppRelationshipComposeParserTest {

    @Test
    fun parsesRelationshipComposeWithQue() {
        val r = WhatsAppRelationshipComposeParser.parse("mandale a mi novia que estoy llegando")!!
        assertEquals("pareja", r.key)
        assertEquals("estoy llegando", r.message)
    }

    @Test
    fun parsesWithoutQueSeparator() {
        val r = WhatsAppRelationshipComposeParser.parse("decile a mi novia estoy llegando")!!
        assertEquals("pareja", r.key)
        assertEquals("estoy llegando", r.message)
    }

    @Test
    fun parsesEscribileToPareja() {
        val r = WhatsAppRelationshipComposeParser.parse("escribile a mi pareja que estoy llegando")!!
        assertEquals("pareja", r.key)
        assertEquals("estoy llegando", r.message)
    }

    @Test
    fun parsesReplyVerbToRelationship() {
        val r = WhatsAppRelationshipComposeParser.parse("respondé a mi novia que ahora voy")!!
        assertEquals("pareja", r.key)
        assertEquals("ahora voy", r.message)
    }

    @Test
    fun parsesMultiWordRelationship() {
        val r = WhatsAppRelationshipComposeParser
            .parse("mandale a mi contacto de prueba que Estela prueba controlada")!!
        assertEquals("qa", r.key)
        assertEquals("estela prueba controlada", r.message)
    }

    @Test
    fun nonRelationshipRecipientIsNotClaimed() {
        // "juan" no es relación → null → lo toma el smart compose genérico.
        assertNull(WhatsAppRelationshipComposeParser.parse("mandale a juan que estoy llegando"))
    }

    @Test
    fun relationshipWithoutMessageIsNotCompose() {
        // Sin mensaje no es compose: lo toma el flujo de apertura (que no escribe).
        assertNull(WhatsAppRelationshipComposeParser.parse("abrí WhatsApp con mi novia"))
        assertNull(WhatsAppRelationshipComposeParser.parse("mandale a mi novia"))
    }

    @Test
    fun openSenderPhrasesAreNotClaimed() {
        assertNull(WhatsAppRelationshipComposeParser.parse("respondé el último WhatsApp"))
        assertNull(WhatsAppRelationshipComposeParser.parse("respondé a quien me escribió"))
    }
}
