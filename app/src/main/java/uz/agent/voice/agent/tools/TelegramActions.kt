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
 * Telegram interfeysi tilidan qat'i nazar (uz/ru/en) bir nechta so'z variantini sinaydi.
 * Bu — coordinate emas, ekrandagi matn/element asosidagi avtomatlashtirish.
 */
object TelegramActions {

    fun openChat(query: String): TelegramOutcome {
        if (!AgentAccessibilityService.isEnabled()) return TelegramOutcome.NoAccessibility

        AgentAccessibilityService.clickByCandidates("search", "qidiruv", "поиск")
        Thread.sleep(500)

        var typed = AgentAccessibilityService.typeText(query)
        if (!typed) {
            AgentAccessibilityService.clickByCandidates("search", "qidiruv", "поиск")
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
