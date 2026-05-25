package ai.ojoclaro.resolver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object InstalledAppResolver {
    private val packageToAppKey = linkedMapOf(
        "com.ubercab" to "uber",
        "com.inDriver.passenger" to "indrive",
        "com.cabify.rider" to "cabify",
        "com.didi.chauffeur.lite" to "didi",
        "com.whatsapp" to "whatsapp",
        "org.telegram.messenger" to "telegram",
        "com.spotify.music" to "spotify",
        "com.google.android.apps.maps" to "google_maps",
        "com.waze" to "waze"
    )

    fun getInstalledAppKeys(context: Context): List<String> {
        val packageManager = context.applicationContext.packageManager
        return packageToAppKey.mapNotNull { (packageName, appKey) ->
            if (packageManager.isPackageInstalled(packageName)) appKey else null
        }
    }

    private fun PackageManager.isPackageInstalled(packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
