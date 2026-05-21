package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.core.screen.ScreenContextCollector
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenChangeAwarenessCoordinator
import com.ojoclaro.android.agent.core.screen.ScreenChangeAwarenessEngine
import com.ojoclaro.android.agent.core.screen.ScreenContextRepository
import com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider
import com.ojoclaro.android.agent.task.followup.AgentTaskFollowUpCoordinator
import com.ojoclaro.android.voice.AgentBridgeVoiceCoordinator

/**
 * Grafo de dependencias del runtime moderno (paquete 4B).
 *
 * **Qué hace:**
 *  - Instancia y mantiene vivas las piezas del pipeline:
 *      provider → collector → repository → router → bridge → dispatchController.
 *  - Registra el router en [OjoClaroAccessibilityService] vía `setSnapshotRouter`.
 *  - Expone el [AgentBridgeDispatchController] y el [ScreenContextRepository]
 *    para que el HomeViewModel (u otro caller) los consuma.
 *  - Permite tearDown explícito que limpia repository y desregistra el router.
 *
 * **Qué NO hace:**
 *  - No guarda Activity ni Context. Es application-scope, sin leaks.
 *  - No instancia el HomeViewModel — el grafo solo provee dependencias.
 *  - No ejecuta acciones ni clicks. Lee pantalla via provider (que ya es
 *    read-only) y deja que el bridge decida.
 *
 * **Default seguro:**
 *  - Si [AgentCoreFeatureFlags.accessibilityRuntimeContextEnabled] está OFF,
 *    el router instalado no colectará nada (lo gating lo hace el router
 *    internamente). El repository quedará en null.
 *  - Si [AgentCoreFeatureFlags.typedConfirmationEnabled] está OFF, el
 *    dispatchController devolverá `FallbackToLegacy` siempre — el VM
 *    sigue su flujo normal.
 *  - Si nadie instancia este grafo, el comportamiento del APK es idéntico
 *    al anterior al paquete 4B.
 *
 * **Ciclo de vida:**
 *  ```
 *  // Al arrancar (Application.onCreate o MainActivity.onCreate):
 *  val graph = OjoClaroRuntimeGraph.create(flags = { config.featureFlags })
 *  graph.install()
 *
 *  // Cuando se cierra el proceso o se desactiva el modo asistido:
 *  graph.tearDown()
 *  ```
 *
 * Thread-safe: install/tearDown usan un lock. Las dependencias derivadas
 * son los componentes mismos (que ya son thread-safe).
 */
class OjoClaroRuntimeGraph private constructor(
    val screenRepository: ScreenContextRepository,
    val screenCollector: ScreenContextCollector,
    val snapshotRouter: AccessibilitySnapshotEventRouter,
    val bridge: AgentRuntimeBridge,
    val dispatchController: AgentBridgeDispatchController,
    /**
     * Paquete 5C — coordinador process-scope para enrutar voz semántica del
     * bridge. Vive con el graph, sobrevive recomposiciones de Compose, y se
     * limpia en [tearDown]. Es siempre seguro consumirlo aunque los flags
     * estén OFF: el dispatch controller devolverá FallbackToLegacy y el
     * coordinator nunca se invocará en ese caso.
     */
    val voiceCoordinator: AgentBridgeVoiceCoordinator,
    /**
     * Paquete 5E — coordinador process-scope que evalúa cambios relevantes
     * de pantalla y emite [com.ojoclaro.android.agent.core.screen.ScreenChangeAnnouncement]
     * en un SharedFlow. Cableado vía `screenRepository.setOnPublishListener`
     * en [install] y limpiado en [tearDown]. Si el flag
     * [com.ojoclaro.android.agent.core.AgentCoreFeatureFlags.screenChangeAwarenessEnabled]
     * está OFF, el coordinator existe pero no emite anuncios.
     */
    val screenChangeAwarenessCoordinator: ScreenChangeAwarenessCoordinator,
    /**
     * Paquete 6D -- coordinator process-scope para follow-up automatico de
     * tareas. No contiene una tarea propia: el HomeViewModel le pasa su
     * AgentTaskOrchestrator activo en cada snapshot para evitar memorias
     * duplicadas. El flag [AgentCoreFeatureFlags.taskAutoFollowUpEnabled]
     * mantiene el comportamiento legacy cuando esta OFF.
     */
    val taskFollowUpCoordinator: AgentTaskFollowUpCoordinator,
    private val routerInstaller: (AccessibilitySnapshotEventRouter?) -> Unit
) {

    private val lock = Any()

    @Volatile
    private var installed: Boolean = false

    /**
     * Registra el [snapshotRouter] en el AccessibilityService. Idempotente.
     */
    fun install() {
        synchronized(lock) {
            if (installed) return
            routerInstaller(snapshotRouter)
            screenRepository.setOnPublishListener { snapshot ->
                screenChangeAwarenessCoordinator.onSnapshot(snapshot)
            }
            installed = true
        }
    }

    /**
     * Desregistra el router, limpia el repository y el pending del bridge.
     * Idempotente.
     */
    fun tearDown() {
        synchronized(lock) {
            if (!installed) {
                screenRepository.setOnPublishListener(null)
                screenRepository.clear()
                bridge.reset()
                voiceCoordinator.resetMemory()
                screenChangeAwarenessCoordinator.reset()
                taskFollowUpCoordinator.reset()
                return
            }
            routerInstaller(null)
            screenRepository.setOnPublishListener(null)
            installed = false
        }
        screenRepository.clear()
        bridge.reset()
        voiceCoordinator.resetMemory()
        screenChangeAwarenessCoordinator.reset()
        taskFollowUpCoordinator.reset()
    }

    fun isInstalled(): Boolean = installed

    companion object {
        /**
         * Constructor por defecto: usa el provider real del AccessibilityService
         * y registra el router en [OjoClaroAccessibilityService]. Para tests,
         * usar [createForTesting] con stubs.
         */
        fun create(
            flags: () -> AgentCoreFeatureFlags
        ): OjoClaroRuntimeGraph = createForTesting(
            flags = flags,
            provider = AndroidAccessibilityScreenContextProvider(),
            routerInstaller = { OjoClaroAccessibilityService.setSnapshotRouter(it) }
        )

        /**
         * Variante para tests: inyectá un provider stub y un installer espía
         * para verificar el cableado sin tocar el AccessibilityService real.
         */
        fun createForTesting(
            flags: () -> AgentCoreFeatureFlags,
            provider: ScreenContextProvider,
            routerInstaller: (AccessibilitySnapshotEventRouter?) -> Unit
        ): OjoClaroRuntimeGraph {
            val repository = ScreenContextRepository(flags = flags)
            val collector = ScreenContextCollector(
                provider = provider,
                repository = repository,
                flags = flags
            )
            val router = AccessibilitySnapshotEventRouter(
                collector = collector,
                flags = flags
            )
            val bridge = AgentRuntimeBridge(flags = flags)
            val dispatch = AgentBridgeDispatchController(
                bridge = bridge,
                screenRepository = repository,
                flags = flags
            )
            val voiceCoordinator = AgentBridgeVoiceCoordinator()
            val changeEngine = ScreenChangeAwarenessEngine()
            val changeCoordinator = ScreenChangeAwarenessCoordinator(
                engine = changeEngine,
                flags = flags
            )
            val taskFollowUpCoordinator = AgentTaskFollowUpCoordinator(
                flags = flags
            )
            return OjoClaroRuntimeGraph(
                screenRepository = repository,
                screenCollector = collector,
                snapshotRouter = router,
                bridge = bridge,
                dispatchController = dispatch,
                voiceCoordinator = voiceCoordinator,
                screenChangeAwarenessCoordinator = changeCoordinator,
                taskFollowUpCoordinator = taskFollowUpCoordinator,
                routerInstaller = routerInstaller
            )
        }
    }
}
