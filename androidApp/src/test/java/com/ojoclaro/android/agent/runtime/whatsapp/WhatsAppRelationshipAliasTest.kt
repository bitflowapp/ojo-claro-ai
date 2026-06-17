package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppRelationshipAliasTest {

    @Test
    fun aliasesOfSameRelationCollapseToOneKey() {
        listOf("mi novia", "novia", "mi pareja", "pareja", "mi mujer", "mi esposa")
            .forEach { assertEquals("pareja", WhatsAppRelationshipAlias.canonicalKey(it), it) }
        listOf("mi contacto de prueba", "contacto de prueba", "mi qa", "contacto qa")
            .forEach { assertEquals("qa", WhatsAppRelationshipAlias.canonicalKey(it), it) }
        assertEquals("mama", WhatsAppRelationshipAlias.canonicalKey("mi mamá"))
    }

    @Test
    fun nonRelationshipReturnsNull() {
        listOf("juan", "abrí WhatsApp", "leeme los chats", "", "mi jefe el gerente")
            .forEach { assertNull(WhatsAppRelationshipAlias.canonicalKey(it), it) }
    }

    @Test
    fun parsesLinkRequests() {
        assertEquals("pareja", WhatsAppRelationshipAlias.parseLinkRequest("este contacto es mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseLinkRequest("esta persona es mi pareja"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseLinkRequest("ella es mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseLinkRequest("guardá a mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseLinkRequest("marcá a este contacto como mi pareja"))
        assertEquals("qa", WhatsAppRelationshipAlias.parseLinkRequest("este contacto es mi contacto de prueba"))
    }

    @Test
    fun doesNotTreatOpenOrReadAsLink() {
        // "abrí el chat de mi novia" es OPEN, no vinculación.
        assertNull(WhatsAppRelationshipAlias.parseLinkRequest("abrí WhatsApp con mi novia"))
        assertNull(WhatsAppRelationshipAlias.parseLinkRequest("abrí el chat de mi novia"))
        assertNull(WhatsAppRelationshipAlias.parseLinkRequest("este contacto es importante"))
        assertNull(WhatsAppRelationshipAlias.parseLinkRequest("leeme los mensajes"))
    }

    @Test
    fun spokenLabelsAreSafe() {
        assertTrue(WhatsAppRelationshipAlias.spokenLabel("pareja").contains("pareja"))
        assertTrue(WhatsAppRelationshipAlias.spokenLabel("qa").isNotBlank())
    }

    @Test
    fun parsesForgetRequests() {
        assertEquals("pareja", WhatsAppRelationshipAlias.parseForgetRequest("olvidá a mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseForgetRequest("olvidate de mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseForgetRequest("ya no es mi novia"))
        assertEquals("pareja", WhatsAppRelationshipAlias.parseForgetRequest("desvinculá a mi pareja"))
        assertEquals("qa", WhatsAppRelationshipAlias.parseForgetRequest("borrá el contacto de mi contacto de prueba"))
    }

    @Test
    fun forgetIgnoresNonRelationshipAndOtherIntents() {
        // No roba frases que no nombran una relación conocida.
        assertNull(WhatsAppRelationshipAlias.parseForgetRequest("olvidá lo que dije"))
        assertNull(WhatsAppRelationshipAlias.parseForgetRequest("borrá los mensajes"))
        assertNull(WhatsAppRelationshipAlias.parseForgetRequest("este contacto es mi novia"))
        assertNull(WhatsAppRelationshipAlias.parseForgetRequest("abrí WhatsApp con mi novia"))
    }
}
