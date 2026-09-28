package uz.agent.voice.ui

import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var executor: ActionExecutor
    private lateinit var statusView: TextView
    private lateinit var lastCommandView: TextView
    private lateinit var lastActionView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = ActionExecutor(this)

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
        root.addView(label("Ruxsatlar: Accessibility - 4-bosqichda; Mikrofon - 2-bosqichda"))

        input = EditText(this).apply {
            hint = "Masalan: Telegramni och"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_DONE) { run(); true } else false
            }
        }
        root.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Bajarish"
            setOnClickListener { run() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Mikrofon (2-bosqich)"
            isEnabled = false
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        log("Tayyor. Buyruq yozing.")
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Color.parseColor("#444444"))
        setPadding(0, dp(4), 0, 0)
    }

    private fun run() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        lastCommandView.text = "Oxirgi buyruq: $text"
        statusView.text = "Holat: bajarilmoqda"
        val action = executor.plan(text)
        lastActionView.text = "Oxirgi action: $action"
        val result = executor.execute(action)
        statusView.text = if (result.ok) "Holat: tayyor" else "Holat: xato"
        log((if (result.ok) "OK  " else "XATO ") + result.message)
        if (result.ok) input.setText("")
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logView.append("[$time] $msg\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
