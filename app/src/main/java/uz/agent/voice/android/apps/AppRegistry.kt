package uz.agent.voice.android.apps

import android.content.Context
import org.json.JSONArray

data class AppEntry(
    val id: String,
    val label: String,
    val aliases: List<String>,
    val packages: List<String>
)

/** Ilova package nomlari assets/apps.json ichida saqlanadi (konfiguratsiya). */
object AppRegistry {
    fun load(ctx: Context): List<AppEntry> {
        val text = ctx.assets.open("apps.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AppEntry(
                id = o.getString("id"),
                label = o.getString("label"),
                aliases = strings(o.getJSONArray("aliases")),
                packages = strings(o.getJSONArray("packages"))
            )
        }
    }

    private fun strings(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }
}
