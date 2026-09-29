package uz.agent.voice.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.validator.ActionValidator
import uz.agent.voice.android.accessibility.AgentAccessibilityService
import uz.agent.voice.android.apps.AppEntry
import uz.agent.voice.android.apps.AppOpener
import uz.agent.voice.android.apps.AppRegistry

/**
 * Pipeline: matn -> Action -> validator -> executor -> natija.
 * Chaqiruvchi bu funksiyani UI oqimida emas, alohida Thread'da ishlatishi kerak
 * (Telegram va boshqa Accessibility actionlar bir necha soniya kutishi mumkin).
 */
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
            ActionNames.OPEN_APP -> openApp(apps.first { it.id == action.params["app"] })
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
            ActionNames.PRESS_BACK ->
                if (AgentAccessibilityService.back()) ActionResult(true, "Orqaga qaytdim.")
                else ActionResult(false, "Orqaga qaytolmadim.")
            ActionNames.PRESS_HOME ->
                if (AgentAccessibilityService.home()) ActionResult(true, "Bosh menyuga chiqdim.")
                else ActionResult(false, "Bosh menyuga chiqolmadim.")
            ActionNames.PRESS_RECENTS ->
                if (AgentAccessibilityService.recents()) ActionResult(true, "Oxirgi ilovalar ochildi.")
                else ActionResult(false, "Oxirgi ilovalarni ocholmadim.")
            ActionNames.CLICK_TEXT -> {
                val text = action.params.getValue("text")
                if (AgentAccessibilityService.clickText(text)) ActionResult(true, "\"$text\" bosildi.")
                else ActionResult(false, "\"$text\" ekranda topilmadi.")
            }
            ActionNames.TYPE_TEXT -> {
                val text = action.params.getValue("text")
                if (AgentAccessibilityService.typeText(text)) ActionResult(true, "Matn kiritildi.")
                else ActionResult(false, "Matn kiritish maydoni topilmadi.")
            }
            ActionNames.SCROLL_DOWN ->
                if (AgentAccessibilityService.scroll(true)) ActionResult(true, "Pastga suryapman.")
                else ActionResult(false, "Scroll qilib bo'lmadi.")
            ActionNames.SCROLL_UP ->
                if (AgentAccessibilityService.scroll(false)) ActionResult(true, "Yuqoriga suryapman.")
                else ActionResult(false, "Scroll qilib bo'lmadi.")
            ActionNames.OPEN_TELEGRAM_CHAT -> {
                val name = action.params.getValue("name")
                val opened = openTelegramFirst(name)
                if (opened.ok) opened else return opened
                describeTelegram(TelegramActions.openChat(name).let { it }, name)
            }
            ActionNames.SEND_TELEGRAM_MESSAGE -> {
                val name = action.params.getValue("name")
                val msg = action.params.getValue("message")
                val openRes = openTelegramFirst(name)
                if (!openRes.ok) return openRes
                when (val r = TelegramActions.openChat(name)) {
                    is TelegramOutcome.Opened -> {
                        Thread.sleep(400)
                        if (!AgentAccessibilityService.typeText(msg)) {
                            ActionResult(false, "Xabar matnini kiritolmadim.")
                        } else {
                            Thread.sleep(300)
                            val sent = AgentAccessibilityService.clickByCandidates("send", "yubor", "отправить")
                            if (sent) ActionResult(true, "\"${r.name}\"ga xabar yuborildi.")
                            else ActionResult(false, "Yuborish tugmasini topolmadim.")
                        }
                    }
                    is TelegramOutcome.Ambiguous ->
                        ActionResult(false, "Bir nechta mos chat topildi: ${r.options.joinToString(", ")}. Aniqroq nom ayting.")
                    TelegramOutcome.NotFound -> ActionResult(false, "\"$name\" nomli chat topilmadi.")
                    TelegramOutcome.NoAccessibility -> ActionResult(false, "Bu amal uchun Accessibility permission kerak.")
                }
            }
            else -> ActionResult(false, "Noma'lum action.")
        }
    }

    /** Telegram'ni oldindan ochib, foreground bo'lishini kutadi. */
    private fun openTelegramFirst(name: String): ActionResult {
        val telegram = apps.firstOrNull { it.id == "telegram" }
            ?: return ActionResult(false, "Telegram ilovasi ro'yxatda yo'q.")
        val res = openApp(telegram)
        if (!res.ok) return res
        Thread.sleep(600)
        return ActionResult(true, "")
    }

    private fun describeTelegram(outcome: TelegramOutcome, name: String): ActionResult = when (outcome) {
        is TelegramOutcome.Opened -> ActionResult(true, "\"${outcome.name}\" ochildi.")
        is TelegramOutcome.Ambiguous ->
            ActionResult(false, "Bir nechta mos chat topildi: ${outcome.options.joinToString(", ")}. Aniqroq nom ayting.")
        TelegramOutcome.NotFound -> ActionResult(false, "\"$name\" nomli chat topilmadi.")
        TelegramOutcome.NoAccessibility -> ActionResult(false, "Bu amal uchun Accessibility permission kerak.")
    }

    private fun openApp(entry: AppEntry): ActionResult {
        val opened = opener.open(entry)
        if (!opened.ok) return opened
        if (!AgentAccessibilityService.isEnabled()) return opened
        val ok = AgentAccessibilityService.waitForForeground(entry.packages)
        return if (ok) ActionResult(true, "${entry.label} ochildi.")
        else ActionResult(false, "${entry.label}ni ochishda muammo bo'ldi.")
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
