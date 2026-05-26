package ai.ojoclaro.router

import ai.ojoclaro.resolver.DeepLinkConstants
import android.content.Intent
import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntentRouterTest {
    @Test
    fun lowConfidenceRoutesToUnknown() {
        val handler = RecordingIntentRouteHandler()
        val gate = RecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(intentJson(intent = "open_app", confidence = 0.59))

        assertEquals(listOf("unknown"), handler.calls)
        assertEquals(0, gate.submittedCount)
    }

    @Test
    fun blockedSensitiveRoutesToBlocked() {
        val handler = RecordingIntentRouteHandler()
        val gate = RecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(
            intentJson(
                intent = "open_app",
                safetyLevel = "blocked_sensitive"
            )
        )

        assertEquals(listOf("blocked"), handler.calls)
        assertEquals(0, gate.submittedCount)
    }

    @Test
    fun openAppAllowSafeRoutesToOpenApp() {
        val handler = RecordingIntentRouteHandler()
        val router = IntentRouter(handler, RecordingSafeExecutionDelegate())

        router.route(intentJson(intent = "open_app"))

        assertEquals(listOf("open_app"), handler.calls)
    }

    @Test
    fun handleOpenAppKnownAppKeyUsesDeepLinkUri() {
        val handler = testHandler()

        val intent = handler.handleOpenApp(
            request(params = mapOf("app_name" to "spotify"))
        )

        assertNotNull(intent)
        assertEquals(DeepLinkConstants.Spotify.OpenHome.uri, intent.dataString)
    }

    @Test
    fun handleOpenAppUnknownAppKeyUsesPlayStoreFallbackUri() {
        val handler = testHandler()

        val intent = handler.handleOpenApp(
            request(params = mapOf("app_name" to "unknown_app"))
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().startsWith("https://play.google.com/store/"))
    }

    @Test
    fun handleOpenAppIntentsUseNewTaskFlag() {
        val handler = testHandler()
        val knownIntent = handler.handleOpenApp(
            request(params = mapOf("app_name" to "uber"))
        )
        val unknownIntent = handler.handleOpenApp(
            request(params = mapOf("app_name" to "unknown_app"))
        )

        assertNotNull(knownIntent)
        assertNotNull(unknownIntent)
        assertTrue(knownIntent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(unknownIntent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleOpenRideAppPreferredUberInstalledReturnsUberIntentAndDisclaimer() {
        val handler = testHandler(installedAppKeys = listOf("uber"))

        val result = handler.handleOpenRideApp(
            request(params = mapOf("preferred_app" to "uber"))
        )

        assertNotNull(result.intent)
        assertEquals(DeepLinkConstants.Uber.OpenHome.uri, result.intent.dataString)
        assertTrue(result.disclaimerText.contains("Uber"))
    }

    @Test
    fun handleOpenRideAppPreferredUberNotInstalledFallsBackToFirstAvailableRideApp() {
        val handler = testHandler(installedAppKeys = listOf("cabify", "didi"))

        val result = handler.handleOpenRideApp(
            request(params = mapOf("preferred_app" to "uber"))
        )

        assertNotNull(result.intent)
        assertEquals(DeepLinkConstants.Cabify.OpenHome.uri, result.intent.dataString)
        assertTrue(result.disclaimerText.contains("Cabify"))
    }

    @Test
    fun handleOpenRideAppNoPreferredAppPicksDidiWhenInstalled() {
        val handler = testHandler(installedAppKeys = listOf("didi"))

        val result = handler.handleOpenRideApp(
            request(params = mapOf("preferred_app" to null))
        )

        assertNotNull(result.intent)
        assertEquals(DeepLinkConstants.Didi.OpenHome.uri, result.intent.dataString)
        assertTrue(result.disclaimerText.contains("DiDi"))
    }

    @Test
    fun handleOpenRideAppNoPreferredAppAndNoRideAppsReturnsEmptyResult() {
        val handler = testHandler(installedAppKeys = emptyList())

        val result = handler.handleOpenRideApp(
            request(params = mapOf("preferred_app" to null))
        )

        assertEquals(null, result.intent)
        assertEquals("", result.disclaimerText)
    }

    @Test
    fun handleOpenWhatsappChatWithContactQueryReturnsWaMeIntent() {
        val handler = testHandler()

        val intent = handler.handleOpenWhatsappChat(
            request(params = mapOf("contact_query" to "Marco"))
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("wa.me"))
    }

    @Test
    fun handleOpenWhatsappChatWithNullContactQueryReturnsNull() {
        val handler = testHandler()

        val intent = handler.handleOpenWhatsappChat(
            request(params = mapOf("contact_query" to null))
        )

        assertEquals(null, intent)
    }

    @Test
    fun handleOpenWhatsappChatIntentUsesNewTaskFlag() {
        val handler = testHandler()

        val intent = handler.handleOpenWhatsappChat(
            request(params = mapOf("contact_query" to "Marco"))
        )

        assertNotNull(intent)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleComposeWhatsappMessageWithBothParamsEncodesMessageText() {
        val handler = testHandler()

        val intent = handler.handleComposeWhatsappMessage(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "message_text" to "hola Marco, estas bien?"
                )
            )
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("hola+Marco%2C+estas+bien%3F"))
    }

    @Test
    fun handleComposeWhatsappMessageWithNullMessageTextDelegatesToChatOpen() {
        val handler = testHandler()

        val intent = handler.handleComposeWhatsappMessage(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "message_text" to null
                )
            )
        )

        assertNotNull(intent)
        assertEquals("https://wa.me/?text=", intent.dataString)
    }

    @Test
    fun handleComposeWhatsappMessageWithNullContactQueryReturnsNull() {
        val handler = testHandler()

        val intent = handler.handleComposeWhatsappMessage(
            request(
                params = mapOf(
                    "contact_query" to null,
                    "message_text" to "hola"
                )
            )
        )

        assertEquals(null, intent)
    }

    @Test
    fun handleOpenPhoneReturnsDialIntent() {
        val handler = testHandler()

        val intent = handler.handleOpenPhone(request(params = emptyMap()))

        assertNotNull(intent)
        assertEquals(Intent.ACTION_DIAL, intent.action)
    }

    @Test
    fun handleOpenPhoneUsesNewTaskFlag() {
        val handler = testHandler()

        val intent = handler.handleOpenPhone(request(params = emptyMap()))

        assertNotNull(intent)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleCallContactWithResolvedPhoneReturnsTelDialIntent() {
        val handler = testHandler()
        val resolvedPhone = "+5491123456789"

        val intent = handler.handleCallContact(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "resolved_phone" to resolvedPhone
                )
            )
        )

        assertNotNull(intent)
        assertEquals("tel:$resolvedPhone", intent.dataString)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleCallContactWithoutResolvedPhoneReturnsEmptyDialIntent() {
        val handler = testHandler()

        val intent = handler.handleCallContact(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "resolved_phone" to null
                )
            )
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(null, intent.dataString)
    }

    @Test
    fun handleCallContactWithNullContactQueryReturnsNull() {
        val handler = testHandler()

        val intent = handler.handleCallContact(
            request(
                params = mapOf(
                    "contact_query" to null,
                    "resolved_phone" to "+5491123456789"
                )
            )
        )

        assertEquals(null, intent)
    }

    @Test
    fun handleCallContactAlwaysUsesNewTaskFlag() {
        val handler = testHandler()

        val resolvedPhoneIntent = handler.handleCallContact(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "resolved_phone" to "+5491123456789"
                )
            )
        )
        val emptyDialIntent = handler.handleCallContact(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "resolved_phone" to null
                )
            )
        )

        assertNotNull(resolvedPhoneIntent)
        assertNotNull(emptyDialIntent)
        assertTrue(resolvedPhoneIntent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(emptyDialIntent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleGetCurrentLocationReturnsGeoIntent() {
        val handler = testHandler()

        val intent = handler.handleGetCurrentLocation(request(params = emptyMap()))

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("geo"))
    }

    @Test
    fun handleGetCurrentLocationUsesNewTaskFlag() {
        val handler = testHandler()

        val intent = handler.handleGetCurrentLocation(request(params = emptyMap()))

        assertNotNull(intent)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleOpenMapsWithDestinationUsesEncodedDestination() {
        val handler = testHandler()

        val intent = handler.handleOpenMaps(
            request(params = mapOf("destination" to "Avenida Corrientes 1234"))
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("Avenida+Corrientes+1234"))
    }

    @Test
    fun handleOpenMapsWithoutDestinationUsesCurrentLocationGeoUri() {
        val handler = testHandler()

        val intent = handler.handleOpenMaps(
            request(params = mapOf("destination" to null))
        )

        assertNotNull(intent)
        assertEquals("geo:0,0", intent.dataString)
    }

    @Test
    fun handleOpenMapsUsesNewTaskFlag() {
        val handler = testHandler()

        val intent = handler.handleOpenMaps(
            request(params = mapOf("destination" to "Obelisco"))
        )

        assertNotNull(intent)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleNavigateToUsesWazeWhenRequestedAndInstalled() {
        val handler = testHandler(installedAppKeys = listOf("waze"))

        val intent = handler.handleNavigateTo(
            request(
                params = mapOf(
                    "destination" to "Obelisco",
                    "app" to "waze"
                )
            )
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("waze://"))
    }

    @Test
    fun handleNavigateToFallsBackToGoogleNavigationWhenWazeNotInstalled() {
        val handler = testHandler(installedAppKeys = emptyList())

        val intent = handler.handleNavigateTo(
            request(
                params = mapOf(
                    "destination" to "Obelisco",
                    "app" to "waze"
                )
            )
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("google.navigation"))
    }

    @Test
    fun handleNavigateToWithoutDestinationReturnsNull() {
        val handler = testHandler(installedAppKeys = listOf("waze"))

        val intent = handler.handleNavigateTo(
            request(
                params = mapOf(
                    "destination" to null,
                    "app" to "waze"
                )
            )
        )

        assertEquals(null, intent)
    }

    @Test
    fun handleNavigateToUsesNewTaskFlag() {
        val handler = testHandler(installedAppKeys = listOf("waze"))

        val intent = handler.handleNavigateTo(
            request(
                params = mapOf(
                    "destination" to "Obelisco",
                    "app" to "waze"
                )
            )
        )

        assertNotNull(intent)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun handleSaveLocationAliasReturnsSaveActionWithAlias() {
        val handler = testHandler()

        val action = handler.handleSaveLocationAlias(
            request(params = mapOf("alias" to "casa"))
        )

        assertNotNull(action)
        assertEquals(LocationAliasActionType.SAVE, action.type)
        assertEquals("casa", action.alias)
    }

    @Test
    fun handleSaveLocationAliasWithNullAliasReturnsNull() {
        val handler = testHandler()

        val action = handler.handleSaveLocationAlias(
            request(params = mapOf("alias" to null))
        )

        assertEquals(null, action)
    }

    @Test
    fun handleListLocationAliasesReturnsListAction() {
        val handler = testHandler()

        val action = handler.handleListLocationAliases(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(LocationAliasActionType.LIST, action.type)
    }

    @Test
    fun handleDeleteLocationAliasReturnsDeleteActionWithAlias() {
        val handler = testHandler()

        val action = handler.handleDeleteLocationAlias(
            request(params = mapOf("alias" to "trabajo"))
        )

        assertNotNull(action)
        assertEquals(LocationAliasActionType.DELETE, action.type)
        assertEquals("trabajo", action.alias)
    }

    @Test
    fun handleDeleteLocationAliasWithNullAliasReturnsNull() {
        val handler = testHandler()

        val action = handler.handleDeleteLocationAlias(
            request(params = mapOf("alias" to null))
        )

        assertEquals(null, action)
    }

    @Test
    fun handleOpenSpotifyReturnsSpotifyUriIntent() {
        val handler = testHandler()

        val intent = handler.handleOpenSpotify(request(params = emptyMap()))

        assertNotNull(intent)
        assertEquals("spotify:", intent.dataString)
    }

    @Test
    fun handlePlayMusicWithQueryReturnsSpotifySearchIntent() {
        val handler = testHandler()

        val intent = handler.handlePlayMusic(
            request(params = mapOf("query" to "Luis Alberto Spinetta"))
        )

        assertNotNull(intent)
        assertTrue(intent.dataString.orEmpty().contains("spotify:search:"))
    }

    @Test
    fun handlePlayMusicWithoutQueryReturnsSpotifyHomeIntent() {
        val handler = testHandler()

        val intent = handler.handlePlayMusic(
            request(params = mapOf("query" to null))
        )

        assertNotNull(intent)
        assertEquals("spotify:", intent.dataString)
    }

    @Test
    fun handlePauseMusicReturnsMediaButtonIntentWithPlayPauseKeycode() {
        val handler = testHandler()

        val intent = handler.handlePauseMusic(request(params = emptyMap()))

        assertNotNull(intent)
        assertEquals(Intent.ACTION_MEDIA_BUTTON, intent.action)
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            (intent as TestMediaButtonIntent).recordedKeyCode
        )
    }

    @Test
    fun handleNextSongReturnsMediaButtonIntentWithNextKeycode() {
        val handler = testHandler()

        val intent = handler.handleNextSong(request(params = emptyMap()))

        assertNotNull(intent)
        assertEquals(Intent.ACTION_MEDIA_BUTTON, intent.action)
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            (intent as TestMediaButtonIntent).recordedKeyCode
        )
    }

    @Test
    fun handleVolumeUpReturnsUpDirection() {
        val handler = testHandler()

        val action = handler.handleVolumeUp(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(VolumeDirection.UP, action.direction)
    }

    @Test
    fun handleVolumeDownReturnsDownDirection() {
        val handler = testHandler()

        val action = handler.handleVolumeDown(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(VolumeDirection.DOWN, action.direction)
    }

    @Test
    fun handleVolumeStepsDefaultToOneWhenNull() {
        val handler = testHandler()

        val upAction = handler.handleVolumeUp(request(params = mapOf("steps" to null)))
        val downAction = handler.handleVolumeDown(request(params = mapOf("steps" to null)))

        assertNotNull(upAction)
        assertNotNull(downAction)
        assertEquals(1, upAction.steps)
        assertEquals(1, downAction.steps)
    }

    @Test
    fun handleVolumeStepsUseProvidedValue() {
        val handler = testHandler()

        val upAction = handler.handleVolumeUp(request(params = mapOf("steps" to 3)))
        val downAction = handler.handleVolumeDown(request(params = mapOf("steps" to 3)))

        assertNotNull(upAction)
        assertNotNull(downAction)
        assertEquals(3, upAction.steps)
        assertEquals(3, downAction.steps)
    }

    @Test
    fun handleRememberMemoryReturnsRememberActionWithValue() {
        val handler = testHandler()

        val action = handler.handleRememberMemory(
            request(params = mapOf("text" to "Mi farmacia queda en la esquina"))
        )

        assertNotNull(action)
        assertEquals(MemoryActionType.REMEMBER, action.type)
        assertEquals("Mi farmacia queda en la esquina", action.value)
    }

    @Test
    fun handleRememberMemoryWithNullTextReturnsNull() {
        val handler = testHandler()

        val action = handler.handleRememberMemory(
            request(params = mapOf("text" to null))
        )

        assertEquals(null, action)
    }

    @Test
    fun handleListMemoryReturnsListMemoryAction() {
        val handler = testHandler()

        val action = handler.handleListMemory(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(MemoryActionType.LIST_MEMORY, action.type)
    }

    @Test
    fun handleClearMemoryReturnsClearMemoryAction() {
        val handler = testHandler()

        val action = handler.handleClearMemory(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(MemoryActionType.CLEAR_MEMORY, action.type)
    }

    @Test
    fun handleSaveContactReturnsSaveContactActionWithKeyAndValue() {
        val handler = testHandler()

        val action = handler.handleSaveContact(
            request(
                params = mapOf(
                    "name" to "Marco",
                    "phone" to "+5491123456789"
                )
            )
        )

        assertNotNull(action)
        assertEquals(MemoryActionType.SAVE_CONTACT, action.type)
        assertEquals("Marco", action.key)
        assertEquals("+5491123456789", action.value)
    }

    @Test
    fun handleSaveContactWithNullNameReturnsNull() {
        val handler = testHandler()

        val action = handler.handleSaveContact(
            request(
                params = mapOf(
                    "name" to null,
                    "phone" to "+5491123456789"
                )
            )
        )

        assertEquals(null, action)
    }

    @Test
    fun handleSaveContactPhoneReturnsSaveContactPhoneActionWithKeyAndValue() {
        val handler = testHandler()

        val action = handler.handleSaveContactPhone(
            request(
                params = mapOf(
                    "contact_query" to "Marco",
                    "phone" to "+5491123456789"
                )
            )
        )

        assertNotNull(action)
        assertEquals(MemoryActionType.SAVE_CONTACT_PHONE, action.type)
        assertEquals("Marco", action.key)
        assertEquals("+5491123456789", action.value)
    }

    @Test
    fun handleSaveContactPhoneWithNullContactQueryReturnsNull() {
        val handler = testHandler()

        val action = handler.handleSaveContactPhone(
            request(
                params = mapOf(
                    "contact_query" to null,
                    "phone" to "+5491123456789"
                )
            )
        )

        assertEquals(null, action)
    }

    @Test
    fun handleListContactsReturnsListContactsAction() {
        val handler = testHandler()

        val action = handler.handleListContacts(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(MemoryActionType.LIST_CONTACTS, action.type)
    }

    @Test
    fun handleDeleteContactReturnsDeleteContactActionWithKey() {
        val handler = testHandler()

        val action = handler.handleDeleteContact(
            request(params = mapOf("contact_query" to "Marco"))
        )

        assertNotNull(action)
        assertEquals(MemoryActionType.DELETE_CONTACT, action.type)
        assertEquals("Marco", action.key)
    }

    @Test
    fun handleDeleteContactWithNullContactQueryReturnsNull() {
        val handler = testHandler()

        val action = handler.handleDeleteContact(
            request(params = mapOf("contact_query" to null))
        )

        assertEquals(null, action)
    }

    @Test
    fun handleCreateReminderReturnsCreateReminderActionWithTextAndDatetime() {
        val handler = testHandler()

        val action = handler.handleCreateReminder(
            request(
                params = mapOf(
                    "text" to "Tomar medicacion",
                    "datetime" to "2026-05-25T20:00:00"
                )
            )
        )

        assertNotNull(action)
        assertEquals(ReminderActionType.CREATE_REMINDER, action.type)
        assertEquals("Tomar medicacion", action.text)
        assertEquals("2026-05-25T20:00:00", action.datetime)
    }

    @Test
    fun handleCreateReminderWithNullTextReturnsNull() {
        val handler = testHandler()

        val action = handler.handleCreateReminder(
            request(
                params = mapOf(
                    "text" to null,
                    "datetime" to "2026-05-25T20:00:00"
                )
            )
        )

        assertEquals(null, action)
    }

    @Test
    fun handleListRemindersReturnsListRemindersAction() {
        val handler = testHandler()

        val action = handler.handleListReminders(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(ReminderActionType.LIST_REMINDERS, action.type)
    }

    @Test
    fun handleCancelReminderReturnsCancelReminderActionWithReminderId() {
        val handler = testHandler()

        val action = handler.handleCancelReminder(
            request(params = mapOf("reminder_id" to "medicacion noche"))
        )

        assertNotNull(action)
        assertEquals(ReminderActionType.CANCEL_REMINDER, action.type)
        assertEquals("medicacion noche", action.reminderId)
    }

    @Test
    fun handleCancelReminderWithNullReminderIdReturnsActionWithNullReminderId() {
        val handler = testHandler()

        val action = handler.handleCancelReminder(
            request(params = mapOf("reminder_id" to null))
        )

        assertNotNull(action)
        assertEquals(ReminderActionType.CANCEL_REMINDER, action.type)
        assertEquals(null, action.reminderId)
    }

    @Test
    fun handleCreateAlarmReturnsCreateAlarmActionWithDatetimeAndLabel() {
        val handler = testHandler()

        val action = handler.handleCreateAlarm(
            request(
                params = mapOf(
                    "datetime" to "2026-05-26T07:30:00",
                    "label" to "levantarse"
                )
            )
        )

        assertNotNull(action)
        assertEquals(ReminderActionType.CREATE_ALARM, action.type)
        assertEquals("2026-05-26T07:30:00", action.datetime)
        assertEquals("levantarse", action.label)
    }

    @Test
    fun handleCreateAlarmWithNullDatetimeReturnsNull() {
        val handler = testHandler()

        val action = handler.handleCreateAlarm(
            request(
                params = mapOf(
                    "datetime" to null,
                    "label" to "levantarse"
                )
            )
        )

        assertEquals(null, action)
    }

    @Test
    fun handleConfirmReturnsConfirmAction() {
        val handler = testHandler()

        val action = handler.handleConfirm(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(ConversationActionType.CONFIRM, action.type)
    }

    @Test
    fun handleCancelReturnsCancelAction() {
        val handler = testHandler()

        val action = handler.handleCancel(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(ConversationActionType.CANCEL, action.type)
    }

    @Test
    fun handleStopSpeakingReturnsStopSpeakingAction() {
        val handler = testHandler()

        val action = handler.handleStopSpeaking(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(ConversationActionType.STOP_SPEAKING, action.type)
    }

    @Test
    fun handleRepeatLastReturnsRepeatLastAction() {
        val handler = testHandler()

        val action = handler.handleRepeatLast(request(params = emptyMap()))

        assertNotNull(action)
        assertEquals(ConversationActionType.REPEAT_LAST, action.type)
    }

    @Test
    fun handleHelpReturnsHelpActionWithTopic() {
        val handler = testHandler()

        val action = handler.handleHelp(
            request(params = mapOf("topic" to "whatsapp"))
        )

        assertNotNull(action)
        assertEquals(ConversationActionType.HELP, action.type)
        assertEquals("whatsapp", action.topic)
    }

    @Test
    fun handleClarifyContactReturnsClarifyContactActionWithCandidates() {
        val handler = testHandler()

        val action = handler.handleClarifyContact(
            request(
                params = mapOf(
                    "action_pending" to "call_contact",
                    "candidates" to listOf("Marco", "Marcos")
                )
            )
        )

        assertNotNull(action)
        assertEquals(ConversationActionType.CLARIFY_CONTACT, action.type)
        assertEquals("call_contact", action.actionPending)
        assertEquals(listOf("Marco", "Marcos"), action.candidates)
    }

    @Test
    fun handleClarifyContactWithNullCandidatesReturnsEmptyList() {
        val handler = testHandler()

        val action = handler.handleClarifyContact(
            request(params = mapOf("candidates" to null))
        )

        assertNotNull(action)
        assertEquals(ConversationActionType.CLARIFY_CONTACT, action.type)
        assertEquals(emptyList(), action.candidates)
    }

    @Test
    fun handleUnknownReturnsUnknownActionWithRawText() {
        val handler = testHandler()

        val action = handler.handleUnknown(
            request(params = mapOf("raw_text" to "no entendi esto"))
        )

        assertNotNull(action)
        assertEquals(ConversationActionType.UNKNOWN, action.type)
        assertEquals("no entendi esto", action.rawText)
    }

    @Test
    fun handleBlockedReturnsBlockedActionWithReason() {
        val handler = testHandler()

        val action = handler.handleBlocked(
            request(params = mapOf("voice_response_template" to "blocked_sensitive"))
        )

        assertNotNull(action)
        assertEquals(ConversationActionType.BLOCKED, action.type)
        assertEquals("blocked_sensitive", action.blockedReason)
    }

    @Test
    fun composeWhatsappMessagePrepareOnlyDelegatesToSafeExecutionGate() {
        val handler = RecordingIntentRouteHandler()
        val gate = RecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(
            intentJson(
                intent = "compose_whatsapp_message",
                safetyLevel = "prepare_only"
            )
        )

        assertEquals(emptyList(), handler.calls)
        assertEquals(1, gate.submittedCount)
    }

    @Test
    fun callContactRequiresConfirmDelegatesToSafeExecutionGate() {
        val handler = RecordingIntentRouteHandler()
        val gate = RecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(
            intentJson(
                intent = "call_contact",
                safetyLevel = "requires_confirm"
            )
        )

        assertEquals(emptyList(), handler.calls)
        assertEquals(1, gate.submittedCount)
    }

    @Test
    fun clarifyContactRoutesToClarifyContact() {
        val handler = RecordingIntentRouteHandler()
        val router = IntentRouter(handler, RecordingSafeExecutionDelegate())

        router.route(intentJson(intent = "clarify_contact"))

        assertEquals(listOf("clarify_contact"), handler.calls)
    }

    @Test
    fun slotFillRoutesToUnknownWithoutCallingSlotFillHandler() {
        val handler = RecordingIntentRouteHandler()
        val router = IntentRouter(handler, RecordingSafeExecutionDelegate())

        router.route(intentJson(intent = "slot_fill"))

        assertEquals(listOf("unknown"), handler.calls)
        assertTrue("slot_fill" !in handler.calls)
        assertTrue("open_app" !in handler.calls)
    }

    @Test
    fun invalidConfirmationRoutesToInvalidConfirmation() {
        val handler = RecordingIntentRouteHandler()
        val router = IntentRouter(handler, RecordingSafeExecutionDelegate())

        router.route(intentJson(intent = "invalid_confirmation"))

        assertEquals(listOf("invalid_confirmation"), handler.calls)
    }

    @Test
    fun nullIntentRoutesToUnknown() {
        val handler = RecordingIntentRouteHandler()
        val router = IntentRouter(handler, RecordingSafeExecutionDelegate())

        router.route(intentJson(intent = null))

        assertEquals(listOf("unknown"), handler.calls)
    }

    @Test
    fun missingSafetyLevelRoutesToBlocked() {
        val handler = RecordingIntentRouteHandler()
        val gate = RecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(
            intentJson(intent = "open_app").minus("safety_level")
        )

        assertEquals(listOf("blocked"), handler.calls)
        assertEquals(0, gate.submittedCount)
    }

    private fun intentJson(
        intent: String?,
        confidence: Double = 0.9,
        safetyLevel: String = "allow_safe"
    ): Map<String, Any?> =
        mapOf(
            "intent" to intent,
            "confidence" to confidence,
            "params" to emptyMap<String, Any?>(),
            "safety_level" to safetyLevel,
            "voice_response" to null,
            "voice_response_template" to null,
            "raw_text" to "abrir app"
        )

    private fun request(params: Map<String, Any?>): IntentRouteRequest =
        IntentRouteRequest(
            intent = null,
            confidence = 0.9,
            params = params,
            safetyLevel = "allow_safe",
            voiceResponse = null,
            voiceResponseTemplate = null,
            rawText = "abrir app"
        )

    private fun testHandler(
        installedAppKeys: List<String> = emptyList()
    ): EmptyIntentRouteHandler =
        EmptyIntentRouteHandler(
            installedAppKeysProvider = { installedAppKeys },
            intentFactory = { spec -> TestIntent(spec) },
            mediaButtonIntentFactory = { keyCode -> TestMediaButtonIntent(keyCode) }
        )
}

private class RecordingIntentRouteHandler : IntentRouteHandler {
    val calls = mutableListOf<String>()

    override fun handleOpenApp(request: IntentRouteRequest): Intent? {
        calls += "open_app"
        return null
    }

    override fun handleClarifyContact(request: IntentRouteRequest): ConversationAction? {
        calls += "clarify_contact"
        return null
    }

    override fun handleInvalidConfirmation(request: IntentRouteRequest) {
        calls += "invalid_confirmation"
    }

    override fun handleUnknown(request: IntentRouteRequest): ConversationAction? {
        calls += "unknown"
        return null
    }

    override fun handleBlocked(request: IntentRouteRequest): ConversationAction? {
        calls += "blocked"
        return null
    }
}

private fun Intent.hasFlag(flag: Int): Boolean =
    flags and flag == flag

private class TestIntent(
    private val spec: IntentSpec
) : Intent() {
    private var recordedFlags = 0

    override fun getAction(): String = spec.action

    override fun getDataString(): String? = spec.uri

    override fun addFlags(flags: Int): Intent {
        recordedFlags = recordedFlags or flags
        return this
    }

    override fun getFlags(): Int = recordedFlags
}

private class TestMediaButtonIntent(
    val recordedKeyCode: Int
) : Intent() {
    override fun getAction(): String = ACTION_MEDIA_BUTTON

    @Suppress("DEPRECATION")
    @Deprecated("Overrides deprecated Android API for JVM test fake.")
    override fun <T : android.os.Parcelable?> getParcelableExtra(name: String?): T? {
        if (name != EXTRA_KEY_EVENT) return null
        @Suppress("UNCHECKED_CAST")
        return KeyEvent(KeyEvent.ACTION_DOWN, recordedKeyCode) as T
    }
}

private class RecordingSafeExecutionDelegate : AgentSafeExecutionDelegate {
    var submittedCount = 0
        private set

    override fun submit(request: IntentRouteRequest) {
        submittedCount += 1
    }
}
