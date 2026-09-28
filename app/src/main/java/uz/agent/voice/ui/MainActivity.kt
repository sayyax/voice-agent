package uz.agent.voice.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.agent.voice.agent.tools.ActionExecutor
import uz.agent.voice.voice.AndroidSttProvider
import uz.agent.voice.voice.AndroidTtsProvider
import uz.agent.voice.voice.STTProvider
import uz.agent.voice.voice.TTSProvider
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
    private var listening = false

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
            hint = "Masalan: Telegramni och"
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
        root.addView(row)

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
        if (hasMic()) startListening() else log("XATO Mikrofon ruxsati berilmadi.")
    }

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermissions() {
        permissionView.text = "Mikrofon: " + (if (hasMic()) "berilgan" else "berilmagan") +
            " | Accessibility: 4-bosqichda"
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

    private fun run(text: String) {
        if (text.isEmpty()) return
        lastCommandView.text = "Oxirgi buyruq: $text"
        statusView.text = "Holat: bajarilmoqda"
        val action = executor.plan(text)
        lastActionView.text = "Oxirgi action: $action"
        val result = executor.execute(action)
        statusView.text = if (result.ok) "Holat: tayyor" else "Holat: xato"
        log((if (result.ok) "OK  " else "XATO ") + result.message)
        tts.speak(result.message)
        if (result.ok) input.setText("")
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logView.append("[$time] $msg\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
