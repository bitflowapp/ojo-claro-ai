package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Blind-First — las respuestas de recuperación son cortas, tranquilas
 * y nunca filtran un número de teléfono.
 */
class WhatsAppBlindRouteNarratorTest {

    private fun allReassuranceLines(): List<String> = listOf(
        WhatsAppBlindRouteNarrator.contactNotFound("Juan"),
        WhatsAppBlindRouteNarrator.unsafeQuery(),
        WhatsAppBlindRouteNarrator.openedButNotInChat("Juan"),
        WhatsAppBlindRouteNarrator.couldNotOpen(),
        WhatsAppBlindRouteNarrator.openedChat("Juan")
    )

    @Test
    fun reassuranceLinesPromiseNothingWritten() {
        allReassuranceLines().forEach { line ->
            assertTrue(line.contains("No escribí nada"), "falta tranquilidad en: $line")
        }
    }

    @Test
    fun linesAreShortAndNonBlank() {
        val all = allReassuranceLines() + listOf(
            WhatsAppBlindRouteNarrator.notInstalled(),
            WhatsAppBlindRouteNarrator.multipleContacts(listOf("Juan", "Juana")),
            WhatsAppBlindRouteNarrator.noNotifications(),
            WhatsAppBlindRouteNarrator.notificationHidden(),
            WhatsAppBlindRouteNarrator.notificationSenderUnresolved("Sofía")
        )
        all.forEach { line ->
            assertTrue(line.isNotBlank(), "línea en blanco")
            assertTrue(line.length <= 240, "línea demasiado larga (${line.length}): $line")
        }
    }

    @Test
    fun linesNeverContainAPhoneNumber() {
        val all = allReassuranceLines() + listOf(
            WhatsAppBlindRouteNarrator.multipleContacts(listOf("Juan", "Juana")),
            WhatsAppBlindRouteNarrator.notificationSenderUnresolved("Sofía"),
            WhatsAppBlindRouteNarrator.notificationHidden()
        )
        // Ninguna respuesta de recuperación debe contener una corrida de 4+ dígitos.
        val digitRun = Regex("\\d{4,}")
        all.forEach { line ->
            assertFalse(digitRun.containsMatchIn(line), "posible número en: $line")
        }
    }

    @Test
    fun multipleContactsListsNamesAndAsksToChoose() {
        val line = WhatsAppBlindRouteNarrator.multipleContacts(listOf("Juan", "Juana", "Julián", "Juancho"))
        assertTrue(line.contains("Juan"))
        assertTrue(line.contains("Juana"))
        // Se acota a 3 nombres como mucho.
        assertFalse(line.contains("Juancho"), "no debería listar más de 3")
        assertTrue(line.contains("exacto"), "debe pedir el nombre exacto")
    }

    @Test
    fun senderUnresolvedNamesSenderAndOffersSafeAlternative() {
        val line = WhatsAppBlindRouteNarrator.notificationSenderUnresolved("Sofía")
        assertTrue(line.contains("Sofía"), "debe nombrar al remitente")
        assertTrue(line.contains("Abrí WhatsApp"), "debe ofrecer alternativa segura")
    }

    @Test
    fun blankNameFallsBackToGeneric() {
        val line = WhatsAppBlindRouteNarrator.contactNotFound("   ")
        assertTrue(line.contains("ese contacto"))
    }

    @Test
    fun emptyContactListStillAsksToChoose() {
        val line = WhatsAppBlindRouteNarrator.multipleContacts(emptyList())
        assertTrue(line.contains("nombre exacto"))
    }
}
