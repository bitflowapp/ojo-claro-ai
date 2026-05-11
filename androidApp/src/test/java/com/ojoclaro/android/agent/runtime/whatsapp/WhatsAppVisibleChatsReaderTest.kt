package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppVisibleChatsReaderTest {

    private fun reader(
        snapshot: ScreenSnapshot? = null,
        isReady: Boolean = true,
        throwOnRead: Boolean = false
    ): WhatsAppVisibleChatsReader {
        val provider = ScreenContextProvider {
            if (throwOnRead) error("boom")
            snapshot
        }
        return WhatsAppVisibleChatsReader(
            provider = provider,
            isAccessibilityReady = { isReady }
        )
    }

    private fun textName(name: String) = ScreenElement(
        label = name,
        role = ScreenElementRole.TEXT,
        isInteractive = false
    )

    private fun chatListSnapshot(
        packageName: String? = "com.whatsapp",
        names: List<String> = listOf("Marco", "Sofi", "Mamá")
    ) = ScreenSnapshot(
        packageName = packageName,
        text = "Chats",
        elements = names.map { textName(it) },
        capturedAtMillis = 0L
    )

    private fun insideChatSnapshot() = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true),
            // Mensajes "privados" del chat — deben ser IGNORADOS por el reader.
            textName("Sofi: hola, ¿cómo estás?"),
            textName("Yo: bien, ¿y vos?")
        ),
        capturedAtMillis = 0L
    )

    @Test
    fun unrelatedTextIsNotConsumed() {
        val r = reader(snapshot = chatListSnapshot()).handle("abrí WhatsApp")
        assertEquals(WhatsAppChatListResponse.NotAChatListCommand, r)
    }

    @Test
    fun repeatLastIsNotConsumed() {
        val r = reader(snapshot = chatListSnapshot()).handle("repetí")
        assertEquals(WhatsAppChatListResponse.NotAChatListCommand, r)
    }

    @Test
    fun screenUnderstandingPhrasesAreNotConsumed() {
        listOf(
            "qué hay en pantalla",
            "resumí la pantalla",
            "dónde estoy",
            "qué puedo hacer acá",
            "leeme lo importante"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppChatListResponse.NotAChatListCommand,
                reader(snapshot = chatListSnapshot()).handle(phrase),
                "phrase '$phrase' must not be consumed by chat list reader"
            )
        }
    }

    @Test
    fun whatsAppGuidedPhrasesAreNotConsumed() {
        listOf(
            "estoy en WhatsApp",
            "cómo mando una foto",
            "cómo mando ubicación",
            "cómo le mando un mensaje"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppChatListResponse.NotAChatListCommand,
                reader(snapshot = chatListSnapshot()).handle(phrase)
            )
        }
    }

    @Test
    fun legacyWhatsAppActionsAreNotConsumed() {
        listOf(
            "abrí WhatsApp",
            "mandale a Marco",
            "mandale un mensaje a Marco",
            "abrí el chat de Marco",
            "llamá a Marco"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppChatListResponse.NotAChatListCommand,
                reader(snapshot = chatListSnapshot()).handle(phrase),
                "legacy WhatsApp phrase '$phrase' must not be consumed by chat list reader"
            )
        }
    }

    @Test
    fun controlPhrasesAreNotConsumed() {
        listOf("callate", "cancelar", "confirmar", "ayuda").forEach { phrase ->
            assertEquals(
                WhatsAppChatListResponse.NotAChatListCommand,
                reader(snapshot = chatListSnapshot()).handle(phrase)
            )
        }
    }

    @Test
    fun accessibilityServiceOffReturnsNotInWhatsApp() {
        val r = reader(snapshot = null, isReady = false).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.NotInWhatsApp)
        val ack = r as WhatsAppChatListResponse.NotInWhatsApp
        assertTrue(ack.spokenText.contains("Accesibilidad", ignoreCase = true))
    }

    @Test
    fun nullSnapshotReturnsStateNotConfident() {
        val r = reader(snapshot = null, isReady = true).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.StateNotConfident)
    }

    @Test
    fun providerThrowingReturnsStateNotConfident() {
        val r = reader(throwOnRead = true).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.StateNotConfident)
    }

    @Test
    fun notInWhatsAppAsksToOpenIt() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "Lista de tareas",
            elements = emptyList(),
            capturedAtMillis = 0L
        )
        val r = reader(snapshot = snapshot).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.NotInWhatsApp)
        val ack = r as WhatsAppChatListResponse.NotInWhatsApp
        assertTrue(ack.spokenText.contains("WhatsApp"))
    }

    @Test
    fun chatListListsVisibleNames() {
        val r = reader(snapshot = chatListSnapshot()).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.Listed)
        val listed = r as WhatsAppChatListResponse.Listed
        assertEquals(3, listed.chats.size)
        assertEquals(listOf("Marco", "Sofi", "Mamá"), listed.chats.map { it.displayName })
        assertTrue(listed.spokenText.contains("Marco"))
        assertTrue(listed.spokenText.contains("Sofi"))
        assertTrue(listed.spokenText.contains("Mamá"))
        assertTrue(listed.spokenText.contains("No leo mensajes completos"))
    }

    @Test
    fun listIsCappedAtFiveChats() {
        val r = reader(
            snapshot = chatListSnapshot(
                names = listOf("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8")
            )
        ).handle("leeme los chats")
        assertTrue(r is WhatsAppChatListResponse.Listed)
        val listed = r as WhatsAppChatListResponse.Listed
        assertEquals(5, listed.chats.size)
    }

    @Test
    fun listResponseDoesNotIncludeMessagePreviews() {
        val snapshot = chatListSnapshot(
            names = listOf(
                "Marco",
                "Sofi: estoy llegando tarde, te aviso",
                "Mamá: te llamo en un rato",
                "Hermano"
            )
        )
        val r = reader(snapshot = snapshot).handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        assertFalse(
            listed.spokenText.contains("estoy llegando", ignoreCase = true),
            "preview content leaked into spoken text"
        )
        assertFalse(
            listed.spokenText.contains("te llamo en un rato", ignoreCase = true),
            "preview content leaked into spoken text"
        )
        assertTrue(listed.spokenText.contains("Marco"))
        assertTrue(listed.spokenText.contains("Hermano"))
    }

    @Test
    fun listResponseDoesNotIncludeUiButtons() {
        val snapshot = chatListSnapshot(
            names = listOf("Marco", "Sofi")
        ).copy(
            elements = listOf(
                ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Buscar", ScreenElementRole.BUTTON, isInteractive = true),
                textName("Marco"),
                textName("Sofi")
            )
        )
        val r = reader(snapshot = snapshot).handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        assertFalse(listed.spokenText.contains("Cámara"))
        assertFalse(listed.spokenText.contains("Buscar"))
    }

    @Test
    fun listResponseDoesNotIncludeSensitiveContent() {
        val snapshot = chatListSnapshot(
            names = listOf("Marco", "CBU 1234567890", "Saldo banco", "Sofi")
        )
        val r = reader(snapshot = snapshot).handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        assertFalse(listed.spokenText.contains("CBU"))
        assertFalse(listed.spokenText.contains("Saldo"))
    }

    @Test
    fun insideChatRespondsHonestlyWithoutReadingMessages() {
        val r = reader(snapshot = insideChatSnapshot()).handle("qué chat ves")
        assertTrue(r is WhatsAppChatListResponse.InsideChat)
        val inside = r as WhatsAppChatListResponse.InsideChat
        assertTrue(inside.spokenText.contains("Estás dentro de un chat"))
        assertTrue(inside.spokenText.contains("No leo mensajes completos", ignoreCase = true))
        // Verificación crítica: NO interpola mensajes.
        assertFalse(inside.spokenText.contains("Sofi: hola"))
        assertFalse(inside.spokenText.contains("Yo: bien"))
        assertFalse(inside.spokenText.contains("Cámara"))
        assertFalse(inside.spokenText.contains("Enviar"))
    }

    @Test
    fun chatListWithOnlyOneNameUsesSingularJoin() {
        val r = reader(snapshot = chatListSnapshot(names = listOf("Marco")))
            .handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        // No debería decir "Marco y Marco" ni " y " si hay un solo chat.
        assertEquals("Veo estos chats visibles: Marco. No leo mensajes completos.", listed.spokenText)
    }

    @Test
    fun chatListWithTwoNamesJoinsWithY() {
        val r = reader(snapshot = chatListSnapshot(names = listOf("Marco", "Sofi")))
            .handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        assertTrue(listed.spokenText.contains("Marco y Sofi"))
    }

    @Test
    fun chatListWithThreeOrMoreNamesJoinsWithComaAndY() {
        val r = reader(snapshot = chatListSnapshot(names = listOf("Marco", "Sofi", "Mamá")))
            .handle("qué chats ves")
        val listed = r as WhatsAppChatListResponse.Listed
        assertTrue(listed.spokenText.contains("Marco, Sofi y Mamá"))
    }

    @Test
    fun whatsAppOpenButNoChatsVisibleReturnsNoChatsVisible() {
        // En WhatsApp, no en chat, sin elementos válidos.
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Estado",
            elements = listOf(
                ScreenElement("Buscar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val r = reader(snapshot = snapshot).handle("qué chats ves")
        assertTrue(r is WhatsAppChatListResponse.NoChatsVisible)
        val none = r as WhatsAppChatListResponse.NoChatsVisible
        assertTrue(none.spokenText.contains("No puedo confirmar"))
    }

    @Test
    fun spokenTextNeverContainsPrivateChatContentFromInsideChat() {
        // Defense in depth: probamos los 4 casos que retornan spokenText con
        // un snapshot que contiene mensajes privados.
        val snapshot = insideChatSnapshot()
        val phrases = listOf(
            "qué chats ves",
            "qué chat ves",
            "leeme los chats"
        )
        phrases.forEach { phrase ->
            val r = reader(snapshot = snapshot).handle(phrase)
            val text = when (r) {
                is WhatsAppChatListResponse.InsideChat -> r.spokenText
                is WhatsAppChatListResponse.Listed -> r.spokenText
                is WhatsAppChatListResponse.NoChatsVisible -> r.spokenText
                is WhatsAppChatListResponse.NotInWhatsApp -> r.spokenText
                is WhatsAppChatListResponse.StateNotConfident -> r.spokenText
                WhatsAppChatListResponse.NotAChatListCommand -> ""
            }
            assertFalse(
                text.contains("hola, ¿cómo estás?", ignoreCase = true),
                "leaked private message for phrase '$phrase'"
            )
            assertFalse(
                text.contains("bien, ¿y vos?", ignoreCase = true),
                "leaked private message for phrase '$phrase'"
            )
        }
    }
}
