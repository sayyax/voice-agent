package uz.agent.voice.agent.tools

import android.content.Context
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.validator.ActionValidator
import uz.agent.voice.android.apps.AppOpener
import uz.agent.voice.android.apps.AppRegistry

/** Pipeline: matn -> Action -> validator -> executor -> natija. */
class ActionExecutor(ctx: Context) {
    private val apps = AppRegistry.load(ctx)
    private val validator = ActionValidator(apps)
    private val opener = AppOpener(ctx)

    fun plan(text: String): Action = CommandParser.parse(text, apps)

    fun execute(action: Action): ActionResult {
        val v = validator.validate(action)
        if (!v.ok) return ActionResult(false, v.reason)
        return when (action.name) {
            ActionNames.OPEN_APP -> {
                val entry = apps.first { it.id == action.params["app"] }
                opener.open(entry)
            }
            else -> ActionResult(false, "Noma'lum action.")
        }
    }
}
