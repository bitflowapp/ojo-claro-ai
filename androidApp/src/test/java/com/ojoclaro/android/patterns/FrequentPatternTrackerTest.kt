package com.ojoclaro.android.patterns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrequentPatternTrackerTest {

    @Test
    fun incrementsCountForSameCommandTypeAndNormalizedCommand() {
        var now = 1L
        val tracker = FrequentPatternTracker(nowMillis = { now++ })

        tracker.recordCommand("leer texto", "READ_TEXT")
        val pattern = tracker.recordCommand("leer texto", "READ_TEXT")

        assertEquals(2, pattern.count)
        assertEquals("READ_TEXT", pattern.commandType)
    }

    @Test
    fun keepsFirstSeenAndUpdatesLastSeen() {
        var now = 100L
        val tracker = FrequentPatternTracker(nowMillis = { now.also { now += 50L } })

        val first = tracker.recordCommand("leer texto", "READ_TEXT")
        val second = tracker.recordCommand("leer texto", "READ_TEXT")

        assertEquals(100L, first.firstSeenMillis)
        assertEquals(100L, second.firstSeenMillis)
        assertEquals(150L, second.lastSeenMillis)
    }

    @Test
    fun storesLastAppPackageWhenProvided() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        val pattern = tracker.recordCommand(
            rawCommand = "abrir whatsapp",
            commandType = "OPEN_WHATSAPP",
            appPackage = "com.whatsapp"
        )

        assertEquals("com.whatsapp", pattern.lastAppPackage)
    }

    @Test
    fun commandsWithDifferentTypesCreateDifferentPatterns() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        tracker.recordCommand("leer texto", "READ_TEXT")
        tracker.recordCommand("leer texto", "READ_VISIBLE_SCREEN")

        val top = tracker.getTopPatterns(limit = 10)

        assertEquals(2, top.size)
        assertTrue(top.any { it.commandType == "READ_TEXT" })
        assertTrue(top.any { it.commandType == "READ_VISIBLE_SCREEN" })
    }

    @Test
    fun topPatternsAreOrderedByCountDescending() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        tracker.recordCommand("abrir whatsapp", "OPEN_WHATSAPP")
        repeat(3) {
            tracker.recordCommand("leer texto", "READ_TEXT")
        }
        repeat(2) {
            tracker.recordCommand("que dice la pantalla", "READ_VISIBLE_SCREEN")
        }

        val top = tracker.getTopPatterns(limit = 3)

        assertEquals("READ_TEXT", top[0].commandType)
        assertEquals("READ_VISIBLE_SCREEN", top[1].commandType)
        assertEquals("OPEN_WHATSAPP", top[2].commandType)
    }

    @Test
    fun topPatternsRespectLimit() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        tracker.recordCommand("leer texto", "READ_TEXT")
        tracker.recordCommand("abrir whatsapp", "OPEN_WHATSAPP")
        tracker.recordCommand("que dice la pantalla", "READ_VISIBLE_SCREEN")

        val top = tracker.getTopPatterns(limit = 2)

        assertEquals(2, top.size)
    }

    @Test
    fun doesNotStoreSensitiveWhatsAppMessageContent() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        val pattern = tracker.recordCommand(
            rawCommand = "mandale a ContactoDemo: codigo 123456",
            commandType = "COMPOSE_WHATSAPP_MESSAGE",
            appPackage = "com.whatsapp"
        )

        assertFalse(pattern.normalizedCommand.contains("ContactoDemo", ignoreCase = true))
        assertFalse(pattern.normalizedCommand.contains("123456"))
        assertFalse(pattern.normalizedCommand.contains("codigo", ignoreCase = true))
        assertTrue(pattern.isSensitive)
    }

    @Test
    fun composeWhatsappPatternStoresOnlyActionTypeNotMessage() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        val pattern = tracker.recordCommand(
            rawCommand = "mandale a mama: estoy llegando a casa",
            commandType = "COMPOSE_WHATSAPP_MESSAGE",
            appPackage = "com.whatsapp"
        )

        assertEquals("COMPOSE_WHATSAPP_MESSAGE", pattern.commandType)
        assertFalse(pattern.normalizedCommand.contains("mama", ignoreCase = true))
        assertFalse(pattern.normalizedCommand.contains("estoy llegando", ignoreCase = true))
        assertTrue(pattern.isSensitive)
    }

    @Test
    fun sensitivePatternsAreNotApprovedForSuggestionsByDefault() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        val pattern = tracker.recordCommand(
            rawCommand = "mandale a ContactoDemo: estoy llegando",
            commandType = "COMPOSE_WHATSAPP_MESSAGE",
            appPackage = "com.whatsapp"
        )

        assertTrue(pattern.isSensitive)
        assertFalse(pattern.userApprovedForSuggestions)
    }

    @Test
    fun nonSensitivePatternCanBeApprovedForSuggestionsByDefault() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        val pattern = tracker.recordCommand(
            rawCommand = "leer texto",
            commandType = "READ_TEXT",
            appPackage = null
        )

        assertFalse(pattern.isSensitive)
        assertTrue(pattern.userApprovedForSuggestions)
    }

    @Test
    fun clearWorks() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        tracker.recordCommand("leer texto", "READ_TEXT")
        tracker.recordCommand("abrir whatsapp", "OPEN_WHATSAPP")

        tracker.clearPatterns()

        assertTrue(tracker.getTopPatterns(limit = 10).isEmpty())
    }

    @Test
    fun emptyTrackerReturnsEmptyTopPatterns() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        assertTrue(tracker.getTopPatterns(limit = 10).isEmpty())
    }

    @Test
    fun zeroLimitReturnsEmptyTopPatterns() {
        val tracker = FrequentPatternTracker(nowMillis = { 1L })

        tracker.recordCommand("leer texto", "READ_TEXT")

        assertTrue(tracker.getTopPatterns(limit = 0).isEmpty())
    }
}
