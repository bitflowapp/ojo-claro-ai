package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V2.2 — resolución segura de contactos. PURO. NO ejecutado (disco C: crítico;
 * ver docs/V22_CONTACT_RESOLVER_REPORT.md).
 */
class ContactResolverTest {

    private fun wa(name: String, i: Int) =
        ContactCandidate(name, index = i, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT)

    private val waChats = listOf(wa("Marco Luna", 0), wa("Sofía", 1), wa("Juan Pérez", 2))

    @Test
    fun exactMatchResolves() {
        val r = ContactResolver.resolve("Marco Luna", TargetApp.WHATSAPP, waChats)
        assertTrue(r is ContactResolution.Resolved)
        assertEquals("Marco Luna", (r as ContactResolution.Resolved).match.name)
        assertEquals(ResolutionSource.EXACT_VISIBLE, r.match.source)
    }

    @Test
    fun partialFirstNameResolves() {
        val r = ContactResolver.resolve("Marco", TargetApp.WHATSAPP, waChats)
        assertTrue(r is ContactResolution.Resolved, "got $r")
        assertEquals("Marco Luna", (r as ContactResolution.Resolved).match.name)
    }

    @Test
    fun sofiMatchesSofia() {
        val r = ContactResolver.resolve("sofi", TargetApp.WHATSAPP, waChats)
        assertTrue(r is ContactResolution.Resolved, "got $r")
        assertEquals("Sofía", (r as ContactResolution.Resolved).match.name)
    }

    @Test
    fun multipleMarcosAreAmbiguous() {
        val chats = listOf(wa("Marco Luna", 0), wa("Marco Pérez", 1))
        val r = ContactResolver.resolve("Marco", TargetApp.WHATSAPP, chats)
        assertTrue(r is ContactResolution.Ambiguous, "got $r")
        assertEquals(2, (r as ContactResolution.Ambiguous).candidates.size)
    }

    @Test
    fun fullNamePicksTheRightMarco() {
        val chats = listOf(wa("Marco Luna", 0), wa("Marco Pérez", 1))
        val r = ContactResolver.resolve("Marco Luna", TargetApp.WHATSAPP, chats)
        assertTrue(r is ContactResolution.Resolved, "got $r")
        assertEquals("Marco Luna", (r as ContactResolution.Resolved).match.name)
    }

    @Test
    fun unknownContactIsNotFound() {
        val r = ContactResolver.resolve("Pedro", TargetApp.WHATSAPP, waChats)
        assertTrue(r is ContactResolution.NotFound, "got $r")
    }

    @Test
    fun authorizedPhoneFallbackForMarcoLuna() {
        val aliases = listOf(ContactAlias("marco luna", "Marco Luna", TargetApp.WHATSAPP, "0000005678"))
        val r = ContactResolver.resolve("Marco Luna", TargetApp.WHATSAPP, emptyList(), aliases)
        assertTrue(r is ContactResolution.Resolved, "got $r")
        assertEquals(ResolutionSource.PHONE_FALLBACK, (r as ContactResolution.Resolved).match.source)
        assertEquals("0000005678", r.match.phoneFallback)
    }

    @Test
    fun sensitiveQueryIsUnsafe() {
        val r = ContactResolver.resolve("mi clave 12345678", TargetApp.WHATSAPP, waChats)
        assertTrue(r is ContactResolution.Unsafe, "got $r")
    }

    @Test
    fun whatsappAndInstagramAreSeparate() {
        val mixed = listOf(
            ContactCandidate("Sofi", index = 0, app = TargetApp.INSTAGRAM, kind = CandidateKind.CHAT),
            ContactCandidate("Marco Luna", index = 1, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT),
        )
        val ig = ContactResolver.resolve("Sofi", TargetApp.INSTAGRAM, mixed)
        assertTrue(ig is ContactResolution.Resolved, "ig got $ig")
        assertEquals("Sofi", (ig as ContactResolution.Resolved).match.name)
        // "Sofi" no debe resolver contra contactos de WhatsApp.
        val wa = ContactResolver.resolve("Sofi", TargetApp.WHATSAPP, mixed)
        assertTrue(wa is ContactResolution.NotFound, "wa got $wa")
    }

    @Test
    fun neverResolvesToActionButton() {
        val withButton = listOf(
            ContactCandidate("Enviar", index = 0, app = TargetApp.WHATSAPP, kind = CandidateKind.BUTTON),
            ContactCandidate("Marco Luna", index = 1, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT),
        )
        val r = ContactResolver.resolve("enviar", TargetApp.WHATSAPP, withButton)
        // "enviar" es un botón de acción, no un contacto → no debe resolver a él.
        assertTrue(r is ContactResolution.NotFound || r is ContactResolution.Unsafe, "got $r")
    }
}
