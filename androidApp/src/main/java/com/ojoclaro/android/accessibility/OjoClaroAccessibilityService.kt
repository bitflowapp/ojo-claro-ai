package com.ojoclaro.android.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.intelligence.ReadableWindowPlanner
import com.ojoclaro.android.agent.intelligence.VisibleNodeSanitizer
import com.ojoclaro.android.agent.intelligence.WindowDescriptor
import com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider
import com.ojoclaro.android.agent.runtime.screen.ScreenScrollOutcome
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingResult
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingUseCase
import com.ojoclaro.android.agent.runtime.instagram.InstagramNameMatcher
import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.presence.AssistantPresenceView
import com.ojoclaro.android.presence.AssistantVisualState
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleChatOpenResult
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatMatcher
import com.ojoclaro.android.global.GlobalAssistantService
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.speech.SpeechController
import java.lang.ref.WeakReference

data class AccessibilityTreeReadDiagnostics(
    val rootAvailable: Boolean,
    val rootPackage: String?,
    val rootClass: String?,
    val rootChildCount: Int,
    val source: String = "UNKNOWN",
    val windowType: Int? = null,
    val ownPackageSelected: Boolean = false,
    val overlayWindowSelected: Boolean = false,
    val visitedNodes: Int,
    val acceptedNodes: Int,
    val discardedNodes: Int,
    val discardedEmpty: Int,
    val discardedInvisible: Int,
    val discardedPrivacy: Int,
    val discardedByLimit: Int,
    val textsFoundCount: Int,
    val contentDescriptionsFoundCount: Int,
    val timestampMillis: Long
)

class OjoClaroAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    // IMPORTANTE: el WindowManager para TYPE_ACCESSIBILITY_OVERLAY debe venir del
    // contexto del PROPIO servicio de accesibilidad, no del applicationContext.
    // Con applicationContext el sistema rechaza la ventana con BadTokenException
    // ("permission denied for window type 2032"), porque la ventana no queda
    // asociada al token del servicio. Verificado físicamente en Moto G15 / API 35.
    private val windowManager: WindowManager? by lazy {
        getSystemService(WindowManager::class.java)
    }

    private lateinit var speechController: SpeechController
    private lateinit var overlayScreenUnderstandingUseCase: ScreenUnderstandingUseCase

    private var overlayView: View? = null
    private var overlayExpanded: Boolean = false
    private var overlayState: OverlayState = OverlayState.IDLE

    // V1.14 — presencia animada del botón flotante colapsado. El estado lo
    // empuja el GlobalAssistantService (voz/TTS/cámara) vía setPresenceState;
    // la vista se actualiza EN SITIO para no reiniciar la animación.
    private var presenceState: AssistantVisualState = AssistantVisualState.IDLE
    private var presenceView: AssistantPresenceView? = null
    private var lastOverlayReadRouteActive: Boolean = false
    private var debugAgentMissionReceiver: BroadcastReceiver? = null

    // Experimento touch-filter (solo debug): mientras está true, el overlay NO
    // se re-muestra en cada evento de accesibilidad. Se restaura explícitamente.
    private var overlaySuppressed: Boolean = false

    private val accessibilityButtonCallback =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                toggleAccessibilityOverlay()
            }
        }

    override fun onCreate() {
        super.onCreate()
        speechController = SpeechController(
            context = this,
            onSpeechStarted = {
                mainHandler.post {
                    overlayState = OverlayState.SPEAKING
                    // V1.14 — el botón colapsado refleja que Estela habla
                    // (lectura iniciada desde el propio overlay).
                    setPresenceStateInternal(AssistantVisualState.SPEAKING)
                }
            },
            onSpeechFinished = {
                mainHandler.post {
                    overlayState = OverlayState.IDLE
                    setPresenceStateInternal(AssistantVisualState.IDLE)
                    if (lastOverlayReadRouteActive) {
                        logOverlayTtsCompleted()
                    }
                    lastOverlayReadRouteActive = false
                }
            },
            onSpeechStopped = {
                mainHandler.post {
                    overlayState = OverlayState.IDLE
                    setPresenceStateInternal(AssistantVisualState.IDLE)
                    lastOverlayReadRouteActive = false
                }
            }
        )
        overlayScreenUnderstandingUseCase = ScreenUnderstandingUseCase(
            provider = AndroidAccessibilityScreenContextProvider(
                readText = { readActiveWindowText() },
                readPackageName = { readActiveWindowPackageName() },
                readNodeSummaries = { readActiveWindowNodeSummaries() }
            ),
            isAccessibilityReady = { isConnected() }
        )
    }

    override fun onServiceConnected() {
        activeService = WeakReference(this)
        if (BuildConfig.DEBUG) {
            Log.i(DIAG_TAG, "service connected (Estela accesibilidad ACTIVA)")
        }
        runCatching {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                accessibilityButtonCallback
            )
        }
        registerDebugAgentMissionReceiver()
        showAccessibilityOverlay(expanded = false)
    }

    /**
     * QA físico del Agent Core (SOLO debug): permite inyectar una misión por
     * adb sin pasar por STT, espejando los receivers debug ya existentes en
     * la app (patrón RECEIVER_EXPORTED + gate BuildConfig.DEBUG).
     *   adb shell am broadcast -a com.ojoclaro.DEBUG_AGENT_MISSION --es goal "..."
     * El receiver vive en este servicio porque es el único proceso siempre
     * activo mientras Accesibilidad está habilitada. En release no se registra.
     */
    private fun registerDebugAgentMissionReceiver() {
        if (!BuildConfig.DEBUG || debugAgentMissionReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // V1.12.1 — limpieza de QA sin texto: corta la llamada de
                // Instagram con guardas duras (el ENDCALL del sistema no
                // funciona ahí y el force-stop mata este servicio).
                if (intent?.action == DEBUG_IG_ENDCALL_ACTION) {
                    tapInstagramEndCallForQaInternal()
                    return
                }
                // Volcado SANITIZADO de lo que ve readVisibleNodeSummaries (sin
                // texto sensible) para alinear fixtures con la vista real.
                if (intent?.action == DEBUG_DUMP_VISIBLE_NODES_ACTION) {
                    dumpVisibleNodesForDebug()
                    return
                }
                // Experimento touch-filter (solo debug): ocultar/mostrar el
                // overlay, loguear las coordenadas reales de la 1ra fila de chat
                // y chequear si estamos dentro de un chat. El toque real lo hace
                // adb input tap (lo que filterTouchesWhenObscured sí filtra).
                if (intent?.action == DEBUG_OVERLAY_HIDE_ACTION) {
                    overlaySuppressed = true
                    Log.i(NAV_TAG, "ESTELA_OVERLAY_TEMP_HIDE_REQUESTED")
                    mainHandler.post {
                        hideAccessibilityOverlay()
                        Log.i(NAV_TAG, "ESTELA_OVERLAY_TEMP_HIDDEN overlayPresent=${overlayView != null}")
                    }
                    return
                }
                if (intent?.action == DEBUG_OVERLAY_SHOW_ACTION) {
                    overlaySuppressed = false
                    mainHandler.post {
                        showAccessibilityOverlay(expanded = false)
                        Log.i(NAV_TAG, "ESTELA_OVERLAY_RESTORED overlayPresent=${overlayView != null}")
                    }
                    return
                }
                if (intent?.action == DEBUG_FIRST_CHAT_BOUNDS_ACTION) {
                    logFirstVisibleChatBoundsForDebug()
                    return
                }
                if (intent?.action == DEBUG_IN_CHAT_CHECK_ACTION) {
                    logInWhatsAppChatForDebug()
                    return
                }
                val text = intent?.getStringExtra(DEBUG_AGENT_MISSION_EXTRA_GOAL)
                    .orEmpty()
                    .take(500)
                if (text.isBlank()) return
                when (intent?.action) {
                    DEBUG_AGENT_MISSION_ACTION ->
                        GlobalAssistantService.debugStartAgentMission(
                            this@OjoClaroAccessibilityService,
                            text
                        )
                    DEBUG_VOICE_TEXT_ACTION ->
                        GlobalAssistantService.debugInjectVoiceText(
                            this@OjoClaroAccessibilityService,
                            text
                        )
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter().apply {
                    addAction(DEBUG_AGENT_MISSION_ACTION)
                    addAction(DEBUG_VOICE_TEXT_ACTION)
                    addAction(DEBUG_IG_ENDCALL_ACTION)
                    addAction(DEBUG_DUMP_VISIBLE_NODES_ACTION)
                    addAction(DEBUG_OVERLAY_HIDE_ACTION)
                    addAction(DEBUG_OVERLAY_SHOW_ACTION)
                    addAction(DEBUG_FIRST_CHAT_BOUNDS_ACTION)
                    addAction(DEBUG_IN_CHAT_CHECK_ACTION)
                },
                ContextCompat.RECEIVER_EXPORTED
            )
            debugAgentMissionReceiver = receiver
        }
    }

    /**
     * QA físico (solo debug): vuelca, SANITIZADO, lo que ve
     * readActiveWindowNodeSummaries() bajo el tag [VISIBLE_NODE_DUMP_TAG]. NUNCA
     * loguea texto sensible (solo flags/longitudes/marcadores/tokens de categoría).
     */
    private fun dumpVisibleNodesForDebug() {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val pkg = readActiveWindowPackageName()
            val summaries = readActiveWindowNodeSummaries()
            val dump = VisibleNodeSanitizer.dump(pkg, summaries)
            VisibleNodeSanitizer.renderLines(dump).forEach { Log.i(VISIBLE_NODE_DUMP_TAG, it) }
        }
    }

    /**
     * Experimento touch-filter (solo debug): loguea el centro (coords, SIN
     * texto) del primer nodo de chat visible para tapearlo con adb input tap.
     */
    private fun logFirstVisibleChatBoundsForDebug() {
        if (!BuildConfig.DEBUG) return
        Log.i(NAV_TAG, "WHATSAPP_TOUCH_FILTER_EXPERIMENT_STARTED")
        Log.i(NAV_TAG, "ROUTING_AUDIT handler=whatsapp_overlay_touch_filter_probe")
        Log.i(NAV_TAG, "ESTELA_OVERLAY_VISIBLE present=${overlayView != null} suppressed=$overlaySuppressed")
        val pkg = readActiveWindowPackageName()
        if (!packageNameLooksLikeWhatsApp(pkg)) {
            Log.i(NAV_TAG, "touchProbe firstChatBounds=none reason=not_whatsapp")
            return
        }
        val root = selectReadableWindowRoot()?.root ?: run {
            Log.i(NAV_TAG, "touchProbe firstChatBounds=none reason=no_root")
            return
        }
        val node = findFirstListRowNode(root, depth = 0)
        if (node == null) {
            Log.i(NAV_TAG, "touchProbe firstChatBounds=none reason=no_row_node")
            return
        }
        val b = Rect()
        runCatching { node.getBoundsInScreen(b) }
        Log.i(NAV_TAG, "touchProbe firstChatBounds cx=${b.centerX()} cy=${b.centerY()} top=${b.top} h=${b.height()}")
    }

    /**
     * Primer nodo "de fila de chat" visible: texto no sensible, debajo de la
     * barra superior (top > umbral), altura típica de fila. Coords sólo para el
     * experimento; nunca se loguea el texto.
     */
    private fun findFirstListRowNode(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        if (visible && !runCatching { node.isPassword }.getOrDefault(false)) {
            val label = safeNodeLabel(node)
            val b = Rect()
            runCatching { node.getBoundsInScreen(b) }
            if (label.isNotBlank() &&
                b.top > FIRST_ROW_MIN_TOP_PX &&
                b.height() in FIRST_ROW_MIN_HEIGHT_PX..FIRST_ROW_MAX_HEIGHT_PX &&
                !WhatsAppVisibleChatMatcher.isSensitiveActionLabel(label)
            ) {
                return node
            }
        }
        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            findFirstListRowNode(child, depth + 1)?.let { return it }
        }
        return null
    }

    /** Experimento: ¿estamos dentro de un chat? (hay campo de texto = chat). */
    private fun logInWhatsAppChatForDebug() {
        if (!BuildConfig.DEBUG) return
        val pkg = readActiveWindowPackageName()
        val inWhatsApp = packageNameLooksLikeWhatsApp(pkg)
        val root = selectReadableWindowRoot()?.root
        val inChat = inWhatsApp && root != null && findWhatsAppEntryField(root) != null
        Log.i(NAV_TAG, "touchProbe inWhatsApp=$inWhatsApp inChat=$inChat overlayPresent=${overlayView != null}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // MVP seguro: escuchar solamente.
        // No taps, no gestos, no almacenamiento, no envío de datos.
        //
        // Hook opcional del paquete 4A: si hay un AccessibilitySnapshotEventRouter
        // registrado (via setSnapshotRouter), notificamos el tipo de evento.
        // El router decide internamente si colectar; si el flag
        // accessibilityRuntimeContextEnabled está OFF (default), no pasa nada.
        val type = event?.eventType ?: return
        if (overlayView == null && !overlaySuppressed) {
            mainHandler.post { showAccessibilityOverlay(expanded = false) }
        }
        val router = snapshotRouter ?: return
        runCatching { router.onEvent(type) }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { showAccessibilityOverlay(expanded = overlayExpanded) }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            Log.i(DIAG_TAG, "service disconnected (Estela accesibilidad INACTIVA)")
        }
        runCatching {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(
                accessibilityButtonCallback
            )
        }
        debugAgentMissionReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        debugAgentMissionReceiver = null
        // Si había router registrado, le avisamos para que limpie el repository.
        // Esto evita que un StructuredScreenSnapshot stale quede expuesto a otros
        // consumidores después de que el usuario desactivó accesibilidad.
        runCatching { snapshotRouter?.onServiceDisconnected() }
        hideAccessibilityOverlay()
        speechController.shutdown()
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
    private fun toggleAccessibilityOverlay() {
        showAccessibilityOverlay(expanded = !overlayExpanded)
    }

    private fun showAccessibilityOverlay(expanded: Boolean) {
        val manager = windowManager ?: return
        hideAccessibilityOverlay()
        overlayExpanded = expanded

        val view = if (expanded) buildExpandedOverlay() else buildCollapsedOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 0
            y = 0
        }

        runCatching {
            manager.addView(view, params)
            overlayView = view
        }.onFailure { error ->
            overlayView = null
            overlayExpanded = false
            if (BuildConfig.DEBUG) {
                Log.w(
                    DIAG_TAG,
                    "overlay add failed type=TYPE_ACCESSIBILITY_OVERLAY " +
                        "err=${error.javaClass.simpleName}: ${error.message}"
                )
            }
        }
    }

    private fun hideAccessibilityOverlay() {
        val view = overlayView ?: return
        overlayView = null
        presenceView = null
        runCatching { windowManager?.removeView(view) }
    }

    /**
     * V1.14 — el botón colapsado ES la presencia animada de Estela. Sigue
     * siendo clickeable para abrir el panel (la voz no depende de verlo). La
     * referencia se guarda para actualizar el estado EN SITIO sin rebuild.
     */
    private fun buildCollapsedOverlay(): View =
        AssistantPresenceView(this).apply {
            minimumWidth = OVERLAY_COLLAPSED_MIN_WIDTH
            minimumHeight = OVERLAY_COLLAPSED_MIN_HEIGHT
            setState(presenceState)
            setOnClickListener { showAccessibilityOverlay(expanded = true) }
            presenceView = this
        }

    /**
     * V1.14 — empuja el estado visual. Si el botón colapsado está visible,
     * actualiza la vista EN SITIO (animación continua); si el panel está
     * abierto o no hay overlay, guarda el estado para el próximo build.
     */
    private fun setPresenceStateInternal(state: AssistantVisualState) {
        presenceState = state
        if (BuildConfig.DEBUG) Log.i(DIAG_TAG, "presence state=$state")
        mainHandler.post {
            val pv = presenceView
            when {
                !overlayExpanded && pv != null -> pv.setState(state)
                overlayView == null -> showAccessibilityOverlay(expanded = false)
                // Panel abierto: el estado se aplicará al colapsar.
            }
        }
    }

    private fun buildExpandedOverlay(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = ContextCompat.getDrawable(
                this@OjoClaroAccessibilityService,
                android.R.drawable.dialog_holo_light_frame
            )

            addView(
                TextView(this@OjoClaroAccessibilityService).apply {
                    text = overlayStateTitle()
                    textSize = 15f
                }
            )
            addView(
                Button(this@OjoClaroAccessibilityService).apply {
                    text = "Leer pantalla"
                    setOnClickListener { readScreenFromOverlay() }
                }
            )
            addView(
                Button(this@OjoClaroAccessibilityService).apply {
                    text = "Hablar"
                    setOnClickListener { startVoiceFromOverlay() }
                }
            )
            addView(
                Button(this@OjoClaroAccessibilityService).apply {
                    text = "Callar"
                    setOnClickListener { silenceFromOverlay() }
                }
            )
            // Fase 3A: mientras hay misión del Agent Core, "Cancelar" la
            // termina por completo. "Callar" solo apaga la voz.
            if (GlobalAssistantService.isAgentMissionActive()) {
                addView(
                    Button(this@OjoClaroAccessibilityService).apply {
                        text = "Cancelar"
                        contentDescription = "Cancelar misión"
                        setOnClickListener { cancelAgentMissionFromOverlay() }
                    }
                )
            }
            addView(
                Button(this@OjoClaroAccessibilityService).apply {
                    text = "Cerrar"
                    setOnClickListener { collapseOverlayPanel() }
                }
            )
        }

    private fun overlayStateTitle(): String =
        when (overlayState) {
            OverlayState.IDLE -> "Estela"
            OverlayState.READING -> "Leyendo pantalla"
            OverlayState.LISTENING -> "Escuchando"
            OverlayState.SPEAKING -> "Hablando"
        }

    private fun readScreenFromOverlay() {
        overlayState = OverlayState.READING
        showAccessibilityOverlay(expanded = false)
        mainHandler.postDelayed({
            val result = overlayScreenUnderstandingUseCase.handle(OVERLAY_READ_SCREEN_COMMAND)
            val spokenText = when (result) {
                ScreenUnderstandingResult.NotAScreenCommand ->
                    "No pude iniciar la lectura de pantalla."
                is ScreenUnderstandingResult.NeedsAccessibilityService ->
                    result.spokenText
                is ScreenUnderstandingResult.Spoken ->
                    result.spokenText
            }
            lastOverlayReadRouteActive = true
            logOverlayReadOutcome(result, ttsRequested = true)
            speechController.speak(spokenText, force = true)
        }, OVERLAY_ACTION_DELAY_MILLIS)
    }

    private fun startVoiceFromOverlay() {
        overlayState = OverlayState.LISTENING
        showAccessibilityOverlay(expanded = false)
        val targetPackage = readActiveWindowPackageName()
        logOverlayVoiceRequested(targetPackage)
        GlobalAssistantService.startOverlayVoice(
            context = this,
            sourcePackageName = targetPackage,
            startListeningDelayMillis = OVERLAY_VOICE_START_DELAY_MILLIS
        )
    }

    private fun silenceFromOverlay() {
        overlayState = OverlayState.IDLE
        speechController.stop()
        GlobalAssistantService.requestSilence(this)
        showAccessibilityOverlay(expanded = false)
    }

    /**
     * Contrae el panel dejando visible el botón "Estela", sin efectos
     * secundarios. A diferencia de "Callar", NO detiene el TTS ni la escucha:
     * solo oculta el menú. Si Estela está hablando, sigue hablando. El botón
     * lateral nunca se destruye: showAccessibilityOverlay reconstruye el
     * colapsado.
     */
    private fun collapseOverlayPanel() {
        showAccessibilityOverlay(expanded = false)
    }

    /** Fase 3A: cancela la misión completa del Agent Core y contrae el panel. */
    private fun cancelAgentMissionFromOverlay() {
        GlobalAssistantService.requestAgentMissionCancel(this)
        showAccessibilityOverlay(expanded = false)
    }

    private fun selectReadableWindowRoot(): ReadableWindowRoot? {
        val windowRoots = runCatching { windows.orEmpty() }
            .getOrDefault(emptyList())
            .mapNotNull { window ->
                val root = runCatching { window.root }.getOrNull() ?: return@mapNotNull null
                val packageName = runCatching { root.packageName?.toString() }.getOrNull()
                val windowType = runCatching { window.type }.getOrNull()
                ReadableWindowRoot(
                    root = root,
                    packageName = packageName,
                    className = runCatching { root.className?.toString() }.getOrNull(),
                    childCount = runCatching { root.childCount }.getOrDefault(0),
                    windowType = windowType,
                    source = if (isExternalApplicationWindow(windowType, packageName)) {
                        SOURCE_REAL_EXTERNAL_ACCESSIBILITY_WINDOW
                    } else {
                        SOURCE_REAL_ACCESSIBILITY_WINDOW
                    },
                    ownPackageSelected = packageName == this@OjoClaroAccessibilityService.packageName,
                    overlayWindowSelected = isAccessibilityOverlayWindow(windowType),
                    active = runCatching { window.isActive }.getOrDefault(false),
                    focused = runCatching { window.isFocused }.getOrDefault(false)
                )
            }

        windowRoots.firstOrNull {
            isExternalApplicationWindow(it.windowType, it.packageName) && (it.active || it.focused)
        }?.let { return it }

        windowRoots.firstOrNull { isExternalApplicationWindow(it.windowType, it.packageName) }
            ?.let { return it }

        windowRoots.firstOrNull {
            it.active && !it.overlayWindowSelected && it.ownPackageSelected.not()
        }?.let { return it }

        val activeRoot = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val packageName = runCatching { activeRoot.packageName?.toString() }.getOrNull()
        return ReadableWindowRoot(
            root = activeRoot,
            packageName = packageName,
            className = runCatching { activeRoot.className?.toString() }.getOrNull(),
            childCount = runCatching { activeRoot.childCount }.getOrDefault(0),
            windowType = null,
            source = SOURCE_ACTIVE_ACCESSIBILITY_WINDOW,
            ownPackageSelected = packageName == this.packageName,
            overlayWindowSelected = false,
            active = true,
            focused = true
        )
    }

    private fun isExternalApplicationWindow(windowType: Int?, packageName: String?): Boolean =
        windowType == AccessibilityWindowInfo.TYPE_APPLICATION &&
            !packageName.isNullOrBlank() &&
            packageName != this.packageName

    private fun isAccessibilityOverlayWindow(windowType: Int?): Boolean =
        windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

    private fun logOverlayReadOutcome(
        result: ScreenUnderstandingResult,
        ttsRequested: Boolean
    ) {
        if (!BuildConfig.DEBUG) return
        val tree = lastTreeReadDiagnostics
        val snapshotAgeMs = tree?.timestampMillis
            ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
            ?: -1L
        val summarizerResultPresent = result is ScreenUnderstandingResult.Spoken
        Log.i(
            SCREEN_DIAG_TAG,
            listOf(
                "route=$ROUTE_OVERLAY_SCREEN",
                "source=${sanitizeTraceToken(tree?.source ?: SOURCE_UNKNOWN)}",
                "targetPackage=${sanitizeTraceToken(tree?.rootPackage ?: "-")}",
                "windowType=${tree?.windowType ?: -1}",
                "ownPackageSelected=${tree?.ownPackageSelected ?: false}",
                "overlayWindowSelected=${tree?.overlayWindowSelected ?: false}",
                "rootAvailable=${tree?.rootAvailable ?: false}",
                "visitedNodes=${tree?.visitedNodes ?: 0}",
                "acceptedNodes=${tree?.acceptedNodes ?: 0}",
                "discardedNodes=${tree?.discardedNodes ?: 0}",
                "textsFoundCount=${tree?.textsFoundCount ?: 0}",
                "contentDescriptionsFoundCount=${tree?.contentDescriptionsFoundCount ?: 0}",
                "snapshotAgeMs=$snapshotAgeMs",
                "fallbackUsed=false",
                "summarizerResultPresent=$summarizerResultPresent",
                "ttsRequested=$ttsRequested",
                "finalState=SPEAKING"
            ).joinToString(" ")
        )
    }

    private fun logOverlayTtsCompleted() {
        if (!BuildConfig.DEBUG) return
        Log.i(
            SCREEN_DIAG_TAG,
            "route=$ROUTE_OVERLAY_SCREEN ttsCompleted=true finalState=IDLE"
        )
    }

    private fun logOverlayVoiceRequested(targetPackage: String?) {
        if (!BuildConfig.DEBUG) return
        Log.i(
            SCREEN_DIAG_TAG,
            listOf(
                "route=$ROUTE_OVERLAY_VOICE",
                "activityOpened=false",
                "targetPackage=${sanitizeTraceToken(targetPackage ?: "-")}",
                "listeningRequested=true",
                "duplicateRequest=false",
                "finalState=LISTENING"
            ).joinToString(" ")
        )
    }

    private fun sanitizeTraceToken(value: String): String =
        value
            .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .take(MAX_TRACE_TOKEN_LENGTH)

    private fun readActiveWindowText(): String {
        return RobotLoopInstrumentation.measure(RobotLoopMetric.ACCESSIBILITY_NODE_TRAVERSAL) {
            val root = selectReadableWindowRoot()?.root ?: return@measure ""

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
        return selectReadableWindowRoot()?.packageName
    }

    private fun readActiveWindowClassNameInternal(): String? {
        return selectReadableWindowRoot()?.className
    }

    /**
     * Trusted Contacts — lee el NÚMERO que WhatsApp muestra como IDENTIDAD del
     * chat/perfil abierto (cuando la persona no está agendada, el título es el
     * número). SÓLO mira nodos de cabecera/título por id estable; jamás el
     * cuerpo de los mensajes, así no confunde un número citado en una
     * conversación con el del contacto. Devuelve E.164/dígitos normalizados o
     * null. Uso en runtime para construir el deep link; el llamador lo redacta
     * (sólo longitud y últimos 4 en logs/voz). Nunca lo loguea entero.
     */
    private fun readVisibleWhatsAppPhoneNumberInternal(): String? {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) return null
        val root = selectReadableWindowRoot()?.root ?: return null
        for (viewId in WA_CONTACT_IDENTITY_IDS) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }
                .getOrNull().orEmpty()
            for (node in nodes) {
                val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
                if (!visible) continue
                val raw = runCatching { node.text?.toString() }.getOrNull()
                    ?: runCatching { node.contentDescription?.toString() }.getOrNull()
                    ?: continue
                SafeContactMemory.extractPhoneNumber(raw)?.let { return it }
            }
        }
        return null
    }

    /**
     * Blind Safety — TÍTULO visible de la cabecera del chat (nombre del contacto
     * si está agendado, o el número si no lo está). A diferencia de
     * [readVisibleWhatsAppPhoneNumberInternal] devuelve el texto CRUDO (no extrae
     * teléfono): sirve para ANUNCIAR el destino por voz y para comparar el label
     * esperado contra lo visible (labelMatches del verificador). Sólo lee nodos de
     * cabecera, nunca el cuerpo del chat. El llamador redacta longitudes en logs.
     */
    private fun readVisibleWhatsAppChatTitleInternal(): String? {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) return null
        val root = selectReadableWindowRoot()?.root ?: return null
        for (viewId in WA_CONTACT_IDENTITY_IDS) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }
                .getOrNull().orEmpty()
            for (node in nodes) {
                val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
                if (!visible) continue
                val raw = runCatching { node.text?.toString() }.getOrNull()
                    ?: runCatching { node.contentDescription?.toString() }.getOrNull()
                    ?: continue
                val trimmed = raw.replace(WHITESPACE_REGEX, " ").trim()
                if (trimmed.isNotBlank()) return trimmed.take(80)
            }
        }
        return null
    }

    /**
     * Navegación segura (Fase 2A): ejecuta el BACK global del sistema. Es una
     * acción reversible que nunca envía, borra, llama ni comparte. Devuelve
     * false si el servicio no puede ejecutarla.
     */
    private fun performGlobalBackInternal(): Boolean {
        val ok = runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }.getOrDefault(false)
        if (BuildConfig.DEBUG) {
            Log.i(NAV_TAG, "back globalAction=GLOBAL_ACTION_BACK ok=$ok")
        }
        return ok
    }

    /**
     * Navegación segura (Fase 2A): desplaza el primer contenedor scrollable
     * visible. Solo realiza ACTION_SCROLL_FORWARD/BACKWARD sobre nodos visibles
     * y scrollables; no toca nada más. Honesto: si no hay contenedor o ninguno
     * pudo moverse, devuelve NO_TARGET en vez de inventar un scroll.
     */
    private fun scrollVisibleContainerInternal(forward: Boolean): ScreenScrollOutcome {
        val root = selectReadableWindowRoot()?.root
            ?: return logScroll(forward, scrollableFound = false, ScreenScrollOutcome.UNAVAILABLE)
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectScrollableNodes(node = root, out = candidates, state = TraversalState(), depth = 0)
        val scrollableFound = candidates.isNotEmpty()
        if (!scrollableFound) return logScroll(forward, scrollableFound = false, ScreenScrollOutcome.NO_TARGET)
        for (node in candidates) {
            val moved = runCatching { node.performAction(action) }.getOrDefault(false)
            if (moved) return logScroll(forward, scrollableFound = true, ScreenScrollOutcome.SCROLLED)
        }
        return logScroll(forward, scrollableFound = true, ScreenScrollOutcome.NO_TARGET)
    }

    /**
     * Log de diagnóstico físico para navegación (solo debug). Reporta dirección,
     * si encontró contenedor scrollable y el resultado. NUNCA loguea contenido.
     */
    private fun logScroll(
        forward: Boolean,
        scrollableFound: Boolean,
        outcome: ScreenScrollOutcome
    ): ScreenScrollOutcome {
        if (BuildConfig.DEBUG) {
            Log.i(NAV_TAG, "scroll forward=$forward scrollableFound=$scrollableFound outcome=$outcome")
        }
        return outcome
    }

    private fun collectScrollableNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        state: TraversalState,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (out.size >= MAX_SCROLLABLE_CANDIDATES) return
        if (state.visitedNodes >= MAX_VISITED_NODES) return

        state.visitedNodes++

        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        val scrollable = runCatching { node.isScrollable }.getOrDefault(false)
        if (visible && scrollable) {
            out.add(node)
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectScrollableNodes(
                node = child,
                out = out,
                state = state,
                depth = depth + 1
            )
        }
    }

    private fun openVisibleWhatsAppChatByNameInternal(targetName: String): VisibleChatOpenResult {
        val packageName = readActiveWindowPackageName()
        if (!packageNameLooksLikeWhatsApp(packageName)) {
            return VisibleChatOpenResult.NotInWhatsApp(packageName)
        }

        val root = selectReadableWindowRoot()?.root
            ?: return VisibleChatOpenResult.NoMatch(targetName)

        val candidate = findVisibleChatClickCandidate(
            node = root,
            targetName = targetName,
            depth = 0
        ) ?: return VisibleChatOpenResult.NoMatch(targetName)

        val clickable = candidate.clickableNode
            ?: return VisibleChatOpenResult.Unsafe(
                displayName = candidate.displayName,
                reason = "no_clickable_ancestor"
            )

        if (!isSafeClickableChatNode(clickable)) {
            return VisibleChatOpenResult.Unsafe(
                displayName = candidate.displayName,
                reason = "sensitive_or_disabled_click_target"
            )
        }

        // NOTA (sprint WhatsApp 2026-06-15): abrir la fila por tap NO funciona en
        // la versión actual de WhatsApp — ni ACTION_CLICK ni un gesto sintético
        // ni un toque real navegan (los nombres son nodos semánticos virtuales
        // sin bounds tappables reales). Se deja ACTION_CLICK; GAS VERIFICA con
        // isInChat tras el intento y responde honesto si no abrió.
        val clicked = runCatching {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)

        return if (clicked) {
            VisibleChatOpenResult.Opened(candidate.displayName)
        } else {
            VisibleChatOpenResult.Failed(candidate.displayName, "action_click_failed")
        }
    }

    // --- V1.2: envío seguro de WhatsApp (leer borrador + tocar enviar) ---

    private fun readWhatsAppDraftInternal(): WhatsAppDraftReadResult {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppDraftReadResult.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root
            ?: return WhatsAppDraftReadResult.NoEntryField
        val entry = findWhatsAppEntryField(root)
            ?: return WhatsAppDraftReadResult.NoEntryField

        val showingHint = runCatching { entry.isShowingHintText }.getOrDefault(false)
        val text = runCatching { entry.text?.toString() }.getOrNull().orEmpty().trim()
        if (showingHint || text.isBlank()) return WhatsAppDraftReadResult.EmptyDraft

        if (BuildConfig.DEBUG) {
            Log.i(NAV_TAG, "whatsappDraftRead=true draftLen=${text.length}")
        }
        return WhatsAppDraftReadResult.Draft(text)
    }

    /**
     * Escribe [text] en el campo de texto del chat de WhatsApp con
     * ACTION_SET_TEXT y RELEE para confirmar que quedó exacto. Escribir un
     * borrador NO envía nada (el envío exige doble confirmación + toque
     * verificado). text vacío = limpiar (usado al cancelar). Nunca loguea el
     * contenido: solo longitudes.
     */
    private fun setWhatsAppDraftInternal(text: String): WhatsAppDraftSetResult {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppDraftSetResult.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root
            ?: return WhatsAppDraftSetResult.NoEntryField
        val entry = findWhatsAppEntryField(root)
            ?: return WhatsAppDraftSetResult.NoEntryField
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val set = runCatching {
            entry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
        if (!set) {
            if (BuildConfig.DEBUG) Log.i(NAV_TAG, "whatsappDraftSet ok=false reason=action_failed")
            return WhatsAppDraftSetResult.SetFailed
        }
        val after = selectReadableWindowRoot()?.root?.let(::findWhatsAppEntryField)
            ?.let { runCatching { it.text?.toString() }.getOrNull().orEmpty().trim() }
            .orEmpty()
        return if (text.isBlank() || sameDraftText(after, text)) {
            if (BuildConfig.DEBUG) Log.i(NAV_TAG, "whatsappDraftSet ok=true draftLen=${text.length}")
            WhatsAppDraftSetResult.SetOk
        } else {
            if (BuildConfig.DEBUG) Log.i(NAV_TAG, "whatsappDraftSet ok=false reason=mismatch_after_set")
            WhatsAppDraftSetResult.MismatchAfterSet
        }
    }

    /**
     * Toca el botón de enviar de WhatsApp SOLO si el campo de texto coincide
     * exactamente (espacios normalizados) con [expectedMessage], que es lo
     * que Estela leyó en voz alta y la persona confirmó. Si el campo cambió,
     * no se envía nada.
     */
    private fun tapWhatsAppSendInternal(expectedMessage: String): WhatsAppSendTapResult {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppSendTapResult.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root
            ?: return WhatsAppSendTapResult.NoSendButton
        val entry = findWhatsAppEntryField(root)
            ?: return WhatsAppSendTapResult.NoSendButton

        val fieldText = runCatching { entry.text?.toString() }.getOrNull().orEmpty()
        if (!sameDraftText(fieldText, expectedMessage)) {
            if (BuildConfig.DEBUG) {
                Log.i(
                    NAV_TAG,
                    "whatsappSendBlocked=field_mismatch fieldLen=${fieldText.trim().length} " +
                        "expectedLen=${expectedMessage.trim().length}"
                )
            }
            return WhatsAppSendTapResult.FieldMismatch
        }

        val sendButton = findWhatsAppSendButton(root)
            ?: return WhatsAppSendTapResult.NoSendButton

        val clicked = runCatching {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)

        if (BuildConfig.DEBUG) {
            Log.i(NAV_TAG, "whatsappSendTap clicked=$clicked draftLen=${expectedMessage.length}")
        }
        return if (clicked) WhatsAppSendTapResult.Sent else WhatsAppSendTapResult.ClickFailed
    }

    // --- V1.11: videollamada de WhatsApp (detectar + tocar SOLO confirmado) ---

    /**
     * Botón de videollamada del chat abierto, por etiqueta accesible
     * ("Videollamada"/"Video call"). A propósito NO matchea "video" a secas:
     * los mensajes de video del chat usan esa descripción y tocarlos abriría
     * un video en vez de llamar.
     */
    private fun findWhatsAppVideoCallButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byDescription = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByDescription(
            node = root,
            out = byDescription,
            state = TraversalState(),
            depth = 0
        ) { description ->
            description.contains("videollamada") ||
                description.contains("video llamada") ||
                description.contains("video call")
        }
        return byDescription.firstOrNull(::isSafeSendTapTarget)
    }

    private fun hasWhatsAppVideoCallButtonInternal(): WhatsAppVideoCallCheck {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppVideoCallCheck.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root ?: return WhatsAppVideoCallCheck.NoButton
        val present = findWhatsAppVideoCallButton(root) != null
        if (BuildConfig.DEBUG) Log.i(NAV_TAG, "whatsappVideoCallButton present=$present")
        return if (present) WhatsAppVideoCallCheck.Present else WhatsAppVideoCallCheck.NoButton
    }

    /**
     * Toca el botón de videollamada. SOLO se llama después de que la persona
     * confirmó en voz alta ("¿Querés que lo toque?" → sí). Re-verifica
     * paquete y botón en el momento del toque: si la pantalla cambió, no toca.
     */
    private fun tapWhatsAppVideoCallInternal(): WhatsAppVideoCallTapResult {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppVideoCallTapResult.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root
            ?: return WhatsAppVideoCallTapResult.NoButton
        val button = findWhatsAppVideoCallButton(root)
            ?: return WhatsAppVideoCallTapResult.NoButton
        val clicked = runCatching {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
        if (BuildConfig.DEBUG) Log.i(NAV_TAG, "whatsappVideoCallTap clicked=$clicked")
        return if (clicked) {
            WhatsAppVideoCallTapResult.Tapped
        } else {
            WhatsAppVideoCallTapResult.ClickFailed
        }
    }

    private fun playVisibleWhatsAppAudioInternal(): WhatsAppAudioPlayResult {
        if (!packageNameLooksLikeWhatsApp(readActiveWindowPackageName())) {
            return WhatsAppAudioPlayResult.NotInWhatsApp
        }
        val root = selectReadableWindowRoot()?.root
            ?: return WhatsAppAudioPlayResult.NoAudioVisible

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectAudioPlayCandidates(root, candidates, TraversalState(), depth = 0)
        // El último candidato en orden de recorrido es el mensaje más
        // reciente visible (el de abajo): "reproducí el último audio".
        val target = candidates.lastOrNull()
            ?: return WhatsAppAudioPlayResult.NoAudioVisible

        val clicked = runCatching {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)

        if (BuildConfig.DEBUG) {
            Log.i(NAV_TAG, "whatsappAudioPlay candidates=${candidates.size} clicked=$clicked")
        }
        return if (clicked) WhatsAppAudioPlayResult.Playing else WhatsAppAudioPlayResult.ClickFailed
    }

    private fun findWhatsAppEntryField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        runCatching {
            root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
                ?.firstOrNull { node ->
                    runCatching { node.isVisibleToUser && node.isEditable }.getOrDefault(false)
                }
        }.getOrNull()

    private fun findWhatsAppSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = runCatching {
            root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                ?.firstOrNull(::isSafeSendTapTarget)
        }.getOrNull()
        if (byId != null) return byId

        // Fallback por descripción exacta ("Enviar"/"Send"): WhatsApp cambia
        // ids entre versiones, pero la etiqueta accesible es estable.
        val byDescription = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByDescription(
            node = root,
            out = byDescription,
            state = TraversalState(),
            depth = 0
        ) { description -> description == "enviar" || description == "send" }
        return byDescription.firstOrNull(::isSafeSendTapTarget)
    }

    private fun isSafeSendTapTarget(node: AccessibilityNodeInfo): Boolean =
        runCatching {
            node.isVisibleToUser && node.isEnabled && node.isClickable
        }.getOrDefault(false)

    /** Igualdad de borrador con espacios normalizados: ni más ni menos. */
    private fun sameDraftText(fieldText: String, expectedMessage: String): Boolean {
        fun norm(value: String) = value.replace(Regex("\\s+"), " ").trim()
        val field = norm(fieldText)
        return field.isNotBlank() && field == norm(expectedMessage)
    }

    // --- V1.12: Instagram Direct (detectar mucho, tocar poco y re-verificado) ---

    /**
     * Clasifica la pantalla de Instagram por MARCADORES de UI (ids reales),
     * nunca por activity: Instagram es single-activity y el nombre de la
     * activity no distingue feed, inbox ni chat. El título del chat se
     * devuelve para hablarlo; jamás se loguea (solo su longitud).
     */
    private fun instagramScreenCheckInternal(): InstagramScreenCheck {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramScreenCheck(InstagramScreenState.NOT_IN_INSTAGRAM)
        }
        val root = selectReadableWindowRoot()?.root
            ?: return InstagramScreenCheck(InstagramScreenState.UNKNOWN)

        val composer = firstVisibleByViewId(root, IG_ID_COMPOSER)
        val header = firstVisibleByViewId(root, IG_ID_THREAD_HEADER)
        if (composer != null || header != null) {
            val title = firstVisibleByViewId(root, IG_ID_HEADER_TITLE)
                ?.let { runCatching { it.text?.toString() }.getOrNull() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            // V1.12.1 — el subtítulo del header trae el handle (so_roomero):
            // necesario para resolver pedidos por username; no se loguea.
            val subtitle = firstVisibleByViewId(root, IG_ID_HEADER_SUBTITLE)
                ?.let { runCatching { it.text?.toString() }.getOrNull() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            logInstagram(
                "instagramScreenState state=THREAD titleLen=${title?.length ?: 0} " +
                    "subtitleLen=${subtitle?.length ?: 0}"
            )
            return InstagramScreenCheck(
                InstagramScreenState.THREAD,
                threadTitle = title,
                threadSubtitle = subtitle
            )
        }
        if (firstVisibleByViewId(root, IG_ID_INBOX_LIST) != null ||
            firstVisibleByViewId(root, IG_ID_INBOX_ACTION_BAR) != null
        ) {
            logInstagram("instagramScreenState state=INBOX")
            return InstagramScreenCheck(InstagramScreenState.INBOX)
        }
        if (firstVisibleByViewId(root, IG_ID_DIRECT_TAB) != null) {
            logInstagram("instagramScreenState state=FEED_OR_HOME")
            return InstagramScreenCheck(InstagramScreenState.FEED_OR_HOME)
        }
        logInstagram("instagramScreenState state=UNKNOWN")
        return InstagramScreenCheck(InstagramScreenState.UNKNOWN)
    }

    /** Tab "Mensaje" (Direct) de la barra inferior: id real + etiqueta EXACTA
     *  como fallback. Abrir el inbox no envía ni confirma nada. */
    private fun openInstagramDirectTabInternal(): InstagramTapOutcome {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramTapOutcome.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: return InstagramTapOutcome.NotFound
        val byId = firstVisibleByViewId(root, IG_ID_DIRECT_TAB)?.takeIf(::isSafeSendTapTarget)
        val target = byId ?: run {
            val byDescription = mutableListOf<AccessibilityNodeInfo>()
            collectNodesByDescription(root, byDescription, TraversalState(), 0) { description ->
                description == "mensaje" || description == "mensajes" || description == "direct"
            }
            byDescription.firstOrNull(::isSafeSendTapTarget)
        }
        if (target == null) {
            logInstagram("instagramDirectTab present=false")
            return InstagramTapOutcome.NotFound
        }
        val clicked = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            .getOrDefault(false)
        logInstagram("instagramDirectTab present=true clicked=$clicked")
        return if (clicked) InstagramTapOutcome.Tapped else InstagramTapOutcome.ClickFailed
    }

    /**
     * Abre un chat del inbox por nombre visible. Matching conservador sobre
     * el nodo de username de cada fila (id real): exacto > empieza-con >
     * prefijo común largo ("sofie" encuentra "Sofia"). La etiqueta REAL
     * matcheada se devuelve para que Estela la diga en voz alta ANTES de
     * cualquier confirmación: ese anuncio es el seguro contra fuzzy-matches.
     */
    private fun openInstagramChatByVisibleNameInternal(targetName: String): InstagramChatOpenResult {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramChatOpenResult.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: return InstagramChatOpenResult.NotInInbox
        if (firstVisibleByViewId(root, IG_ID_INBOX_LIST) == null &&
            firstVisibleByViewId(root, IG_ID_INBOX_ACTION_BAR) == null
        ) {
            return InstagramChatOpenResult.NotInInbox
        }

        val usernames = runCatching {
            root.findAccessibilityNodeInfosByViewId(IG_ID_INBOX_USERNAME)
        }.getOrNull().orEmpty()
            .filter { runCatching { it.isVisibleToUser }.getOrDefault(false) }

        var bestNode: AccessibilityNodeInfo? = null
        var bestLabel = ""
        var bestScore = Int.MAX_VALUE
        var bestField = "row_username"
        var bestCandidateLen = 0
        for (node in usernames) {
            val label = runCatching { node.text?.toString() }.getOrNull()?.trim().orEmpty()
            if (label.isBlank()) continue
            val score = InstagramNameMatcher.score(label, targetName)
            if (score != InstagramNameMatcher.NO_MATCH && score < bestScore) {
                bestNode = node
                bestLabel = label
                bestScore = score
                bestCandidateLen = label.length
                if (score == 0) break
            }
        }

        val rowNodes = runCatching {
            root.findAccessibilityNodeInfosByViewId(IG_ID_INBOX_ROW)
        }.getOrNull().orEmpty()
            .filter { runCatching { it.isVisibleToUser }.getOrDefault(false) }

        // V1.12.1 — fallback: metadata accesible de la FILA. Solo el primer
        // segmento del desc (el nombre); el resto trae preview de mensajes
        // privados y JAMÁS se usa para matchear ni se loguea.
        if (bestNode == null) {
            for (row in rowNodes) {
                val namePart = runCatching { row.contentDescription?.toString() }.getOrNull()
                    ?.substringBefore(",")?.trim().orEmpty()
                if (namePart.isBlank()) continue
                val score = InstagramNameMatcher.score(namePart, targetName)
                if (score != InstagramNameMatcher.NO_MATCH && score < bestScore) {
                    bestNode = row
                    bestLabel = namePart
                    bestScore = score
                    bestField = "row_desc"
                    bestCandidateLen = namePart.length
                    if (score == 0) break
                }
            }
        }

        // V1.12.2: metadata descendiente del avatar/story. El recorrido es
        // row-first: la metadata solo identifica la fila actual, y el toque
        // final se hace sobre el row del chat, nunca sobre el avatar.
        if (bestNode == null) {
            for (row in rowNodes) {
                val match = findInstagramInboxAvatarMetadataMatch(row, targetName) ?: continue
                if (match.score >= bestScore) continue
                bestNode = row
                bestLabel = match.label
                bestScore = match.score
                bestField = "avatar_metadata_row_descendant"
                bestCandidateLen = match.candidateLen
                if (match.score == 0) break
            }
        }

        val matched = bestNode ?: run {
            logInstagram("instagramChatOpen match=false queryLen=${targetName.length}")
            return InstagramChatOpenResult.NoMatch(targetName)
        }

        val clickable = clickableSelfOrAncestor(matched)
            ?: return InstagramChatOpenResult.Unsafe("no_clickable_ancestor")
        val clicked = runCatching { clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            .getOrDefault(false)
        logInstagram(
            "instagramChatOpen match=true field=$bestField score=$bestScore " +
                "queryLen=${targetName.length} candidateLen=$bestCandidateLen clicked=$clicked"
        )
        return if (clicked) {
            InstagramChatOpenResult.Opened(bestLabel)
        } else {
            InstagramChatOpenResult.ClickFailed
        }
    }

    private data class InstagramInboxAvatarMetadataMatch(
        val label: String,
        val score: Int,
        val candidateLen: Int
    )

    private fun findInstagramInboxAvatarMetadataMatch(
        row: AccessibilityNodeInfo,
        targetName: String
    ): InstagramInboxAvatarMetadataMatch? =
        findInstagramInboxAvatarMetadataMatch(row, targetName, TraversalState(), 0)

    private fun findInstagramInboxAvatarMetadataMatch(
        node: AccessibilityNodeInfo,
        targetName: String,
        state: TraversalState,
        depth: Int
    ): InstagramInboxAvatarMetadataMatch? {
        if (depth > MAX_TREE_DEPTH) return null
        if (state.visitedNodes >= MAX_VISITED_NODES) return null
        state.visitedNodes++

        var best: InstagramInboxAvatarMetadataMatch? = null
        val viewId = runCatching { node.viewIdResourceName }.getOrNull()
        if (viewId == IG_ID_INBOX_AVATAR &&
            runCatching { node.isVisibleToUser }.getOrDefault(false)
        ) {
            val metadata = runCatching { node.contentDescription?.toString() }
                .getOrNull()?.trim().orEmpty()
            val score = InstagramNameMatcher.scoreInboxAvatarMetadata(metadata, targetName)
            if (score != InstagramNameMatcher.NO_MATCH) {
                val label = InstagramNameMatcher.extractInboxAvatarHandle(metadata) ?: targetName
                best = InstagramInboxAvatarMetadataMatch(
                    label = label,
                    score = score,
                    candidateLen = label.length
                )
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val childMatch = findInstagramInboxAvatarMetadataMatch(
                child,
                targetName,
                state,
                depth + 1
            ) ?: continue
            val currentBest = best
            if (currentBest == null || childMatch.score < currentBest.score) {
                best = childMatch
                if (childMatch.score == 0) break
            }
        }
        return best
    }

    private fun findInstagramComposer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        runCatching {
            root.findAccessibilityNodeInfosByViewId(IG_ID_COMPOSER)
                ?.firstOrNull { node ->
                    runCatching { node.isVisibleToUser && node.isEditable }.getOrDefault(false)
                }
        }.getOrNull()

    private fun readInstagramDraftText(composer: AccessibilityNodeInfo): String {
        val showingHint = runCatching { composer.isShowingHintText }.getOrDefault(false)
        if (showingHint) return ""
        return runCatching { composer.text?.toString() }.getOrNull().orEmpty().trim()
    }

    /**
     * Escribe el borrador en el composer con ACTION_SET_TEXT y RELEE el campo:
     * lo que quedó escrito debe ser exacto a lo pedido (texto vacío = limpiar,
     * usado al cancelar). Escribir un borrador no envía nada: enviar exige la
     * confirmación fuerte y el toque verificado de [tapInstagramSendInternal].
     */
    private fun setInstagramDraftTextInternal(text: String): InstagramDraftSetResult {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramDraftSetResult.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: return InstagramDraftSetResult.NotInThread
        val composer = findInstagramComposer(root) ?: return InstagramDraftSetResult.NoComposer
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val set = runCatching {
            composer.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
        if (!set) {
            logInstagram("instagramDraftSet ok=false reason=action_failed")
            return InstagramDraftSetResult.SetFailed
        }
        val after = selectReadableWindowRoot()?.root?.let(::findInstagramComposer)
            ?.let(::readInstagramDraftText).orEmpty()
        return if (text.isBlank() || sameDraftText(after, text)) {
            logInstagram("instagramDraftSet ok=true draftLen=${text.length}")
            InstagramDraftSetResult.SetOk
        } else {
            logInstagram("instagramDraftSet ok=false reason=mismatch_after_set")
            InstagramDraftSetResult.MismatchAfterSet
        }
    }

    /**
     * Toca ENVIAR de Instagram SOLO si el campo coincide exactamente con lo
     * que la persona escuchó y confirmó con palabra fuerte. Si el campo
     * cambió, no se envía nada (mismo contrato que WhatsApp).
     */
    private fun tapInstagramSendInternal(expectedMessage: String): InstagramSendTapResult {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramSendTapResult.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: return InstagramSendTapResult.NoComposer
        val composer = findInstagramComposer(root) ?: return InstagramSendTapResult.NoComposer
        val fieldText = readInstagramDraftText(composer)
        if (!sameDraftText(fieldText, expectedMessage)) {
            logInstagram(
                "instagramSendBlocked=field_mismatch fieldLen=${fieldText.length} " +
                    "expectedLen=${expectedMessage.trim().length}"
            )
            return InstagramSendTapResult.FieldMismatch
        }
        val sendButton = findInstagramSendButton(root)
            ?: return InstagramSendTapResult.NoSendButton
        val clicked = runCatching { sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            .getOrDefault(false)
        logInstagram("instagramSendTap clicked=$clicked draftLen=${expectedMessage.length}")
        return if (clicked) InstagramSendTapResult.Sent else InstagramSendTapResult.ClickFailed
    }

    private fun findInstagramSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = runCatching {
            root.findAccessibilityNodeInfosByViewId(IG_ID_SEND_BUTTON)
                ?.firstOrNull(::isSafeSendTapTarget)
        }.getOrNull()
        if (byId != null) return byId

        // Visto en device: la etiqueta "Enviar" vive en un hijo NO clickeable
        // del botón. Matchear la descripción exacta y subir al ancestro
        // clickeable real.
        val byDescription = mutableListOf<AccessibilityNodeInfo>()
        collectDescribedNodes(root, byDescription, TraversalState(), 0) { description ->
            description == "enviar" || description == "send"
        }
        return byDescription.asSequence()
            .mapNotNull(::clickableSelfOrAncestor)
            .firstOrNull()
    }

    /**
     * Botón de videollamada del chat de Instagram, por etiqueta accesible
     * fuerte ("Videollamada", la misma que WhatsApp en español). A propósito
     * NO matchea "video" a secas: un mensaje con video del hilo usaría esa
     * descripción y tocarlo abriría un video en vez de llamar.
     */
    private fun findInstagramVideoCallButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byDescription = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByDescription(root, byDescription, TraversalState(), 0) { description ->
            description.contains("videollamada") ||
                description.contains("video llamada") ||
                description.contains("video call")
        }
        return byDescription.firstOrNull(::isSafeSendTapTarget)
    }

    private fun hasInstagramVideoCallButtonInternal(): InstagramVideoCallCheck {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramVideoCallCheck.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: return InstagramVideoCallCheck.NoButton
        if (findInstagramComposer(root) == null &&
            firstVisibleByViewId(root, IG_ID_THREAD_HEADER) == null
        ) {
            logInstagram("instagramVideoCallButton present=false reason=not_in_thread")
            return InstagramVideoCallCheck.NotInThread
        }
        val present = findInstagramVideoCallButton(root) != null
        logInstagram("instagramVideoCallButton present=$present")
        return if (present) InstagramVideoCallCheck.Present else InstagramVideoCallCheck.NoButton
    }

    /**
     * Toca videollamada en Instagram SOLO tras confirmación hablada. Re-verifica
     * paquete EXACTO, pantalla de chat y botón EN el momento del toque: si algo
     * cambió desde la pregunta, no toca nada.
     */
    private fun tapInstagramVideoCallInternal(): InstagramVideoCallTapResult {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramVideoCallTapResult.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root
            ?: return InstagramVideoCallTapResult.NoButton
        if (findInstagramComposer(root) == null &&
            firstVisibleByViewId(root, IG_ID_THREAD_HEADER) == null
        ) {
            return InstagramVideoCallTapResult.NotInThread
        }
        val button = findInstagramVideoCallButton(root)
            ?: return InstagramVideoCallTapResult.NoButton
        val clicked = runCatching { button.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            .getOrDefault(false)
        logInstagram("instagramVideoCallTap clicked=$clicked")
        return if (clicked) {
            InstagramVideoCallTapResult.Tapped
        } else {
            InstagramVideoCallTapResult.ClickFailed
        }
    }

    /**
     * SOLO detección del botón de mensaje de voz (informativo para la guía
     * hablada). Grabar exige mantener presionado: gesto continuo, prohibido
     * de automatizar por contrato — esta clase no tiene NINGUNA función que
     * toque este botón.
     */
    private fun findInstagramAudioButtonInternal(): InstagramAudioButtonInfo {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            return InstagramAudioButtonInfo(present = false)
        }
        val root = selectReadableWindowRoot()?.root
            ?: return InstagramAudioButtonInfo(present = false)
        val byId = firstVisibleByViewId(root, IG_ID_VOICE_BUTTON)
        val node = byId ?: run {
            val described = mutableListOf<AccessibilityNodeInfo>()
            collectDescribedNodes(root, described, TraversalState(), 0) { description ->
                description.contains("mensaje de voz") || description.contains("voice message")
            }
            described.firstOrNull()
        }
        val label = node?.let { runCatching { it.contentDescription?.toString() }.getOrNull() }
        logInstagram("instagramAudioButton present=${node != null}")
        return InstagramAudioButtonInfo(present = node != null, label = label)
    }

    /**
     * V1.12.1 — botón de COLGAR de la llamada de Instagram, por etiqueta
     * accesible FUERTE de fin de llamada. Jamás matchea "salir", "leave" ni
     * "terminar" a secas: exigen contexto de llamada.
     */
    private fun findInstagramEndCallButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byDescription = mutableListOf<AccessibilityNodeInfo>()
        collectDescribedNodes(root, byDescription, TraversalState(), 0) { description ->
            description.contains("finalizar llamada") ||
                description.contains("terminar llamada") ||
                description.contains("salir de la llamada") ||
                description.contains("abandonar llamada") ||
                description.contains("end call") ||
                description.contains("leave call") ||
                description.contains("hang up") ||
                description.contains("colgar")
        }
        return byDescription.asSequence()
            .mapNotNull(::clickableSelfOrAncestor)
            .firstOrNull()
    }

    /**
     * Corte de llamada de Instagram SOLO para limpieza de QA (broadcast de
     * debug; ninguna frase de usuario llega acá). El KEYCODE_ENDCALL no
     * corta llamadas de Instagram (verificado en device: RtcCallActivity lo
     * ignora), y el force-stop mata el servicio de accesibilidad: este es el
     * camino de contención seguro. Guardas: paquete EXACTO, pantalla de
     * llamada real (sin composer/inbox/tab visibles) y botón fuerte; si no
     * hay botón accesible, NO toca nada y el corte queda humano.
     */
    private fun tapInstagramEndCallForQaInternal(): InstagramEndCallTapResult {
        if (!packageNameLooksLikeInstagram(readActiveWindowPackageName())) {
            logInstagram("instagramEndCall outcome=not_in_instagram clicked=false")
            return InstagramEndCallTapResult.NotInInstagram
        }
        val root = selectReadableWindowRoot()?.root ?: run {
            logInstagram("instagramEndCall outcome=no_root clicked=false")
            return InstagramEndCallTapResult.NoButton
        }
        val looksLikeCallScreen = findInstagramComposer(root) == null &&
            firstVisibleByViewId(root, IG_ID_INBOX_LIST) == null &&
            firstVisibleByViewId(root, IG_ID_DIRECT_TAB) == null
        if (!looksLikeCallScreen) {
            logInstagram("instagramEndCall outcome=not_in_call_screen clicked=false")
            return InstagramEndCallTapResult.NotInCallScreen
        }
        val button = findInstagramEndCallButton(root) ?: run {
            logInstagram("instagramEndCall outcome=not_found clicked=false")
            return InstagramEndCallTapResult.NoButton
        }
        val clicked = runCatching { button.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            .getOrDefault(false)
        logInstagram("instagramEndCall outcome=${if (clicked) "performed" else "click_failed"} clicked=$clicked")
        return if (clicked) {
            InstagramEndCallTapResult.Tapped
        } else {
            InstagramEndCallTapResult.ClickFailed
        }
    }

    private fun firstVisibleByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? =
        runCatching {
            root.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull { runCatching { it.isVisibleToUser }.getOrDefault(false) }
        }.getOrNull()

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops <= MAX_CLICKABLE_ANCESTOR_HOPS) {
            if (isSafeSendTapTarget(current)) return current
            current = runCatching { current.parent }.getOrNull()
            hops++
        }
        return null
    }

    /** Como [collectNodesByDescription] pero SIN exigir clickable: para
     *  etiquetas que viven en hijos no clickeables y para detección pura. */
    private fun collectDescribedNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        state: TraversalState,
        depth: Int,
        matches: (String) -> Boolean
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (state.visitedNodes >= MAX_VISITED_NODES) return
        state.visitedNodes++

        val description = runCatching { node.contentDescription?.toString() }.getOrNull()
        if (description != null) {
            val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (visible && matches(foldInstagramLabel(description))) {
                out.add(node)
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectDescribedNodes(child, out, state, depth + 1, matches)
        }
    }

    private fun foldInstagramLabel(value: String): String =
        value.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .trim()

    private fun logInstagram(message: String) {
        if (BuildConfig.DEBUG) Log.i(IG_NAV_TAG, message)
    }

    private fun collectAudioPlayCandidates(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        state: TraversalState,
        depth: Int
    ) {
        collectNodesByDescription(node, out, state, depth) { description ->
            description.contains("reproducir") || description == "play" ||
                description.contains("play voice message") ||
                description.contains("mensaje de voz")
        }
    }

    private fun collectNodesByDescription(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        state: TraversalState,
        depth: Int,
        matches: (String) -> Boolean
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (state.visitedNodes >= MAX_VISITED_NODES) return
        state.visitedNodes++

        val description = runCatching { node.contentDescription?.toString() }.getOrNull()
        if (description != null) {
            val folded = description.lowercase()
                .replace('í', 'i').replace('ó', 'o').replace('á', 'a')
                .replace('é', 'e').replace('ú', 'u')
            val clickableSelf = runCatching {
                node.isVisibleToUser && node.isEnabled && node.isClickable
            }.getOrDefault(false)
            if (matches(folded) && clickableSelf) {
                out.add(node)
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectNodesByDescription(child, out, state, depth + 1, matches)
        }
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
            if (MULTI_WINDOW_ACCESSIBILITY_SNAPSHOT) multiWindowNodeSummaries()
            else singleRootNodeSummaries()
        }
    }

    /** Comportamiento histórico: una sola ventana (selectReadableWindowRoot). */
    private fun singleRootNodeSummaries(): List<AccessibilityNodeSummary> {
        val readableRoot = selectReadableWindowRoot()
        if (readableRoot == null) {
            lastTreeReadDiagnostics = TraversalDiagnosticState(
                rootAvailable = false,
                timestampMillis = System.currentTimeMillis()
            ).toDiagnostics()
            return emptyList()
        }
        val diagnosticState = TraversalDiagnosticState(
            rootAvailable = true,
            rootPackage = readableRoot.packageName,
            rootClass = readableRoot.className,
            rootChildCount = readableRoot.childCount,
            source = readableRoot.source,
            windowType = readableRoot.windowType,
            ownPackageSelected = readableRoot.ownPackageSelected,
            overlayWindowSelected = readableRoot.overlayWindowSelected,
            timestampMillis = System.currentTimeMillis()
        )
        val out = mutableListOf<AccessibilityNodeSummary>()
        collectVisibleNodes(node = readableRoot.root, out = out, state = diagnosticState, depth = 0)
        lastTreeReadDiagnostics = diagnosticState.toDiagnostics()
        return out
    }

    /**
     * Snapshot MULTI-VENTANA: lee TODAS las ventanas de la app foreground (no solo
     * la de arriba), fusiona y deduplica. Recupera nodos perdidos en ventanas
     * hermanas / Compose (p.ej. el carrusel de Uber detrás del overlay de rating).
     * Si no hay ventanas de app, cae a rootInActiveWindow (comportamiento viejo).
     * Mantiene el filtro de visibleToUser y los límites (más altos, acotados).
     */
    private fun multiWindowNodeSummaries(): List<AccessibilityNodeSummary> {
        val windowList = runCatching { windows.orEmpty() }.getOrDefault(emptyList())
        val descriptors = windowList.mapIndexed { i, w ->
            val root = runCatching { w.root }.getOrNull()
            val pkg = root?.let { runCatching { it.packageName?.toString() }.getOrNull() }
            val type = runCatching { w.type }.getOrNull()
            WindowDescriptor(
                index = i,
                packageName = pkg,
                isApplicationWindow = isExternalApplicationWindow(type, pkg),
                isOverlay = isAccessibilityOverlayWindow(type),
                isSystemUi = pkg == ReadableWindowPlanner.SYSTEM_UI_PACKAGE,
                active = runCatching { w.isActive }.getOrDefault(false),
                focused = runCatching { w.isFocused }.getOrDefault(false)
            )
        }
        val chosen = ReadableWindowPlanner.choose(descriptors).toSet()
        val roots: List<AccessibilityNodeInfo> = if (chosen.isNotEmpty()) {
            windowList.filterIndexed { i, _ -> i in chosen }
                .mapNotNull { runCatching { it.root }.getOrNull() }
        } else {
            listOfNotNull(runCatching { rootInActiveWindow }.getOrNull())
        }
        if (roots.isEmpty()) {
            lastTreeReadDiagnostics = TraversalDiagnosticState(
                rootAvailable = false,
                timestampMillis = System.currentTimeMillis()
            ).toDiagnostics()
            return emptyList()
        }
        val diagnosticState = TraversalDiagnosticState(
            rootAvailable = true,
            rootPackage = descriptors.firstOrNull { it.index in chosen }?.packageName,
            source = SOURCE_REAL_ACCESSIBILITY_WINDOW,
            timestampMillis = System.currentTimeMillis()
        )
        val collected = mutableListOf<AccessibilityNodeSummary>()
        val perRoot = mutableListOf<Int>()
        for (root in roots) {
            val before = collected.size
            collectVisibleNodes(
                node = root,
                out = collected,
                state = diagnosticState,
                depth = 0,
                emitCap = MAX_NODES_MULTIWINDOW,
                visitedCap = MAX_VISITED_MULTIWINDOW,
                charsCap = MAX_CHARS_MULTIWINDOW
            )
            perRoot.add(collected.size - before)
            if (collected.size >= MAX_NODES_MULTIWINDOW) break
        }
        val deduped = ReadableWindowPlanner.dedupe(collected, MAX_NODES_MULTIWINDOW)
        lastTreeReadDiagnostics = diagnosticState.toDiagnostics()
        logMultiWindowSnapshot(windowList.size, roots.size, perRoot, deduped.size, descriptors)
        return deduped
    }

    /** Log de diagnóstico SIN datos sensibles (solo conteos/paquetes). */
    private fun logMultiWindowSnapshot(
        windowCount: Int,
        chosenRoots: Int,
        perRoot: List<Int>,
        finalNodes: Int,
        descriptors: List<WindowDescriptor>
    ) {
        if (!BuildConfig.DEBUG) return
        val distinctPkgs = descriptors.mapNotNull { it.packageName }.distinct().size
        val appWindows = descriptors.count { it.isApplicationWindow && !it.isSystemUi && !it.isOverlay }
        Log.i(
            SCREEN_DIAG_TAG,
            "multiWindowSnapshot windows=$windowCount appWindows=$appWindows " +
                "chosenRoots=$chosenRoots perRoot=${perRoot.joinToString("/")} " +
                "finalNodes=$finalNodes distinctPkgs=$distinctPkgs"
        )
    }

    private fun collectVisibleNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeSummary>,
        state: TraversalDiagnosticState,
        depth: Int,
        emitCap: Int = MAX_NODES_EMITTED,
        visitedCap: Int = MAX_VISITED_NODES,
        charsCap: Int = MAX_TOTAL_CHARS
    ) {
        if (depth > MAX_TREE_DEPTH) {
            state.discardedByLimit++
            return
        }
        if (out.size >= emitCap) {
            state.discardedByLimit++
            return
        }
        if (state.visitedNodes >= visitedCap) {
            state.discardedByLimit++
            return
        }
        if (state.totalChars >= charsCap) {
            state.discardedByLimit++
            return
        }

        state.visitedNodes++

        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        if (visible) {
            val summary = buildNodeSummary(node)
            if (summary != null && shouldEmit(summary)) {
                out.add(summary)
                state.acceptedNodes++
                if (!summary.text.isNullOrBlank()) state.textsFoundCount++
                if (!summary.contentDescription.isNullOrBlank()) {
                    state.contentDescriptionsFoundCount++
                }
                state.totalChars +=
                    (summary.text?.length ?: 0) +
                    (summary.contentDescription?.length ?: 0) +
                    (summary.hint?.length ?: 0)
            } else {
                state.discardedEmpty++
            }
        } else {
            state.discardedInvisible++
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        val safeChildCount = childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)

        for (index in 0 until safeChildCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectVisibleNodes(
                node = child,
                out = out,
                state = state,
                depth = depth + 1,
                emitCap = emitCap,
                visitedCap = visitedCap,
                charsCap = charsCap
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

        val isClickable = runCatching { node.isClickable }.getOrDefault(false)
        val isEditable = runCatching { node.isEditable }.getOrDefault(false)
        val isCheckable = runCatching { node.isCheckable }.getOrDefault(false)
        val childLabel = if (
            textValue == null &&
            description == null &&
            hint == null &&
            (isClickable || isEditable || isCheckable)
        ) {
            findFirstVisibleChildLabel(node = node, depth = 0)
        } else {
            null
        }
        val effectiveDescription = description ?: childLabel

        // Si no hay nada legible y el nodo no es interactivo, no vale la pena.
        val noContent = textValue == null && effectiveDescription == null && hint == null
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
            contentDescription = effectiveDescription?.take(MAX_SINGLE_TEXT_LENGTH),
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

    private fun findFirstVisibleChildLabel(
        node: AccessibilityNodeInfo,
        depth: Int
    ): String? {
        if (depth > MAX_LABEL_LOOKAHEAD_DEPTH) return null
        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_LABEL_LOOKAHEAD_CHILDREN)
        var firstDescription: String? = null
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val visible = runCatching { child.isVisibleToUser }.getOrDefault(false)
            val password = runCatching { child.isPassword }.getOrDefault(false)
            if (visible && !password) {
                val text = normalizeText(runCatching { child.text?.toString() }.getOrNull())
                    .takeIf { it.isNotBlank() && it.length <= MAX_SINGLE_TEXT_LENGTH }
                if (!text.isNullOrBlank()) return text

                if (firstDescription.isNullOrBlank()) {
                    firstDescription = listOf(
                        runCatching { child.contentDescription?.toString() }.getOrNull(),
                        runCatching { child.hintText?.toString() }.getOrNull()
                    )
                        .firstOrNull { !it.isNullOrBlank() }
                        ?.let(::normalizeText)
                        ?.takeIf { it.isNotBlank() && it.length <= MAX_SINGLE_TEXT_LENGTH }
                }
            }
            findFirstVisibleChildText(child, depth + 1)?.let { return it }
            if (firstDescription.isNullOrBlank()) {
                firstDescription = findFirstVisibleChildLabel(child, depth + 1)
            }
        }
        return firstDescription
    }

    private fun findFirstVisibleChildText(
        node: AccessibilityNodeInfo,
        depth: Int
    ): String? {
        if (depth > MAX_LABEL_LOOKAHEAD_DEPTH) return null
        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_LABEL_LOOKAHEAD_CHILDREN)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val visible = runCatching { child.isVisibleToUser }.getOrDefault(false)
            val password = runCatching { child.isPassword }.getOrDefault(false)
            if (visible && !password) {
                val text = normalizeText(runCatching { child.text?.toString() }.getOrNull())
                    .takeIf { it.isNotBlank() && it.length <= MAX_SINGLE_TEXT_LENGTH }
                if (!text.isNullOrBlank()) return text
            }
            findFirstVisibleChildText(child, depth + 1)?.let { return it }
        }
        return null
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

    private fun findVisibleChatClickCandidate(
        node: AccessibilityNodeInfo,
        targetName: String,
        depth: Int
    ): VisibleChatClickCandidate? {
        if (depth > MAX_TREE_DEPTH) return null

        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        val enabled = runCatching { node.isEnabled }.getOrDefault(true)
        val password = runCatching { node.isPassword }.getOrDefault(false)
        if (visible && enabled && !password) {
            val label = safeNodeLabel(node)
            if (
                label.isNotBlank() &&
                WhatsAppVisibleChatMatcher.matchesTargetLabel(label, targetName) &&
                !WhatsAppVisibleChatMatcher.isSensitiveActionLabel(label)
            ) {
                return VisibleChatClickCandidate(
                    displayName = targetName.trim().replace(Regex("\\s+"), " "),
                    clickableNode = findSafeClickableSelfOrAncestor(node)
                )
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
            .coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val result = findVisibleChatClickCandidate(child, targetName, depth + 1)
            if (result != null) return result
        }
        return null
    }

    private fun findSafeClickableSelfOrAncestor(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops <= MAX_CLICKABLE_ANCESTOR_HOPS) {
            if (isSafeClickableChatNode(current)) return current
            current = runCatching { current.parent }.getOrNull()
            hops += 1
        }
        return null
    }

    private fun isSafeClickableChatNode(node: AccessibilityNodeInfo): Boolean {
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        val enabled = runCatching { node.isEnabled }.getOrDefault(true)
        val clickable = runCatching { node.isClickable }.getOrDefault(false)
        val password = runCatching { node.isPassword }.getOrDefault(false)
        val editable = runCatching { node.isEditable }.getOrDefault(false)
        val checkable = runCatching { node.isCheckable }.getOrDefault(false)
        if (!visible || !enabled || !clickable || password || editable || checkable) return false

        val label = safeNodeLabel(node)
        if (label.isNotBlank() && WhatsAppVisibleChatMatcher.isSensitiveActionLabel(label)) {
            return false
        }

        return true
    }

    private fun safeNodeLabel(node: AccessibilityNodeInfo): String {
        val isPassword = runCatching { node.isPassword }.getOrDefault(false)
        if (isPassword) return ""
        return listOf(
            runCatching { node.text?.toString() }.getOrNull(),
            runCatching { node.contentDescription?.toString() }.getOrNull(),
            runCatching { node.hintText?.toString() }.getOrNull()
        )
            .firstOrNull { !it.isNullOrBlank() }
            ?.let(::normalizeText)
            .orEmpty()
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

    private enum class OverlayState {
        IDLE,
        READING,
        LISTENING,
        SPEAKING
    }

    private data class ReadableWindowRoot(
        val root: AccessibilityNodeInfo,
        val packageName: String?,
        val className: String?,
        val childCount: Int,
        val windowType: Int?,
        val source: String,
        val ownPackageSelected: Boolean,
        val overlayWindowSelected: Boolean,
        val active: Boolean,
        val focused: Boolean
    )

    private data class TraversalDiagnosticState(
        val rootAvailable: Boolean = false,
        val rootPackage: String? = null,
        val rootClass: String? = null,
        val rootChildCount: Int = -1,
        val source: String = SOURCE_UNKNOWN,
        val windowType: Int? = null,
        val ownPackageSelected: Boolean = false,
        val overlayWindowSelected: Boolean = false,
        var visitedNodes: Int = 0,
        var acceptedNodes: Int = 0,
        var discardedEmpty: Int = 0,
        var discardedInvisible: Int = 0,
        var discardedPrivacy: Int = 0,
        var discardedByLimit: Int = 0,
        var textsFoundCount: Int = 0,
        var contentDescriptionsFoundCount: Int = 0,
        var totalChars: Int = 0,
        val timestampMillis: Long
    ) {
        fun toDiagnostics(): AccessibilityTreeReadDiagnostics =
            AccessibilityTreeReadDiagnostics(
                rootAvailable = rootAvailable,
                rootPackage = rootPackage,
                rootClass = rootClass,
                rootChildCount = rootChildCount,
                source = source,
                windowType = windowType,
                ownPackageSelected = ownPackageSelected,
                overlayWindowSelected = overlayWindowSelected,
                visitedNodes = visitedNodes,
                acceptedNodes = acceptedNodes,
                discardedNodes = discardedEmpty + discardedInvisible + discardedPrivacy + discardedByLimit,
                discardedEmpty = discardedEmpty,
                discardedInvisible = discardedInvisible,
                discardedPrivacy = discardedPrivacy,
                discardedByLimit = discardedByLimit,
                textsFoundCount = textsFoundCount,
                contentDescriptionsFoundCount = contentDescriptionsFoundCount,
                timestampMillis = timestampMillis
            )
    }

    private data class VisibleChatClickCandidate(
        val displayName: String,
        val clickableNode: AccessibilityNodeInfo?
    )

    companion object {
        private const val MAX_TEXT_ITEMS = 24
        private const val MAX_NODES_EMITTED = 32
        private const val MAX_SINGLE_TEXT_LENGTH = 280
        private const val MAX_TOTAL_CHARS = 2_000
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_VISITED_NODES = 160
        private const val MAX_CHILDREN_PER_NODE = 40

        // Snapshot MULTI-VENTANA: lee todas las ventanas de la app foreground (no
        // solo la de arriba), para no perder nodos en ventanas hermanas / Compose.
        // Flag defensivo: si rompe algo, volver a false = comportamiento viejo.
        private const val MULTI_WINDOW_ACCESSIBILITY_SNAPSHOT = true
        private const val MAX_NODES_MULTIWINDOW = 80
        private const val MAX_VISITED_MULTIWINDOW = 600
        private const val MAX_CHARS_MULTIWINDOW = 6_000
        private const val MAX_CLICKABLE_ANCESTOR_HOPS = 4
        private const val MAX_SCROLLABLE_CANDIDATES = 6
        private const val MAX_LABEL_LOOKAHEAD_DEPTH = 2
        private const val MAX_LABEL_LOOKAHEAD_CHILDREN = 12
        private const val OVERLAY_COLLAPSED_MIN_WIDTH = 96
        private const val OVERLAY_COLLAPSED_MIN_HEIGHT = 64
        private const val OVERLAY_ACTION_DELAY_MILLIS = 180L
        private const val OVERLAY_VOICE_START_DELAY_MILLIS = 250L
        private const val OVERLAY_READ_SCREEN_COMMAND = "leer la pantalla"
        private const val MAX_TRACE_TOKEN_LENGTH = 96

        private const val DIAG_TAG = "EstelaAccessibility"
        private const val NAV_TAG = "EstelaWhatsAppNav"
        private const val SCREEN_DIAG_TAG = "EstelaScreenDiagnostic"
        private const val IG_NAV_TAG = "EstelaInstagramNav"

        // V1.12 — Instagram Direct: paquete EXACTO (la matriz de seguridad
        // exige package == com.instagram.android, sin variantes) e ids reales
        // capturados en device (IG 433.x, UI en español). Si Instagram cambia
        // un id, los detectores caen al fallback por etiqueta accesible.
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val IG_ID_DIRECT_TAB = "com.instagram.android:id/direct_tab"
        private const val IG_ID_INBOX_LIST =
            "com.instagram.android:id/inbox_refreshable_thread_list_recyclerview"
        private const val IG_ID_INBOX_ACTION_BAR =
            "com.instagram.android:id/direct_inbox_action_bar"
        private const val IG_ID_INBOX_USERNAME = "com.instagram.android:id/row_inbox_username"
        private const val IG_ID_INBOX_ROW = "com.instagram.android:id/row_inbox_container"
        private const val IG_ID_INBOX_AVATAR = "com.instagram.android:id/avatar_container"
        private const val IG_ID_THREAD_HEADER = "com.instagram.android:id/direct_thread_header"
        private const val IG_ID_HEADER_TITLE = "com.instagram.android:id/header_title"
        private const val IG_ID_HEADER_SUBTITLE = "com.instagram.android:id/header_subtitle"
        private const val IG_ID_COMPOSER =
            "com.instagram.android:id/row_thread_composer_edittext"
        private const val IG_ID_SEND_BUTTON =
            "com.instagram.android:id/row_thread_composer_send_button_container"
        private const val IG_ID_VOICE_BUTTON =
            "com.instagram.android:id/row_thread_composer_voice"

        /**
         * Trusted Contacts — ids de WhatsApp donde aparece la IDENTIDAD del chat
         * abierto (título de la conversación y nombre en el perfil del contacto).
         * Para un contacto NO agendado, ese texto es el propio número. Sólo se
         * leen estos nodos de cabecera, nunca el cuerpo de los mensajes.
         */
        private val WA_CONTACT_IDENTITY_IDS = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp:id/conversation_contact_status",
            "com.whatsapp:id/contact_title"
        )

        const val DEBUG_AGENT_MISSION_ACTION = "com.ojoclaro.DEBUG_AGENT_MISSION"
        const val DEBUG_VOICE_TEXT_ACTION = "com.ojoclaro.DEBUG_VOICE_TEXT"
        const val DEBUG_IG_ENDCALL_ACTION = "com.ojoclaro.DEBUG_IG_ENDCALL"
        const val DEBUG_DUMP_VISIBLE_NODES_ACTION = "com.ojoclaro.DEBUG_DUMP_VISIBLE_NODES"
        // Experimento touch-filter (solo debug).
        const val DEBUG_OVERLAY_HIDE_ACTION = "com.ojoclaro.DEBUG_OVERLAY_HIDE"
        const val DEBUG_OVERLAY_SHOW_ACTION = "com.ojoclaro.DEBUG_OVERLAY_SHOW"
        const val DEBUG_FIRST_CHAT_BOUNDS_ACTION = "com.ojoclaro.DEBUG_FIRST_CHAT_BOUNDS"
        const val DEBUG_IN_CHAT_CHECK_ACTION = "com.ojoclaro.DEBUG_IN_CHAT_CHECK"
        const val DEBUG_AGENT_MISSION_EXTRA_GOAL = "goal"
        private const val VISIBLE_NODE_DUMP_TAG = "EstelaVisibleNodeDump"

        // Geometría del experimento touch-filter: la 1ra fila de chat está
        // debajo de barra+buscador+filtros; el nodo de nombre tiene altura
        // moderada. (Pantalla de referencia 1080x2400.)
        private const val FIRST_ROW_MIN_TOP_PX = 420
        private const val FIRST_ROW_MIN_HEIGHT_PX = 24
        private const val FIRST_ROW_MAX_HEIGHT_PX = 320

        private const val SOURCE_UNKNOWN = "UNKNOWN"
        private const val SOURCE_REAL_EXTERNAL_ACCESSIBILITY_WINDOW = "REAL_EXTERNAL_ACCESSIBILITY_WINDOW"
        private const val SOURCE_REAL_ACCESSIBILITY_WINDOW = "REAL_ACCESSIBILITY_WINDOW"
        private const val SOURCE_ACTIVE_ACCESSIBILITY_WINDOW = "ACTIVE_ACCESSIBILITY_WINDOW"
        private const val ROUTE_OVERLAY_SCREEN = "SCREEN_UNDERSTANDING_ACCESSIBILITY_OVERLAY"
        private const val ROUTE_OVERLAY_VOICE = "OVERLAY_VOICE_ENTRYPOINT"

        private val WHITESPACE_REGEX = Regex("\\s+")

        @Volatile
        private var activeService: WeakReference<OjoClaroAccessibilityService>? = null

        @Volatile
        private var lastTreeReadDiagnostics: AccessibilityTreeReadDiagnostics? = null

        /**
         * Router opcional para Structured Screen Snapshot v1.
         *
         * El paquete 4A introduce este hook: cualquier capa de runtime que
         * quiera recibir snapshots vivos de pantalla puede registrar un
         * [AccessibilitySnapshotEventRouter] vía [setSnapshotRouter]. El
         * servicio lo invoca en `onAccessibilityEvent` con el tipo de evento
         * crudo (Int) — el router decide internamente, respetando el feature
         * flag `accessibilityRuntimeContextEnabled`.
         *
         * Si nadie lo setea, el comportamiento del servicio es idéntico al
         * legacy (no-op en eventos). Esto preserva la app sin regresión.
         *
         * No persiste referencias fuertes al router — si el dueño se va, el
         * servicio queda en estado seguro (no invoca un router muerto).
         */
        @Volatile
        private var snapshotRouter: AccessibilitySnapshotEventRouter? = null

        /**
         * Registra el router. Para limpiar, pasar `null`. Idempotente.
         */
        fun setSnapshotRouter(router: AccessibilitySnapshotEventRouter?) {
            snapshotRouter = router
        }

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
         * Nombre de clase de la ventana activa (p. ej. "com.whatsapp.Conversation").
         * Metadata de UI — NO es PII ni contenido de chat. Lo usa
         * [com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector] para
         * distinguir "dentro de un chat" de la lista de chats/login con una señal
         * robusta (corrige el falso negativo de inChat).
         */
        fun readActiveWindowClassName(): String? {
            return activeService?.get()?.readActiveWindowClassNameInternal()
        }

        /**
         * Trusted Contacts — número visible en la cabecera del chat/perfil de
         * WhatsApp (sólo identidad del contacto, nunca el cuerpo del chat), o
         * null. El llamador lo usa para construir el deep link y lo redacta en
         * todo log/voz (longitud + últimos 4). Nunca se loguea entero.
         */
        fun readVisibleWhatsAppPhoneNumber(): String? {
            return activeService?.get()?.readVisibleWhatsAppPhoneNumberInternal()
        }

        /**
         * Blind Safety — título crudo de la cabecera del chat de WhatsApp (nombre
         * o número), o null. Para anunciar el destino por voz y verificar por
         * label. El llamador redacta (longitud/últimos-4) en todo log.
         */
        fun readVisibleWhatsAppChatTitle(): String? {
            return activeService?.get()?.readVisibleWhatsAppChatTitleInternal()
        }

        /**
         * Devuelve los nodos visibles de la ventana activa como datos planos.
         * Pensado para Structured Screen Snapshot v1. Nunca expone valores de
         * campos password — para esos nodos [AccessibilityNodeSummary.text] es null.
         */
        fun readVisibleNodeSummaries(): List<AccessibilityNodeSummary> {
            return activeService?.get()?.readActiveWindowNodeSummaries().orEmpty()
        }

        fun lastTreeReadDiagnostics(): AccessibilityTreeReadDiagnostics? {
            return lastTreeReadDiagnostics
        }

        // --- V1.2: envío seguro + audio de WhatsApp ---

        /** Lee el borrador del campo de texto del chat abierto. */
        fun readWhatsAppDraft(): WhatsAppDraftReadResult =
            activeService?.get()?.readWhatsAppDraftInternal()
                ?: WhatsAppDraftReadResult.ServiceUnavailable

        /**
         * Escribe el borrador en el campo de texto del chat abierto con
         * ACTION_SET_TEXT (compose desde voz). Escribir JAMÁS envía: el envío
         * exige la doble confirmación y el toque verificado de tapWhatsAppSend.
         */
        fun setWhatsAppDraft(text: String): WhatsAppDraftSetResult =
            activeService?.get()?.setWhatsAppDraftInternal(text)
                ?: WhatsAppDraftSetResult.ServiceUnavailable

        /**
         * Toca enviar SOLO si el campo coincide con [expectedMessage]
         * (lo que la persona escuchó y confirmó). Nunca envía otra cosa.
         */
        fun tapWhatsAppSend(expectedMessage: String): WhatsAppSendTapResult {
            if (expectedMessage.isBlank()) return WhatsAppSendTapResult.FieldMismatch
            return activeService?.get()?.tapWhatsAppSendInternal(expectedMessage)
                ?: WhatsAppSendTapResult.ServiceUnavailable
        }

        /** Toca el botón de reproducir del último audio visible del chat. */
        fun playVisibleWhatsAppAudio(): WhatsAppAudioPlayResult =
            activeService?.get()?.playVisibleWhatsAppAudioInternal()
                ?: WhatsAppAudioPlayResult.ServiceUnavailable

        /** V1.11 — ¿hay botón de videollamada visible en el chat abierto? */
        fun hasWhatsAppVideoCallButton(): WhatsAppVideoCallCheck =
            activeService?.get()?.hasWhatsAppVideoCallButtonInternal()
                ?: WhatsAppVideoCallCheck.ServiceUnavailable

        /** V1.11 — toca videollamada SOLO tras confirmación hablada explícita. */
        fun tapWhatsAppVideoCall(): WhatsAppVideoCallTapResult =
            activeService?.get()?.tapWhatsAppVideoCallInternal()
                ?: WhatsAppVideoCallTapResult.ServiceUnavailable

        fun openVisibleWhatsAppChatByName(targetName: String): VisibleChatOpenResult {
            val cleanTarget = targetName.trim().replace(Regex("\\s+"), " ")
            if (cleanTarget.isBlank()) {
                return VisibleChatOpenResult.NoMatch(targetName)
            }
            return activeService?.get()?.openVisibleWhatsAppChatByNameInternal(cleanTarget)
                ?: VisibleChatOpenResult.Failed(cleanTarget, "accessibility_service_unavailable")
        }

        // --- V1.12: Instagram Direct ---

        /** Estado de pantalla de Instagram por marcadores de UI (no activity). */
        fun instagramScreenCheck(): InstagramScreenCheck =
            activeService?.get()?.instagramScreenCheckInternal()
                ?: InstagramScreenCheck(InstagramScreenState.UNKNOWN)

        /** Toca el tab "Mensaje" (Direct). Abrir el inbox no envía nada. */
        fun openInstagramDirectTab(): InstagramTapOutcome =
            activeService?.get()?.openInstagramDirectTabInternal()
                ?: InstagramTapOutcome.ServiceUnavailable

        /** Abre un chat del inbox por nombre visible (matching conservador). */
        fun openInstagramChatByVisibleName(targetName: String): InstagramChatOpenResult {
            val clean = targetName.trim().replace(Regex("\\s+"), " ")
            if (clean.isBlank()) return InstagramChatOpenResult.NoMatch(targetName)
            return activeService?.get()?.openInstagramChatByVisibleNameInternal(clean)
                ?: InstagramChatOpenResult.ServiceUnavailable
        }

        /** Escribe el borrador en el composer (escribir JAMÁS envía). */
        fun setInstagramDraftText(text: String): InstagramDraftSetResult =
            activeService?.get()?.setInstagramDraftTextInternal(text)
                ?: InstagramDraftSetResult.ServiceUnavailable

        /** Limpia el borrador que Estela misma escribió (al cancelar). */
        fun clearInstagramDraftText(): InstagramDraftSetResult =
            activeService?.get()?.setInstagramDraftTextInternal("")
                ?: InstagramDraftSetResult.ServiceUnavailable

        /** Toca ENVIAR solo si el campo coincide con lo confirmado. */
        fun tapInstagramSend(expectedMessage: String): InstagramSendTapResult {
            if (expectedMessage.isBlank()) return InstagramSendTapResult.FieldMismatch
            return activeService?.get()?.tapInstagramSendInternal(expectedMessage)
                ?: InstagramSendTapResult.ServiceUnavailable
        }

        /** ¿Hay botón de videollamada visible en el chat de Instagram? */
        fun hasInstagramVideoCallButton(): InstagramVideoCallCheck =
            activeService?.get()?.hasInstagramVideoCallButtonInternal()
                ?: InstagramVideoCallCheck.ServiceUnavailable

        /** Toca videollamada SOLO tras confirmación hablada explícita. */
        fun tapInstagramVideoCall(): InstagramVideoCallTapResult =
            activeService?.get()?.tapInstagramVideoCallInternal()
                ?: InstagramVideoCallTapResult.ServiceUnavailable

        /** Detección informativa del botón de mensaje de voz (jamás se toca). */
        fun findInstagramAudioButton(): InstagramAudioButtonInfo =
            activeService?.get()?.findInstagramAudioButtonInternal()
                ?: InstagramAudioButtonInfo(present = false)

        /**
         * V1.12.1 — corte de llamada de Instagram SOLO para limpieza de QA
         * (disparado por el broadcast de debug, jamás por voz del usuario).
         */
        fun tapInstagramEndCallForQa(): InstagramEndCallTapResult =
            activeService?.get()?.tapInstagramEndCallForQaInternal()
                ?: InstagramEndCallTapResult.ServiceUnavailable

        fun isConnected(): Boolean {
            return activeService?.get() != null
        }

        /**
         * V1.14 — empuja el estado visual de la presencia animada de Estela.
         * Lo llama el GlobalAssistantService desde los call sites de voz, TTS
         * y cámara. Estado seguro si no hay servicio: no-op.
         */
        fun setPresenceState(state: AssistantVisualState) {
            activeService?.get()?.setPresenceStateInternal(state)
        }

        /**
         * V1.10.1 — lanza una activity desde el contexto del servicio de
         * accesibilidad. En Android 12+ el startActivity de un service común
         * puede ser ignorado EN SILENCIO por las restricciones de background
         * launch ("WhatsApp no abre y Estela dice que sí"); el contexto de un
         * accessibility service activo está exento. No agrega capacidades:
         * solo hace confiable la apertura que el usuario ya pidió.
         */
        fun launchIntentFromService(intent: Intent): Boolean {
            val service = activeService?.get() ?: return false
            return runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
            }.isSuccess
        }

        /**
         * true si el botón/panel TYPE_ACCESSIBILITY_OVERLAY está actualmente
         * agregado al WindowManager. Lo usa el Agent Core para reportar el
         * estado real en check_accessibility_status.
         */
        fun isOverlayAttached(): Boolean {
            return activeService?.get()?.overlayView != null
        }

        /**
         * Ejecuta el BACK global si el servicio está conectado. Devuelve false
         * si no hay servicio activo (estado seguro).
         */
        fun performGlobalBack(): Boolean {
            return activeService?.get()?.performGlobalBackInternal() ?: false
        }

        /**
         * Desplaza el primer contenedor scrollable visible. Si no hay servicio
         * conectado, devuelve UNAVAILABLE para que el use case guíe a activar
         * Accesibilidad.
         */
        fun scrollVisibleContainer(forward: Boolean): ScreenScrollOutcome {
            return activeService?.get()?.scrollVisibleContainerInternal(forward)
                ?: ScreenScrollOutcome.UNAVAILABLE
        }

        private fun packageNameLooksLikeWhatsApp(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val lower = packageName.lowercase()
            return lower in WhatsAppScreenDetector.KNOWN_PACKAGES || lower.contains("whatsapp")
        }

        /** V1.12 — Instagram es EXACTO por contrato: jamás un contains. */
        private fun packageNameLooksLikeInstagram(packageName: String?): Boolean =
            packageName == INSTAGRAM_PACKAGE
    }
}
