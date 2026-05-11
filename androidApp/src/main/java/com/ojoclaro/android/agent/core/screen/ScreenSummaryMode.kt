package com.ojoclaro.android.agent.core.screen

/**
 * Modo de resumen que el usuario pidió.
 *
 *  - SHORT: una línea, prioridad a heading.
 *  - DETAILED: hasta N líneas con los elementos importantes y bullets de botones.
 *  - WHERE_AM_I: "estás en X. Hay tantos botones, tantos campos."
 *  - WHAT_CAN_I_DO: lista de acciones disponibles (botones, links, edits).
 *  - IMPORTANT: solo lo que parece urgente o accionable.
 */
enum class ScreenSummaryMode {
    SHORT,
    DETAILED,
    WHERE_AM_I,
    WHAT_CAN_I_DO,
    IMPORTANT
}
