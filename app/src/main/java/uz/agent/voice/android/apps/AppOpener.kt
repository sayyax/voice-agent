package uz.agent.voice.android.apps

import android.content.Context
import android.content.Intent
import uz.agent.voice.agent.tools.ActionResult

class AppOpener(private val ctx: Context) {

    fun open(entry: AppEntry): ActionResult {
        val pm = ctx.packageManager
        for (pkg in entry.packages) {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            return launch(intent, entry.label)
        }
        // Fallback: o'rnatilgan ilovalar orasidan nomi bo'yicha qidirish
        val found = searchByLabel(entry.label)
        if (found != null) {
            val intent = pm.getLaunchIntentForPackage(found)
            if (intent != null) return launch(intent, entry.label)
        }
        return ActionResult(false, "${entry.label} ilovasi telefonda topilmadi.")
    }

    private fun launch(intent: Intent, label: String): ActionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            // Eslatma: foreground tekshiruvi 4-bosqichda (Accessibility) qo'shiladi.
            ActionResult(true, "$label ochish buyrug'i yuborildi.")
        } catch (e: Exception) {
            ActionResult(false, "$label ni ochishda muammo bo'ldi: ${e.message}")
        }
    }

    fun searchByLabel(query: String): String? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val q = query.lowercase()
        return pm.queryIntentActivities(main, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?.activityInfo?.packageName
    }
}
