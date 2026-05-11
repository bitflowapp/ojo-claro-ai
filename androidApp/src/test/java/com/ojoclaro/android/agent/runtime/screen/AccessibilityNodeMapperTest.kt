package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityNodeMapperTest {

    private fun summary(
        text: String? = null,
        contentDescription: String? = null,
        hint: String? = null,
        className: String? = null,
        isClickable: Boolean = false,
        isEditable: Boolean = false,
        isCheckable: Boolean = false,
        isChecked: Boolean = false,
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
        isChecked = isChecked,
        isPassword = isPassword,
        isHeading = isHeading,
        isEnabled = isEnabled
    )

    @Test
    fun mapsClickableButtonClassNameToButton() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "Enviar",
                    className = "android.widget.Button",
                    isClickable = true
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals(ScreenElementRole.BUTTON, result[0].role)
        assertEquals("Enviar", result[0].label)
        assertTrue(result[0].isInteractive)
        assertFalse(result[0].isPassword)
    }

    @Test
    fun mapsEditableTextNodeToEditText() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    hint = "Buscar contactos",
                    className = "android.widget.EditText",
                    isEditable = true
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals(ScreenElementRole.EDIT_TEXT, result[0].role)
        assertEquals("Buscar contactos", result[0].label)
        assertTrue(result[0].isInteractive)
    }

    @Test
    fun mapsCheckableCheckBoxToCheckbox() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "Acepto los términos",
                    className = "android.widget.CheckBox",
                    isCheckable = true,
                    isClickable = true
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals(ScreenElementRole.CHECKBOX, result[0].role)
    }

    @Test
    fun mapsImageViewWithoutClickToImage() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    contentDescription = "Logo de la app",
                    className = "android.widget.ImageView",
                    isClickable = false
                )
            )
        )
        // ImageView no-clickable → IMAGE role.
        assertEquals(1, result.size)
        assertEquals(ScreenElementRole.IMAGE, result[0].role)
        assertFalse(result[0].isInteractive)
    }

    @Test
    fun clickableImageViewBecomesButton() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    contentDescription = "Tocá para acción",
                    className = "android.widget.ImageView",
                    isClickable = true
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals(ScreenElementRole.BUTTON, result[0].role)
        assertTrue(result[0].isInteractive)
    }

    @Test
    fun headingFlagWinsOverOtherRoles() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "Mi sección",
                    className = "android.widget.TextView",
                    isHeading = true
                )
            )
        )
        assertEquals(ScreenElementRole.HEADING, result[0].role)
    }

    @Test
    fun passwordNodeNeverExposesText() {
        // Aunque venga un text con un valor enmascarado, NUNCA debemos
        // emitirlo como label. Solo la descripción/hint del campo es válida.
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "•••••••",
                    contentDescription = "Contraseña",
                    className = "android.widget.EditText",
                    isEditable = true,
                    isPassword = true
                )
            )
        )
        assertEquals(1, result.size)
        val element = result[0]
        assertEquals("Contraseña", element.label)
        assertTrue(element.isPassword)
        assertFalse(
            element.label.contains("•"),
            "el label nunca debe incluir el valor del campo password"
        )
    }

    @Test
    fun passwordNodeWithoutLabelGetsDropped() {
        // Si un password no tiene contentDescription ni hint, no hay label
        // legítimo — lo descartamos en lugar de leer el text.
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "•••••••",
                    className = "android.widget.EditText",
                    isEditable = true,
                    isPassword = true
                )
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun duplicateLabelsCollapseInTheSameRole() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(text = "Aceptar", className = "android.widget.Button", isClickable = true),
                summary(text = "Aceptar", className = "android.widget.Button", isClickable = true),
                summary(text = "ACEPTAR", className = "android.widget.Button", isClickable = true)
            )
        )
        assertEquals(1, result.size)
        assertEquals("Aceptar", result[0].label)
    }

    @Test
    fun bankingLikeLabelIsDropped() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "Saldo de cuenta bancaria",
                    className = "android.widget.TextView"
                ),
                summary(
                    text = "Hola",
                    className = "android.widget.TextView"
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals("Hola", result[0].label)
    }

    @Test
    fun longLabelGetsTruncated() {
        val long = "x".repeat(500)
        val result = AccessibilityNodeMapper.map(
            listOf(summary(text = long, className = "android.widget.TextView"))
        )
        assertEquals(1, result.size)
        assertTrue(result[0].label.length <= AccessibilityNodeMapper.MAX_LABEL_CHARS)
    }

    @Test
    fun disabledNodeIsNotInteractive() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(
                    text = "Guardar",
                    className = "android.widget.Button",
                    isClickable = true,
                    isEnabled = false
                )
            )
        )
        assertEquals(1, result.size)
        assertFalse(result[0].isInteractive)
    }

    @Test
    fun emptySummariesProduceEmptyElements() {
        assertEquals(emptyList<ScreenElement>(), AccessibilityNodeMapper.map(emptyList()))
    }

    @Test
    fun nodesWithoutLabelAreSkipped() {
        val result = AccessibilityNodeMapper.map(
            listOf(
                summary(),
                summary(text = "Hola", className = "android.widget.TextView")
            )
        )
        assertEquals(1, result.size)
        assertEquals("Hola", result[0].label)
    }

    @Test
    fun preservesUpToMaxElements() {
        val many = (1..50).map {
            summary(text = "Botón $it", className = "android.widget.Button", isClickable = true)
        }
        val result = AccessibilityNodeMapper.map(many)
        assertTrue(result.size <= AccessibilityNodeMapper.MAX_ELEMENTS)
    }
}
