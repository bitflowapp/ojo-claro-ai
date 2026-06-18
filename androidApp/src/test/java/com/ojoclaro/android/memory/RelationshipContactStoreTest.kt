package com.ojoclaro.android.memory

import com.ojoclaro.android.test.FakeSharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Store de contactos por relación. Números SINTÉTICOS únicamente; jamás un
 * número real ni en logs.
 */
class RelationshipContactStoreTest {

    private fun store() = RelationshipContactStore(FakeSharedPreferences())

    @Test
    fun linkAndResolve() {
        val s = store()
        val linked = s.link("pareja", "Mi pareja", "+5491150000000", source = "voice_link")!!
        assertEquals("pareja", linked.key)
        assertEquals("0000", linked.phoneEnding)
        val r = s.resolve("pareja")!!
        assertEquals("+5491150000000", r.phoneE164)
        assertEquals("Mi pareja", r.label)
        assertTrue(s.isLinked("pareja"))
    }

    @Test
    fun resolveAfterAliasNormalizationFindsSameContact() {
        val s = store()
        s.link("pareja", "Mi pareja", "+5491150000000")
        // "mi novia" y "mi pareja" → clave "pareja" → mismo contacto.
        val key1 = com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppRelationshipAlias.canonicalKey("mi novia")!!
        val key2 = com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppRelationshipAlias.canonicalKey("mi pareja")!!
        assertEquals("0000", s.resolve(key1)!!.phoneEnding)
        assertEquals("0000", s.resolve(key2)!!.phoneEnding)
    }

    @Test
    fun unlinkedResolvesNull() {
        assertNull(store().resolve("pareja"))
        assertFalse(store().isLinked("pareja"))
    }

    @Test
    fun invalidNumberIsRejected() {
        assertNull(store().link("pareja", "x", "12"))     // too short
        assertNull(store().link("pareja", "x", "abc"))    // not a number
        assertNull(store().link("", "x", "+5491150000000")) // blank key
    }

    @Test
    fun forgetAndClearAll() {
        val s = store()
        s.link("pareja", "P", "+5491150000000")
        s.link("qa", "Q", "+5490000001234")
        s.forget("pareja")
        assertNull(s.resolve("pareja"))
        assertTrue(s.isLinked("qa"))
        s.clearAll()
        assertNull(s.resolve("qa"))
    }

    @Test
    fun redactedLogHasNoFullNumber() {
        val s = store()
        val c = s.link("pareja", "Mi pareja", "+5491150000000")!!
        val log = c.redactedForLog()
        assertFalse(log.contains("0000000000"), "full number in log")
        assertFalse(log.contains("+5491150000000"), "full number in log")
        assertTrue(log.contains("phoneLen="))
        assertTrue(log.contains("source="))
        assertTrue(log.contains("key=pareja"))
    }

    @Test
    fun toStringIsRedactedAndNeverLeaksFullNumber() {
        val c = store().link("pareja", "Mi pareja", "+5491150000000")!!
        val s = c.toString()
        // Nunca el número completo ni el fragmento largo del local.
        assertFalse(s.contains("+5491150000000"), "toString leaked full number")
        assertFalse(s.contains("0000000000"), "toString leaked local fragment")
        // Sólo metadata segura.
        assertTrue(s.contains("canonicalKey=pareja"))
        assertTrue(s.contains("aliasGroup=pareja"))
        assertTrue(s.contains("phoneLen="))
        assertTrue(s.contains("source="))
        assertTrue(s.contains("labelLen="))
    }

    @Test
    fun deepLinkDestinationUsesRuntimeNumberButLogsRedacted() {
        // El número completo se usa SÓLO para resolver el destino en runtime; el
        // destino que viaja a logs/voz expone únicamente los últimos 4 dígitos.
        val rel = store().link("pareja", "Mi pareja", "+5491150000000")!!
        val destination = com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestination.of(
            source = com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationSource.CONTACT,
            confidence = com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationConfidence.HIGH,
            label = rel.label,
            phoneE164 = rel.phoneE164
        )
        assertEquals("0000", destination.phoneEnding)
        val log = destination.redactedForLog()
        assertFalse(log.contains("0000000000"), "full number in destination log")
        assertFalse(log.contains("0000"), "destination log must not carry even the ending digits")
        assertTrue(log.contains("hasEnding=true"))
    }
}
