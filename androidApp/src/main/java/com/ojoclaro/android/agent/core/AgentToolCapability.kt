package com.ojoclaro.android.agent.core

/**
 * Capacidades declarativas que un tool puede tener.
 *
 * No describen qué APIs Android usan — eso es detalle de implementación. Describen
 * dimensiones que la policy de seguridad debe ver: ¿abre app externa? ¿lee la
 * pantalla? ¿requiere internet? Sobre eso se filtra antes de ejecutar.
 */
enum class AgentToolCapability {
    OPENS_EXTERNAL_APP,
    PREPARES_MESSAGE_WITHOUT_SENDING,
    READS_VISIBLE_SCREEN,
    READS_CAMERA,
    READS_LOCATION,
    READS_CONTACTS_MEMORY,
    WRITES_MEMORY,
    READS_PREFERENCES,
    WRITES_PREFERENCES,
    REQUIRES_NETWORK,
    REQUIRES_CONFIRMATION,
    OFFLINE_CAPABLE,
    EMERGENCY_CAPABLE,

    /** Marca explícita: este tool nunca debería operar pantallas bancarias/pagos. */
    FORBIDDEN_ON_BANKING,

    /** El tool dice "abrí algo", pero NO completa la acción sin que el usuario toque. */
    NEVER_AUTO_COMPLETES
}
