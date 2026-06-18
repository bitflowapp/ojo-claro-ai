package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot

/**
 * Fixtures sintéticos de [ScreenSnapshot] para probar WhatsApp sin Accesibilidad
 * viva: cada uno reproduce una pantalla real (chat abierto, lista, login,
 * teclado abierto, desconocida) con su `activityClassName`. Reutilizable por
 * tests del detector y de capacidades.
 */
object WhatsAppScreenFixtures {

    private const val PKG = "com.whatsapp"

    private fun edit(label: String) = ScreenElement(label, ScreenElementRole.EDIT_TEXT, isInteractive = true)
    private fun button(label: String) = ScreenElement(label, ScreenElementRole.BUTTON, isInteractive = true)
    private fun text(label: String) = ScreenElement(label, ScreenElementRole.TEXT, isInteractive = false)

    private fun snap(activity: String?, elements: List<ScreenElement>, text: String = "") =
        ScreenSnapshot(
            packageName = PKG,
            text = text,
            elements = elements,
            capturedAtMillis = 0L,
            activityClassName = activity
        )

    /** Chat abierto, composer vacío (sin borrador), con botones de composer. */
    fun chatOpenEmptyComposer() = snap(
        "com.whatsapp.Conversation",
        listOf(edit(""), button("Cámara"), button("Adjuntar"), button("Micrófono"))
    )

    /** Chat abierto con borrador YA escrito (el caso del falso negativo). */
    fun chatOpenWithDraft(draft: String = "estoy llegando") = snap(
        "com.whatsapp.Conversation",
        listOf(edit(draft), button("Adjuntar"), button("Enviar"))
    )

    /** Teclado abierto: a veces solo quedan visibles el campo (con texto) y enviar. */
    fun keyboardOpenWithDraft(draft: String = "hola") = snap(
        "com.whatsapp.Conversation",
        listOf(edit(draft), button("Enviar"))
    )

    /** Lista de chats (Home). Tiene campo de BÚSQUEDA, no composer. */
    fun chatList() = snap(
        "com.whatsapp.HomeActivity",
        listOf(edit("Buscar"), text("Sofi"), text("Familia"), text("Trabajo")),
        text = "Chats"
    )

    /** Pantalla de verificación de número / login. */
    fun loginVerify() = snap(
        "com.whatsapp.registration.VerifyPhoneNumber",
        listOf(edit(""))
    )

    /** Pantalla de WhatsApp no reconocible como chat (Estados/Comunidades), sin activity. */
    fun unknownWhatsAppScreen() = snap(
        activity = null,
        elements = listOf(text("Estados"), text("Comunidades")),
        text = "Estados Comunidades Llamadas"
    )
}
