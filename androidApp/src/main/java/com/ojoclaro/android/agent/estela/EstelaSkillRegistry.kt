package com.ojoclaro.android.agent.estela

class EstelaSkillRegistry(
    private val skills: List<EstelaSkill> = defaultSkills()
) {
    fun skillFor(step: EstelaPlanStep): EstelaSkill? =
        skills.firstOrNull { it.canHandle(step) }

    fun registeredSkillIds(): List<String> =
        skills.map { it.id }

    companion object {
        fun defaultSkills(): List<EstelaSkill> = listOf(
            OpenAppSkill,
            ReadScreenSkill,
            WhatsAppVisibleChatSkill,
            WhatsAppPrepareMessageSkill,
            PhoneDialSkill,
            YouTubeSearchSkill,
            SpotifyOpenSkill,
            CameraOcrSkill,
            HelpSkill
        )
    }
}

interface EstelaSkill {
    val id: String
    fun canHandle(step: EstelaPlanStep): Boolean
}

data object OpenAppSkill : EstelaSkill {
    override val id: String = "open_app"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.OpenExternalApp
}

data object ReadScreenSkill : EstelaSkill {
    override val id: String = "read_screen"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.ReadVisibleScreen
}

data object WhatsAppVisibleChatSkill : EstelaSkill {
    override val id: String = "whatsapp_visible_chat"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.FindVisibleTarget ||
            step is EstelaPlanStep.ClickVisibleTarget
}

data object WhatsAppPrepareMessageSkill : EstelaSkill {
    override val id: String = "whatsapp_prepare_message"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.PrepareMessage
}

data object PhoneDialSkill : EstelaSkill {
    override val id: String = "phone_dial"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.OpenDialer
}

data object YouTubeSearchSkill : EstelaSkill {
    override val id: String = "youtube_search"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.SearchExternalApp &&
            step.app.equals("YouTube", ignoreCase = true)
}

data object SpotifyOpenSkill : EstelaSkill {
    override val id: String = "spotify_open"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.SearchExternalApp &&
            step.app.equals("Spotify", ignoreCase = true)
}

data object CameraOcrSkill : EstelaSkill {
    override val id: String = "camera_ocr"
    override fun canHandle(step: EstelaPlanStep): Boolean = false
}

data object HelpSkill : EstelaSkill {
    override val id: String = "help"
    override fun canHandle(step: EstelaPlanStep): Boolean =
        step is EstelaPlanStep.Speak
}
