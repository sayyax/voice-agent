package uz.agent.voice.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionExecutor
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.android.accessibility.AgentAccessibilityService
import uz.agent.voice.android.files.FileTools
import uz.agent.voice.config.Prefs
import uz.agent.voice.llm.GeminiProvider
import uz.agent.voice.llm.LLMPlan
import uz.agent.voice.voice.AndroidSttProvider
import uz.agent.voice.voice.AndroidTtsProvider
import uz.agent.voice.voice.STTProvider
import uz.agent.voice.voice.TTSProvider
import uz.agent.voice.wake.WakeWordService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var executor: ActionExecutor
    private lateinit var stt: STTProvider
    private lateinit var tts: TTSProvider
    private lateinit var statusView: TextView
    private lateinit var lastCommandView: TextView
    private lateinit var lastActionView: TextView
    private lateinit var permissionView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var micButton: Button
    private lateinit var wakeButton: Button
    private var listening = false
    private var pendingSteps: List<Action>? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val sttListener = object : STTProvider.Listener {
        override fun onListening() {
            setListening(true)
            statusView.text = "Holat: tinglayapman..."
        }

        override fun onPartial(text: String) {
            input.setText(text)
            input.setSelection(text.length)
        }

        override fun onFinal(text: String) {
            setListening(false)
            input.setText(text)
            run(text)
        }

        override fun onError(message: String) {
            setListening(false)
            statusView.text = "Holat: tayyor"
            log("XATO $message")
            tts.speak(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = ActionExecutor(this)
        stt = AndroidSttProvider(this)
        tts = AndroidTtsProvider(this) { info ->
            runOnUiThread { if (::logView.isInitialized) log("Ovoz chiqishi: $info") }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Ovozli Agent"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
        })

        statusView = label("Holat: tayyor").also { root.addView(it) }
        lastCommandView = label("Oxirgi buyruq: -").also { root.addView(it) }
        lastActionView = label("Oxirgi action: -").also { root.addView(it) }
        permissionView = label("").also { root.addView(it) }

        input = EditText(this).apply {
            hint = "Masalan: bot.py faylini top va Termuxda ishga tushir"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_DONE) { run(text.toString().trim()); true } else false
            }
        }
        root.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Bajarish"
            setOnClickListener { run(input.text.toString().trim()) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        micButton = Button(this).apply {
            text = "Mikrofon"
            setOnClickListener { toggleMic() }
        }
        row.addView(micButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Sozlamalar"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = "Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(Button(this).apply {
            text = "Qo'ng'iroq ruxsati"
            setOnClickListener { ensureCallPermissions() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        wakeButton = Button(this).apply {
            text = "Doim tinglash: o'chiq"
            setOnClickListener { toggleWakeWord() }
        }
        root.addView(wakeButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        root.addView(Button(this).apply {
            text = "Fayllar ruxsatini ochish"
            setOnClickListener { openFilePermissionSettings() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        logView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            typeface = Typeface.MONOSPACE
        }
        logScroll = ScrollView(this).apply { addView(logView) }
        root.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12) })

        setContentView(root)
        log("Tayyor. Mikrofon tugmasini bosib gapiring yoki yozing.")
        if (!stt.isAvailable()) {
            log("XATO Telefonda ovoz tanish xizmati topilmadi. Google ilovasini yangilang.")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        wakeButton.text = if (WakeWordService.isRunning) "Doim tinglash: yoqiq" else "Doim tinglash: o'chiq"
    }

    override fun onDestroy() {
        stt.release()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
        if (requestCode == 1) {
            if (hasMic()) startListening() else log("XATO Mikrofon ruxsati berilmadi.")
        }
    }

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasContacts() =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun hasCallPhone() =
        checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun ensureCallPermissions() {
        val need = mutableListOf<String>()
        if (!hasContacts()) need += Manifest.permission.READ_CONTACTS
        if (!hasCallPhone()) need += Manifest.permission.CALL_PHONE
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 2)
    }

    private fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true

    private fun toggleWakeWord() {
        if (WakeWordService.isRunning) {
            stopService(Intent(this, WakeWordService::class.java))
            wakeButton.text = "Doim tinglash: o'chiq"
            return
        }
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifications()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        }
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        wakeButton.text = "Doim tinglash: yoqiq"
        log("Doim tinglash yoqildi. \"Agent\" deb chaqiring.")
    }

    private fun openFilePermissionSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            }
            startActivity(intent)
        } catch (e: Exception) {
            log("XATO Fayl ruxsati sozlamasini ochib bo'lmadi: ${e.message}")
        }
    }

    private fun refreshPermissions() {
        val llm = if (Prefs.geminiKey(this) != null) "Gemini bor" else "Gemini yo'q"
        val acc = if (AgentAccessibilityService.isEnabled()) "yoqilgan" else "o'chiq"
        val calls = if (hasContacts() && hasCallPhone()) "bor" else "yo'q"
        val files = if (FileTools.hasAccess()) "bor" else "yo'q"
        permissionView.text = "Mikrofon: " + (if (hasMic()) "bor" else "yo'q") +
            " | LLM: $llm | Accessibility: $acc | Qo'ng'iroq: $calls | Fayllar: $files"
    }

    private fun toggleMic() {
        if (listening) { stt.stop(); return }
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (!stt.isAvailable()) {
            log("XATO Ovoz tanish xizmati mavjud emas.")
            return
        }
        tts.stop()
        stt.start(sttListener)
    }

    private fun setListening(on: Boolean) {
        listening = on
        micButton.text = if (on) "To'xtatish" else "Mikrofon"
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Color.parseColor("#444444"))
        setPadding(0, dp(4), 0, 0)
    }

    /** Avval tasdiq kutilyaptimi tekshiradi, keyin oflayn parser, keyin LLM. */
    private fun run(text: String) {
        if (text.isEmpty()) return
        lastCommandView.text = "Oxirgi buyruq: $text"

        val pending = pendingSteps
        if (pending != null) {
            when (CommandParser.parseYesNo(text)) {
                true -> { pendingSteps = null; performSteps(pending); return }
                false -> { pendingSteps = null; report(true, "Bekor qilindi."); return }
                null -> { report(false, "Tushunmadim. \"Ha\" yoki \"yo'q\" deng."); return }
            }
        }

        val local = executor.plan(text)
        if (local.name != ActionNames.UNKNOWN) {
            performSteps(listOf(local))
            return
        }
        val key = Prefs.geminiKey(this)
        if (key == null) {
            report(false, "Bu buyruq uchun Gemini kerak. Sozlamalarda API kalitni kiriting.")
            return
        }
        statusView.text = "Holat: o'ylayapman..."
        val model = Prefs.model(this)
        val ids = executor.appIds()
        Thread {
            val plan = GeminiProvider(key, model).plan(text, ids)
            runOnUiThread { handlePlan(plan) }
        }.start()
    }

    private fun handlePlan(plan: LLMPlan) {
        if (plan.error != null) {
            report(false, plan.error)
            return
        }
        val steps = plan.steps
        if (steps.isEmpty() || (steps.size == 1 && steps[0].name == ActionNames.UNKNOWN)) {
            report(false, plan.say ?: "Buyruqni tushunmadim.")
            return
        }
        if (steps.size == 1 && steps[0].name == ActionNames.CLARIFY) {
            val q = plan.say ?: "Buyruqni aniqroq ayting."
            statusView.text = "Holat: tayyor"
            log("AGENT $q")
            tts.speak(q)
            return
        }
        if (steps.any { it.name == ActionNames.CALL_CONTACT || it.name == ActionNames.CALL_NUMBER }) {
            ensureCallPermissions()
        }
        if (steps.any { it.name in ActionNames.needsConfirmation }) {
            pendingSteps = steps
            val q = confirmQuestionForSteps(steps)
            statusView.text = "Holat: tasdiq kutilmoqda"
            log("AGENT $q")
            tts.speak(q)
            return
        }
        performSteps(steps)
    }

    private fun describeStep(a: Action): String = when (a.name) {
        ActionNames.OPEN_APP -> "\"${a.params["app"]}\" ilovasini ochish"
        ActionNames.FIND_FILE -> "\"${a.params["name"]}\" faylini topish"
        ActionNames.MOVE_FILE -> "\"${a.params["name"]}\" faylini ko'chirish"
        ActionNames.RENAME_FILE -> "\"${a.params["name"]}\" faylini qayta nomlash"
        ActionNames.RUN_TERMUX -> "Termux'da \"${a.params["command"]}\" ni bajarish"
        ActionNames.SEND_TELEGRAM_MESSAGE -> "\"${a.params["name"]}\"ga xabar yuborish"
        ActionNames.CALL_CONTACT -> "\"${a.params["name"]}\"ga qo'ng'iroq qilish"
        ActionNames.CALL_NUMBER -> "\"${a.params["number"]}\"ga qo'ng'iroq qilish"
        else -> a.name
    }

    private fun confirmQuestionForSteps(steps: List<Action>): String =
        steps.joinToString(", keyin ") { describeStep(it) } + ". Davom etaymi?"

    private fun performSteps(steps: List<Action>) {
        lastActionView.text = "Oxirgi action: " + steps.joinToString(" -> ")
        statusView.text = "Holat: bajarilmoqda"
        Thread {
            val result = executor.executeSteps(steps)
            runOnUiThread { report(result.ok, result.message) }
        }.start()
    }

    private fun report(ok: Boolean, msg: String) {
        statusView.text = if (ok) "Holat: tayyor" else "Holat: xato"
        log((if (ok) "OK  " else "XATO ") + msg)
        tts.speak(msg)
        if (ok) input.setText("")
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logView.append("[$time] $msg\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
