package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsAppCriticalGuard — última línea local antes del LLM. Cubre los verbos
 * mutantes/de acción y (FASE 4 fuzz) el pedido de TOCAR un botón peligroso por
 * descripción visual ("tocá el botón verde"), que un no-vidente usa para
 * enviar/llamar y antes caía a NO_MATCH genérico (sin negativa ni gate).
 */
class WhatsAppCriticalGuardTest {

    @Test
    fun mutatingVerbsAreCritical() {
        listOf(
            "mandá el mensaje", "enviá eso", "respondéle que sí", "borrá el chat",
            "bloquealo", "reenviá esto", "reportá este contacto", "archivá el chat",
            "silenciá el grupo", "pagale", "transferile plata", "mandá una foto",
            "compartí mi ubicación", "llamá ahora", "hacé una videollamada",
            "mandale un audio a alguien"
        ).forEach { assertTrue(WhatsAppCriticalGuard.isCritical(it), "debería ser crítico: \"$it\"") }
    }

    @Test
    fun dangerousButtonRequestsAreCritical() {
        // Pedido de tocar el botón peligroso (verde = enviar/llamar) en TODAS sus
        // formas, incluida "dale al botón verde" (el normalizer come "dale").
        listOf(
            "tocá el botón verde", "apretá el botón verde", "dale al botón verde",
            "presioná el botón verde", "tocá el botón de enviar",
            "apretá el botón de enviar", "presioná el botón de mandar",
            "tocá el botón de llamar", "dale al botón de videollamada",
            "apretá el último botón verde", "marcá el botón de audio"
        ).forEach { assertTrue(WhatsAppCriticalGuard.isCritical(it), "botón peligroso debería ser crítico: \"$it\"") }
    }

    @Test
    fun safeButtonsAndReadsAreNotCritical() {
        // NO robar botones SEGUROS ni lectura: el regex exige verbo+botón+objetivo
        // peligroso (verde/enviar/llamar/...). "botón de atrás" / "de inicio" NO.
        listOf(
            "tocá el botón de atrás", "apretá el botón de inicio", "dale al botón de volver",
            "tocá atrás", "volvé para atrás", "leé los chats", "qué dice la pantalla",
            "leeme los mensajes", "qué ves", "abrí whatsapp", "abrí el chat de alguien",
            "subí", "bajá", "qué botones hay"
        ).forEach { assertFalse(WhatsAppCriticalGuard.isCritical(it), "NO debería ser crítico: \"$it\"") }
    }

    @Test
    fun readVerbsAreNeverCritical() {
        // Contrato: los verbos de LECTURA nunca son críticos (deben seguir su ruta).
        listOf("leé", "leeme", "mostrame", "qué dice", "qué chats hay", "leé arriba")
            .forEach { assertFalse(WhatsAppCriticalGuard.isCritical(it), "lectura no es crítica: \"$it\"") }
    }

    @Test
    fun blankNeverThrowsNorCritical() {
        listOf("", "   ", "??", "🙂").forEach {
            assertFalse(WhatsAppCriticalGuard.isCritical(it), "vacío/ruido no es crítico: \"$it\"")
        }
    }
}
