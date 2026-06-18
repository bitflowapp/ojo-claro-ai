package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.intelligence.ScreenIntent
import com.ojoclaro.android.agent.intelligence.ScreenIntelligencePhrases
import com.ojoclaro.android.agent.intelligence.TargetApp
import com.ojoclaro.android.agent.runtime.conversation.ConversationGate
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Hotfix routing post-QA: buscá/encontrá→abrí (mismo flujo seguro) y "qué dice ahí"→lectura local. */
class EstelaColloquialNormalizerTest {

    private fun norm(s: String) = EstelaColloquialNormalizer.normalize(s)

    // ---- BUG 1: "buscá/encontrá el chat de X" = misma acción que "abrí el chat de X" ----

    @Test
    fun buscarYEncontrarChatSeCanonizanAAbrir() {
        assertEquals("abri el chat de sofi", norm("buscá el chat de Sofi"))
        assertEquals("abri el chat de sofi", norm("encontrá el chat de Sofi"))
        assertEquals("abri el chat de sofi", norm("buscar el chat de sofi"))
        assertEquals("abri la conversacion con juan", norm("buscá la conversación con Juan"))
        assertEquals("abri el chat de mama", norm("buscame el chat de mamá"))
    }

    @Test
    fun buscarChatMapeaALaMismaAccionLocalQueAbrir() {
        val viaBusca = ScreenIntelligencePhrases.parse(norm("buscá el chat de Sofi"))
        val viaEncontra = ScreenIntelligencePhrases.parse(norm("encontrá el chat de Sofi"))
        val viaAbri = ScreenIntelligencePhrases.parse(norm("abrí el chat de Sofi"))
        assertIs<ScreenIntent.OpenChat>(viaBusca)
        assertIs<ScreenIntent.OpenChat>(viaAbri)
        assertEquals("sofi", viaBusca.rawName)
        assertEquals(viaAbri, viaBusca, "buscá debe resolver a la MISMA acción que abrí")
        assertEquals(viaAbri, viaEncontra, "encontrá debe resolver a la MISMA acción que abrí")
    }

    @Test
    fun busquedaDeLugarNoSeReescribeComoChat() {
        // "buscá la farmacia" es búsqueda de lugar (Outdoor), NO apertura de chat.
        assertFalse(norm("buscá la farmacia").startsWith("abri"))
        assertFalse(norm("buscá un restaurante").startsWith("abri"))
    }

    @Test
    fun abrirWhatsAppDeContactoSeCanonizaAlMismoChatSeguro() {
        val canonical = "abri el chat de marco luna en whatsapp"
        listOf(
            "abrir el WhatsApp de Marco Luna",
            "abrí el WhatsApp de Marco Luna",
            "abrime el WhatsApp de Marco Luna",
            "abrí el wsp de Marco Luna",
            "abrí el wp de Marco Luna",
            "abrí WhatsApp de Marco Luna",
            "quiero abrir el WhatsApp de Marco Luna",
            "andá al WhatsApp de Marco Luna",
            "entrar al WhatsApp de Marco Luna"
        ).forEach { phrase ->
            assertEquals(canonical, norm(phrase), "debe canonizar: \"$phrase\"")
            val intent = ScreenIntelligencePhrases.parse(norm(phrase))
            assertIs<ScreenIntent.OpenChat>(intent)
            assertEquals("marco luna", intent.rawName)
            assertEquals(TargetApp.WHATSAPP, intent.app)
        }
    }

    // ---- BUG 2: "qué dice ahí" = lectura local de pantalla, NUNCA LLM ----

    @Test
    fun queDiceAhiEsLecturaLocalDePantalla() {
        listOf("qué dice ahí", "qué dice", "qué dice acá", "qué pone ahí", "leé eso").forEach {
            assertEquals("lee la pantalla", norm(it), "lectura contextual: \"$it\"")
        }
        // "lee la pantalla" lo resuelve handleGlobalScreenQuery (antes del router LLM)…
        assertNotNull(ScreenQueryPhrases.classify("lee la pantalla"))
        // …y jamás es charla conversacional.
        assertFalse(ConversationGate.isConversational("lee la pantalla"))
    }

    // ---- Seguridad: no se relajó nada peligroso ----

    @Test
    fun accionesPeligrosasSiguenBloqueadasAntesDelLlm() {
        // "tocá el botón enviar" sigue siendo acción crítica (guard antes del LLM); no es charla.
        assertTrue(WhatsAppCriticalGuard.isCritical("tocá el botón enviar"))
        assertFalse(ConversationGate.isConversational("tocá el botón enviar"))
        // el normalizer no la convierte en algo inocuo (sigue conteniendo "enviar").
        assertTrue(norm("tocá el botón enviar").contains("enviar"))
    }
}
