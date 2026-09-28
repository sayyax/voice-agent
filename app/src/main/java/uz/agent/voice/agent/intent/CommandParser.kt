package uz.agent.voice.agent.intent

import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.android.apps.AppEntry

object CommandParser {

    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o",
        'қ' to "q", 'ғ' to "g", 'ҳ' to "h", 'ы' to "i", 'ь' to "", 'ъ' to ""
    )

    private fun toLatin(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(cyr[c] ?: c.toString())
        return sb.toString()
    }

    fun normalize(s: String): String =
        toLatin(s.lowercase())
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
