package com.ojoclaro.android.test

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {

    private val lock = Any()
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> {
        return synchronized(lock) {
            LinkedHashMap(values)
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue

        return synchronized(lock) {
            values[key] as? String ?: defValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?
    ): MutableSet<String>? {
        if (key == null) return defValues

        return synchronized(lock) {
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue

        return synchronized(lock) {
            values[key] as? Int ?: defValue
        }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue

        return synchronized(lock) {
            values[key] as? Long ?: defValue
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue

        return synchronized(lock) {
            values[key] as? Float ?: defValue
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue

        return synchronized(lock) {
            values[key] as? Boolean ?: defValue
        }
    }

    override fun contains(key: String?): Boolean {
        if (key == null) return false

        return synchronized(lock) {
            values.containsKey(key)
        }
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener == null) return

        synchronized(lock) {
            listeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener == null) return

        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    private inner class Editor : SharedPreferences.Editor {

        private val changes = linkedMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(
            key: String?,
            value: String?
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = value
            }
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = values?.toSet()
            }
        }

        override fun putInt(
            key: String?,
            value: Int
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = value
            }
        }

        override fun putLong(
            key: String?,
            value: Long
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = value
            }
        }

        override fun putFloat(
            key: String?,
            value: Float
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = value
            }
        }

        override fun putBoolean(
            key: String?,
            value: Boolean
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = value
            }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                changes[key] = REMOVED
            }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearAll = true
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val changedKeys: List<String>
            val listenersSnapshot: List<SharedPreferences.OnSharedPreferenceChangeListener>

            synchronized(lock) {
                val changed = linkedSetOf<String>()

                if (clearAll) {
                    changed.addAll(values.keys)
                    values.clear()
                }

                changes.forEach { (key, value) ->
                    if (value === REMOVED) {
                        if (values.containsKey(key)) {
                            values.remove(key)
                            changed.add(key)
                        }
                    } else {
                        val hadKey = values.containsKey(key)
                        val previousValue = values[key]

                        if (!hadKey || previousValue != value) {
                            values[key] = value
                            changed.add(key)
                        }
                    }
                }

                changedKeys = changed.toList()
                listenersSnapshot = listeners.toList()
            }

            if (changedKeys.isEmpty() || listenersSnapshot.isEmpty()) return

            changedKeys.forEach { key ->
                listenersSnapshot.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key)
                }
            }
        }
    }

    private object REMOVED
}
