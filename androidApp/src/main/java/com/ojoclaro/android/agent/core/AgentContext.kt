package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskType
import com.ojoclaro.android.risk.RiskWarning

/**
 * Composer del contexto del agente.
 *
 * `AgentContextSnapshot` es el value-class pasivo que recibe el planner/evaluator.
 * `AgentContext` es la fábrica que toma los ingredientes crudos (comando,
 * pantalla, paquete activo, memoria, modo) y produce un [AgentContextSnapshot]
 * enriquecido con [RiskWarning]s pre-computados.
 *
 * Por qué existe como capa separada:
 *  - El [com.ojoclaro.android.agent.core.AgentActionEvaluator] no debe tener que
 *    saber cómo leer pantalla, ni cuándo correr el RiskDetector.
 *  - El caller (HomeViewModel/UI) ya tiene acceso al texto visible vía
 *    [com.ojoclaro.android.accessibility.OjoClaroAccessibilityService] y al
 *    nombre del paquete activo. Acá lo combinamos una sola vez.
 *  - Es puro Kotlin, sin Android, sin coroutines. Testeable directo.
 *
 * Reglas:
 *  - Si el caller pasa [screenText] vacío y `screenPackageName` null, no se
 *    corre el detector — los warnings quedan vacíos.
 *  - Los warnings se ordenan por severidad descendente (3 → 1), así el
 *    consumidor puede inspeccionar el primero como "el más grave".
 *  - Esta clase NO almacena el texto crudo de la pantalla. Solo deriva flags
 *    y warnings. Lo que sí se pasa al snapshot es la pantalla resumida
 *    ([AgentScreenContext.shortSummary]), que ya está acotada por el caller.
 */
object AgentContext {

    fun build(
        mode: AgentExecutionMode,
        agentState: AgentState,
        nowMillis: Long,
        screen: AgentScreenContext? = null,
        commandRawText: String = "",
        memory: AgentMemoryContext = AgentMemoryContext(),
        hasPendingExternalAction: Boolean = false,
        hasActiveChainedPlan: Boolean = false,
        isInEmergency: Boolean = false,
        riskDetector: RiskDetector = DEFAULT_RISK_DETECTOR
    ): AgentContextSnapshot {
        val screenWarnings = computeScreenWarnings(screen, riskDetector)
        val commandWarnings = computeCommandWarnings(commandRawText, riskDetector)
        val effectiveScreen = screen?.enrichWith(screenWarnings) ?: screen

        return AgentContextSnapshot(
            mode = mode,
            agentState = agentState,
            screen = effectiveScreen,
            memory = memory,
            hasPendingExternalAction = hasPendingExternalAction,
            hasActiveChainedPlan = hasActiveChainedPlan,
            isInEmergency = isInEmergency,
            nowMillis = nowMillis,
            screenRiskWarnings = screenWarnings,
            commandRiskWarnings = commandWarnings
        )
    }

    private fun computeScreenWarnings(
        screen: AgentScreenContext?,
        detector: RiskDetector
    ): List<RiskWarning> {
        if (screen == null) return emptyList()
        val fromSummary = if (screen.shortSummary.isNotBlank()) {
            detector.detectFromVisibleText(screen.shortSummary)
        } else {
            emptyList()
        }
        val fromPackage = detector.detectFromPackageName(screen.packageName)
        return mergeBySeverity(fromSummary + fromPackage)
    }

    private fun computeCommandWarnings(
        commandRawText: String,
        detector: RiskDetector
    ): List<RiskWarning> {
        if (commandRawText.isBlank()) return emptyList()
        return mergeBySeverity(detector.detectFromCommand(commandRawText))
    }

    private fun mergeBySeverity(warnings: List<RiskWarning>): List<RiskWarning> {
        if (warnings.isEmpty()) return emptyList()
        return warnings
            .distinctBy { it.type }
            .sortedByDescending { it.severity }
    }

    /**
     * Si el caller no marcó manualmente isSensitive/isBankingScreen pero los
     * warnings pre-computados lo evidencian, derivamos los flags. Es defensivo:
     * jamás bajamos sensibilidad, solo subimos.
     */
    private fun AgentScreenContext.enrichWith(warnings: List<RiskWarning>): AgentScreenContext {
        if (warnings.isEmpty()) return this
        val derivedBank = isBankingScreen || warnings.any { it.type == RiskType.BANKING_SCREEN }
        val derivedPassword =
            containsPasswordField || warnings.any { it.type == RiskType.PASSWORD_FIELD }
        val derivedVerification =
            containsVerificationCode || warnings.any { it.type == RiskType.VERIFICATION_CODE }
        val derivedSensitive = isSensitive || derivedBank || derivedPassword || derivedVerification
        return copy(
            isSensitive = derivedSensitive,
            isBankingScreen = derivedBank,
            containsPasswordField = derivedPassword,
            containsVerificationCode = derivedVerification
        )
    }

    private val DEFAULT_RISK_DETECTOR = RiskDetector()

    /**
     * Overload que arma el snapshot a partir de un [StructuredScreenSnapshot]
     * real (paquete 3). Los warnings y flags de pantalla ya vienen calculados
     * por el [com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshotBuilder],
     * así que NO volvemos a correr el RiskDetector sobre el texto crudo: solo
     * combinamos lo que el structured ya derivó.
     *
     * El [commandRawText] sí pasa por el detector — el comando del usuario es
     * input nuevo y necesita revisión independiente.
     */
    fun buildFromStructured(
        mode: AgentExecutionMode,
        agentState: AgentState,
        nowMillis: Long,
        structured: StructuredScreenSnapshot?,
        commandRawText: String = "",
        memory: AgentMemoryContext = AgentMemoryContext(),
        hasPendingExternalAction: Boolean = false,
        hasActiveChainedPlan: Boolean = false,
        isInEmergency: Boolean = false,
        riskDetector: RiskDetector = DEFAULT_RISK_DETECTOR
    ): AgentContextSnapshot {
        val agentScreen = structured?.let(::toAgentScreenContext)
        val screenWarnings = structured?.warnings.orEmpty()
        val commandWarnings = computeCommandWarnings(commandRawText, riskDetector)

        return AgentContextSnapshot(
            mode = mode,
            agentState = agentState,
            screen = agentScreen,
            memory = memory,
            hasPendingExternalAction = hasPendingExternalAction,
            hasActiveChainedPlan = hasActiveChainedPlan,
            isInEmergency = isInEmergency,
            nowMillis = nowMillis,
            screenRiskWarnings = screenWarnings,
            commandRiskWarnings = commandWarnings
        )
    }

    /**
     * Convierte un [StructuredScreenSnapshot] a [AgentScreenContext] sin
     * perder los flags derivados (banking, password, verification).
     *
     * El `shortSummary` se construye con la primera línea redactada o el
     * heading focalizado, recortado. NUNCA contiene texto crudo sin redactar.
     */
    fun toAgentScreenContext(structured: StructuredScreenSnapshot): AgentScreenContext {
        val summarySeed = structured.focusedLabel
            ?: structured.redactedTextLines.firstOrNull()
            ?: ""
        val shortSummary = summarySeed.take(AGENT_SCREEN_SUMMARY_CHARS).trim()
        return AgentScreenContext(
            packageName = structured.packageName,
            shortSummary = shortSummary,
            isSensitive = structured.signals.isHotZone,
            isBankingScreen = structured.signals.isBankingApp,
            containsPasswordField = structured.signals.hasPasswordField,
            containsVerificationCode = structured.signals.hasVerificationCode
        )
    }

    private const val AGENT_SCREEN_SUMMARY_CHARS = 120
}
