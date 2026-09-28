package uz.agent.voice.agent.validator

import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.android.apps.AppEntry

data class Validation(val ok: Boolean, val reason: String = "")

class ActionValidator(private val apps: List<AppEntry>) {
    fun validate(action: Action): Validation {
        if (action.name !in ActionNames.supported) {
            return Validation(false, "Bu buyruqni hali tushunmayman.")
        }
        return when (action.name) {
            ActionNames.OPEN_APP -> {
                val id = action.params["app"]
                    ?: return Validation(false, "Qaysi ilovani ochish kerak?")
                if (apps.none { it.id == id }) Validation(false, "Noma'lum ilova: $id")
                else Validation(true)
            }
            else -> Validation(false, "Noma'lum action.")
        }
    }
}
