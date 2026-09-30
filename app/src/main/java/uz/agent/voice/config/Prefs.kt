package uz.agent.voice.config

import android.content.Context

object Prefs {
    const val DEFAULT_MODEL = "gemini-3.5-flash"
    const val GEMINI_KEY = "gemini_key"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    fun model(ctx: Context): String =
        sp(ctx).getString("model", DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL

    fun setModel(ctx: Context, model: String) {
        sp(ctx).edit().putString("model", model.trim()).apply()
    }

    fun geminiKey(ctx: Context): String? =
        SecureStore.get(ctx, GEMINI_KEY)?.takeIf { it.isNotBlank() }
}
