package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppVisibleMessagesReaderTest {

    private fun reader(
        snapshot: ScreenSnapshot?,
        isReady: Boolean = true,
        now: Long = 0L
    ): WhatsAppVisibleMessagesReader {
        val provider = ScreenContextProvider { snapshot }
        return WhatsAppVisibleMessagesReader(
            provider = provider,
            isAccessibilityReady = { isReady },
            clock = { now }
        )
    }

    private fun msg(text: String) = ScreenElement(text, ScreenElementRole.TEXT, isInteractive = false)

    /** Pantalla DENTRO de un chat: campo mensaje + botones + textos de mensajes. */
    private fun insideChatSnapshot(
        messages: List<String> = listOf("Sofi: hola, ¿cómo estás?", "Yo: bien, ¿y vos?"),
        capturedAtMillis: Long = 0L
    ) = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
        ) + messages.map { msg(it) },
        capturedAtMillis = capturedAtMillis
    )

    @Test
    fun comandoNoDeMensajesNoSeConsume() {
        val r = reader(insideChatSnapshot()).handle("abrí WhatsApp")
        assertEquals(WhatsAppMessagesResponse.NotAMessageCommand, r)
    }

    @Test
    fun accesibilidadApagadaGuiaActivarla() {
        val r = reader(snapshot = null, isReady = false).handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.NeedsAccessibilityService)
    }

    @Test
    fun fueraDeWhatsAppLoDice() {
        val notes = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "Notas",
            elements = listOf(msg("una nota cualquiera")),
            capturedAtMillis = 0L
        )
        val r = reader(notes).handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.NotInWhatsApp)
    }

    @Test
    fun enListaDeChatsPideEntrarAUnChat() {
        val chatList = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Chats",
            elements = listOf(msg("Marco"), msg("Sofi"), msg("Mamá")),
            capturedAtMillis = 0L
        )
        val r = reader(chatList).handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.NotInChat)
    }

    @Test
    fun dentroDeChatLeeLosMensajesVisibles() {
        val r = reader(insideChatSnapshot()).handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.Read)
        val read = r as WhatsAppMessagesResponse.Read
        assertTrue(read.messages.any { it.contains("hola", ignoreCase = true) })
        assertTrue(read.spokenText.contains("hola", ignoreCase = true))
        // No incluye botones de UI.
        assertFalse(read.spokenText.contains("Enviar"))
        assertFalse(read.spokenText.contains("Cámara"))
    }

    @Test
    fun modoUltimoLeeSoloElUltimoMensaje() {
        val r = reader(
            insideChatSnapshot(messages = listOf("primero", "segundo", "el último de todos"))
        ).handle("último mensaje")
        assertTrue(r is WhatsAppMessagesResponse.Read)
        val read = r as WhatsAppMessagesResponse.Read
        assertEquals(1, read.messages.size)
        assertEquals("el último de todos", read.messages.first())
        assertFalse(read.spokenText.contains("primero"))
    }

    @Test
    fun filtraHorasYNumerosSueltos() {
        val r = reader(
            insideChatSnapshot(messages = listOf("12:45", "1234", "nos vemos mañana"))
        ).handle("leé los mensajes")
        assertTrue(r is WhatsAppMessagesResponse.Read)
        val read = r as WhatsAppMessagesResponse.Read
        assertTrue(read.messages.contains("nos vemos mañana"))
        assertFalse(read.messages.contains("12:45"))
        assertFalse(read.messages.contains("1234"))
    }

    @Test
    fun dentroDeChatSinMensajesLoDiceHonestamente() {
        val r = reader(insideChatSnapshot(messages = emptyList())).handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.NoMessages)
    }

    @Test
    fun snapshotViejoSeRechaza() {
        // capturedAt=0, clock muy adelante => stale (> 5s por defecto).
        val r = reader(insideChatSnapshot(capturedAtMillis = 0L), now = 10_000L)
            .handle("qué mensajes hay")
        assertTrue(r is WhatsAppMessagesResponse.StaleSnapshot)
    }
}
