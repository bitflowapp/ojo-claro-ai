package com.ojoclaro.android.accessibility

/**
 * Snapshot inmutable y plano de un AccessibilityNodeInfo.
 *
 * Existe específicamente para que el resto de la app NO maneje
 * AccessibilityNodeInfo (cuyo lifecycle es propenso a fugas / recycle()).
 *
 * Reglas:
 *  - Si [isPassword] es true, [text] DEBE ser null. El productor (servicio)
 *    nunca expone el valor de un campo de contraseña.
 *  - Los strings deben venir ya recortados a un máximo razonable.
 *  - Esta clase no se persiste, no se serializa y no sale del proceso.
 */
data class AccessibilityNodeSummary(
    val text: String?,
    val contentDescription: String?,
    val hint: String?,
    val className: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isPassword: Boolean,
    val isHeading: Boolean,
    val isEnabled: Boolean
)
