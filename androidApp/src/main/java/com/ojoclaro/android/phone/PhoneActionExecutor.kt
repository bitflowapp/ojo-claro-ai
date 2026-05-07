package com.ojoclaro.android.phone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ojoclaro.android.external.CommandResult

data class DialIntentSpec(
    val action: String,
    val dataUri: String?,
    val requiredPermission: String? = null
)

class PhoneActionExecutor(
    private val context: Context? = null,
    private val intentStarter: ((Intent) -> Unit)? = null
) {
    fun buildDialIntent(phoneNumber: String?): Intent {
        val spec = buildDialIntentSpec(phoneNumber)
        return if (spec.dataUri == null) {
            Intent(spec.action)
        } else {
            Intent(spec.action, Uri.parse(spec.dataUri))
        }
    }

    fun buildDialIntentSpec(phoneNumber: String?): DialIntentSpec {
        val cleanNumber = sanitizePhoneNumber(phoneNumber)
        return DialIntentSpec(
            action = Intent.ACTION_DIAL,
            dataUri = cleanNumber?.let { "$TEL_SCHEME$it" },
            requiredPermission = REQUIRED_PERMISSION
        )
    }

    fun openDialer(): CommandResult =
        startSafely(
            intent = buildDialIntent(phoneNumber = null),
            successText = "Abrí Teléfono."
        )

    fun prepareCall(contactName: String, phoneNumber: String?): CommandResult {
        val cleanNumber = sanitizePhoneNumber(phoneNumber)
        val safeContact = contactName.replace(Regex("\\s+"), " ").trim().ifBlank { "ese contacto" }
        val successText = if (cleanNumber == null) {
            "Abrí Teléfono para elegir a $safeContact."
        } else {
            "Abrí Teléfono con el número de $safeContact preparado. Tocá llamar solo si querés continuar."
        }
        return startSafely(
            intent = buildDialIntent(cleanNumber),
            successText = successText
        )
    }

    private fun startSafely(
        intent: Intent,
        successText: String
    ): CommandResult {
        intentStarter?.let { starter ->
            return try {
                starter(intent)
                CommandResult.Success(successText)
            } catch (_: ActivityNotFoundException) {
                CommandResult.Failed(
                    spokenText = "No pude abrir TelÃ©fono ahora. IntentÃ¡ abrirlo vos.",
                    recoverable = true
                )
            } catch (_: SecurityException) {
                CommandResult.Failed(
                    spokenText = "El sistema no me dejÃ³ abrir TelÃ©fono. IntentÃ¡ abrirlo vos.",
                    recoverable = true
                )
            }
        }

        val safeContext = context ?: return CommandResult.Failed(
            spokenText = "No pude abrir Teléfono ahora. Intentá abrirlo vos.",
            recoverable = true
        )

        return try {
            if (safeContext !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeContext.startActivity(intent)
            CommandResult.Success(successText)
        } catch (_: ActivityNotFoundException) {
            CommandResult.Failed(
                spokenText = "No pude abrir Teléfono ahora. Intentá abrirlo vos.",
                recoverable = true
            )
        } catch (_: SecurityException) {
            CommandResult.Failed(
                spokenText = "El sistema no me dejó abrir Teléfono. Intentá abrirlo vos.",
                recoverable = true
            )
        }
    }

    companion object {
        const val DEFAULT_EMERGENCY_NUMBER = "911"
        const val RESPONSIBLE_EMERGENCY_NOTICE =
            "Si estás en peligro, intentá llamar a emergencias o pedir ayuda cercana."

        val REQUIRED_PERMISSION: String? = null

        private const val TEL_SCHEME = "tel:"
        private val phoneCandidateRegex = Regex("\\+?\\d[\\d\\s().-]{1,}\\d|\\b\\d{3}\\b")

        fun sanitizePhoneNumber(phoneNumber: String?): String? {
            val raw = phoneNumber?.trim().orEmpty()
            if (raw.isBlank()) return null

            val compact = raw
                .filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                .let { value ->
                    if (value.count { it == '+' } > 1) return null
                    if ('+' in value && !value.startsWith("+")) return null
                    value
                }

            val digits = compact.count(Char::isDigit)
            if (digits !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) return null
            return compact
        }

        fun extractSafePhoneNumber(text: String): String? =
            phoneCandidateRegex.findAll(text)
                .mapNotNull { sanitizePhoneNumber(it.value) }
                .firstOrNull()

        private const val MIN_PHONE_DIGITS = 3
        private const val MAX_PHONE_DIGITS = 15
    }
}
