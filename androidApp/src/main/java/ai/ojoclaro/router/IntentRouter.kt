package ai.ojoclaro.router

import ai.ojoclaro.resolver.DeepLinkConstants
import ai.ojoclaro.resolver.DeepLinkSpec
import ai.ojoclaro.resolver.InstalledAppResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionGate
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionRequest
import com.ojoclaro.android.consent.ConsentPhrases
import java.net.URLEncoder

data class IntentRouteRequest(
    val intent: String?,
    val confidence: Double,
    val params: Map<String, Any?>,
    val safetyLevel: String?,
    val voiceResponse: String?,
    val voiceResponseTemplate: String?,
    val rawText: String
) {
    companion object {
        fun from(parsedJson: Map<String, Any?>): IntentRouteRequest =
            IntentRouteRequest(
                intent = parsedJson["intent"] as? String,
                confidence = parsedJson["confidence"].toDoubleOrDefault(),
                params = parsedJson["params"].toStringKeyMap(),
                safetyLevel = parsedJson["safety_level"] as? String,
                voiceResponse = parsedJson["voice_response"] as? String,
                voiceResponseTemplate = parsedJson["voice_response_template"] as? String,
                rawText = (parsedJson["raw_text"] as? String).orEmpty()
            )

        private fun Any?.toDoubleOrDefault(): Double =
            when (this) {
                is Number -> toDouble()
                is String -> toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

        private fun Any?.toStringKeyMap(): Map<String, Any?> =
            (this as? Map<*, *>)
                ?.entries
                ?.associate { entry -> entry.key.toString() to entry.value }
                ?: emptyMap()
    }
}

data class RideAppResult(
    val intent: Intent?,
    val disclaimerText: String
)

data class VolumeAction(
    val direction: VolumeDirection,
    val steps: Int
)

enum class VolumeDirection {
    UP,
    DOWN
}

data class MemoryAction(
    val type: MemoryActionType,
    val key: String? = null,
    val value: String? = null
)

enum class MemoryActionType {
    REMEMBER,
    LIST_MEMORY,
    CLEAR_MEMORY,
    SAVE_CONTACT,
    SAVE_CONTACT_PHONE,
    LIST_CONTACTS,
    DELETE_CONTACT
}

data class ReminderAction(
    val type: ReminderActionType,
    val text: String? = null,
    val datetime: String? = null,
    val reminderId: String? = null,
    val label: String? = null
)

enum class ReminderActionType {
    CREATE_REMINDER,
    LIST_REMINDERS,
    CANCEL_REMINDER,
    CREATE_ALARM
}

data class LocationAliasAction(
    val type: LocationAliasActionType,
    val alias: String? = null
)

enum class LocationAliasActionType {
    SAVE,
    LIST,
    DELETE
}

data class ConversationAction(
    val type: ConversationActionType,
    val topic: String? = null,
    val candidates: List<String>? = null,
    val actionPending: String? = null,
    val rawText: String? = null,
    val blockedReason: String? = null
)

enum class ConversationActionType {
    CONFIRM,
    CANCEL,
    STOP_SPEAKING,
    REPEAT_LAST,
    HELP,
    CLARIFY_CONTACT,
    UNKNOWN,
    BLOCKED
}

/**
 * Descriptor for vision intents (read_ocr_text, read_visible_screen).
 *
 * The router only emits the descriptor; the actual reading lives elsewhere:
 *  - READ_OCR   → the in-app TextScanScreen / TextRecognitionAnalyzer pipeline.
 *  - READ_SCREEN → the AccessibilityService inspecting AccessibilityNodeInfo.
 *
 * We deliberately do NOT return an Android Intent for READ_OCR: the existing
 * OCR pipeline is an in-app Compose destination, not an Intent-launchable
 * Activity. Returning a descriptor keeps the navigation choice with the
 * caller (ViewModel / orchestrator) and out of the router.
 */
data class CameraAction(
    val type: CameraActionType
)

enum class CameraActionType {
    READ_OCR,
    READ_SCREEN
}

/**
 * Sealed result wrapper for [IntentRouter.routeAndCollect].
 *
 * `route()` returns Unit (legacy callers don't care); `routeAndCollect()`
 * returns this so the caller can dispatch each action variant to the right
 * side-effect channel (startActivity, audio manager, in-app camera nav, etc).
 *
 * `Delegated` is emitted when the router hands the request to the safe
 * execution gate (prepare_only / requires_confirm). The caller should not
 * dispatch anything in that case — the gate owns the next step.
 */
sealed interface IntentRouteResult {
    data class LaunchIntent(val intent: Intent) : IntentRouteResult
    data class OpenRide(val result: RideAppResult) : IntentRouteResult
    data class Vision(val action: CameraAction) : IntentRouteResult
    data class Memory(val action: MemoryAction) : IntentRouteResult
    data class Reminder(val action: ReminderAction) : IntentRouteResult
    data class Volume(val action: VolumeAction) : IntentRouteResult
    data class LocationAlias(val action: LocationAliasAction) : IntentRouteResult
    data class Conversation(val action: ConversationAction) : IntentRouteResult
    data object AppNotFound : IntentRouteResult
    data object InvalidConfirmation : IntentRouteResult
    data object Delegated : IntentRouteResult
    data object NoOp : IntentRouteResult
}

fun interface AgentSafeExecutionDelegate {
    fun submit(request: IntentRouteRequest)
}

class AgentSafeExecutionGateDelegate(
    private val gate: AgentSafeExecutionGate = AgentSafeExecutionGate()
) : AgentSafeExecutionDelegate {
    override fun submit(request: IntentRouteRequest) {
        gate.decide(
            AgentSafeExecutionRequest(
                plan = null,
                proposal = null,
                userCommand = request.rawText
            )
        )
    }
}

interface IntentRouteHandler {
    fun handleOpenApp(request: IntentRouteRequest): Intent? = null
    fun handleOpenRideApp(request: IntentRouteRequest): RideAppResult =
        RideAppResult(intent = null, disclaimerText = "")
    fun handleAppNotFound(request: IntentRouteRequest) = Unit
    fun handleComposeWhatsappMessage(request: IntentRouteRequest): Intent? = null
    fun handleOpenWhatsappChat(request: IntentRouteRequest): Intent? = null
    fun handleCallContact(request: IntentRouteRequest): Intent? = null
    fun handleOpenPhone(request: IntentRouteRequest): Intent? = null
    fun handleReadOcrText(request: IntentRouteRequest): CameraAction? = null
    fun handleReadVisibleScreen(request: IntentRouteRequest): CameraAction? = null
    fun handleGetCurrentLocation(request: IntentRouteRequest): Intent? = null
    fun handleOpenMaps(request: IntentRouteRequest): Intent? = null
    fun handleNavigateTo(request: IntentRouteRequest): Intent? = null
    fun handleSaveLocationAlias(request: IntentRouteRequest): LocationAliasAction? = null
    fun handleListLocationAliases(request: IntentRouteRequest): LocationAliasAction? = null
    fun handleDeleteLocationAlias(request: IntentRouteRequest): LocationAliasAction? = null
    fun handlePlayMusic(request: IntentRouteRequest): Intent? = null
    fun handlePauseMusic(request: IntentRouteRequest): Intent? = null
    fun handleNextSong(request: IntentRouteRequest): Intent? = null
    fun handleOpenSpotify(request: IntentRouteRequest): Intent? = null
    fun handleVolumeUp(request: IntentRouteRequest): VolumeAction? = null
    fun handleVolumeDown(request: IntentRouteRequest): VolumeAction? = null
    fun handleRememberMemory(request: IntentRouteRequest): MemoryAction? = null
    fun handleListMemory(request: IntentRouteRequest): MemoryAction? = null
    fun handleClearMemory(request: IntentRouteRequest): MemoryAction? = null
    fun handleSaveContact(request: IntentRouteRequest): MemoryAction? = null
    fun handleSaveContactPhone(request: IntentRouteRequest): MemoryAction? = null
    fun handleListContacts(request: IntentRouteRequest): MemoryAction? = null
    fun handleDeleteContact(request: IntentRouteRequest): MemoryAction? = null
    fun handleCreateReminder(request: IntentRouteRequest): ReminderAction? = null
    fun handleListReminders(request: IntentRouteRequest): ReminderAction? = null
    fun handleCancelReminder(request: IntentRouteRequest): ReminderAction? = null
    fun handleCreateAlarm(request: IntentRouteRequest): ReminderAction? = null
    fun handleConfirm(request: IntentRouteRequest): ConversationAction? = null
    fun handleCancel(request: IntentRouteRequest): ConversationAction? = null
    fun handleStopSpeaking(request: IntentRouteRequest): ConversationAction? = null
    fun handleRepeatLast(request: IntentRouteRequest): ConversationAction? = null
    fun handleHelp(request: IntentRouteRequest): ConversationAction? = null
    fun handleClarifyContact(request: IntentRouteRequest): ConversationAction? = null
    fun handleInvalidConfirmation(request: IntentRouteRequest) = Unit
    fun handleUnknown(request: IntentRouteRequest): ConversationAction? = null
    fun handleBlocked(request: IntentRouteRequest): ConversationAction? = null
}

class EmptyIntentRouteHandler(
    private val context: Context? = null,
    private val installedAppKeysProvider: (() -> List<String>)? = null,
    private val intentFactory: (IntentSpec) -> Intent = ::newIntent,
    private val mediaButtonIntentFactory: (Int) -> Intent = ::buildMediaButtonIntent
) : IntentRouteHandler {
    override fun handleOpenApp(request: IntentRouteRequest): Intent? =
        buildOpenAppIntent(
            appKey = request.params["app_name"] as? String,
            intentFactory = intentFactory
        )

    override fun handleOpenRideApp(request: IntentRouteRequest): RideAppResult {
        val availableRideApps = installedAppKeys()
            .filter { appKey -> appKey in RideAppKeys }
        val preferredApp = (request.params["preferred_app"] as? String)
            ?.trim()
            ?.takeIf { appKey -> appKey.isNotEmpty() }
        val chosenApp = when {
            preferredApp in availableRideApps -> preferredApp
            else -> availableRideApps.firstOrNull()
        } ?: return RideAppResult(intent = null, disclaimerText = "")

        val appLabel = RideAppLabels.getValue(chosenApp)
        return RideAppResult(
            intent = buildOpenAppIntent(
                appKey = chosenApp,
                intentFactory = intentFactory
            ),
            disclaimerText = ConsentPhrases.rideAppOpenDisclaimer(appLabel)
        )
    }

    override fun handleOpenWhatsappChat(request: IntentRouteRequest): Intent? {
        if (!request.hasContactQuery()) return null
        return buildWhatsappIntent(WhatsappBaseUri, intentFactory)
    }

    override fun handleComposeWhatsappMessage(request: IntentRouteRequest): Intent? {
        if (!request.hasContactQuery()) return null
        val messageText = (request.params["message_text"] as? String)
            ?.takeIf { text -> text.isNotBlank() }
            ?: return handleOpenWhatsappChat(request)
        return buildWhatsappIntent(
            uri = WhatsappBaseUri + URLEncoder.encode(messageText, Charsets.UTF_8.name()),
            intentFactory = intentFactory
        )
    }

    override fun handleOpenPhone(request: IntentRouteRequest): Intent =
        buildDialIntent(phoneNumber = null, intentFactory = intentFactory)

    override fun handleCallContact(request: IntentRouteRequest): Intent? {
        if (!request.hasContactQuery()) return null
        return buildDialIntent(
            phoneNumber = request.params["resolved_phone"] as? String,
            intentFactory = intentFactory
        )
    }

    override fun handleGetCurrentLocation(request: IntentRouteRequest): Intent =
        buildViewIntent(uri = "geo:0,0?q=my+location", intentFactory = intentFactory)

    override fun handleOpenMaps(request: IntentRouteRequest): Intent {
        val destination = (request.params["destination"] as? String)
            ?.takeIf { value -> value.isNotBlank() }
        val uri = when (destination) {
            null -> "geo:0,0"
            else -> "geo:0,0?q=${destination.encodeForUriQuery()}"
        }
        return buildViewIntent(uri = uri, intentFactory = intentFactory)
    }

    override fun handleNavigateTo(request: IntentRouteRequest): Intent? {
        val destination = (request.params["destination"] as? String)
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        val requestedApp = request.params["app"] as? String
        val uri = when {
            requestedApp == "waze" && "waze" in installedAppKeys() ->
                "waze://?q=${destination.encodeForUriQuery()}&navigate=yes"
            else ->
                "google.navigation:q=${destination.encodeForUriQuery()}"
        }
        return buildViewIntent(uri = uri, intentFactory = intentFactory)
    }

    override fun handleReadOcrText(request: IntentRouteRequest): CameraAction? {
        if (request.isBlockedSensitive()) return null
        return CameraAction(type = CameraActionType.READ_OCR)
    }

    override fun handleReadVisibleScreen(request: IntentRouteRequest): CameraAction? {
        if (request.isBlockedSensitive()) return null
        return CameraAction(type = CameraActionType.READ_SCREEN)
    }

    override fun handleSaveLocationAlias(request: IntentRouteRequest): LocationAliasAction? {
        val alias = request.params["alias"] as? String ?: return null
        return LocationAliasAction(type = LocationAliasActionType.SAVE, alias = alias)
    }

    override fun handleListLocationAliases(request: IntentRouteRequest): LocationAliasAction =
        LocationAliasAction(type = LocationAliasActionType.LIST)

    override fun handleDeleteLocationAlias(request: IntentRouteRequest): LocationAliasAction? {
        val alias = request.params["alias"] as? String ?: return null
        return LocationAliasAction(type = LocationAliasActionType.DELETE, alias = alias)
    }

    override fun handleOpenSpotify(request: IntentRouteRequest): Intent =
        buildViewIntent(uri = SpotifyHomeUri, intentFactory = intentFactory)

    override fun handlePlayMusic(request: IntentRouteRequest): Intent {
        val query = (request.params["query"] as? String)
            ?.takeIf { value -> value.isNotBlank() }
            ?: return handleOpenSpotify(request)
        return buildViewIntent(
            uri = "spotify:search:${query.encodeForUriQuery()}",
            intentFactory = intentFactory
        )
    }

    override fun handlePauseMusic(request: IntentRouteRequest): Intent =
        mediaButtonIntentFactory(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    override fun handleNextSong(request: IntentRouteRequest): Intent =
        mediaButtonIntentFactory(KeyEvent.KEYCODE_MEDIA_NEXT)

    override fun handleVolumeUp(request: IntentRouteRequest): VolumeAction =
        VolumeAction(direction = VolumeDirection.UP, steps = request.volumeSteps())

    override fun handleVolumeDown(request: IntentRouteRequest): VolumeAction =
        VolumeAction(direction = VolumeDirection.DOWN, steps = request.volumeSteps())

    override fun handleRememberMemory(request: IntentRouteRequest): MemoryAction? {
        val text = request.params["text"] as? String ?: return null
        return MemoryAction(type = MemoryActionType.REMEMBER, value = text)
    }

    override fun handleListMemory(request: IntentRouteRequest): MemoryAction =
        MemoryAction(type = MemoryActionType.LIST_MEMORY)

    override fun handleClearMemory(request: IntentRouteRequest): MemoryAction =
        MemoryAction(type = MemoryActionType.CLEAR_MEMORY)

    override fun handleSaveContact(request: IntentRouteRequest): MemoryAction? {
        val name = request.params["name"] as? String ?: return null
        val phone = request.params["phone"] as? String
        return MemoryAction(type = MemoryActionType.SAVE_CONTACT, key = name, value = phone)
    }

    override fun handleSaveContactPhone(request: IntentRouteRequest): MemoryAction? {
        val contactQuery = request.params["contact_query"] as? String ?: return null
        val phone = request.params["phone"] as? String
        return MemoryAction(
            type = MemoryActionType.SAVE_CONTACT_PHONE,
            key = contactQuery,
            value = phone
        )
    }

    override fun handleListContacts(request: IntentRouteRequest): MemoryAction =
        MemoryAction(type = MemoryActionType.LIST_CONTACTS)

    override fun handleDeleteContact(request: IntentRouteRequest): MemoryAction? {
        val contactQuery = request.params["contact_query"] as? String ?: return null
        return MemoryAction(type = MemoryActionType.DELETE_CONTACT, key = contactQuery)
    }

    override fun handleCreateReminder(request: IntentRouteRequest): ReminderAction? {
        val text = request.params["text"] as? String ?: return null
        val datetime = request.params["datetime"] as? String
        return ReminderAction(
            type = ReminderActionType.CREATE_REMINDER,
            text = text,
            datetime = datetime
        )
    }

    override fun handleListReminders(request: IntentRouteRequest): ReminderAction =
        ReminderAction(type = ReminderActionType.LIST_REMINDERS)

    override fun handleCancelReminder(request: IntentRouteRequest): ReminderAction =
        ReminderAction(
            type = ReminderActionType.CANCEL_REMINDER,
            reminderId = request.params["reminder_id"] as? String
        )

    override fun handleCreateAlarm(request: IntentRouteRequest): ReminderAction? {
        val datetime = request.params["datetime"] as? String ?: return null
        val label = request.params["label"] as? String
        return ReminderAction(
            type = ReminderActionType.CREATE_ALARM,
            datetime = datetime,
            label = label
        )
    }

    override fun handleConfirm(request: IntentRouteRequest): ConversationAction =
        ConversationAction(type = ConversationActionType.CONFIRM)

    override fun handleCancel(request: IntentRouteRequest): ConversationAction =
        ConversationAction(type = ConversationActionType.CANCEL)

    override fun handleStopSpeaking(request: IntentRouteRequest): ConversationAction =
        ConversationAction(type = ConversationActionType.STOP_SPEAKING)

    override fun handleRepeatLast(request: IntentRouteRequest): ConversationAction =
        ConversationAction(type = ConversationActionType.REPEAT_LAST)

    override fun handleHelp(request: IntentRouteRequest): ConversationAction =
        ConversationAction(
            type = ConversationActionType.HELP,
            topic = request.params["topic"] as? String
        )

    override fun handleClarifyContact(request: IntentRouteRequest): ConversationAction =
        ConversationAction(
            type = ConversationActionType.CLARIFY_CONTACT,
            candidates = request.params["candidates"].toStringList(),
            actionPending = request.params["action_pending"] as? String
        )

    override fun handleUnknown(request: IntentRouteRequest): ConversationAction =
        ConversationAction(
            type = ConversationActionType.UNKNOWN,
            rawText = request.params["raw_text"] as? String
        )

    override fun handleBlocked(request: IntentRouteRequest): ConversationAction =
        ConversationAction(
            type = ConversationActionType.BLOCKED,
            blockedReason = request.params["voice_response_template"] as? String
        )

    private fun installedAppKeys(): List<String> =
        installedAppKeysProvider?.invoke()
            ?: context?.let { appContext ->
                InstalledAppResolver.getInstalledAppKeys(appContext)
            }
            ?: emptyList()
}

class IntentRouter(
    private val handler: IntentRouteHandler = EmptyIntentRouteHandler(),
    private val safeExecutionDelegate: AgentSafeExecutionDelegate = AgentSafeExecutionGateDelegate()
) {
    constructor(
        handler: IntentRouteHandler,
        safeExecutionGate: AgentSafeExecutionGate
    ) : this(handler, AgentSafeExecutionGateDelegate(safeExecutionGate))

    fun route(parsedJson: Map<String, Any?>) {
        routeAndCollect(parsedJson)
    }

    /**
     * Like [route] but returns the typed result so callers can dispatch each
     * variant (Intent → startActivity, CameraAction → camera nav, etc.).
     *
     * Safety contract is identical to [route]: blocked_sensitive and missing
     * safety_level funnel to handleBlocked; low confidence falls back to
     * handleUnknown; prepare_only / requires_confirm submit to the safe
     * execution gate (returning [IntentRouteResult.Delegated] so the caller
     * knows not to act).
     */
    fun routeAndCollect(parsedJson: Map<String, Any?>): IntentRouteResult {
        val request = IntentRouteRequest.from(parsedJson)

        when (request.safetyLevel) {
            null,
            SafetyLevelBlocked -> {
                return handler.handleBlocked(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            }
        }

        if (request.confidence < MinimumConfidence) {
            return handler.handleUnknown(request)?.let { IntentRouteResult.Conversation(it) }
                ?: IntentRouteResult.NoOp
        }

        return when (request.safetyLevel) {
            SafetyLevelAllowSafe -> dispatchAllowed(request)
            SafetyLevelPrepareOnly,
            SafetyLevelRequiresConfirm -> {
                safeExecutionDelegate.submit(request)
                IntentRouteResult.Delegated
            }
            else -> {
                handler.handleBlocked(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            }
        }
    }

    private fun dispatchAllowed(request: IntentRouteRequest): IntentRouteResult {
        return when (request.intent) {
            "open_app" -> handler.handleOpenApp(request)?.let { IntentRouteResult.LaunchIntent(it) }
                ?: IntentRouteResult.NoOp
            "open_ride_app" -> IntentRouteResult.OpenRide(handler.handleOpenRideApp(request))
            "app_not_found" -> {
                handler.handleAppNotFound(request)
                IntentRouteResult.AppNotFound
            }
            "compose_whatsapp_message" ->
                handler.handleComposeWhatsappMessage(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "open_whatsapp_chat" ->
                handler.handleOpenWhatsappChat(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "call_contact" ->
                handler.handleCallContact(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "open_phone" ->
                handler.handleOpenPhone(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "read_ocr_text" ->
                handler.handleReadOcrText(request)?.let { IntentRouteResult.Vision(it) }
                    ?: IntentRouteResult.NoOp
            "read_visible_screen" ->
                handler.handleReadVisibleScreen(request)?.let { IntentRouteResult.Vision(it) }
                    ?: IntentRouteResult.NoOp
            "get_current_location" ->
                handler.handleGetCurrentLocation(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "open_maps" ->
                handler.handleOpenMaps(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "navigate_to" ->
                handler.handleNavigateTo(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "save_location_alias" ->
                handler.handleSaveLocationAlias(request)?.let { IntentRouteResult.LocationAlias(it) }
                    ?: IntentRouteResult.NoOp
            "list_location_aliases" ->
                handler.handleListLocationAliases(request)?.let { IntentRouteResult.LocationAlias(it) }
                    ?: IntentRouteResult.NoOp
            "delete_location_alias" ->
                handler.handleDeleteLocationAlias(request)?.let { IntentRouteResult.LocationAlias(it) }
                    ?: IntentRouteResult.NoOp
            "play_music" ->
                handler.handlePlayMusic(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "pause_music" ->
                handler.handlePauseMusic(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "next_song" ->
                handler.handleNextSong(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "open_spotify" ->
                handler.handleOpenSpotify(request)?.let { IntentRouteResult.LaunchIntent(it) }
                    ?: IntentRouteResult.NoOp
            "volume_up" ->
                handler.handleVolumeUp(request)?.let { IntentRouteResult.Volume(it) }
                    ?: IntentRouteResult.NoOp
            "volume_down" ->
                handler.handleVolumeDown(request)?.let { IntentRouteResult.Volume(it) }
                    ?: IntentRouteResult.NoOp
            "remember_memory" ->
                handler.handleRememberMemory(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "list_memory" ->
                handler.handleListMemory(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "clear_memory" ->
                handler.handleClearMemory(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "save_contact" ->
                handler.handleSaveContact(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "save_contact_phone" ->
                handler.handleSaveContactPhone(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "list_contacts" ->
                handler.handleListContacts(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "delete_contact" ->
                handler.handleDeleteContact(request)?.let { IntentRouteResult.Memory(it) }
                    ?: IntentRouteResult.NoOp
            "create_reminder" ->
                handler.handleCreateReminder(request)?.let { IntentRouteResult.Reminder(it) }
                    ?: IntentRouteResult.NoOp
            "list_reminders" ->
                handler.handleListReminders(request)?.let { IntentRouteResult.Reminder(it) }
                    ?: IntentRouteResult.NoOp
            "cancel_reminder" ->
                handler.handleCancelReminder(request)?.let { IntentRouteResult.Reminder(it) }
                    ?: IntentRouteResult.NoOp
            "create_alarm" ->
                handler.handleCreateAlarm(request)?.let { IntentRouteResult.Reminder(it) }
                    ?: IntentRouteResult.NoOp
            "confirm" ->
                handler.handleConfirm(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "cancel" ->
                handler.handleCancel(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "stop_speaking" ->
                handler.handleStopSpeaking(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "repeat_last" ->
                handler.handleRepeatLast(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "help" ->
                handler.handleHelp(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "clarify_contact" ->
                handler.handleClarifyContact(request)?.let { IntentRouteResult.Conversation(it) }
                    ?: IntentRouteResult.NoOp
            "invalid_confirmation" -> {
                handler.handleInvalidConfirmation(request)
                IntentRouteResult.InvalidConfirmation
            }
            else -> handler.handleUnknown(request)?.let { IntentRouteResult.Conversation(it) }
                ?: IntentRouteResult.NoOp
        }
    }

    private companion object {
        const val MinimumConfidence = 0.6
        const val SafetyLevelAllowSafe = "allow_safe"
        const val SafetyLevelPrepareOnly = "prepare_only"
        const val SafetyLevelRequiresConfirm = "requires_confirm"
        const val SafetyLevelBlocked = "blocked_sensitive"
    }
}

private val RideAppKeys = listOf("uber", "didi", "cabify", "indrive")

private val RideAppLabels = mapOf(
    "uber" to "Uber",
    "didi" to "DiDi",
    "cabify" to "Cabify",
    "indrive" to "inDrive"
)

private fun buildOpenAppIntent(
    appKey: String?,
    intentFactory: (IntentSpec) -> Intent
): Intent? {
    val normalizedAppKey = appKey?.trim()?.takeIf { key -> key.isNotEmpty() } ?: return null
    val spec = deepLinkSpecFor(normalizedAppKey)
    val uri = spec?.uri ?: fallbackPlayStoreUrlFor(normalizedAppKey)
    return intentFactory(IntentSpec(action = Intent.ACTION_VIEW, uri = uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

data class IntentSpec(
    val action: String,
    val uri: String?
)

private fun newIntent(spec: IntentSpec): Intent =
    when (spec.uri) {
        null -> Intent(spec.action)
        else -> Intent(spec.action, Uri.parse(spec.uri))
    }

private fun String.encodeForUriQuery(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

private const val SpotifyHomeUri = "spotify:"

private fun IntentRouteRequest.volumeSteps(): Int =
    (params["steps"] as? Int)?.takeIf { steps -> steps > 0 } ?: 1

private fun buildViewIntent(
    uri: String,
    intentFactory: (IntentSpec) -> Intent
): Intent =
    intentFactory(IntentSpec(action = Intent.ACTION_VIEW, uri = uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private const val WhatsappBaseUri = "https://wa.me/?text="

private fun IntentRouteRequest.hasContactQuery(): Boolean =
    (params["contact_query"] as? String)?.isNotBlank() == true

private fun Any?.toStringList(): List<String> =
    (this as? List<*>)
        ?.mapNotNull { value -> value as? String }
        ?: emptyList()

/**
 * Defense-in-depth check for vision handlers. IntentRouter.route() already
 * funnels blocked_sensitive to handleBlocked, but if a handler is invoked
 * directly (tests, custom orchestrators) the descriptor must not be emitted.
 * Both locations are checked because some upstream pipelines may pass
 * safety_level inside params instead of the canonical top-level field.
 */
private fun IntentRouteRequest.isBlockedSensitive(): Boolean =
    safetyLevel == BlockedSensitiveLevel ||
        params["safety_level"] == BlockedSensitiveLevel

private const val BlockedSensitiveLevel = "blocked_sensitive"

private fun buildWhatsappIntent(
    uri: String,
    intentFactory: (IntentSpec) -> Intent
): Intent =
    buildViewIntent(uri = uri, intentFactory = intentFactory)

private fun buildDialIntent(
    phoneNumber: String?,
    intentFactory: (IntentSpec) -> Intent
): Intent {
    val phoneUri = phoneNumber
        ?.takeIf { value -> value.isNotBlank() }
        ?.let { value -> "tel:$value" }
    return intentFactory(IntentSpec(action = Intent.ACTION_DIAL, uri = phoneUri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun buildMediaButtonIntent(keyCode: Int): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        putExtra(
            Intent.EXTRA_KEY_EVENT,
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )
    }

private fun deepLinkSpecFor(appKey: String): DeepLinkSpec? =
    when (appKey) {
        "uber" -> DeepLinkConstants.Uber.OpenHome
        "didi" -> DeepLinkConstants.Didi.OpenHome
        "cabify" -> DeepLinkConstants.Cabify.OpenHome
        "indrive" -> DeepLinkConstants.InDrive.OpenHome
        "whatsapp" -> DeepLinkConstants.WhatsApp.OpenChatWithPhone
        "spotify" -> DeepLinkConstants.Spotify.OpenHome
        "google_maps" -> DeepLinkConstants.Maps.NavigateToDestination
        "waze" -> DeepLinkConstants.Waze.NavigateToDestination
        else -> null
    }

private fun fallbackPlayStoreUrlFor(appKey: String): String =
    when (appKey) {
        "uber" -> DeepLinkConstants.Uber.OpenHome.fallbackPlayStoreUrl
        "didi" -> DeepLinkConstants.Didi.OpenHome.fallbackPlayStoreUrl
        "cabify" -> DeepLinkConstants.Cabify.OpenHome.fallbackPlayStoreUrl
        "indrive" -> DeepLinkConstants.InDrive.OpenHome.fallbackPlayStoreUrl
        "whatsapp" -> DeepLinkConstants.WhatsApp.OpenChatWithPhone.fallbackPlayStoreUrl
        "spotify" -> DeepLinkConstants.Spotify.OpenHome.fallbackPlayStoreUrl
        "google_maps" -> DeepLinkConstants.Maps.NavigateToDestination.fallbackPlayStoreUrl
        "waze" -> DeepLinkConstants.Waze.NavigateToDestination.fallbackPlayStoreUrl
        else -> "https://play.google.com/store/search?q=$appKey&c=apps"
    }
