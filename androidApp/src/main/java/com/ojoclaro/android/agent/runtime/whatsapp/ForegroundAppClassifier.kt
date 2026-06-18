package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Blind Safety (#3) — clasifica el paquete de una ventana que pasó a foreground
 * para llevar un rastro robusto de "¿WhatsApp es el app real adelante?".
 *
 * Por qué: cuando Estela escucha, un overlay/actividad propia o el teclado pueden
 * quedar arriba y el "active window" deja de reportar WhatsApp, aunque WhatsApp
 * siga siendo el app de fondo real. El servicio recuerda el último foreground de
 * WhatsApp y lo OLVIDA sólo cuando OTRO app real toma el frente.
 *
 *  - WHATSAPP  : WhatsApp pasó al frente → marcar contexto WhatsApp.
 *  - OTHER_APP : otro app real (o el launcher) tomó el frente → ya NO es WhatsApp.
 *  - IGNORE    : teclado / systemui / nuestra propia app → no cambia el rastro.
 *
 * PURO: sin Android, sin estado, sin IO. Testeable directo.
 */
object ForegroundAppClassifier {

    enum class Kind { WHATSAPP, OTHER_APP, IGNORE }

    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun classify(packageName: String?, ownPackage: String?): Kind {
        val p = packageName?.lowercase()?.trim()
        if (p.isNullOrBlank()) return Kind.IGNORE
        if (!ownPackage.isNullOrBlank() && p == ownPackage.lowercase()) return Kind.IGNORE
        if (p.contains("ojoclaro")) return Kind.IGNORE
        if (p in WHATSAPP_PACKAGES || p.contains("whatsapp")) return Kind.WHATSAPP
        // Teclado / UI del sistema / launcher de transición → no es "irse" de WhatsApp.
        if (p == "android" ||
            p.contains("systemui") ||
            p.contains("inputmethod") ||
            p.endsWith(".latin")
        ) {
            return Kind.IGNORE
        }
        // Cualquier otro app real (incluye el launcher al ir a Home) → ya no WhatsApp.
        return Kind.OTHER_APP
    }
}
