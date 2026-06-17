package com.ojoclaro.android.agent.core.screen

/**
 * Paquete 5E — Importancia de un [ScreenChangeAnnouncement].
 *
 * - [LOW]: anuncio opcional. Si TalkBack está activo o el usuario está
 *   ocupado con otra acción, se puede ignorar sin pérdida.
 * - [NORMAL]: anuncio útil. Se respeta cooldown.
 * - [HIGH]: anuncio importante. Se habla con force si corresponde, pero
 *   sigue respetando cooldown por semanticKey.
 * - [CRITICAL]: anuncio de seguridad (banca, contraseña, OTP). Puede
 *   romper cooldown si el `reasonKey` cambió (ej. de password a banking).
 */
enum class ScreenChangeImportance {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
