package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidAccessibilityScreenContextProviderTest {

    private fun nodeSummary(
        text: String? = null,
        contentDescription: String? = null,
        hint: String? = null,
        className: String? = null,
        isClickable: Boolean = false,
        isEditable: Boolean = false,
        isCheckable: Boolean = false,
        isPassword: Boolean = false,
        isHeading: Boolean = false,
        isEnabled: Boolean = true
    ) = AccessibilityNodeSummary(
        text = text,
        contentDescription = contentDescription,
        hint = hint,
        className = className,
        isClickable = isClickable,
        isEditable = isEditable,
        isCheckable = isCheckable,
        isChecked = false,
        isPassword = isPassword,
        isHeading = isHeading,
        isEnabled = isEnabled
    )

    @Test
    fun returnsNullWhenAllSourcesAreEmpty() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "" },
            readPackageName = { null },
            readNodeSummaries = { emptyList() },
            clock = { 0L }
        )
        assertNull(provider.current())
    }

    @Test
    fun returnsNullWhenTextBlankAndPackageBlankAndNoNodes() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "   " },
            readPackageName = { "  " },
            readNodeSummaries = { emptyList() },
            clock = { 0L }
        )
        assertNull(provider.current())
    }

    @Test
    fun returnsSnapshotWithJustPackageNameWhenTextIsBlank() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "" },
            readPackageName = { "com.example.app" },
            readNodeSummaries = { emptyList() },
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
            readNodeSummaries = { emptyList() },
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
            readNodeSummaries = { emptyList() },
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
            readNodeSummaries = { emptyList() },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertEquals("com.example.app", snapshot.packageName)
        assertEquals("", snapshot.text)
    }

    @Test
    fun readPackageNameThrowingDoesNotCrash() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Hola" },
            readPackageName = { error("service crashed") },
            readNodeSummaries = { emptyList() },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertNull(snapshot.packageName)
        assertEquals("Hola", snapshot.text)
    }

    @Test
    fun readNodeSummariesThrowingDoesNotCrash() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Hola" },
            readPackageName = { "com.example.app" },
            readNodeSummaries = { error("service crashed") },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertEquals("Hola", snapshot.text)
        assertTrue(snapshot.elements.isEmpty())
    }

    @Test
    fun allCallbacksThrowingResultsInNullSnapshot() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { error("a") },
            readPackageName = { error("b") },
            readNodeSummaries = { error("c") },
            clock = { 0L }
        )
        assertNull(provider.current())
    }

    @Test
    fun snapshotIncludesMappedElementsFromNodeSummaries() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Pantalla principal" },
            readPackageName = { "com.example.app" },
            readNodeSummaries = {
                listOf(
                    nodeSummary(
                        text = "Mi cuenta",
                        className = "android.widget.TextView",
                        isHeading = true
                    ),
                    nodeSummary(
                        text = "Guardar",
                        className = "android.widget.Button",
                        isClickable = true
                    ),
                    nodeSummary(
                        hint = "Buscar",
                        className = "android.widget.EditText",
                        isEditable = true
                    )
                )
            },
            clock = { 0L }
        )

        val snapshot = provider.current()
        assertNotNull(snapshot)
        val roles = snapshot.elements.map { it.role }
        assertTrue(ScreenElementRole.HEADING in roles)
        assertTrue(ScreenElementRole.BUTTON in roles)
        assertTrue(ScreenElementRole.EDIT_TEXT in roles)
    }

    @Test
    fun passwordValueIsNeverInElements() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "Inicio de sesión" },
            readPackageName = { "com.example.login" },
            readNodeSummaries = {
                listOf(
                    nodeSummary(
                        text = "supersecreto123",
                        contentDescription = "Contraseña",
                        className = "android.widget.EditText",
                        isEditable = true,
                        isPassword = true
                    ),
                    nodeSummary(
                        text = "Ingresar",
                        className = "android.widget.Button",
                        isClickable = true
                    )
                )
            },
            clock = { 0L }
        )

        val snapshot = provider.current()
        assertNotNull(snapshot)
        // El campo de contraseña existe como elemento pero su label es "Contraseña",
        // nunca el valor del usuario.
        val passwordElement = snapshot.elements.firstOrNull { it.isPassword }
        assertNotNull(passwordElement)
        assertEquals("Contraseña", passwordElement.label)
        assertFalse(snapshot.elements.any { it.label.contains("supersecreto") })
    }

    @Test
    fun snapshotProducedFromOnlyNodesWithoutTextOrPackage() {
        val provider = AndroidAccessibilityScreenContextProvider(
            readText = { "" },
            readPackageName = { null },
            readNodeSummaries = {
                listOf(
                    nodeSummary(
                        text = "Aceptar",
                        className = "android.widget.Button",
                        isClickable = true
                    )
                )
            },
            clock = { 0L }
        )
        val snapshot = provider.current()
        assertNotNull(snapshot)
        assertEquals(1, snapshot.elements.size)
        assertEquals("Aceptar", snapshot.elements[0].label)
    }
}
