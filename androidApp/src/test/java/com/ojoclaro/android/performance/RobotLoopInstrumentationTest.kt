package com.ojoclaro.android.performance

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotLoopInstrumentationTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.enabled = true
        RobotLoopInstrumentation.localLogSink = null
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
        RobotLoopInstrumentation.clear()
    }

    @Test
    fun instrumentationStoresDurationsOnlyAndNoSensitiveContent() {
        val sensitiveSnapshotText = "Sofi: mi clave es 1234 y mi saldo banco es 999"

        val result = RobotLoopInstrumentation.measure(RobotLoopMetric.SCREEN_SNAPSHOT) {
            sensitiveSnapshotText
        }

        assertEquals(sensitiveSnapshotText, result)
        val events = RobotLoopInstrumentation.snapshot()
        assertEquals(1, events.size)
        assertEquals(RobotLoopMetric.SCREEN_SNAPSHOT, events.single().metric)

        val serialized = events.single().toString()
        assertFalse(serialized.contains("Sofi", ignoreCase = true))
        assertFalse(serialized.contains("clave", ignoreCase = true))
        assertFalse(serialized.contains("saldo", ignoreCase = true))
        assertFalse(serialized.contains("1234"))
        assertTrue(events.single().durationNanos >= 0L)
    }

    @Test
    fun instrumentationCanBeDisabledLocally() {
        RobotLoopInstrumentation.enabled = false

        RobotLoopInstrumentation.measure(RobotLoopMetric.WHATSAPP_DETECTOR) {
            "anything"
        }
        RobotLoopInstrumentation.recordElapsedNanos(
            metric = RobotLoopMetric.VISIBLE_CHATS_READER,
            elapsedNanos = 10L
        )

        assertTrue(RobotLoopInstrumentation.snapshot().isEmpty())
    }

    @Test
    fun localLogSinkReceivesSafeMetricOnlyWhenEnabled() {
        val logged = mutableListOf<RobotLoopMetricEvent>()
        RobotLoopInstrumentation.localLogSink = logged::add

        RobotLoopInstrumentation.recordElapsedNanos(
            metric = RobotLoopMetric.ROUTINE_PREFERENCE_APPLIER,
            elapsedNanos = 2_000_000L
        )

        assertEquals(
            listOf(RobotLoopMetric.ROUTINE_PREFERENCE_APPLIER),
            logged.map { it.metric }
        )
        assertFalse(logged.single().toString().contains("mensaje", ignoreCase = true))
    }

    @Test
    fun localMetricSinkFailureDoesNotBreakInstrumentation() {
        RobotLoopInstrumentation.localLogSink = { error("sink failed") }

        RobotLoopInstrumentation.recordElapsedNanos(
            metric = RobotLoopMetric.SCREEN_SUMMARIZER,
            elapsedNanos = 3_000_000L
        )

        assertEquals(1, RobotLoopInstrumentation.snapshot().size)
        assertEquals(RobotLoopMetric.SCREEN_SUMMARIZER, RobotLoopInstrumentation.snapshot().single().metric)
    }

    @Test
    fun safeLogsStoreCountersAndBooleansOnly() {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.WHATSAPP_VISIBLE_CHATS_READER,
                result = RobotLoopLogResult.LISTED,
                durationMillis = 3L,
                packageName = "com.whatsapp",
                elementCount = 12,
                buttonCount = 3,
                fieldCount = 1,
                whatsappDetected = true,
                chatOpen = false,
                visibleChatCount = 2
            )
        )

        val log = RobotLoopInstrumentation.safeLogSnapshot().single().toString()
        assertTrue(log.contains("visibleChatCount=2"))
        assertFalse(log.contains("Sofi", ignoreCase = true))
        assertFalse(log.contains("estoy llegando", ignoreCase = true))
        assertFalse(log.contains("clave", ignoreCase = true))
    }

    @Test
    fun safeLogsCanBeDisabledLocally() {
        RobotLoopInstrumentation.safeLogsEnabled = false

        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.SCREEN_UNDERSTANDING,
                result = RobotLoopLogResult.OK
            )
        )

        assertTrue(RobotLoopInstrumentation.safeLogSnapshot().isEmpty())
    }

    @Test
    fun safeLogSinkFailureDoesNotBreakInstrumentation() {
        RobotLoopInstrumentation.localSafeLogSink = { error("safe sink failed") }

        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.ROUTING_AUDIT,
                result = RobotLoopLogResult.CORRECTED,
                handler = "voice_correction",
                commandRedacted = true
            )
        )

        assertEquals(1, RobotLoopInstrumentation.safeLogSnapshot().size)
        assertTrue(
            RobotLoopInstrumentation.safeLogSnapshot()
                .single()
                .toLogLine()
                .contains("handler=voice_correction")
        )
    }

    @Test
    fun voiceCommandSafeLogKeepsCommandRedacted() {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.VOICE_COMMAND,
                result = RobotLoopLogResult.UNDERSTOOD,
                durationMillis = 4L,
                robotState = "WAITING_WHATSAPP_ACTION",
                commandRedacted = true,
                handler = "agent_conversation",
                understood = true
            )
        )

        val log = RobotLoopInstrumentation.safeLogSnapshot().single().toLogLine()
        assertTrue(log.contains("commandRedacted=true"))
        assertTrue(log.contains("handler=agent_conversation"))
        assertFalse(log.contains("Marco", ignoreCase = true))
        assertFalse(log.contains("clave", ignoreCase = true))
        assertFalse(log.contains("mensaje privado", ignoreCase = true))
    }

    @Test
    fun routingAuditLogKeepsCommandRedactedAndShowsConsumedFlag() {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.ROUTING_AUDIT,
                result = RobotLoopLogResult.NOT_A_COMMAND,
                requestId = 7L,
                robotState = "READY",
                commandRedacted = true,
                handler = "visible_chats",
                understood = false,
                consumed = false,
                reasonCode = "not_matched",
                appState = "IDLE"
            )
        )

        val log = RobotLoopInstrumentation.safeLogSnapshot().single().toLogLine()
        assertTrue(log.contains("stage=ROUTING_AUDIT"))
        assertTrue(log.contains("requestId=7"))
        assertTrue(log.contains("handler=visible_chats"))
        assertTrue(log.contains("consumed=false"))
        assertTrue(log.contains("reason=not_matched"))
        assertTrue(log.contains("commandRedacted=true"))
        assertFalse(log.contains("ContactoDemo", ignoreCase = true))
        assertFalse(log.contains("texto real", ignoreCase = true))
    }

    @Test
    fun voiceCorrectionLogKeepsOnlySafeRoutingMetadata() {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.ROUTING_AUDIT,
                result = RobotLoopLogResult.CORRECTED,
                requestId = 9L,
                durationMillis = 2L,
                commandRedacted = true,
                handler = "voice_correction",
                targetIntent = "OPEN_WHATSAPP",
                confidence = "HIGH",
                consumed = false
            )
        )

        val log = RobotLoopInstrumentation.safeLogSnapshot().single().toLogLine()
        assertTrue(log.contains("handler=voice_correction"))
        assertTrue(log.contains("result=CORRECTED"))
        assertTrue(log.contains("targetIntent=OPEN_WHATSAPP"))
        assertTrue(log.contains("confidence=HIGH"))
        assertTrue(log.contains("commandRedacted=true"))
        assertFalse(log.contains("abrir ure Max", ignoreCase = true))
        assertFalse(log.contains("Marco", ignoreCase = true))
        assertFalse(log.contains("chat", ignoreCase = true))
    }
}
