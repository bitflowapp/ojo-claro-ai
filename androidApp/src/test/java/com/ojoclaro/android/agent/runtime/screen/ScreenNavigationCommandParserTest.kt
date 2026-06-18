package com.ojoclaro.android.agent.runtime.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenNavigationCommandParserTest {

    @Test
    fun detectaScrollDown() {
        listOf("bajá", "baja", "bajá la pantalla", "más abajo", "bajame", "scroll abajo").forEach {
            assertEquals(
                ScreenNavigationCommand.ScrollDown,
                ScreenNavigationCommandParser.parse(it),
                "'$it' debería ser ScrollDown"
            )
        }
    }

    @Test
    fun detectaScrollUp() {
        listOf("subí", "subi", "subí la pantalla", "más arriba", "subime", "scroll arriba").forEach {
            assertEquals(
                ScreenNavigationCommand.ScrollUp,
                ScreenNavigationCommandParser.parse(it),
                "'$it' debería ser ScrollUp"
            )
        }
    }

    @Test
    fun detectaBack() {
        listOf("volver", "volvé", "atrás", "ir atrás", "pantalla anterior", "back").forEach {
            assertEquals(
                ScreenNavigationCommand.Back,
                ScreenNavigationCommandParser.parse(it),
                "'$it' debería ser Back"
            )
        }
    }

    @Test
    fun detectaScrollWhatsAppNuevasFrases() {
        // Sprint WhatsApp (comando F).
        listOf("seguí leyendo", "scrolleá", "leé más abajo", "leeme más abajo").forEach {
            assertEquals(
                ScreenNavigationCommand.ScrollDown,
                ScreenNavigationCommandParser.parse(it),
                "'$it' debería ser ScrollDown"
            )
        }
        listOf("leé más arriba", "mensajes anteriores", "buscá mensajes anteriores", "scrolleá para arriba").forEach {
            assertEquals(
                ScreenNavigationCommand.ScrollUp,
                ScreenNavigationCommandParser.parse(it),
                "'$it' debería ser ScrollUp"
            )
        }
    }

    @Test
    fun volveArribaEsScrollUpNoBack() {
        // Membership exacta: "volvé arriba" => ScrollUp; "volvé" solo => Back.
        assertEquals(ScreenNavigationCommand.ScrollUp, ScreenNavigationCommandParser.parse("volvé arriba"))
        assertEquals(ScreenNavigationCommand.Back, ScreenNavigationCommandParser.parse("volvé"))
    }

    @Test
    fun noMatcheaOtrosComandos() {
        listOf(
            "abrí WhatsApp",
            "qué hay en pantalla",
            "leé los mensajes",
            "abrí el primer chat",
            "mandale a Marco",
            "hola",
            ""
        ).forEach {
            assertNull(
                ScreenNavigationCommandParser.parse(it),
                "'$it' no debería matchear navegación"
            )
        }
    }
}
