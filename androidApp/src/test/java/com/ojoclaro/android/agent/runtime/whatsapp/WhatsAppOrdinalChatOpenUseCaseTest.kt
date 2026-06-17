package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppOrdinalChatOpenUseCaseTest {

    private class FakeOpener(
        private val result: VisibleChatOpenResult
    ) : VisibleChatOpener {
        var openedName: String? = null
        var calls = 0
        override fun open(targetName: String): VisibleChatOpenResult {
            calls++
            openedName = targetName
            return result
        }
    }

    private fun useCase(
        snapshot: ScreenSnapshot?,
        opener: FakeOpener,
        ready: Boolean = true
    ) = WhatsAppOrdinalChatOpenUseCase(
        provider = ScreenContextProvider { snapshot },
        opener = opener,
        isAccessibilityReady = { ready }
    )

    private fun name(n: String) = ScreenElement(n, ScreenElementRole.TEXT, isInteractive = false)

    private fun chatList(names: List<String>) = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "Chats",
        elements = names.map { name(it) },
        capturedAtMillis = 0L
    )

    private fun insideChat() = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
        ),
        capturedAtMillis = 0L
    )

    // --- Paso 1: handle() resuelve pero NUNCA abre ---

    @Test
    fun comandoNoOrdinalNoAbreNada() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("x"))
        val r = useCase(chatList(listOf("Marco")), opener).handle("qué chats ves")
        assertEquals(WhatsAppOrdinalChatResponse.NotAnOrdinalCommand, r)
        assertEquals(0, opener.calls)
    }

    @Test
    fun accesibilidadApagadaGuiaActivarla() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("x"))
        val r = useCase(null, opener, ready = false).handle("abrí el primer chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.NeedsAccessibilityService)
        assertEquals(0, opener.calls)
    }

    @Test
    fun fueraDeWhatsAppLoDice() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("x"))
        val notes = ScreenSnapshot("com.example.notes", "Notas", emptyList(), 0L)
        val r = useCase(notes, opener).handle("abrí el primer chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.NotInWhatsApp)
        assertEquals(0, opener.calls)
    }

    @Test
    fun dentroDeUnChatPideVolverALaLista() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("x"))
        val r = useCase(insideChat(), opener).handle("abrí el primer chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.AlreadyInChat)
        assertEquals(0, opener.calls)
    }

    @Test
    fun numeroFueraDeRangoNoAbreAlAzar() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("x"))
        val r = useCase(chatList(listOf("Marco", "Sofi")), opener).handle("abrí el tercer chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.OutOfRange)
        assertEquals(0, opener.calls)
    }

    @Test
    fun primerChatPideConfirmacionSinAbrir() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("Marco"))
        val r = useCase(chatList(listOf("Marco", "Sofi", "Mamá")), opener).handle("abrí el primer chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.NeedsConfirmation)
        val confirm = r as WhatsAppOrdinalChatResponse.NeedsConfirmation
        assertEquals("Marco", confirm.displayName)
        assertEquals(0, confirm.index)
        assertTrue(confirm.spokenText.contains("Marco"))
        assertTrue(confirm.spokenText.contains("abra", ignoreCase = true))
        // Clave: NO abrió nada todavía.
        assertEquals(0, opener.calls)
    }

    @Test
    fun segundoChatResuelveElSegundoNombre() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("Sofi"))
        val r = useCase(chatList(listOf("Marco", "Sofi", "Mamá")), opener).handle("abrí el segundo chat")
        assertTrue(r is WhatsAppOrdinalChatResponse.NeedsConfirmation)
        assertEquals("Sofi", (r as WhatsAppOrdinalChatResponse.NeedsConfirmation).displayName)
        assertEquals(0, opener.calls)
    }

    // --- Paso 2: confirmOpen() ejecuta el click seguro ---

    @Test
    fun confirmOpenAbreElChatResuelto() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("Marco"))
        val r = useCase(chatList(listOf("Marco")), opener).confirmOpen("Marco")
        assertTrue(r is WhatsAppOrdinalChatResponse.Opened)
        assertEquals("Marco", opener.openedName)
        assertEquals(1, opener.calls)
    }

    @Test
    fun confirmOpenClickInseguroDevuelveCouldNotOpen() {
        val opener = FakeOpener(VisibleChatOpenResult.Unsafe("Marco", "sensitive"))
        val r = useCase(chatList(listOf("Marco")), opener).confirmOpen("Marco")
        assertTrue(r is WhatsAppOrdinalChatResponse.CouldNotOpen)
    }

    @Test
    fun confirmOpenSinAccesibilidadGuiaActivarla() {
        val opener = FakeOpener(VisibleChatOpenResult.Opened("Marco"))
        val r = useCase(chatList(listOf("Marco")), opener, ready = false).confirmOpen("Marco")
        assertTrue(r is WhatsAppOrdinalChatResponse.NeedsAccessibilityService)
        assertEquals(0, opener.calls)
    }
}
