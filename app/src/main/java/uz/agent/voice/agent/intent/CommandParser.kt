package uz.agent.voice.agent.intent

import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.android.apps.AppEntry

/** Oddiy qoidaga asoslangan parser. 3-bosqichda LLMProvider bilan almashtiriladi/to'ldiriladi. */
object CommandParser {

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[’‘ʻʼ`´']"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun parse(text: String, apps: List<AppEntry>): Action {
        val t = normalize(text)
        if (t.isEmpty()) return Action(ActionNames.UNKNOWN)

        val words = t.split(" ")
        val wantsOpen = words.any { it.startsWith("och") || it.startsWith("kir") } ||
            t.contains("ishga tushir")
        if (!wantsOpen) return Action(ActionNames.UNKNOWN)

        var best: Pair<AppEntry, Int>? = null
        for (app in apps) {
            for (alias in app.aliases) {
                val a = normalize(alias)
                if (a.isNotEmpty() && t.contains(a)) {
                    if (best == null || a.length > best.second) best = app to a.length
                }
            }
        }
        val app = best?.first ?: return Action(ActionNames.UNKNOWN)
        return Action(ActionNames.OPEN_APP, mapOf("app" to app.id))
    }
}
