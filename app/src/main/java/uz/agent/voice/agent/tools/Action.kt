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
    const val CLARIFY = "clarify"
    const val UNKNOWN = "unknown"

    /** Executor bajara oladigan actionlar. */
    val supported = setOf(
        OPEN_APP, OPEN_URL, SEARCH_YOUTUBE,
        PRESS_BACK, PRESS_HOME, PRESS_RECENTS,
        CLICK_TEXT, TYPE_TEXT, SCROLL_DOWN, SCROLL_UP
    )

    /** Accessibility ruxsati yoqilgan bo'lishi shart bo'lgan actionlar. */
    val needsAccessibility = setOf(
        PRESS_BACK, PRESS_HOME, PRESS_RECENTS, CLICK_TEXT, TYPE_TEXT, SCROLL_DOWN, SCROLL_UP
    )
}
