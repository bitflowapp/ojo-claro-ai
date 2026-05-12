package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals

class HumanResponseStyleProviderTest {

    private fun pref(key: String, value: String, t: Long = 0L) =
        RoutinePreference(key = key, value = value, updatedAtMillis = t)

    @Test
    fun returnsDefaultStyleWhenStoreIsEmpty() {
        val store = HumanRoutineMemoryStore()
        val provider = StoreBackedHumanResponseStyleProvider(store)
        assertEquals(HumanResponseStyle.DEFAULT, provider.current())
    }

    @Test
    fun reflectsShortResponseLengthFromStore() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_LENGTH,
            RoutinePreferenceValues.LENGTH_SHORT
        ))
        val style = StoreBackedHumanResponseStyleProvider(store).current()
        assertEquals(ResponseLength.SHORT, style.length)
        assertEquals(true, style.isShort)
    }

    @Test
    fun reflectsSlowSpeedFromStore() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_SPEED,
            RoutinePreferenceValues.SPEED_SLOW
        ))
        val style = StoreBackedHumanResponseStyleProvider(store).current()
        assertEquals(ResponseSpeed.SLOW, style.speed)
        assertEquals(true, style.isSlow)
    }

    @Test
    fun reflectsClearClarityFromStore() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_CLARITY,
            RoutinePreferenceValues.CLARITY_CLEAR
        ))
        val style = StoreBackedHumanResponseStyleProvider(store).current()
        assertEquals(ResponseClarity.CLEAR, style.clarity)
        assertEquals(true, style.isClear)
    }

    @Test
    fun multiplePreferencesCombine() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_LENGTH,
            RoutinePreferenceValues.LENGTH_SHORT
        ))
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_SPEED,
            RoutinePreferenceValues.SPEED_SLOW
        ))
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_CLARITY,
            RoutinePreferenceValues.CLARITY_CLEAR
        ))
        val style = StoreBackedHumanResponseStyleProvider(store).current()
        assertEquals(ResponseLength.SHORT, style.length)
        assertEquals(ResponseSpeed.SLOW, style.speed)
        assertEquals(ResponseClarity.CLEAR, style.clarity)
    }

    @Test
    fun unknownValueFallsBackToNormal() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_LENGTH,
            "anything-weird"
        ))
        val style = StoreBackedHumanResponseStyleProvider(store).current()
        assertEquals(ResponseLength.NORMAL, style.length)
    }

    @Test
    fun changesReflectImmediately() {
        val store = HumanRoutineMemoryStore()
        val provider = StoreBackedHumanResponseStyleProvider(store)
        assertEquals(ResponseLength.NORMAL, provider.current().length)
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_LENGTH,
            RoutinePreferenceValues.LENGTH_SHORT
        ))
        assertEquals(ResponseLength.SHORT, provider.current().length)
        store.setPreference(pref(
            RoutinePreferenceKeys.RESPONSE_LENGTH,
            RoutinePreferenceValues.LENGTH_NORMAL
        ))
        assertEquals(ResponseLength.NORMAL, provider.current().length)
    }

    @Test
    fun ttsRateMultiplierMaps() {
        assertEquals(0.85f, ResponseSpeed.SLOW.ttsRateMultiplier)
        assertEquals(1.0f, ResponseSpeed.NORMAL.ttsRateMultiplier)
    }
}
