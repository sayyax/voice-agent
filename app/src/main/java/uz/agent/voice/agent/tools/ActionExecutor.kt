package uz.agent.voice.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.validator.ActionValidator
import uz.agent.voice.android.apps.AppOpener
import uz.agent.voice.android.apps.AppRegistry

/** Pipeline: matn -> Action -> validator -> executor -> natija. */
class ActionExecutor(private val ctx: Context) {
    private val apps = AppRegistry.load(ctx)
    private val validator = ActionValidator(apps)
    private val opener = AppOpener(ctx)

    fun appIds(): List<String> = apps.map { it.id }

    /** Tez, oflayn qoida asosidagi parser. Tushunmasa UNKNOWN qaytaradi. */
    fun plan(text: String): Action = CommandParser.parse(text, apps)

    fun execute(action: Action): ActionResult {
        val v = validator.validate(action)
        if (!v.ok) return ActionResult(false, v.reason)
        return when (action.name) {
            ActionNames.OPEN_APP -> {
                val entry = apps.first { it.id == action.params["app"] }
                opener.open(entry)
            }
            ActionNames.OPEN_URL -> {
                val url = action.params.getValue("url")
                if (view(url, null)) ActionResult(true, "Havola ochish buyrug'i yuborildi.")
                else ActionResult(false, "Havolani ochib bo'lmadi.")
            }
            ActionNames.SEARCH_YOUTUBE -> {
                val q = action.params.getValue("query")
                val url = "https://www.youtube.com/results?search_query=" + Uri.encode(q)
                val ok = view(url, "com.google.android.youtube") || view(url, null)
                if (ok) ActionResult(true, "YouTube'da qidiruv ochildi.")
                else ActionResult(false, "YouTube qidiruvini ochib bo'lmadi.")
            }
            else -> ActionResult(false, "Noma'lum action.")
        }
    }

    private fun view(url: String, pkg: String?): Boolean {
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) i.setPackage(pkg)
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
