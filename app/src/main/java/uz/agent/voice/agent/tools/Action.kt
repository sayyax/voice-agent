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
    const val CLARIFY = "clarify"
    const val UNKNOWN = "unknown"

    /** Executor bajara oladigan actionlar. */
    val supported = setOf(OPEN_APP, OPEN_URL, SEARCH_YOUTUBE)
}
