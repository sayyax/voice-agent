package uz.agent.voice.agent.validator

import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.android.accessibility.AgentAccessibilityService
import uz.agent.voice.android.apps.AppEntry

data class Validation(val ok: Boolean, val reason: String = "")

class ActionValidator(private val apps: List<AppEntry>) {
    fun validate(action: Action): Validation {
        if (action.name !in ActionNames.supported) {
            return Validation(false, "Bu buyruqni hali bajara olmayman.")
        }
        if (action.name in ActionNames.needsAccessibility && !AgentAccessibilityService.isEnabled()) {
            return Validation(false, "Bu amal uchun Accessibility permission kerak.")
        }
        return when (action.name) {
            ActionNames.OPEN_APP -> {
                val id = action.params["app"]
                if (id.isNullOrBlank()) Validation(false, "Qaysi ilovani ochish kerak?")
                else if (apps.none { it.id == id }) Validation(false, "Noma'lum ilova: $id")
                else Validation(true)
            }
            ActionNames.OPEN_URL -> {
                val url = action.params["url"].orEmpty()
                if (url.startsWith("https://") || url.startsWith("http://")) Validation(true)
                else Validation(false, "Havola noto'g'ri.")
            }
            ActionNames.SEARCH_YOUTUBE -> {
                if (action.params["query"].isNullOrBlank()) Validation(false, "Nimani qidirish kerak?")
                else Validation(true)
            }
            ActionNames.CLICK_TEXT -> {
                if (action.params["text"].isNullOrBlank()) Validation(false, "Nimani bosish kerak?")
                else Validation(true)
            }
            ActionNames.TYPE_TEXT -> {
                if (action.params["text"].isNullOrBlank()) Validation(false, "Nima yozish kerak?")
                else Validation(true)
            }
            ActionNames.OPEN_TELEGRAM_CHAT -> {
                if (action.params["name"].isNullOrBlank()) Validation(false, "Qaysi chat yoki guruhni ochish kerak?")
                else Validation(true)
            }
            ActionNames.SEND_TELEGRAM_MESSAGE -> {
                if (action.params["name"].isNullOrBlank()) Validation(false, "Kimga yuborish kerak?")
                else if (action.params["message"].isNullOrBlank()) Validation(false, "Nima deb yozish kerak?")
                else Validation(true)
            }
            ActionNames.CALL_CONTACT -> {
                if (action.params["name"].isNullOrBlank()) Validation(false, "Kimga qo'ng'iroq qilish kerak?")
                else Validation(true)
            }
            ActionNames.CALL_NUMBER -> {
                val n = action.params["number"]
                if (n.isNullOrBlank() || n.count { it.isDigit() } < 5) Validation(false, "Qaysi raqamga qo'ng'iroq qilish kerak?")
                else Validation(true)
            }
            ActionNames.PRESS_BACK, ActionNames.PRESS_HOME, ActionNames.PRESS_RECENTS,
            ActionNames.SCROLL_DOWN, ActionNames.SCROLL_UP -> Validation(true)
            else -> Validation(false, "Noma'lum action.")
        }
    }
}
