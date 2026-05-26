package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner
import com.ojoclaro.android.agent.core.screen.ScreenChangeAnnouncement
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.followup.AgentTaskFollowUpCoordinator
import com.ojoclaro.android.voice.AgentBridgeVoiceCoordinator
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto puro y testeable para elegir la dependencia opcional del bridge.
 *
 * Si el runtime graph no está instalado, devuelve null y el HomeViewModel
 * conserva el flujo legacy. Si existe, pasa el controller; el controller
 * decide por flags si maneja o devuelve FallbackToLegacy.
 */
internal fun selectAgentBridgeDispatchControllerForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): AgentBridgeDispatchController? =
    owner.currentGraph()?.dispatchController

/**
 * Paquete 5C — equivalente para el coordinador semántico de voz. Devuelve el
 * mismo coordinador process-scope que vive en el graph, así sobrevive
 * recomposiciones de Compose y mantiene memoria de dedup entre comandos.
 */
internal fun selectAgentBridgeVoiceCoordinatorForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): AgentBridgeVoiceCoordinator? =
    owner.currentGraph()?.voiceCoordinator

/**
 * Paquete 5E — devuelve el flow de anuncios de cambio de pantalla del graph
 * si está instalado, o null. El HomeViewModel decide en init si suscribirse.
 */
internal fun selectScreenChangeAnnouncementsForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): SharedFlow<ScreenChangeAnnouncement>? =
    owner.currentGraph()?.screenChangeAwarenessCoordinator?.announcements

/**
 * Paquete 6D -- coordinator de follow-up automatico de tareas activas.
 * Devuelve null si el graph no esta instalado; el coordinator mismo respeta
 * taskAutoFollowUpEnabled para preservar legacy con el flag OFF.
 */
internal fun selectAgentTaskFollowUpCoordinatorForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): AgentTaskFollowUpCoordinator? =
    owner.currentGraph()?.taskFollowUpCoordinator

/**
 * Paquete 6D -- flujo de snapshots estructurados que alimenta el follow-up.
 * El HomeViewModel decide si suscribirse segun haya coordinator inyectado.
 */
internal fun selectTaskAutoFollowUpSnapshotsForHome(
    owner: RuntimeGraphOwner = RuntimeGraphOwner.INSTANCE
): StateFlow<StructuredScreenSnapshot?>? =
    owner.currentGraph()?.screenRepository?.state
