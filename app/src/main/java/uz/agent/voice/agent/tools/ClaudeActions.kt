package uz.agent.voice.agent.tools

import uz.agent.voice.android.accessibility.AgentAccessibilityService

/**
 * Claude ilovasiga matn yozib yuborish. Ilova ochilgach kutish, matn kiritish va
 * yuborish tugmasini (bir nechta til varianti bo'yicha) bosishdan iborat.
 * Eslatma: Claude'ning javobini kutish/o'qib berish hali qo'llab-quvvatlanmaydi.
 */
object ClaudeActions {
    private val sendWords = listOf("send", "yubor", "yuborish", "отправить")
    private val sendIds = listOf("send")

    fun sendText(text: String): Boolean {
        if (!AgentAccessibilityService.isEnabled()) return false
        Thread.sleep(700)
        if (!AgentAccessibilityService.typeText(text)) return false
        Thread.sleep(300)
        return AgentAccessibilityService.clickByCandidates(*sendWords.toTypedArray()) ||
            AgentAccessibilityService.clickByIdContains(*sendIds.toTypedArray())
    }
}
