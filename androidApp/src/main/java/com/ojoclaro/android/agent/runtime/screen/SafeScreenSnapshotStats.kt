package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot

data class SafeScreenSnapshotStats(
    val packageName: String?,
    val elementCount: Int,
    val buttonCount: Int,
    val fieldCount: Int
)

fun ScreenSnapshot?.safeStats(): SafeScreenSnapshotStats =
    if (this == null) {
        SafeScreenSnapshotStats(
            packageName = null,
            elementCount = 0,
            buttonCount = 0,
            fieldCount = 0
        )
    } else {
        SafeScreenSnapshotStats(
            packageName = packageName,
            elementCount = elements.size,
            buttonCount = elements.count {
                it.role == ScreenElementRole.BUTTON && it.isInteractive && !it.isPassword
            },
            fieldCount = elements.count {
                it.role == ScreenElementRole.EDIT_TEXT && !it.isPassword
            }
        )
    }
