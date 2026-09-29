package uz.agent.voice.llm

import org.json.JSONArray
import org.json.JSONObject
import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(
    private val apiKey: String,
    private val model: String
) : LLMProvider {

    override fun plan(userText: String, appIds: List<String>): LLMPlan {
        val conn = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)

            val body = JSONObject()
                .put(
                    "system_instruction",
                    JSONObject().put(
                        "parts", JSONArray().put(JSONObject().put("text", systemPrompt(appIds)))
                    )
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "parts", JSONArray().put(JSONObject().put("text", userText))
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject().put("temperature", 0).put("responseMimeType", "application/json")
                )
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return LLMPlan(error = httpError(code, text))
            parse(text)
        } catch (e: Exception) {
            LLMPlan(error = "Internet yoki LLM xatosi: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).getJSONObject("error").getString("message").take(120)
        } catch (e: Exception) {
            ""
        }
        val base = when (code) {
            400 -> "So'rov xato. Kalit yoki model nomini tekshiring."
            401, 403 -> "API kalit noto'g'ri yoki ruxsat yo'q."
            404 -> "Model topilmadi: $model. Sozlamalarda model nomini o'zgartiring."
            429 -> "Bepul limit tugadi. Biroz kutib qayta urinib ko'ring."
            else -> "LLM xatosi ($code)."
        }
        return if (detail.isEmpty()) base else "$base [$detail]"
    }

    private fun parse(responseText: String): LLMPlan {
        return try {
            val parts = JSONObject(responseText)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            val raw = sb.toString().trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val o = JSONObject(raw)
            val name = o.optString("action", ActionNames.UNKNOWN)
            val say = o.optString("say", "").ifBlank { null }
            val params = mutableMapOf<String, String>()
            o.optJSONObject("params")?.let { po ->
                po.keys().forEach { k -> params[k] = po.optString(k, "") }
            }
            LLMPlan(action = Action(name, params), say = say)
        } catch (e: Exception) {
            LLMPlan(error = "LLM javobini o'qib bo'lmadi.")
        }
    }

    private fun systemPrompt(appIds: List<String>): String = """
You are the intent parser of an Uzbek voice assistant running on an Android phone.
The user speaks Uzbek (sometimes mixed with Russian or English). The text comes from speech recognition, so it may contain spelling mistakes; be tolerant.
Convert the user's command into exactly ONE JSON object and output nothing else.

Allowed actions:
- {"action":"open_app","params":{"app":"<id>"}}  where <id> is one of: ${appIds.joinToString(", ")}
- {"action":"open_url","params":{"url":"https://..."}}  (url must start with https://; for example "Google'ga kir" -> https://www.google.com)
- {"action":"search_youtube","params":{"query":"<search text>"}}
- {"action":"press_back"} / {"action":"press_home"} / {"action":"press_recents"}
- {"action":"click_text","params":{"text":"<visible text of the button/element on the CURRENT screen>"}}
- {"action":"type_text","params":{"text":"<text to type into the currently focused input field>"}}
- {"action":"scroll_down"} / {"action":"scroll_up"}
- {"action":"open_telegram_chat","params":{"name":"<chat or group name>"}}  for requests like "Telegramdagi X guruhini och" / "X chatiga kir"
- {"action":"send_telegram_message","params":{"name":"<chat or group name>","message":"<message text>"}}  for requests like "X'ga mana shu xabarni yubor: ..." / "X'ga yoz: ..."
- {"action":"clarify","say":"<one short Uzbek question>"}  when the command is ambiguous or a needed detail is missing
- {"action":"unknown","say":"Bu buyruqni hali bajara olmayman."}  when the command is none of the above (phone calls, file management, Termux commands are not supported yet)

Rules:
- Never invent app ids. If the app is not in the list, use unknown.
- click_text and type_text only affect the screen the phone is already showing; do not use them to plan multi-step tasks other than what open_telegram_chat/send_telegram_message already cover.
- For send_telegram_message, never add a "confirmed" param yourself — the app always asks the user to confirm before sending.
- "say" is optional and must be short, natural Uzbek (Latin script).
- Output valid JSON only, no markdown.
""".trimIndent()
}
