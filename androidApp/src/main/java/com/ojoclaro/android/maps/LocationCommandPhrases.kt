package com.ojoclaro.android.maps

object LocationCommandPhrases {
    const val STREET_SAFETY_NOTICE =
        "Te puedo ayudar a orientarte, pero no puedo detectar todos los peligros de la calle."

    const val LOCATION_PERMISSION_REQUIRED =
        "Para decirte dónde estás, necesito permiso de ubicación. Solo lo uso cuando me lo pedís."

    const val LOCATION_PERMISSION_DENIED =
        "Sin ubicación no puedo decirte dónde estás, pero puedo abrir mapas para que busques un destino."

    const val CURRENT_LOCATION_READY =
        "Tu ubicación aproximada está lista. Puedo abrirla en mapas."

    fun navigateConfirmation(destination: String): String =
        "Voy a abrir mapas hacia $destination. Confirmá para continuar."

    fun saveAliasConfirmation(alias: String): String =
        "Voy a guardar esta ubicación como $alias. Confirmá para guardar."

    fun missingAlias(alias: String): String =
        "No tengo guardada la ubicación $alias. Podés decir: guardá esta ubicación como $alias."
}
