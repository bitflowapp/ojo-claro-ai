package com.ojoclaro.android.agent.runtime.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidAccessibilityScreenContextProviderTest {

    @Test
    fun returnsNullWhenBothTextAndPackageAreEmpty() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "" },
            readPackageName = { null },
            clock = { 0L }
        )
        assertNull(provider.current())
    }

    @Test
    fun returnsNullWhenTextBlankAndPackageBlank() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "   " },
            readPackageName = { "  " },
            clock = { 0L }
        )
        assertNull(provider.current())
    }

    @Test
    fun returnsSnapshotWithJustPackageNameWhenTextIsBlank() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "" },
            readPackageName = { "com.example.app" },
            clock = { 42L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertEquals("com.example.app", snapshot.packageName)
        assertEquals("", snapshot.text)
        assertTrue(snapshot.elements.isEmpty())
        assertEquals(42L, snapshot.capturedAtMillis)
    }

    @Test
    fun returnsSnapshotWithTextEvenWhenPackageMissing() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Hola mundo" },
            readPackageName = { null },
            clock = { 7L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertNull(snapshot.packageName)
        assertEquals("Hola mundo", snapshot.text)
    }

    @Test
    fun trimsLeadingAndTrailingWhitespaceFromText() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "   Hola   " },
            readPackageName = { "com.example.app" },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertEquals("Hola", snapshot.text)
    }

    @Test
    fun readTextThrowingDoesNotCrash() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { error("service crashed") },
            readPackageName = { "com.example.app" },
            clock = { 0L }
        )
        val snapshot = provider.current()
        // packageName se mantiene; el texto queda vacío.
        assertNotNull(snapshot)
        assertEquals("com.example.app", snapshot.packageName)
        assertEquals("", snapshot.text)
    }

    @Test
    fun readPackageNameThrowingDoesNotCrash() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Hola" },
            readPackageName = { error("service crashed") },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertNull(snapshot.packageName)
        assertEquals("Hola", snapshot.text)
    }

    @Test
    fun bothCallbacksThrowingResultsInNullSnapshot() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { error("a") },
            readPackageName = { error("b") },
            clock = { 0L }
        )
        // text se vuelve "" y packageName null → ambos blank → null.
        assertNull(provider.current())
    }
}
