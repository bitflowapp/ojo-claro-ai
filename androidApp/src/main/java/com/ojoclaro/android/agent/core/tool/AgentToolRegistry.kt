package com.ojoclaro.android.agent.core.tool

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentTool
import com.ojoclaro.android.agent.core.AgentToolCapability
import com.ojoclaro.android.agent.core.AgentToolId

/**
 * Registro estático de los tools que Ojo Claro reconoce HOY.
 *
 * Reglas:
 *  - El registry NO ejecuta acciones. Solo describe metadata.
 *  - La ejecución vive en las capas Android existentes (WhatsAppIntentHelper,
 *    MapsActionExecutor, PhoneActionExecutor, AccessibilityScreenReader, etc.).
 *  - Para que un LLM use un tool, primero debe estar acá. Lo que no aparece
 *    en este registro se rechaza por seguridad.
 *  - GENERIC_APP existe SOLO como interfaz reservada. Por default es BLOCKED.
 */
class AgentToolRegistry(
    private val tools: Map<AgentToolId, AgentTool> = DEFAULT_TOOLS
) {

    fun all(): Collection<AgentTool> = tools.values

    fun byId(id: AgentToolId): AgentTool? = tools[id]

    fun availableIn(mode: AgentExecutionMode): List<AgentTool> =
        tools.values.filter { it.isAvailableIn(mode) }

    fun coversIntent(intent: AgentIntent): List<AgentTool> =
        tools.values.filter { it.covers(intent) }

    fun firstFor(intent: AgentIntent, mode: AgentExecutionMode): AgentTool? =
        tools.values.firstOrNull { it.covers(intent) && it.isAvailableIn(mode) }

    fun isWhitelistedId(id: AgentToolId): Boolean = id in tools

    fun isWhitelistedToolName(name: String): Boolean {
        val match = AgentToolId.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return false
        return isWhitelistedId(match)
    }

    companion object {
        val ALL_MODES: Set<AgentExecutionMode> = AgentExecutionMode.entries.toSet()

        private val WHATSAPP_TOOL = AgentTool(
            id = AgentToolId.WHATSAPP,
            displayName = "WhatsApp",
            description = "Prepara mensajes de WhatsApp o abre la app. Nunca envía automáticamente.",
            capabilities = setOf(
                AgentToolCapability.OPENS_EXTERNAL_APP,
                AgentToolCapability.PREPARES_MESSAGE_WITHOUT_SENDING,
                AgentToolCapability.READS_CONTACTS_MEMORY,
                AgentToolCapability.REQUIRES_CONFIRMATION,
                AgentToolCapability.NEVER_AUTO_COMPLETES,
                AgentToolCapability.FORBIDDEN_ON_BANKING
            ),
            requiredSlots = setOf(AgentSlotName.CONTACT_NAME),
            optionalSlots = setOf(AgentSlotName.MESSAGE_TEXT),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = true,
            coversIntents = setOf(
                AgentIntent.OPEN_WHATSAPP,
                AgentIntent.OPEN_WHATSAPP_CHAT,
                AgentIntent.COMPOSE_WHATSAPP_MESSAGE
            )
        )

        private val MAPS_TOOL = AgentTool(
            id = AgentToolId.MAPS,
            displayName = "Maps",
            description = "Abre Maps o navegación. No conduce ni detecta peligros.",
            capabilities = setOf(
                AgentToolCapability.OPENS_EXTERNAL_APP,
                AgentToolCapability.READS_LOCATION,
                AgentToolCapability.NEVER_AUTO_COMPLETES,
                AgentToolCapability.FORBIDDEN_ON_BANKING
            ),
            requiredSlots = emptySet(),
            optionalSlots = setOf(AgentSlotName.DESTINATION, AgentSlotName.LOCATION_ALIAS),
            risk = AgentRiskLevel.LOW,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = true,
            coversIntents = setOf(
                AgentIntent.OPEN_MAPS,
                AgentIntent.GET_CURRENT_LOCATION,
                AgentIntent.NAVIGATE_TO_DESTINATION
            )
        )

        private val PHONE_TOOL = AgentTool(
            id = AgentToolId.PHONE,
            displayName = "Teléfono",
            description = "Abre el marcador o prepara un número. La llamada la dispara el usuario.",
            capabilities = setOf(
                AgentToolCapability.OPENS_EXTERNAL_APP,
                AgentToolCapability.READS_CONTACTS_MEMORY,
                AgentToolCapability.REQUIRES_CONFIRMATION,
                AgentToolCapability.NEVER_AUTO_COMPLETES,
                AgentToolCapability.EMERGENCY_CAPABLE,
                AgentToolCapability.FORBIDDEN_ON_BANKING
            ),
            requiredSlots = emptySet(),
            optionalSlots = setOf(AgentSlotName.CONTACT_NAME, AgentSlotName.PHONE_NUMBER),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = true,
            coversIntents = setOf(
                AgentIntent.OPEN_PHONE,
                AgentIntent.CALL_CONTACT
            )
        )

        private val SCREEN_READER_TOOL = AgentTool(
            id = AgentToolId.SCREEN_READER,
            displayName = "Lector de pantalla",
            description = "Lee texto visible cuando el usuario lo pide explícitamente.",
            capabilities = setOf(
                AgentToolCapability.READS_VISIBLE_SCREEN,
                AgentToolCapability.REQUIRES_CONFIRMATION,
                AgentToolCapability.FORBIDDEN_ON_BANKING,
                AgentToolCapability.OFFLINE_CAPABLE
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.HIGH,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED
            ),
            emergencyCapable = false,
            coversIntents = setOf(AgentIntent.READ_VISIBLE_SCREEN)
        )

        private val OCR_TOOL = AgentTool(
            id = AgentToolId.OCR,
            displayName = "Lectura con cámara",
            description = "Lee texto con la cámara con OCR local.",
            capabilities = setOf(
                AgentToolCapability.READS_CAMERA,
                AgentToolCapability.OFFLINE_CAPABLE,
                AgentToolCapability.FORBIDDEN_ON_BANKING
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = false,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED
            ),
            emergencyCapable = false,
            coversIntents = setOf(AgentIntent.READ_OCR_TEXT)
        )

        private val REPEAT_LAST_TOOL = AgentTool(
            id = AgentToolId.REPEAT_LAST,
            displayName = "Repetir",
            description = "Repite la última frase dicha por el asistente.",
            capabilities = setOf(
                AgentToolCapability.OFFLINE_CAPABLE
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.NONE,
            requiresConfirmation = false,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = true,
            coversIntents = setOf(AgentIntent.REPEAT_LAST)
        )

        private val EMERGENCY_TOOL = AgentTool(
            id = AgentToolId.EMERGENCY,
            displayName = "Modo emergencia",
            description = "Activa modo emergencia y prepara llamada/WhatsApp con contacto configurado. Nunca afirma que la ayuda fue enviada.",
            capabilities = setOf(
                AgentToolCapability.EMERGENCY_CAPABLE,
                AgentToolCapability.OPENS_EXTERNAL_APP,
                AgentToolCapability.READS_CONTACTS_MEMORY,
                AgentToolCapability.REQUIRES_CONFIRMATION,
                AgentToolCapability.NEVER_AUTO_COMPLETES,
                AgentToolCapability.OFFLINE_CAPABLE
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.HIGH,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = true,
            coversIntents = emptySet()
        )

        private val MEMORY_TOOL = AgentTool(
            id = AgentToolId.MEMORY,
            displayName = "Memoria personal",
            description = "Guarda, lista o borra recuerdos personales seguros. Pide confirmación.",
            capabilities = setOf(
                AgentToolCapability.READS_CONTACTS_MEMORY,
                AgentToolCapability.WRITES_MEMORY,
                AgentToolCapability.REQUIRES_CONFIRMATION,
                AgentToolCapability.OFFLINE_CAPABLE,
                AgentToolCapability.FORBIDDEN_ON_BANKING
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED
            ),
            emergencyCapable = false,
            coversIntents = setOf(
                AgentIntent.REMEMBER_MEMORY,
                AgentIntent.LIST_MEMORY,
                AgentIntent.CLEAR_MEMORY,
                AgentIntent.SAVE_CONTACT,
                AgentIntent.SAVE_CONTACT_PHONE,
                AgentIntent.LIST_CONTACTS,
                AgentIntent.DELETE_CONTACT,
                AgentIntent.SAVE_LOCATION_ALIAS,
                AgentIntent.LIST_LOCATION_ALIASES,
                AgentIntent.DELETE_LOCATION_ALIAS
            )
        )

        private val PREFERENCE_TOOL = AgentTool(
            id = AgentToolId.PREFERENCE,
            displayName = "Preferencias",
            description = "Ajusta estilo de respuesta y comportamiento del asistente, con opt-in.",
            capabilities = setOf(
                AgentToolCapability.READS_PREFERENCES,
                AgentToolCapability.WRITES_PREFERENCES,
                AgentToolCapability.OFFLINE_CAPABLE
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.LOW,
            requiresConfirmation = true,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED
            ),
            emergencyCapable = false,
            coversIntents = emptySet()
        )

        /**
         * GENERIC_APP queda como interfaz reservada. Por defecto está BLOQUEADO
         * — no se puede usar bajo ningún modo, no ejecuta nada, no acepta slots.
         * Se mantiene en el registry para que el planner pueda razonar sobre su
         * existencia y rechazar consultas que lo invoquen.
         */
        private val GENERIC_APP_TOOL = AgentTool(
            id = AgentToolId.GENERIC_APP,
            displayName = "Apps genéricas",
            description = "Reservado. Ejecución general sobre apps de terceros no está habilitada.",
            capabilities = setOf(
                AgentToolCapability.FORBIDDEN_ON_BANKING,
                AgentToolCapability.NEVER_AUTO_COMPLETES
            ),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.BLOCKED,
            requiresConfirmation = false,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = false,
            coversIntents = emptySet()
        )

        val DEFAULT_TOOLS: Map<AgentToolId, AgentTool> = listOf(
            WHATSAPP_TOOL,
            MAPS_TOOL,
            PHONE_TOOL,
            SCREEN_READER_TOOL,
            OCR_TOOL,
            REPEAT_LAST_TOOL,
            EMERGENCY_TOOL,
            MEMORY_TOOL,
            PREFERENCE_TOOL,
            GENERIC_APP_TOOL
        ).associateBy { it.id }
    }
}
