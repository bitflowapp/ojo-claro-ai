package com.ojoclaro.android.agent.mission

/**
 * Clasificador de privacidad por paquete (Fase 3A).
 *
 *  - PUBLIC_UI: Ajustes, launcher, la propia app. Pueden viajar etiquetas
 *    públicas sanitizadas al planner.
 *  - PRIVATE_APP: WhatsApp, SMS, correo, contactos. Solo capacidades
 *    abstractas (conteos/booleans). Nada de nombres, mensajes ni teléfonos.
 *  - SENSITIVE_APP: bancos, billeteras, autenticadores, contraseñas. Ni
 *    etiquetas ni contenido; herramientas limitadas a back/speak/ask/finish/fail.
 *  - UNKNOWN: sin paquete o no reconocido. Se trata como privado a efectos
 *    de contenido (conservador).
 */
object AgentPrivacyClassifier {

    private val SENSITIVE_TOKENS = listOf(
        "bank", "banco", "bbva", "santander", "galicia", "macro", "patagonia",
        "uala", "brubank", "naranja", "mercadopago", "wallet", "billetera",
        "paypal", "binance", "authenticator", "autenticador", "password",
        "1password", "bitwarden", "lastpass", "keepass", "token"
    )

    private val PRIVATE_TOKENS = listOf(
        "whatsapp", "telegram", "signal", "messaging", "mms", "sms",
        "gmail", "outlook", "mail", "contacts", "contactos", "dialer"
    )

    private val PUBLIC_PACKAGES = setOf(
        "com.android.settings",
        "com.ojoclaro.android"
    )

    private val PUBLIC_TOKENS = listOf("launcher", "settings")

    fun classify(packageName: String?): AgentPrivacyClass {
        val normalized = packageName?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return AgentPrivacyClass.UNKNOWN
        if (normalized in PUBLIC_PACKAGES) return AgentPrivacyClass.PUBLIC_UI
        if (SENSITIVE_TOKENS.any { normalized.contains(it) }) return AgentPrivacyClass.SENSITIVE_APP
        if (PRIVATE_TOKENS.any { normalized.contains(it) }) return AgentPrivacyClass.PRIVATE_APP
        if (PUBLIC_TOKENS.any { normalized.contains(it) }) return AgentPrivacyClass.PUBLIC_UI
        return AgentPrivacyClass.UNKNOWN
    }
}
