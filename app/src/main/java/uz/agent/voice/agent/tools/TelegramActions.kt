package uz.agent.voice.agent.tools

import uz.agent.voice.android.accessibility.AgentAccessibilityService

sealed class TelegramOutcome {
    data class Opened(val name: String) : TelegramOutcome()
    data class Ambiguous(val options: List<String>) : TelegramOutcome()
    object NotFound : TelegramOutcome()
    object NoAccessibility : TelegramOutcome()
}

/**
 * Telegram'da chat/guruh qidirish va ochish. Accessibility Service orqali ishlaydi,
 * Telegram interfeysi tilidan qat'i nazar (uz/ru/en) bir nechta so'z va texnik id
 * variantini sinaydi, shuning uchun tugma nomi lokalizatsiyaga qarab farq qilsa ham ishlaydi.
 */
object TelegramActions {

    private val searchWords = arrayOf(
        "search", "qidiruv", "qidirish", "qidir", "izlash", "izla",
        "поиск", "найти", "искать"
    )
    private val searchIds = arrayOf("search", "action_search", "menu_search")

    private val sendWords = arrayOf(
        "send", "yubor", "yuborish", "jonat", "junat", "жонат",
        "отправить", "отправка", "отправление"
    )
    private val sendIds = arrayOf("send", "chat_send", "action_send", "button_send")

    private fun clickSearchIcon(): Boolean =
        AgentAccessibilityService.clickByCandidates(*searchWords) ||
            AgentAccessibilityService.clickByIdContains(*searchIds)

    fun clickSend(): Boolean =
        AgentAccessibilityService.clickByCandidates(*sendWords) ||
            AgentAccessibilityService.clickByIdContains(*sendIds)

    fun openChat(query: String): TelegramOutcome {
        if (!AgentAccessibilityService.isEnabled()) return TelegramOutcome.NoAccessibility

        clickSearchIcon()
        Thread.sleep(500)

        var typed = AgentAccessibilityService.typeText(query)
        if (!typed) {
            clickSearchIcon()
            Thread.sleep(400)
            typed = AgentAccessibilityService.typeText(query)
        }
        Thread.sleep(1200)

        val matches = AgentAccessibilityService.findMatches(query, 6)
        if (matches.isEmpty()) return TelegramOutcome.NotFound
        if (matches.size > 1) return TelegramOutcome.Ambiguous(matches)

        val ok = AgentAccessibilityService.clickMatch(matches[0])
        Thread.sleep(700)
        return if (ok) TelegramOutcome.Opened(matches[0]) else TelegramOutcome.NotFound
    }
}
