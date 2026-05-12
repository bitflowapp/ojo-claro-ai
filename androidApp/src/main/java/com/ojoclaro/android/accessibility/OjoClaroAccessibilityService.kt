package com.ojoclaro.android.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ojoclaro.android.MainActivity
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.voice.OjoClaroIntents
import java.lang.ref.WeakReference

class OjoClaroAccessibilityService : AccessibilityService() {

    private val accessibilityButtonCallback =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                openHomeInListeningMode()
            }
        }

    override fun onServiceConnected() {
        activeService = WeakReference(this)
        runCatching {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                accessibilityButtonCallback
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // MVP seguro: escuchar solamente.
        // No taps, no gestos, no almacenamiento, no envío de datos.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(
                accessibilityButtonCallback
            )
        }
        if (activeService?.get() === this) {
            activeService = null
        }
        super.onDestroy()
    }

    /**
     * Abre Home en modo escucha. Lo invocamos cuando el usuario toca el botón
     * flotante de Accesibilidad asignado a Ojo Claro.
     *
     * Importante: el voice loop NO arranca desde el servicio. La UI lo levanta
     * cuando la actividad queda visible. Acá solo emitimos un intent.
     */
    private fun openHomeInListeningMode() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = OjoClaroIntents.ACTION_START_LISTENING
                putExtra(OjoClaroIntents.EXTRA_START_LISTENING, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        }
    }

    private fun readActiveWindowText(): String {
        return RobotLoopInstrumentation.measure(RobotLoopMetric.ACCESSIBILITY_NODE_TRAVERSAL) {
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return@measure ""

            val collected = linkedSetOf<String>()
            val traversalState = TraversalState()

            collectVisibleText(
                node = root,
                output = collected,
                state = traversalState,
                depth = 0
            )

            collected.joinToString(separator = ". ")
        }
    }

    private fun readActiveWindowPackageName(): String? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        return runCatching { root.packageName?.toString() }.getOrNull()
    }

    /**
     * Recorrido estructurado de la ventana activa. Devuelve una lista plana de
     * [AccessibilityNodeSummary], cada uno representando un nodo visible que
     * trae texto/descripción/hint o acción interactiva.
     *
     * Reglas de seguridad:
     *  - Si el nodo es password, NUNCA se incluye su [AccessibilityNodeSummary.text]
     *    (se setea a null). Solo se conserva contentDescription/hint, que son
     *    etiquetas del campo, no el valor que el usuario tipeó.
     *  - Se mantienen los mismos límites de depth/nodes/chars que el recorrido
     *    de texto plano para no inflar el costo del traversal.
     */
    private fun readActiveWindowNodeSummaries(): List<AccessibilityNodeSummary> {
        return RobotLoopInstrumentation.measure(RobotLoopMetric.ACCESSIBILITY_NODE_TRAVERSAL) {
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return@measure emptyList()
            val out = mutableListOf<AccessibilityNodeSummary>()
            val state = TraversalState()
            collectVisibleNodes(node = root, out = out, state = state, depth = 0)
            out
        }
    }

    private fun collectVisibleNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeSummary>,
        state: TraversalState,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (out.size >= MAX_NODES_EMITTED) return
        if (state.visitedNodes >= MAX_VISITED_NODES) return
        if (state.totalChars >= MAX_TOTAL_CHARS) return

        state.visitedNodes++

        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        if (visible) {
            val summary = buildNodeSummary(node)
            if (summary != null && shouldEmit(summary)) {
                out.add(summary)
                state.totalChars +=
                    (summary.text?.length ?: 0) +
                    (summary.contentDescription?.length ?: 0) +
                    (summary.hint?.length ?: 0)
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        val safeChildCount = childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)

        for (index in 0 until safeChildCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectVisibleNodes(
                node = child,
                out = out,
                state = state,
                depth = depth + 1
            )
        }
    }

    private fun buildNodeSummary(node: AccessibilityNodeInfo): AccessibilityNodeSummary? {
        val isPassword = runCatching { node.isPassword }.getOrDefault(false)
        val rawText = runCatching { node.text?.toString() }.getOrNull()
        // Nunca exponer valor de password.
        val textValue = if (isPassword) null else normalizeText(rawText).takeIf { it.isNotBlank() }
        val description = normalizeText(
            runCatching { node.contentDescription?.toString() }.getOrNull()
        ).takeIf { it.isNotBlank() }
        val hint = normalizeText(
            runCatching { node.hintText?.toString() }.getOrNull()
        ).takeIf { it.isNotBlank() }

        // Si no hay nada legible y el nodo no es interactivo, no vale la pena.
        val isClickable = runCatching { node.isClickable }.getOrDefault(false)
        val isEditable = runCatching { node.isEditable }.getOrDefault(false)
        val isCheckable = runCatching { node.isCheckable }.getOrDefault(false)
        val noContent = textValue == null && description == null && hint == null
        val noAction = !isClickable && !isEditable && !isCheckable
        if (noContent && noAction) return null

        val className = runCatching { node.className?.toString() }.getOrNull()
        // node.isHeading requiere API 28; minSdk del proyecto es 26.
        val isHeading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { node.isHeading }.getOrDefault(false)
        } else {
            false
        }
        val isChecked = runCatching { node.isChecked }.getOrDefault(false)
        val isEnabled = runCatching { node.isEnabled }.getOrDefault(true)

        return AccessibilityNodeSummary(
            text = textValue?.take(MAX_SINGLE_TEXT_LENGTH),
            contentDescription = description?.take(MAX_SINGLE_TEXT_LENGTH),
            hint = hint?.take(MAX_SINGLE_TEXT_LENGTH),
            className = className?.take(MAX_SINGLE_TEXT_LENGTH),
            isClickable = isClickable,
            isEditable = isEditable,
            isCheckable = isCheckable,
            isChecked = isChecked,
            isPassword = isPassword,
            isHeading = isHeading,
            isEnabled = isEnabled
        )
    }

    /**
     * Filtro de bajo nivel. Se descartan textos/labels que son demasiado largos
     * (probable basura/concat de subárbol) y entries sin ningún campo legible.
     */
    private fun shouldEmit(summary: AccessibilityNodeSummary): Boolean {
        val anyText = summary.text ?: summary.contentDescription ?: summary.hint
        if (anyText == null && !summary.isClickable && !summary.isEditable && !summary.isCheckable) {
            return false
        }
        return true
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        output: LinkedHashSet<String>,
        state: TraversalState,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (output.size >= MAX_TEXT_ITEMS) return
        if (state.visitedNodes >= MAX_VISITED_NODES) return
        if (state.totalChars >= MAX_TOTAL_CHARS) return

        state.visitedNodes++

        if (!isReadableNode(node)) return

        addSafeText(
            rawValue = node.text?.toString(),
            output = output,
            state = state
        )

        addSafeText(
            rawValue = node.contentDescription?.toString(),
            output = output,
            state = state
        )

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        val safeChildCount = childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)

        for (index in 0 until safeChildCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue

            collectVisibleText(
                node = child,
                output = output,
                state = state,
                depth = depth + 1
            )
        }
    }

    private fun isReadableNode(node: AccessibilityNodeInfo): Boolean {
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        if (!visible) return false

        val password = runCatching { node.isPassword }.getOrDefault(false)
        if (password) return false

        return true
    }

    private fun addSafeText(
        rawValue: String?,
        output: LinkedHashSet<String>,
        state: TraversalState
    ) {
        val value = normalizeText(rawValue)
        if (value.isBlank()) return
        if (value.length > MAX_SINGLE_TEXT_LENGTH) return
        if (state.totalChars + value.length > MAX_TOTAL_CHARS) return

        val added = output.add(value)
        if (added) {
            state.totalChars += value.length
        }
    }

    private fun normalizeText(value: String?): String {
        return value
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            .orEmpty()
    }

    private data class TraversalState(
        var visitedNodes: Int = 0,
        var totalChars: Int = 0
    )

    companion object {
        private const val MAX_TEXT_ITEMS = 24
        private const val MAX_NODES_EMITTED = 32
        private const val MAX_SINGLE_TEXT_LENGTH = 280
        private const val MAX_TOTAL_CHARS = 2_000
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_VISITED_NODES = 160
        private const val MAX_CHILDREN_PER_NODE = 40

        private val WHITESPACE_REGEX = Regex("\\s+")

        @Volatile
        private var activeService: WeakReference<OjoClaroAccessibilityService>? = null

        fun readVisibleText(): String {
            return activeService?.get()?.readActiveWindowText().orEmpty()
        }

        /**
         * Devuelve el nombre del paquete de la ventana activa, o null si el
         * servicio no está conectado o no hay raíz accesible. Lo usa el Agent
         * Runtime para clasificar pantallas sensibles (banca, pagos). No es
         * PII por sí mismo: es metadata del paquete.
         */
        fun readActivePackageName(): String? {
            return activeService?.get()?.readActiveWindowPackageName()
        }

        /**
         * Devuelve los nodos visibles de la ventana activa como datos planos.
         * Pensado para Structured Screen Snapshot v1. Nunca expone valores de
         * campos password — para esos nodos [AccessibilityNodeSummary.text] es null.
         */
        fun readVisibleNodeSummaries(): List<AccessibilityNodeSummary> {
            return activeService?.get()?.readActiveWindowNodeSummaries().orEmpty()
        }

        fun isConnected(): Boolean {
            return activeService?.get() != null
        }
    }
}
