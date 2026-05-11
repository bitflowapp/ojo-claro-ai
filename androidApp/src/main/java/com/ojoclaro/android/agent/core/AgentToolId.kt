package com.ojoclaro.android.agent.core

/**
 * Identidad estable de las herramientas conocidas.
 *
 * El whitelist está cerrado a propósito: si un LLM o capa externa devuelve un id
 * fuera de este enum, el planner lo rechaza. No agregar valores nuevos sin
 * agregar también la política de riesgo y los tests de seguridad.
 */
enum class AgentToolId {
    WHATSAPP,
    MAPS,
    PHONE,
    SCREEN_READER,
    OCR,
    REPEAT_LAST,
    EMERGENCY,
    MEMORY,
    PREFERENCE,
    GENERIC_APP
}
