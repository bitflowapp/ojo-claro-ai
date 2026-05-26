package com.ojoclaro.android.agent.task.followup

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObservationType
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenUpdateResult
import java.util.Locale

class AgentTaskFollowUpCoordinator(
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val policy: AgentTaskFollowUpPolicy = AgentTaskFollowUpPolicy(),
    private val cooldown: AgentTaskFollowUpCooldown = AgentTaskFollowUpCooldown(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    @Volatile
    private var previousSnapshot: StructuredScreenSnapshot? = null

    fun isEnabled(): Boolean = flags().taskAutoFollowUpEnabled

    fun onSnapshot(
        currentPlan: AgentTaskPlan?,
        currentSnapshot: StructuredScreenSnapshot?,
        currentAppStateName: String? = null,
        hasPendingConfirmation: Boolean = false,
        isTalkBackActive: Boolean = false,
        observeScreenForCurrentTask: (StructuredScreenSnapshot?) -> AgentTaskScreenUpdateResult
    ): AgentTaskFollowUpDecision {
        val event = AgentTaskFollowUpEvent(
            currentPlan = currentPlan,
            previousSnapshot = previousSnapshot,
            currentSnapshot = currentSnapshot,
            currentAppStateName = currentAppStateName,
            hasPendingConfirmation = hasPendingConfirmation,
            isTalkBackActive = isTalkBackActive,
            nowMillis = clock()
        )
        val decision = decide(event, observeScreenForCurrentTask)
        previousSnapshot = currentSnapshot
        return decision
    }

    fun decide(
        event: AgentTaskFollowUpEvent,
        observeScreenForCurrentTask: (StructuredScreenSnapshot?) -> AgentTaskScreenUpdateResult
    ): AgentTaskFollowUpDecision {
        if (!flags().taskAutoFollowUpEnabled) {
            return AgentTaskFollowUpDecision.noOp(
                trigger = AgentTaskFollowUpTrigger.FLAG_DISABLED,
                suppressReason = AgentTaskFollowUpSuppressReason.FLAG_DISABLED
            )
        }

        val policyResult = policy.evaluate(event)
        if (!policyResult.shouldObserve) {
            return AgentTaskFollowUpDecision.noOp(
                trigger = policyResult.trigger,
                suppressReason = suppressReasonFor(policyResult.trigger)
            )
        }

        val observation = observeScreenForCurrentTask(event.currentSnapshot)
        val candidate = speechCandidate(
            event = event,
            policyResult = policyResult,
            observation = observation
        ) ?: return AgentTaskFollowUpDecision(
            action = AgentTaskFollowUpAction.OBSERVE_ONLY,
            trigger = policyResult.trigger,
            importance = policyResult.importance,
            observationResult = observation,
            semanticKey = policyResult.semanticKey,
            reasonKey = policyResult.reasonKey,
            suppressReason = AgentTaskFollowUpSuppressReason.NO_SPEECH_CANDIDATE
        )

        if (candidate.text.containsUnsafeCompletionClaim()) {
            return suppress(
                policyResult = policyResult,
                observation = observation,
                reason = AgentTaskFollowUpSuppressReason.UNSAFE_SPEECH,
                spokenText = candidate.text,
                semanticKey = candidate.semanticKey,
                reasonKey = candidate.reasonKey,
                importance = candidate.importance
            )
        }

        if (event.hasPendingConfirmation &&
            candidate.importance < AgentTaskFollowUpImportance.HIGH
        ) {
            return suppress(
                policyResult = policyResult,
                observation = observation,
                reason = AgentTaskFollowUpSuppressReason.PENDING_CONFIRMATION,
                spokenText = candidate.text,
                semanticKey = candidate.semanticKey,
                reasonKey = candidate.reasonKey,
                importance = candidate.importance
            )
        }

        if (event.isTalkBackActive &&
            candidate.importance < AgentTaskFollowUpImportance.HIGH
        ) {
            return suppress(
                policyResult = policyResult,
                observation = observation,
                reason = AgentTaskFollowUpSuppressReason.TALKBACK_ACTIVE,
                spokenText = candidate.text,
                semanticKey = candidate.semanticKey,
                reasonKey = candidate.reasonKey,
                importance = candidate.importance
            )
        }

        if (!cooldown.shouldAllow(
                semanticKey = candidate.semanticKey,
                importance = candidate.importance,
                reasonKey = candidate.reasonKey,
                nowMillis = event.nowMillis
            )
        ) {
            return suppress(
                policyResult = policyResult,
                observation = observation,
                reason = AgentTaskFollowUpSuppressReason.COOLDOWN,
                spokenText = candidate.text,
                semanticKey = candidate.semanticKey,
                reasonKey = candidate.reasonKey,
                importance = candidate.importance
            )
        }

        cooldown.remember(
            semanticKey = candidate.semanticKey,
            reasonKey = candidate.reasonKey,
            nowMillis = event.nowMillis
        )
        return AgentTaskFollowUpDecision(
            action = AgentTaskFollowUpAction.SPEAK,
            trigger = policyResult.trigger,
            importance = candidate.importance,
            observationResult = observation,
            spokenText = candidate.text,
            semanticKey = candidate.semanticKey,
            reasonKey = candidate.reasonKey,
            forceSpeech = candidate.importance >= AgentTaskFollowUpImportance.HIGH
        )
    }

    fun reset() {
        previousSnapshot = null
        cooldown.reset()
    }

    private fun speechCandidate(
        event: AgentTaskFollowUpEvent,
        policyResult: AgentTaskFollowUpPolicyResult,
        observation: AgentTaskScreenUpdateResult
    ): SpeechCandidate? {
        val text = observation.spokenText?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val specificSpeech = text != observation.safeStatusText.trim()
        val canSpeakUnchangedCue = policyResult.trigger == AgentTaskFollowUpTrigger.TASK_SCREEN_CUE_CHANGED &&
            specificSpeech
        val shouldSpeak = observation.blocked ||
            observation.changed ||
            canSpeakUnchangedCue
        if (!shouldSpeak) return null

        val type = observation.observation.type
        val importance = importanceForObservation(type, policyResult.importance)
        val semanticKey = semanticKeyFor(
            type = type,
            policyResult = policyResult,
            packageName = observation.observation.packageName
        )
        val reasonKey = reasonKeyFor(
            event = event,
            type = type,
            policyResult = policyResult
        )
        return SpeechCandidate(
            text = text,
            importance = importance,
            semanticKey = semanticKey,
            reasonKey = reasonKey
        )
    }

    private fun importanceForObservation(
        type: AgentTaskScreenObservationType,
        fallback: AgentTaskFollowUpImportance
    ): AgentTaskFollowUpImportance =
        when (type) {
            AgentTaskScreenObservationType.SENSITIVE_SCREEN_BLOCKED ->
                AgentTaskFollowUpImportance.CRITICAL
            AgentTaskScreenObservationType.RIDE_PAYMENT_VISIBLE,
            AgentTaskScreenObservationType.RIDE_PRICE_OR_DRIVER_VISIBLE,
            AgentTaskScreenObservationType.RIDE_FINAL_CONFIRMATION_VISIBLE,
            AgentTaskScreenObservationType.WHATSAPP_CHAT_CANDIDATE_VISIBLE,
            AgentTaskScreenObservationType.WHATSAPP_SEND_BUTTON_VISIBLE ->
                AgentTaskFollowUpImportance.HIGH
            AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED -> fallback
            else -> AgentTaskFollowUpImportance.NORMAL
        }

    private fun semanticKeyFor(
        type: AgentTaskScreenObservationType,
        policyResult: AgentTaskFollowUpPolicyResult,
        packageName: String?
    ): String {
        if (type == AgentTaskScreenObservationType.SENSITIVE_SCREEN_BLOCKED) {
            return "task.followup.sensitive"
        }
        if (policyResult.semanticKey != null &&
            type == AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED
        ) {
            return policyResult.semanticKey
        }
        return "task.followup.observation.${type.name.lowercase(Locale.ROOT)}." +
            packageName.orEmpty().lowercase(Locale.ROOT)
    }

    private fun reasonKeyFor(
        event: AgentTaskFollowUpEvent,
        type: AgentTaskScreenObservationType,
        policyResult: AgentTaskFollowUpPolicyResult
    ): String? =
        if (type == AgentTaskScreenObservationType.SENSITIVE_SCREEN_BLOCKED) {
            event.currentSnapshot?.let { policy.sensitiveReason(it) }
                ?: policyResult.reasonKey
        } else {
            policyResult.reasonKey
        }

    private fun suppress(
        policyResult: AgentTaskFollowUpPolicyResult,
        observation: AgentTaskScreenUpdateResult,
        reason: AgentTaskFollowUpSuppressReason,
        spokenText: String?,
        semanticKey: String?,
        reasonKey: String?,
        importance: AgentTaskFollowUpImportance
    ): AgentTaskFollowUpDecision =
        AgentTaskFollowUpDecision(
            action = AgentTaskFollowUpAction.SUPPRESS,
            trigger = policyResult.trigger,
            importance = importance,
            observationResult = observation,
            spokenText = spokenText,
            semanticKey = semanticKey,
            reasonKey = reasonKey,
            suppressReason = reason
        )

    private fun suppressReasonFor(
        trigger: AgentTaskFollowUpTrigger
    ): AgentTaskFollowUpSuppressReason =
        when (trigger) {
            AgentTaskFollowUpTrigger.FLAG_DISABLED -> AgentTaskFollowUpSuppressReason.FLAG_DISABLED
            AgentTaskFollowUpTrigger.NO_ACTIVE_TASK -> AgentTaskFollowUpSuppressReason.NO_ACTIVE_TASK
            AgentTaskFollowUpTrigger.NO_SNAPSHOT -> AgentTaskFollowUpSuppressReason.NO_SNAPSHOT
            else -> AgentTaskFollowUpSuppressReason.NO_RELEVANT_CHANGE
        }

    private fun String.containsUnsafeCompletionClaim(): Boolean {
        val normalized = AgentTaskPlannerSafeNormalize.normalize(this)
        return normalized.contains("taxi " + "pedido") ||
            normalized.contains("viaje " + "solicitado") ||
            normalized.contains("mensaje " + "enviado") ||
            normalized.contains("audio " + "enviado")
    }

    private data class SpeechCandidate(
        val text: String,
        val importance: AgentTaskFollowUpImportance,
        val semanticKey: String,
        val reasonKey: String?
    )
}

private object AgentTaskPlannerSafeNormalize {
    fun normalize(text: String): String =
        com.ojoclaro.android.agent.task.AgentTaskPlanner.normalize(text)
}
