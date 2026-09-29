package uz.agent.voice.agent.tools

data class Action(
    val name: String,
    val params: Map<String, String> = emptyMap()
) {
    override fun toString(): String =
        if (params.isEmpty()) name else "$name$params"
}

data class ActionResult(val ok: Boolean, val message: String)

object ActionNames {
    const val OPEN_APP = "open_app"
    const val OPEN_URL = "open_url"
    const val SEARCH_YOUTUBE = "search_youtube"
    const val PRESS_BACK = "press_back"
    const val PRESS_HOME = "press_home"
    const val PRESS_RECENTS = "press_recents"
    const val CLICK_TEXT = "click_text"
    const val TYPE_TEXT = "type_text"
    const val SCROLL_DOWN = "scroll_down"
    const val SCROLL_UP = "scroll_up"
    const val OPEN_TELEGRAM_CHAT = "open_telegram_chat"
    const val SEND_TELEGRAM_MESSAGE = "send_telegram_message"
    const val CLARIFY = "clarify"
    const val UNKNOWN = "unknown"

    /** Executor bajara oladigan actionlar. */
    val supported = setOf(
        OPEN_APP, OPEN_URL, SEARCH_YOUTUBE,
        PRESS_BACK, PRESS_HOME, PRESS_RECENTS,
        CLICK_TEXT, TYPE_TEXT, SCROLL_DOWN, SCROLL_UP,
        OPEN_TELEGRAM_CHAT, SEND_TELEGRAM_MESSAGE
    )

    /** Accessibility ruxsati yoqilgan bo'lishi shart bo'lgan actionlar. */
    val needsAccessibility = setOf(
        PRESS_BACK, PRESS_HOME, PRESS_RECENTS, CLICK_TEXT, TYPE_TEXT, SCROLL_DOWN, SCROLL_UP,
        OPEN_TELEGRAM_CHAT, SEND_TELEGRAM_MESSAGE
    )

    /** Bajarishdan oldin foydalanuvchidan ovozli tasdiq so'raladigan actionlar. */
    val needsConfirmation = setOf(SEND_TELEGRAM_MESSAGE)
}
