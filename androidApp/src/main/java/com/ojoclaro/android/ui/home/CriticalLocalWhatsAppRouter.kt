package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale

internal data class CriticalLocalWhatsAppCommand(
    val rawText: String,
    val normalizedText: String,
    val spokenText: String,
    val routeLabel: String,
    val targetPackageName: String,
    val externalAction: ExternalActionEvent
)

internal fun detectCriticalLocalWhatsAppCommand(rawText: String): CriticalLocalWhatsAppCommand? {
    val normalized = VoicePhraseNormalizer.normalizeForParser(rawText)
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return null

    val folded = normalized.foldForCriticalRouting()
    if (!Regex("\\bwhatsapp\\b").containsMatchIn(folded)) return null

    val businessRequested = Regex("\\b(?:business|empresa|negocio)\\b").containsMatchIn(folded)
    val directOpen = isCriticalWhatsAppOpenCommand(folded)
    val guidedOpen = isCriticalWhatsAppMessageOpenCommand(folded)
    if (!directOpen && !guidedOpen) return null

    val appName = if (businessRequested) "WhatsApp Business" else "WhatsApp"
    val packageName = if (businessRequested) {
        AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE
    } else {
        AppCapabilityRegistry.WHATSAPP_PACKAGE
    }
    val spokenText = if (guidedOpen) {
        "Abro $appName. Elegi el contacto y dictame el mensaje cuando estes listo."
    } else {
        "Abro $appName. No voy a enviar ningun mensaje."
    }
    val action = if (businessRequested) {
        ExternalActionEvent.OpenSafeApp(
            appName = appName,
            packageName = packageName,
            userConfirmed = true
        )
    } else {
        ExternalActionEvent.OpenWhatsApp
    }

    return CriticalLocalWhatsAppCommand(
        rawText = rawText,
        normalizedText = normalized,
        spokenText = spokenText,
        routeLabel = if (guidedOpen) "LOCAL_WHATSAPP_GUIDED_OPEN" else "LOCAL_WHATSAPP",
        targetPackageName = packageName,
        externalAction = action
    )
}

private fun isCriticalWhatsAppOpenCommand(folded: String): Boolean =
    Regex(
        "^(?:abrir|quiero abrir|entrar a|entra a|quiero entrar a|anda a|anda al)\\s+" +
            "(?:el\\s+)?whatsapp(?:\\s+(?:business|empresa|negocio))?$"
    ).matches(folded)

private fun isCriticalWhatsAppMessageOpenCommand(folded: String): Boolean {
    val startsAsMessageRequest =
        Regex("^(?:quiero\\s+)?(?:mandar|enviar|escribir|decir|avisar)\\b").containsMatchIn(folded)
    if (!startsAsMessageRequest) return false
    return Regex("\\bwhatsapp\\b").containsMatchIn(folded)
}

private fun String.foldForCriticalRouting(): String {
    val withoutAccents = Normalizer.normalize(
        lowercase(Locale("es", "AR")),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")
    return withoutAccents
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.', ',', ';', ':', '!', '?', '\u00bf', '\u00a1')
}
