package uz.agent.voice.agent.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.validator.ActionValidator
import uz.agent.voice.android.accessibility.AgentAccessibilityService
import uz.agent.voice.android.apps.AppEntry
import uz.agent.voice.android.apps.AppOpener
import uz.agent.voice.android.apps.AppRegistry
import uz.agent.voice.android.contacts.ContactsLookup
import uz.agent.voice.agent.wiki.WikiManager
import uz.agent.voice.config.Prefs
import uz.agent.voice.android.files.FileTools
import uz.agent.voice.termux.TermuxBridge

/**
 * Pipeline: matn -> Action(lar) -> validator -> executor -> natija.
 * Chaqiruvchi bu funksiyalarni UI oqimida emas, alohida Thread'da ishlatishi kerak.
 */
class ActionExecutor(private val ctx: Context) {
    private val apps = AppRegistry.load(ctx)
    private val validator = ActionValidator(apps)
    private val opener = AppOpener(ctx)

    fun appIds(): List<String> = apps.map { it.id }

    /** Tez, oflayn qoida asosidagi parser. Tushunmasa UNKNOWN qaytaradi. */
    fun plan(text: String): Action = CommandParser.parse(text, apps)

    /** Bir nechta actionni ketma-ket bajaradi, birinchi xatoda to'xtaydi. */
    fun executeSteps(steps: List<Action>): ActionResult {
        var last = ActionResult(true, "")
        for (step in steps) {
            last = execute(step)
            if (!last.ok) return last
        }
        return last
    }

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
                else if (AgentAccessibilityService.clickByOcr(text)) ActionResult(true, "\"$text\" (skrinshotdan) bosildi.")
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
                val opened = openTelegramFirst()
                if (!opened.ok) return opened
                describeTelegram(TelegramActions.openChat(name), name)
            }
            ActionNames.SEND_TELEGRAM_MESSAGE -> {
                val name = action.params.getValue("name")
                val msg = action.params.getValue("message")
                val opened = openTelegramFirst()
                if (!opened.ok) return opened
                when (val r = TelegramActions.openChat(name)) {
                    is TelegramOutcome.Opened -> {
                        Thread.sleep(400)
                        if (!AgentAccessibilityService.typeText(msg)) {
                            ActionResult(false, "Xabar matnini kiritolmadim.")
                        } else {
                            Thread.sleep(300)
                            if (TelegramActions.clickSend()) ActionResult(true, "\"${r.name}\"ga xabar yuborildi.")
                            else ActionResult(false, "Yuborish tugmasini topolmadim.")
                        }
                    }
                    is TelegramOutcome.Ambiguous ->
                        ActionResult(false, "Bir nechta mos chat topildi: ${r.options.joinToString(", ")}. Aniqroq nom ayting.")
                    TelegramOutcome.NotFound -> ActionResult(false, "\"$name\" nomli chat topilmadi.")
                    TelegramOutcome.NoAccessibility -> ActionResult(false, "Bu amal uchun Accessibility permission kerak.")
                }
            }
            ActionNames.CALL_CONTACT -> {
                val name = action.params.getValue("name")
                val hasContacts = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!hasContacts) return ActionResult(false, "Bu amal uchun Kontaktlar (READ_CONTACTS) permission kerak.")
                val matches = ContactsLookup.find(ctx, name)
                if (matches.isEmpty()) return ActionResult(false, "\"$name\" nomli kontakt topilmadi.")
                if (matches.size > 1) {
                    return ActionResult(false, "Bir nechta mos kontakt topildi: ${matches.joinToString(", ") { it.name }}. Aniqroq nom ayting.")
                }
                placeCall(matches[0].number, matches[0].name)
            }
            ActionNames.CALL_NUMBER -> {
                val number = action.params.getValue("number")
                placeCall(number, number)
            }
            ActionNames.FIND_FILE -> {
                if (!FileTools.hasAccess()) return ActionResult(false, "Bu amal uchun Fayllar (barcha fayllarga kirish) permission kerak.")
                val name = action.params.getValue("name")
                val f = FileTools.find(name) ?: return ActionResult(false, "\"$name\" nomli fayl topilmadi.")
                ActionResult(true, "Topildi: ${f.name} (${f.parentFile?.name} papkasida)")
            }
            ActionNames.MOVE_FILE -> {
                if (!FileTools.hasAccess()) return ActionResult(false, "Bu amal uchun Fayllar (barcha fayllarga kirish) permission kerak.")
                val name = action.params.getValue("name")
                val dest = action.params["dest"]
                val f = FileTools.find(name) ?: return ActionResult(false, "\"$name\" nomli fayl topilmadi.")
                val moved = FileTools.moveToDownloadSubfolder(f, dest)
                    ?: return ActionResult(false, "Faylni ko'chirib bo'lmadi.")
                val sub = if (dest.isNullOrBlank()) "" else "$dest/"
                ActionResult(
                    true,
                    "\"${moved.name}\" ko'chirildi. Termux'da: ~/storage/downloads/$sub${moved.name}"
                )
            }
            ActionNames.RENAME_FILE -> {
                if (!FileTools.hasAccess()) return ActionResult(false, "Bu amal uchun Fayllar (barcha fayllarga kirish) permission kerak.")
                val name = action.params.getValue("name")
                val newName = action.params.getValue("new_name")
                val f = FileTools.find(name) ?: return ActionResult(false, "\"$name\" nomli fayl topilmadi.")
                val renamed = FileTools.rename(f, newName) ?: return ActionResult(false, "Nomini o'zgartirib bo'lmadi.")
                ActionResult(true, "Fayl nomi \"${renamed.name}\" ga o'zgartirildi.")
            }
            ActionNames.RUN_TERMUX -> {
                val command = action.params.getValue("command")
                val r = TermuxBridge.run(ctx, command)
                if (!r.ranAtAll) ActionResult(false, r.stderr.ifBlank { "Termux bilan bog'lanib bo'lmadi." })
                else if (r.exitCode == 0) {
                    val out = r.stdout.trim().takeLast(300).ifBlank { "(chiqish yo'q)" }
                    ActionResult(true, "Bajarildi. Natija: $out")
                } else {
                    val err = r.stderr.trim().takeLast(300).ifBlank { r.stdout.trim().takeLast(300) }
                    ActionResult(false, "Xato (kod ${r.exitCode}): ${err.ifBlank { "noma'lum xato" }}")
                }
            }
            ActionNames.OPEN_CLAUDE_WITH_TEXT -> {
                val text = action.params.getValue("text")
                val claude = apps.firstOrNull { it.id == "claude" }
                    ?: return ActionResult(false, "Claude ilovasi ro'yxatda yo'q.")
                val opened = openApp(claude)
                if (!opened.ok) return opened
                if (ClaudeActions.sendText(text)) ActionResult(true, "Claude'ga matn yuborildi.")
                else ActionResult(false, "Claude'ga matn yuborib bo'lmadi.")
            }
            ActionNames.WIKI_ADD_SOURCE -> {
                if (!WikiManager.hasAccess()) return ActionResult(false, "Bu amal uchun Fayllar (barcha fayllarga kirish) permission kerak.")
                val key = Prefs.geminiKey(ctx) ?: return ActionResult(false, "Bu amal uchun Gemini kaliti kerak. Sozlamalarda kiriting.")
                val model = Prefs.model(ctx)
                val content = action.params.getValue("content")
                val title = action.params["title"]?.takeIf { it.isNotBlank() } ?: content.take(60)
                val category = action.params["category"] ?: "notes"
                val outcome = WikiManager.curate(key, model, title, content, category)
                ActionResult(outcome.ok, outcome.message)
            }
            ActionNames.WIKI_ASK -> {
                if (!WikiManager.hasAccess()) return ActionResult(false, "Bu amal uchun Fayllar (barcha fayllarga kirish) permission kerak.")
                val key = Prefs.geminiKey(ctx) ?: return ActionResult(false, "Bu amal uchun Gemini kaliti kerak. Sozlamalarda kiriting.")
                val model = Prefs.model(ctx)
                val question = action.params.getValue("question")
                val outcome = WikiManager.ask(key, model, question)
                ActionResult(outcome.ok, outcome.message)
            }
            else -> ActionResult(false, "Noma'lum action.")
        }
    }

    private fun placeCall(number: String, label: String): ActionResult {
        val hasCallPermission = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return try {
            val i = Intent(intentAction, Uri.parse("tel:" + Uri.encode(number)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            if (hasCallPermission) ActionResult(true, "$label ga qo'ng'iroq qilinmoqda.")
            else ActionResult(true, "Terish ekrani ochildi. Qo'ng'iroq qilish uchun qo'lda bosing (ruxsat berilmagan).")
        } catch (e: Exception) {
            ActionResult(false, "Qo'ng'iroq qilib bo'lmadi.")
        }
    }

    private fun openTelegramFirst(): ActionResult {
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
