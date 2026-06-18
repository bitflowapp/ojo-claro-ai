package com.ojoclaro.android.agent.intelligence

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary

/**
 * Lógica PURA de selección multi-ventana para el snapshot de accesibilidad.
 *
 * Problema diagnosticado: leer solo `selectReadableWindowRoot()` (una ventana)
 * pierde nodos que viven en OTRA ventana de la misma app (p.ej. el carrusel de
 * Uber en `UberComposeView`, detrás del overlay de rating). `uiautomator` ve 238
 * nodos porque fusiona todas las ventanas; Estela veía 16.
 *
 * Esta clase decide QUÉ ventanas leer (sin tocar Android): el servicio mapea sus
 * `windows` a [WindowDescriptor], llama [choose], lee los roots elegidos, y
 * fusiona+deduplica con [dedupe]. El traversal real de AccessibilityNodeInfo
 * queda en el servicio (no testeable en unit).
 */
data class WindowDescriptor(
    val index: Int,
    val packageName: String?,
    /** TYPE_APPLICATION y NO el paquete propio (ya calculado por el servicio). */
    val isApplicationWindow: Boolean,
    /** TYPE_ACCESSIBILITY_OVERLAY (el overlay propio de Estela, p.ej.). */
    val isOverlay: Boolean,
    val isSystemUi: Boolean,
    val active: Boolean,
    val focused: Boolean,
)

object ReadableWindowPlanner {

    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    // Delimitador improbable en texto de UI, para la clave de deduplicación.
    private const val SEP = ""

    /**
     * Devuelve los índices de ventana a leer.
     *  - Si hay ventanas de APP (no overlay propio, no SystemUI): leer TODAS las
     *    del paquete foreground (el de la ventana activa/focada; si ninguna, el de
     *    la primera). Así se recuperan ventanas hermanas de la misma app.
     *  - Si no hay app real: caer a las no-overlay (incl. SystemUI → lockscreen),
     *    o a todas si no quedara nada.
     *  - Nunca incluye overlays (la barra/botón propio de Estela).
     */
    fun choose(windows: List<WindowDescriptor>): List<Int> {
        if (windows.isEmpty()) return emptyList()
        val appWindows = windows.filter { it.isApplicationWindow && !it.isSystemUi && !it.isOverlay }
        if (appWindows.isNotEmpty()) {
            val fgPackage = appWindows.firstOrNull { it.active || it.focused }?.packageName
                ?: appWindows.first().packageName
            return appWindows.filter { it.packageName == fgPackage }.map { it.index }
        }
        val nonOverlay = windows.filter { !it.isOverlay }
        return (if (nonOverlay.isNotEmpty()) nonOverlay else windows).map { it.index }
    }

    /**
     * Fusiona/deduplica nodos de varias ventanas, respetando un máximo.
     *  - Deduplica por (text|contentDescription|hint|className|clickable|editable).
     *  - NO deduplica nodos SIN etiqueta (botones/íconos): se conservan todos para
     *    no perder controles relevantes.
     */
    fun dedupe(nodes: List<AccessibilityNodeSummary>, max: Int): List<AccessibilityNodeSummary> {
        val seen = HashSet<String>()
        val out = ArrayList<AccessibilityNodeSummary>(minOf(nodes.size, max))
        for (n in nodes) {
            if (out.size >= max) break
            val hasLabel = !(n.text.isNullOrBlank() &&
                n.contentDescription.isNullOrBlank() &&
                n.hint.isNullOrBlank())
            if (!hasLabel) {
                out.add(n)
                continue
            }
            val key = listOf(
                n.text.orEmpty(),
                n.contentDescription.orEmpty(),
                n.hint.orEmpty(),
                n.className.orEmpty(),
                n.isClickable.toString(),
                n.isEditable.toString(),
            ).joinToString(SEP)
            if (seen.add(key)) out.add(n)
        }
        return out
    }
}
