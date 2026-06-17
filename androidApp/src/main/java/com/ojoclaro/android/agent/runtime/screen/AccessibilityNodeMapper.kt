package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Mapper puro (sin Android) que transforma [AccessibilityNodeSummary] en
 * [ScreenElement] aplicando filtros de seguridad y dedupe.
 *
 * Reglas:
 *  - Si el nodo es password, NUNCA emitimos el text/value. Usamos hint o
 *    contentDescription como label, marcando isPassword = true.
 *  - Si el label aparenta tener contenido financiero/sensible, el elemento
 *    se descarta.
 *  - Dedupe por (label normalizado + role).
 *  - Cap superior de elementos para no inflar el summary.
 *  - Labels muy largos se recortan a [MAX_LABEL_CHARS].
 */
object AccessibilityNodeMapper {

    const val MAX_LABEL_CHARS = 80
    const val MAX_ELEMENTS = 24
    const val MAX_SUMMARIES_TO_SCAN = 64

    fun map(summaries: List<AccessibilityNodeSummary>): List<ScreenElement> {
        return RobotLoopInstrumentation.measure(RobotLoopMetric.ACCESSIBILITY_NODE_MAPPING) {
            val out = mutableListOf<ScreenElement>()
            val seen = LinkedHashSet<String>()
            var scanned = 0
            for (summary in summaries) {
                if (out.size >= MAX_ELEMENTS) break
                if (scanned >= MAX_SUMMARIES_TO_SCAN) break
                scanned += 1
                val element = mapOne(summary) ?: continue
                val key = dedupeKey(element)
                if (!seen.add(key)) continue
                out.add(element)
            }
            out
        }
    }

    private fun mapOne(summary: AccessibilityNodeSummary): ScreenElement? {
        val label = chooseLabel(summary) ?: return null
        if (label.isBlank()) return null
        // Filtro de contenido sensible: si el label parece banca o secreto,
        // descartamos el elemento. No reemplazamos por algo "redactado":
        // preferimos que no exista a riesgo de leerlo por error.
        if (PrivacyGuard.containsSensitiveFinancialData(label)) return null

        val role = chooseRole(summary)
        val isInteractive = summary.isClickable || summary.isEditable || summary.isCheckable

        return ScreenElement(
            label = label.take(MAX_LABEL_CHARS),
            role = role,
            isInteractive = isInteractive && summary.isEnabled,
            isPassword = summary.isPassword
        )
    }

    /**
     * Etiqueta para hablar. Para password nodes, NUNCA usamos text (es valor).
     * Usamos contentDescription o hint, que son el nombre del campo.
     */
    private fun chooseLabel(summary: AccessibilityNodeSummary): String? {
        val candidates = if (summary.isPassword) {
            // password: solo descripciones/hint del campo, nunca el text
            listOf(summary.contentDescription, summary.hint)
        } else {
            listOf(summary.text, summary.contentDescription, summary.hint)
        }
        return candidates
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }

    /**
     * Mapping aproximado a [ScreenElementRole]. La prioridad respeta:
     *  - heading explícito > editable > checkable > clickable > className-based.
     */
    private fun chooseRole(summary: AccessibilityNodeSummary): ScreenElementRole {
        val className = summary.className.orEmpty()

        return when {
            summary.isHeading -> ScreenElementRole.HEADING
            summary.isEditable -> ScreenElementRole.EDIT_TEXT
            summary.isCheckable && classNameContains(className, "CheckBox", "Switch", "RadioButton") ->
                ScreenElementRole.CHECKBOX
            classNameContains(className, "Button", "ImageButton") -> ScreenElementRole.BUTTON
            classNameContains(className, "ImageView") ->
                if (summary.isClickable) ScreenElementRole.BUTTON else ScreenElementRole.IMAGE
            classNameContains(className, "EditText") -> ScreenElementRole.EDIT_TEXT
            classNameContains(className, "TextView") -> ScreenElementRole.TEXT
            summary.isClickable -> ScreenElementRole.BUTTON
            summary.isCheckable -> ScreenElementRole.CHECKBOX
            else -> ScreenElementRole.UNKNOWN
        }
    }

    private fun classNameContains(className: String, vararg tokens: String): Boolean =
        tokens.any { className.contains(it, ignoreCase = true) }

    private fun dedupeKey(element: ScreenElement): String =
        element.role.name + "|" + TextMatchNormalizer.normalize(element.label)
}
