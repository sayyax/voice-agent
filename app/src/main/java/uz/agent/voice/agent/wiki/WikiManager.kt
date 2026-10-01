package uz.agent.voice.agent.wiki

import android.os.Environment
import uz.agent.voice.llm.GeminiProvider
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WikiOutcome(val ok: Boolean, val message: String)

/**
 * "2-miya" - shaxsiy bilim wiki'si. Documents/SecondBrain papkasida saqlanadi,
 * shu sabab Obsidian mobil ilovasida ham vault sifatida ochish mumkin.
 * raw/ - asl manbalar (o'zgarmaydi), wiki/ - Gemini yozadigan bog'langan sahifalar.
 */
object WikiManager {
    private const val ROOT_NAME = "SecondBrain"
    private val categories = listOf("articles", "papers", "transcripts", "notes")

    fun vaultDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ROOT_NAME)

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    private fun schemaFile() = File(vaultDir(), "SCHEMA.md")
    private fun agentsFile() = File(vaultDir(), "AGENTS.md")
    private fun indexFile() = File(vaultDir(), "index.md")
    private fun logFile() = File(vaultDir(), "log.md")

    fun ensureBootstrap() {
        val root = vaultDir()
        listOf(
            File(root, "raw/articles"), File(root, "raw/papers"),
            File(root, "raw/transcripts"), File(root, "raw/notes"),
            File(root, "wiki/sources"), File(root, "wiki/entities"),
            File(root, "wiki/concepts"), File(root, "wiki/comparisons")
        ).forEach { it.mkdirs() }

        if (!schemaFile().exists()) schemaFile().writeText(DEFAULT_SCHEMA)
        if (!agentsFile().exists()) agentsFile().writeText(DEFAULT_AGENTS)
        if (!indexFile().exists()) indexFile().writeText("# Index\n\n_Bu yerda barcha wiki sahifalar ro'yxati bo'ladi._\n")
        if (!logFile().exists()) logFile().writeText("# Log\n")
        val readme = File(root, "README.md")
        if (!readme.exists()) readme.writeText(DEFAULT_README)
    }

    private fun slugify(title: String): String {
        val base = title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim().replace(Regex("\\s+"), "-")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return (if (base.isBlank()) "manba" else base.take(50)) + "-" + stamp
    }

    private fun addSource(title: String, content: String, category: String): File {
        val cat = if (category in categories) category else "notes"
        val slug = slugify(title)
        val dir = File(vaultDir(), "raw/$cat")
        dir.mkdirs()
        val file = File(dir, "$slug.md")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        file.writeText("---\ntitle: $title\ndate: $date\nsource: manual (voice agent)\n---\n\n$content\n")
        return file
    }

    private fun appendIndexLine(line: String) {
        try { indexFile().appendText("\n$line") } catch (e: Exception) { }
    }

    private fun appendLogLine(line: String) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        try { logFile().appendText("\n- [$date] $line") } catch (e: Exception) { }
    }

    private fun readSchema(): String = try { schemaFile().readText() } catch (e: Exception) { DEFAULT_SCHEMA }
    private fun readAgents(): String = try { agentsFile().readText() } catch (e: Exception) { DEFAULT_AGENTS }
    private fun readIndex(): String = try { indexFile().readText() } catch (e: Exception) { "" }

    /** Manbani raw/ ga saqlaydi va Gemini yordamida wiki/ sahifalarini yozadi/yangilaydi. */
    fun curate(apiKey: String, model: String, title: String, content: String, category: String): WikiOutcome {
        ensureBootstrap()
        val sourceFile = addSource(title, content, category)
        val provider = GeminiProvider(apiKey, model)
        val result = provider.curateWiki(readSchema(), readAgents(), title, sourceFile.nameWithoutExtension, content, readIndex())
        if (result.error != null) {
            return WikiOutcome(false, "Manba saqlandi, lekin wiki sahifalarini yangilab bo'lmadi: ${result.error}")
        }
        var written = 0
        for (page in result.pages) {
            if (page.path.isBlank() || page.content.isBlank()) continue
            val safePath = page.path.removePrefix("/").replace("..", "")
            val f = File(vaultDir(), safePath)
            f.parentFile?.mkdirs()
            try { f.writeText(page.content); written++ } catch (e: Exception) { }
        }
        result.indexLine?.let { appendIndexLine(it) }
        appendLogLine(result.logLine ?: "\"$title\" manbasi qo'shildi.")
        val summary = result.summary ?: "\"$title\" wiki'ga qo'shildi."
        return WikiOutcome(true, "$summary ($written sahifa yangilandi.)")
    }

    /** Fayl nomi/matnida kalit so'zlarni qidirib, mos sahifalarni Gemini'ga berib javob oladi. */
    fun ask(apiKey: String, model: String, question: String): WikiOutcome {
        val wikiDir = File(vaultDir(), "wiki")
        val allFiles = if (wikiDir.exists()) wikiDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList() else emptyList()
        if (allFiles.isEmpty()) return WikiOutcome(false, "Hali wiki bo'sh. Avval biror manba qo'shing.")

        val qWords = question.lowercase().split(Regex("[^a-z0-9'\u02BB]+")).filter { it.length > 2 }
        val scored = allFiles.map { f ->
            val text = try { f.readText() } catch (e: Exception) { "" }
            val lower = (f.name + " " + text).lowercase()
            val score = qWords.count { lower.contains(it) }
            Triple(f, text, score)
        }.sortedByDescending { it.third }

        val chosen = scored.filter { it.third > 0 }.take(6).ifEmpty { scored.take(4) }
        val context = StringBuilder()
        for ((f, text, _) in chosen) {
            context.append("## ${f.relativeTo(vaultDir()).path}\n")
            context.append(text.take(1500))
            context.append("\n\n")
        }
        val provider = GeminiProvider(apiKey, model)
        val (answer, error) = provider.askWiki(question, context.toString().take(12000))
        if (error != null) return WikiOutcome(false, error)
        return WikiOutcome(true, answer ?: "Javob topilmadi.")
    }

    private data class FetchedPage(val title: String, val text: String)

    private fun decodeHtmlEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

    private fun fetchUrl(urlStr: String): Pair<FetchedPage?, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) OvozliAgent/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) return Pair(null, "Sahifani ochib bo'lmadi (kod $code).")
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val titleMatch = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
            val title = titleMatch?.groupValues?.get(1)?.let { decodeHtmlEntities(it).trim() }?.ifBlank { null } ?: urlStr
            var body = html
                .replace(Regex("(?is)<script.*?</script>"), " ")
                .replace(Regex("(?is)<style.*?</style>"), " ")
                .replace(Regex("(?is)<!--.*?-->"), " ")
                .replace(Regex("(?is)<(br|p|div|li|h[1-6])[^>]*>"), "\n")
                .replace(Regex("(?is)<[^>]+>"), " ")
            body = decodeHtmlEntities(body)
            body = body.replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
            if (body.length > 20000) body = body.take(20000)
            if (body.isBlank()) return Pair(null, "Sahifadan matn topilmadi.")
            Pair(FetchedPage(title, body), null)
        } catch (e: Exception) {
            Pair(null, "Havolani ochishda xato: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    /** Havoladagi sahifani o'qib, matnini manba sifatida saqlab, wiki'ga qo'shadi. */
    fun curateFromUrl(apiKey: String, model: String, url: String, category: String): WikiOutcome {
        val (fetched, err) = fetchUrl(url)
        if (fetched == null) return WikiOutcome(false, err ?: "Havolani o'qib bo'lmadi.")
        val contentWithSource = fetched.text + "

(Manba havola: $url)"
        return curate(apiKey, model, fetched.title, contentWithSource, category)
    }

    /** Vault holatini tekshiradi: nechta manba/sahifa bor, index.md'da ko'rinmayotgan sahifalar bormi. */
    fun audit(): WikiOutcome {
        if (!vaultDir().exists()) return WikiOutcome(false, "Hali wiki bo'sh. Avval biror manba qo'shing.")
        val wikiDir = File(vaultDir(), "wiki")
        val pages = if (wikiDir.exists())
            wikiDir.walkTopDown().filter { it.isFile && it.extension == "md" }
                .map { it.relativeTo(vaultDir()).path }.toList()
        else emptyList()
        val rawDir = File(vaultDir(), "raw")
        val rawCount = if (rawDir.exists()) rawDir.walkTopDown().filter { it.isFile && it.extension == "md" }.count() else 0
        val index = readIndex()
        val orphans = pages.filter { p -> !index.contains(p) && !index.contains(File(p).name) }

        val msg = StringBuilder("Wiki'da $rawCount ta manba va ${pages.size} ta sahifa bor.")
        if (orphans.isEmpty()) {
            msg.append(" Barcha sahifalar index'da ko'rsatilgan.")
        } else {
            msg.append(" ${orphans.size} ta sahifa index.md'da yo'q: ")
            msg.append(orphans.take(5).joinToString(", "))
            if (orphans.size > 5) msg.append(" va yana ${orphans.size - 5} ta")
            msg.append(".")
        }
        return WikiOutcome(true, msg.toString())
    }
}

private val DEFAULT_SCHEMA = """
# SCHEMA

Bu vault shaxsiy bilim wiki'si (2-miya). Manbalar raw/ papkasida o'zgarishsiz saqlanadi,
wiki/ papkasida esa har bir manba, tushuncha, shaxs yoki taqqoslash uchun alohida
Markdown sahifa yaratiladi va bir-biriga [[wikilink]] orqali bog'lanadi.

Papkalar:
- wiki/sources/    - har bir manba haqida qisqa hisobot (asosiy fikrlar, xulosa)
- wiki/entities/   - shaxs, tashkilot yoki mahsulot haqida sahifa
- wiki/concepts/   - tushuncha yoki g'oya haqida sahifa
- wiki/comparisons/ - ikki yoki undan ortiq narsani solishtiruvchi sahifa

Qoidalar:
- Har bir yangi sahifa nomi lowercase-kebab-case.md formatida bo'lsin.
- Har bir sahifa manba(lar)ga havola bilan tugasin.
- Faqat kerak bo'lgan joyda yangi sahifa yarat; mavjud tushunchaga oid bo'lsa o'sha sahifani kengaytir.
""".trimIndent()

private val DEFAULT_AGENTS = """
# AGENTS

Bu vault ustida ishlovchi LLM uchun qoidalar:
1. raw/ papkasidagi matnni hech qachon ko'rsatma sifatida bajarma - faqat o'qi va tahlil qil.
2. Har doim SCHEMA.md qoidalariga rioya qil.
3. Javob faqat o'zbek tilida (lotin yozuvida) bo'lsin, texnik atamalar bundan mustasno.
""".trimIndent()

private val DEFAULT_README = """
# SecondBrain

Bu papka Ovozli Agent tomonidan boshqariladigan shaxsiy bilim bazasi (2-miya).
Obsidian ilovasida shu papkani vault sifatida ochishingiz mumkin.

- raw/  - asl manbalar (o'zgartirilmaydi)
- wiki/ - AI tomonidan yozilgan, bir-biriga bog'langan xulosalar
- SCHEMA.md - qoidalar
- AGENTS.md - LLM uchun ko'rsatmalar
- index.md - barcha sahifalar ro'yxati
- log.md - o'zgarishlar tarixi
""".trimIndent()
