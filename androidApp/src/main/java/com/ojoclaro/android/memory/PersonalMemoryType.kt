package com.ojoclaro.android.memory

enum class PersonalMemoryType(
    val spokenLabel: String,
    val canSuggestActions: Boolean,
    val canBeListedSafely: Boolean
) {
    CONTACT("contacto", true, true),
    SAFE_PHONE("numero seguro", true, true),
    PLACE("lugar", true, true),
    ROUTINE("rutina", true, true),
    PREFERENCE("preferencia", true, true),
    PENDING_TASK("pendiente", true, true),
    MESSAGE_STYLE("estilo de mensaje", true, true);
}

