package com.ojoclaro.android.agent.core.screen

/**
 * Señales clasificadas de una pantalla.
 *
 * Cada señal es booleana y derivada de inspección determinista del snapshot
 * crudo + el `packageName`. Nunca contienen texto del usuario — solo flags
 * binarios. La idea es que el Agent Core pueda razonar "¿estoy en un chat?
 * ¿hay un campo de pago?" sin ver el contenido.
 *
 * Reglas de combinación:
 *  - Las señales se calculan independientemente.
 *  - Una señal "true" no implica que otra sea "false": una pantalla bancaria
 *    también puede ser un formulario con scroll.
 *  - Si la pantalla está vacía, todas son false.
 */
data class ScreenSignals(
    val hasPasswordField: Boolean = false,
    val hasPaymentOrTransferSignals: Boolean = false,
    val isBankingApp: Boolean = false,
    val isMessagingApp: Boolean = false,
    val hasScrollableContent: Boolean = false,
    val hasVerificationCode: Boolean = false,
    val hasPersonalDataRequest: Boolean = false,
    val hasFormFields: Boolean = false
) {
    val isHotZone: Boolean
        get() = hasPasswordField || isBankingApp || hasPaymentOrTransferSignals ||
            hasVerificationCode

    companion object {
        val EMPTY: ScreenSignals = ScreenSignals()
    }
}
