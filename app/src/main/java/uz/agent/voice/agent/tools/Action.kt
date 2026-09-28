package uz.agent.voice.agent.tools

/** LLM / parser chiqaradigan tuzilmali action. Android'ga to'g'ridan-to'g'ri buyruq yuborilmaydi. */
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
    const val UNKNOWN = "unknown"
    val supported = setOf(OPEN_APP)
}
