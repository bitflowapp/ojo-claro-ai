package com.ojoclaro.android.agent.apps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun interface InstalledAppResolver {
    fun isPackageInstalled(packageName: String): Boolean

    companion object {
        val NONE: InstalledAppResolver = InstalledAppResolver { false }
    }
}

class AndroidInstalledAppResolver(
    context: Context
) : InstalledAppResolver {
    private val appContext = context.applicationContext

    override fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
