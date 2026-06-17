package com.ojoclaro.android.agent.situation

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentHintTest {

    @Test
    fun null_devuelve_unknown() {
        assertEquals(EnvironmentHint.UNKNOWN, environmentHintFor(null))
    }

    @Test
    fun blank_devuelve_unknown() {
        assertEquals(EnvironmentHint.UNKNOWN, environmentHintFor(""))
        assertEquals(EnvironmentHint.UNKNOWN, environmentHintFor("   "))
    }

    @Test
    fun ojoclaro_devuelve_in_ojoclaro() {
        assertEquals(EnvironmentHint.IN_OJOCLARO, environmentHintFor("com.ojoclaro.android"))
    }

    @Test
    fun whatsapp_normal_devuelve_in_whatsapp() {
        assertEquals(EnvironmentHint.IN_WHATSAPP, environmentHintFor("com.whatsapp"))
    }

    @Test
    fun whatsapp_business_devuelve_in_whatsapp() {
        assertEquals(EnvironmentHint.IN_WHATSAPP, environmentHintFor("com.whatsapp.w4b"))
    }

    @Test
    fun google_maps_devuelve_in_maps() {
        assertEquals(EnvironmentHint.IN_MAPS, environmentHintFor("com.google.android.apps.maps"))
    }

    @Test
    fun waze_devuelve_in_maps() {
        assertEquals(EnvironmentHint.IN_MAPS, environmentHintFor("com.waze"))
    }

    @Test
    fun chrome_devuelve_in_browser() {
        assertEquals(EnvironmentHint.IN_BROWSER, environmentHintFor("com.android.chrome"))
    }

    @Test
    fun paquete_desconocido_devuelve_other_app() {
        assertEquals(EnvironmentHint.OTHER_APP, environmentHintFor("com.alguna.app.random"))
    }
}
