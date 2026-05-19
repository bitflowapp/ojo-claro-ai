package com.ojoclaro.android.agent.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenChangeMemoryTest {

    @Test
    fun firstAnnouncementIsAllowed() {
        val memory = ScreenChangeMemory()
        assertTrue(
            memory.shouldAllow(
                semanticKey = "x",
                importance = ScreenChangeImportance.NORMAL,
                reasonKey = "r",
                cooldownMs = 10_000L,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun repeatedInsideCooldownIsBlocked() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("x", "r", 1_000L)

        assertFalse(
            memory.shouldAllow(
                semanticKey = "x",
                importance = ScreenChangeImportance.NORMAL,
                reasonKey = "r",
                cooldownMs = 10_000L,
                nowMillis = 5_000L
            )
        )
    }

    @Test
    fun afterCooldownExpiryIsAllowed() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("x", "r", 1_000L)

        assertTrue(
            memory.shouldAllow(
                semanticKey = "x",
                importance = ScreenChangeImportance.NORMAL,
                reasonKey = "r",
                cooldownMs = 10_000L,
                nowMillis = 12_000L
            )
        )
    }

    @Test
    fun criticalCanBreakCooldownIfReasonChanges() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("hot", "password", 1_000L)

        assertTrue(
            memory.shouldAllow(
                semanticKey = "hot",
                importance = ScreenChangeImportance.CRITICAL,
                reasonKey = "banking",
                cooldownMs = 10_000L,
                nowMillis = 2_000L
            ),
            "CRITICAL with different reasonKey should break cooldown"
        )
    }

    @Test
    fun criticalDoesNotBreakCooldownIfSameReason() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("hot", "password", 1_000L)

        assertFalse(
            memory.shouldAllow(
                semanticKey = "hot",
                importance = ScreenChangeImportance.CRITICAL,
                reasonKey = "password",
                cooldownMs = 10_000L,
                nowMillis = 2_000L
            )
        )
    }

    @Test
    fun lastAppPackageTracks() {
        val memory = ScreenChangeMemory()
        assertNull(memory.lastAppPackage())

        memory.rememberAppPackage("com.whatsapp")
        assertEquals("com.whatsapp", memory.lastAppPackage())

        memory.rememberAppPackage(null)
        // null se ignora: el último válido sigue presente.
        assertEquals("com.whatsapp", memory.lastAppPackage())
    }

    @Test
    fun resetClearsAllKeys() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("x", "r", 1_000L)
        memory.rememberAppPackage("com.whatsapp")

        memory.reset()

        assertTrue(
            memory.shouldAllow("x", ScreenChangeImportance.NORMAL, "r", 10_000L, 1_500L),
            "Reset should clear cooldown memory"
        )
        assertNull(memory.lastAppPackage())
    }

    @Test
    fun cooldownZeroAlwaysAllows() {
        val memory = ScreenChangeMemory()
        memory.rememberAnnouncement("x", "r", 1_000L)
        assertTrue(
            memory.shouldAllow("x", ScreenChangeImportance.LOW, "r", cooldownMs = 0L, nowMillis = 1_001L)
        )
    }
}
