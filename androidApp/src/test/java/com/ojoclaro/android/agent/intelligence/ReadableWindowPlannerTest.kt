package com.ojoclaro.android.agent.intelligence

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Selección multi-ventana + deduplicación (PURO). Cubre el fix de captura: leer
 * todas las ventanas de la app foreground, no solo el overlay de arriba.
 */
class ReadableWindowPlannerTest {

    private fun w(
        index: Int,
        pkg: String?,
        app: Boolean = true,
        overlay: Boolean = false,
        sysui: Boolean = false,
        active: Boolean = false,
        focused: Boolean = false,
    ) = WindowDescriptor(index, pkg, app, overlay, sysui, active, focused)

    private fun node(
        text: String? = null,
        desc: String? = null,
        className: String? = "android.widget.TextView",
        clickable: Boolean = false,
    ) = AccessibilityNodeSummary(
        text = text, contentDescription = desc, hint = null, className = className,
        isClickable = clickable, isEditable = false, isCheckable = false, isChecked = false,
        isPassword = false, isHeading = false, isEnabled = true
    )

    // ---- choose ----

    @Test fun twoAppWindowsSamePackageAreBothChosen() {
        // El caso Uber: overlay de rating + carrusel, ambas com.ubercab.
        val chosen = ReadableWindowPlanner.choose(
            listOf(w(0, "com.ubercab", active = true), w(1, "com.ubercab"))
        )
        assertEquals(listOf(0, 1), chosen)
    }

    @Test fun systemUiOnTopNotChosenWhenAppExists() {
        val chosen = ReadableWindowPlanner.choose(
            listOf(
                w(0, "com.android.systemui", app = false, sysui = true, active = true),
                w(1, "com.instagram.android", focused = true),
            )
        )
        assertEquals(listOf(1), chosen)
    }

    @Test fun onlySystemUiFallsBackToIt() {
        val chosen = ReadableWindowPlanner.choose(
            listOf(w(0, "com.android.systemui", app = false, sysui = true, active = true))
        )
        assertEquals(listOf(0), chosen)
    }

    @Test fun ownOverlayNeverChosen() {
        val chosen = ReadableWindowPlanner.choose(
            listOf(
                w(0, "com.ojoclaro.android", app = false, overlay = true),
                w(1, "com.ubercab", active = true),
            )
        )
        assertEquals(listOf(1), chosen)
    }

    @Test fun picksForegroundAppPackageOnly() {
        // Dos apps (split-screen): solo la activa/focada.
        val chosen = ReadableWindowPlanner.choose(
            listOf(w(0, "com.whatsapp", active = true), w(1, "com.instagram.android"))
        )
        assertEquals(listOf(0), chosen)
    }

    @Test fun emptyReturnsEmpty() {
        assertTrue(ReadableWindowPlanner.choose(emptyList()).isEmpty())
    }

    // ---- dedupe ----

    @Test fun identicalLabeledNodesDeduped() {
        val out = ReadableWindowPlanner.dedupe(
            listOf(node(text = "UberX"), node(text = "UberX")), max = 50
        )
        assertEquals(1, out.size)
    }

    @Test fun distinctLabelsKept() {
        val out = ReadableWindowPlanner.dedupe(
            listOf(node(text = "UberX"), node(text = "Uber Comfort")), max = 50
        )
        assertEquals(2, out.size)
    }

    @Test fun labellessNodesAllKept() {
        // Botones sin etiqueta no se deduplican (no perder controles).
        val out = ReadableWindowPlanner.dedupe(
            listOf(
                node(className = "android.widget.Button", clickable = true),
                node(className = "android.widget.Button", clickable = true),
            ),
            max = 50
        )
        assertEquals(2, out.size)
    }

    @Test fun maxRespected() {
        val nodes = (1..5).map { node(text = "n$it") }
        assertEquals(3, ReadableWindowPlanner.dedupe(nodes, max = 3).size)
    }
}
