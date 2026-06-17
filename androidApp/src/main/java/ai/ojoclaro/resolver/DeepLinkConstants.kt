package ai.ojoclaro.resolver

data class DeepLinkSpec(
    val uri: String,
    val fallbackPlayStoreUrl: String
)

object DeepLinkConstants {
    object Uber {
        val OpenHome = DeepLinkSpec(
            uri = "uber://",
            fallbackPlayStoreUrl = playStoreUrl("com.ubercab")
        )
    }

    object Didi {
        val OpenHome = DeepLinkSpec(
            uri = "didiglobal://",
            fallbackPlayStoreUrl = playStoreUrl("com.didi.chauffeur.lite")
        )
    }

    object Cabify {
        val OpenHome = DeepLinkSpec(
            uri = "cabify://",
            fallbackPlayStoreUrl = playStoreUrl("com.cabify.rider")
        )
    }

    object InDrive {
        val OpenHome = DeepLinkSpec(
            uri = "indriver://",
            fallbackPlayStoreUrl = playStoreUrl("com.inDriver.passenger")
        )
    }

    object WhatsApp {
        val OpenChatWithPhone = DeepLinkSpec(
            uri = "https://wa.me/{phone}",
            fallbackPlayStoreUrl = playStoreUrl("com.whatsapp")
        )
    }

    object Spotify {
        val OpenHome = DeepLinkSpec(
            uri = "spotify://",
            fallbackPlayStoreUrl = playStoreUrl("com.spotify.music")
        )

        val OpenSearch = DeepLinkSpec(
            uri = "spotify:search:{query}",
            fallbackPlayStoreUrl = playStoreUrl("com.spotify.music")
        )
    }

    object Maps {
        val NavigateToDestination = DeepLinkSpec(
            uri = "google.navigation:q={destination}",
            fallbackPlayStoreUrl = playStoreUrl("com.google.android.apps.maps")
        )
    }

    object Waze {
        val NavigateToDestination = DeepLinkSpec(
            uri = "waze://?q={destination}",
            fallbackPlayStoreUrl = playStoreUrl("com.waze")
        )
    }

    private fun playStoreUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"
}
