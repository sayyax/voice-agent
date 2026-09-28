package uz.agent.voice.llm

import uz.agent.voice.agent.tools.Action

/** LLM natijasi: action, foydalanuvchiga aytiladigan matn (say) yoki xato. */
data class LLMPlan(
    val action: Action? = null,
    val say: String? = null,
    val error: String? = null
)

/** LLM provider interfeysi. plan() bloklovchi chaqiruv: orqa oqimda ishlating. */
interface LLMProvider {
    fun plan(userText: String, appIds: List<String>): LLMPlan
}
