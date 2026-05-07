package com.ojoclaro.android.external

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ojoclaro.android.privacy.PrivacyGuard

data class WhatsAppChatIntentSpec(
    val action: String,
    val dataUri: String,
    val packageName: String?
)

class WhatsAppIntentHelper(
    private val context: Context
) {
    fun openWhatsApp(): CommandResult {
        val packageName = firstInstalledWhatsAppPackage()
            ?: return CommandResult.Failed(
                spokenText = "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a preparar mensajes.",
                recoverable = true
            )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return CommandResult.Failed(
                spokenText = "Encontré WhatsApp, pero el sistema no me dejó abrirlo. Intentá abrirlo vos.",
                recoverable = true
            )

        return startSafely(
            intent = launchIntent,
            successText = "Abrí WhatsApp."
        )
    }

    fun composeMessage(contactName: String, messageText: String): CommandResult {
        val cleanMessage = normalizeMessage(messageText)

        if (cleanMessage.isBlank()) {
            return CommandResult.Failed(
                spokenText = "El mensaje está vacío. Volvé a dictarlo.",
                recoverable = true
            )
        }

        if (!PrivacyGuard.isSafeMessagePayload(cleanMessage)) {
            return CommandResult.Failed(
                spokenText = "No voy a preparar ese mensaje porque puede contener datos sensibles.",
                recoverable = true
            )
        }

        val packageName = firstInstalledWhatsAppPackage()
            ?: return CommandResult.Failed(
                spokenText = "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a preparar mensajes.",
                recoverable = true
            )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cleanMessage)
            setPackage(packageName)
        }

        if (intent.resolveActivity(context.packageManager) == null) {
            return CommandResult.Failed(
                spokenText = "Encontré WhatsApp, pero no pude preparar el mensaje. Intentá abrir WhatsApp manualmente.",
                recoverable = true
            )
        }

        val safeContactName = contactName
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CONTACT_NAME_CHARS)

        return startSafely(
            intent = intent,
            successText = "Abrí WhatsApp con el mensaje preparado. Elegí el chat de $safeContactName y confirmá manualmente antes de enviarlo."
        )
    }

    /**
     * Abre WhatsApp directamente sobre el chat del [phoneE164] indicado.
     *
     * Reglas duras:
     *  - NO incluye texto. El intent abre el chat con el campo vacío.
     *  - NO envía nada automáticamente.
     *  - Usa ACTION_VIEW con URI https://wa.me/<digitos>.
     *  - Aplica setPackage("com.whatsapp" o "com.whatsapp.w4b") si está instalado;
     *    si no, devuelve fallo claro y NO abre un chooser web.
     */
    fun openChat(contactName: String, phoneE164: String): CommandResult {
        val spec = buildOpenChatIntentSpec(phoneE164) { firstInstalledWhatsAppPackage() != null }
            ?: return CommandResult.Failed(
                spokenText = "No pude preparar el chat porque ese número no parece válido.",
                recoverable = true
            )

        val packageName = firstInstalledWhatsAppPackage()
            ?: return CommandResult.Failed(
                spokenText = "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a abrir chats.",
                recoverable = true
            )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spec.dataUri)).apply {
            setPackage(packageName)
        }

        val safeContact = contactName
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "ese contacto" }
            .take(MAX_CONTACT_NAME_CHARS)

        return startSafely(
            intent = intent,
            successText = "Abrí el chat de WhatsApp con $safeContact. No envío ningún mensaje."
        )
    }

    private fun firstInstalledWhatsAppPackage(): String? {
        return WHATSAPP_PACKAGES.firstOrNull(::isPackageInstalled)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun startSafely(
        intent: Intent,
        successText: String
    ): CommandResult {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            CommandResult.Success(successText)
        } catch (_: ActivityNotFoundException) {
            CommandResult.Failed(
                spokenText = "No pude abrir WhatsApp ahora. Intentá abrirlo vos.",
                recoverable = true
            )
        } catch (_: SecurityException) {
            CommandResult.Failed(
                spokenText = "El sistema no me dejó abrir WhatsApp. Intentá abrirlo vos.",
                recoverable = true
            )
        }
    }

    private fun normalizeMessage(message: String): String {
        return message
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_MESSAGE_CHARS)
    }

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        private val WHATSAPP_PACKAGES = listOf(
            WHATSAPP_PACKAGE,
            WHATSAPP_BUSINESS_PACKAGE
        )

        private const val MAX_MESSAGE_CHARS = 1_000
        private const val MAX_CONTACT_NAME_CHARS = 80

        /**
         * Construye el spec del intent de "abrir chat" sin tocar Context. Pensado
         * para tests unitarios y para que el caller decida cómo materializarlo.
         *
         * Reglas:
         *  - Devuelve null si el [phoneE164] no es un número aceptable.
         *  - NUNCA agrega `?text=` al URI: la apertura no incluye mensaje.
         *  - Usa el esquema https://wa.me/<dígitos> (sin "+"), que es la forma
         *    pública documentada por WhatsApp.
         */
        fun buildOpenChatIntentSpec(
            phoneE164: String,
            isWhatsAppInstalled: () -> Boolean = { true }
        ): WhatsAppChatIntentSpec? {
            val digits = phoneE164.trim()
                .removePrefix("+")
                .filter(Char::isDigit)
            if (digits.length !in MIN_OPEN_CHAT_DIGITS..MAX_OPEN_CHAT_DIGITS) return null

            return WhatsAppChatIntentSpec(
                action = Intent.ACTION_VIEW,
                dataUri = "https://wa.me/$digits",
                packageName = if (isWhatsAppInstalled()) WHATSAPP_PACKAGE else null
            )
        }

        private const val MIN_OPEN_CHAT_DIGITS = 6
        private const val MAX_OPEN_CHAT_DIGITS = 15
    }
}
