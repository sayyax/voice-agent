package uz.agent.voice.llm

import org.json.JSONArray
import org.json.JSONObject
import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionNames
import java.net.HttpURLConnection
import java.net.URL

data class WikiPage(val path: String, val content: String)
data class WikiCurationResult(
    val pages: List<WikiPage> = emptyList(),
    val indexLine: String? = null,
    val logLine: String? = null,
    val summary: String? = null,
    val error: String? = null
)

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
            val say = o.optString("say", "").ifBlank { null }
            val stepsArr = o.optJSONArray("steps")
            val steps = mutableListOf<Action>()
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val so = stepsArr.getJSONObject(i)
                    val name = so.optString("action", ActionNames.UNKNOWN)
                    val params = mutableMapOf<String, String>()
                    so.optJSONObject("params")?.let { po ->
                        po.keys().forEach { k -> params[k] = po.optString(k, "") }
                    }
                    steps.add(Action(name, params))
                }
            }
            LLMPlan(steps = steps, say = say)
        } catch (e: Exception) {
            LLMPlan(error = "LLM javobini o'qib bo'lmadi.")
        }
    }

    fun curateWiki(
        schema: String, agentsDoc: String, sourceTitle: String,
        sourceSlug: String, sourceContent: String, currentIndex: String
    ): WikiCurationResult {
        val conn = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 40000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            val sys = wikiCurationPrompt(schema, agentsDoc)
            val user = "Source title: " + sourceTitle + "\n" +
                "Source slug: " + sourceSlug + "\n" +
                "Current index.md:\n---\n" + currentIndex + "\n---\n" +
                "Source content:\n---\n" + sourceContent + "\n---"
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
                ))
                .put("generationConfig", JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return WikiCurationResult(error = httpError(code, text))
            parseCuration(text)
        } catch (e: Exception) {
            WikiCurationResult(error = "Internet yoki LLM xatosi: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCuration(responseText: String): WikiCurationResult {
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
            val pagesArr = o.optJSONArray("pages")
            val pages = mutableListOf<WikiPage>()
            if (pagesArr != null) {
                for (i in 0 until pagesArr.length()) {
                    val po = pagesArr.getJSONObject(i)
                    pages.add(WikiPage(po.optString("path", ""), po.optString("content", "")))
                }
            }
            WikiCurationResult(
                pages = pages,
                indexLine = o.optString("indexLine", "").ifBlank { null },
                logLine = o.optString("logLine", "").ifBlank { null },
                summary = o.optString("summary", "").ifBlank { null }
            )
        } catch (e: Exception) {
            WikiCurationResult(error = "Wiki javobini o'qib bo'lmadi.")
        }
    }

    private fun wikiCurationPrompt(schema: String, agentsDoc: String): String = """
You maintain the user's personal Markdown knowledge base ("SecondBrain" / "2-miya vault").
Follow these rules exactly:
$schema

$agentsDoc

Output ONLY one JSON object of the form:
{"summary":"<1-2 sentence Uzbek summary of what you did>","pages":[{"path":"wiki/<folder>/<slug>.md","content":"<full markdown content>"}],"indexLine":"<one markdown list line to append to index.md>","logLine":"<one short Uzbek sentence for log.md>"}
Write 1 to 4 pages depending on how much the source contains (at least one wiki/sources/ page). No markdown fences, no extra text.
""".trimIndent()

    fun askWiki(question: String, contextText: String): Pair<String?, String?> {
        val conn = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            val sys = """
You answer questions using the user's personal knowledge base (Markdown wiki pages, Uzbek or English).
Answer briefly in Uzbek, based ONLY on the provided pages. If the answer isn't there, say so honestly.
Mention which page(s) it came from at the end, in parentheses, using their file names.
""".trimIndent()
            val user = "Wiki pages:\n---\n" + contextText + "\n---\nQuestion: " + question
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
                ))
                .put("generationConfig", JSONObject().put("temperature", 0.2))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return Pair(null, httpError(code, text))
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            Pair(sb.toString().trim().ifBlank { null }, null)
        } catch (e: Exception) {
            Pair(null, "Internet yoki LLM xatosi: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun systemPrompt(appIds: List<String>): String = """
You are the intent parser of an Uzbek voice assistant running on an Android phone. Understand every command fully and precisely before deciding anything - read the whole sentence, including Uzbek suffixes that mark who does what to whom (masalan "-ga", "-ni", "-dan", "-day"), before you decide on an action.
The user speaks Uzbek (sometimes mixed with Russian or English, sometimes typed in Cyrillic script). The text comes from speech recognition, so it may contain spelling mistakes, dropped word endings, or wrong word breaks; read past these errors rather than reacting to individual broken words.
Convert the user's command into exactly ONE JSON object of the form {"steps":[...],"say":"..."} and output nothing else.
"steps" is an array of one or more action objects, each {"action":"<name>","params":{...}}, executed in order. Most commands need only one step.

Available actions:
- open_app {app:"<id>"}  where <id> is one of: ${appIds.joinToString(", ")}
- open_url {url:"https://..."}
- search_youtube {query:"<text>"}
- press_back / press_home / press_recents  (no params)
- click_text {text:"<visible text on the CURRENT screen>"}
- type_text {text:"<text to type into the focused field>"}
- scroll_down / scroll_up  (no params)
- open_telegram_chat {name:"<chat or group name>"}
- send_telegram_message {name:"<chat or group name>", message:"<text>"}
- call_contact {name:"<contact name as said, e.g. Ali, Onam>"}
- call_number {number:"<digits only>"}
- find_file {name:"<part of file name>"}  (searches Download/Documents/DCIM/Pictures)
- move_file {name:"<part of file name>", dest:"<optional subfolder name under Download, empty for Download root>"}
- rename_file {name:"<part of file name>", new_name:"<new file name with extension>"}
- run_termux {command:"<shell command to run inside Termux>"}
- open_claude_with_text {text:"<text to send to the Claude app>"}
- wiki_add_source {content:"<the source text to remember, keep the user's own wording/facts as given>", title:"<short title inferred from the content if not stated>", category:"<one of articles|papers|transcripts|notes, default notes>"}
- wiki_ask {question:"<user's question to answer from their personal wiki>"}
- wiki_add_url {url:"<https:// link the user wants saved to their wiki>", category:"<one of articles|papers|transcripts|notes, default articles>"}
- wiki_audit  with no params — check the personal wiki's consistency (page/source counts, pages missing from index.md)
- clarify  with top-level "say" set to one short Uzbek question — when the command is ambiguous or a needed detail is missing
- unknown  with top-level "say" set to a short Uzbek explanation — when the command is none of the above

Chaining rule: for a compound request like "Claude bergan faylni Termuxga joyla va ishga tushir" (find a file, then move it, then run it), output multiple steps in order, e.g.:
{"steps":[{"action":"find_file","params":{"name":"..."}},{"action":"move_file","params":{"name":"...","dest":""}},{"action":"run_termux","params":{"command":"cd ~/storage/downloads && python ..."}}]}
Only chain steps when the user's single utterance clearly describes multiple sequential actions; otherwise output one step.

Rules:
- Never invent app ids. If the app is not in the list, use unknown.
- click_text and type_text only affect the screen the phone is already showing.
- For send_telegram_message, call_contact, call_number, move_file, rename_file and run_termux, never add a "confirmed" param yourself — the app always asks the user to confirm before doing these.
- run_termux commands must be exactly what should run in bash, nothing else added.
- Never guess a missing, unclear, or ambiguous parameter (a name, file, command, number, or which of several matching actions was meant). If the command could reasonably mean more than one thing, or a needed detail wasn't actually said, output clarify and ask precisely for that one missing detail in Uzbek instead of proceeding on a guess.
- Only act once you are confident you understood the complete command; a partial or rushed reading of the sentence is not enough to choose an action.
- "say" is optional (used mainly with clarify/unknown) and must be short, natural, friendly Uzbek (Latin script) - phrase it the way a person would actually say it out loud, not a stiff translation.
- Output valid JSON only, no markdown.
""".trimIndent()
}
