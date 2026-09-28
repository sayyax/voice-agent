package uz.agent.voice.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import uz.agent.voice.config.Prefs
import uz.agent.voice.config.SecureStore
import uz.agent.voice.llm.GeminiProvider

class SettingsActivity : Activity() {

    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var resultView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Sozlamalar"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
        })

        root.addView(label("Gemini API kalit"))
        keyInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            hint = if (Prefs.geminiKey(this@SettingsActivity) != null)
                "Saqlangan (o'zgartirish uchun yangisini kiriting)" else "AIza..."
        }
        root.addView(keyInput)

        root.addView(label("Model nomi"))
        modelInput = EditText(this).apply {
            setSingleLine(true)
            setText(Prefs.model(this@SettingsActivity))
        }
        root.addView(modelInput)

        root.addView(Button(this).apply {
            text = "Saqlash"
            setOnClickListener { save() }
        })
        root.addView(Button(this).apply {
            text = "Tekshirish (Gemini'ga so'rov)"
            setOnClickListener { test() }
        })
        root.addView(Button(this).apply {
            text = "Kalitni o'chirish"
            setOnClickListener {
                SecureStore.remove(this@SettingsActivity, Prefs.GEMINI_KEY)
                keyInput.hint = "AIza..."
                toast("Kalit o'chirildi")
            }
        })
        root.addView(Button(this).apply {
            text = "Orqaga"
            setOnClickListener { finish() }
        })

        resultView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(resultView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Color.parseColor("#444444"))
        setPadding(0, dp(16), 0, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun save() {
        val key = keyInput.text.toString().trim()
        if (key.isNotEmpty()) {
            SecureStore.put(this, Prefs.GEMINI_KEY, key)
            keyInput.setText("")
            keyInput.hint = "Saqlangan (o'zgartirish uchun yangisini kiriting)"
        }
        Prefs.setModel(this, modelInput.text.toString())
        toast("Saqlandi")
    }

    private fun test() {
        val key = keyInput.text.toString().trim().ifEmpty { Prefs.geminiKey(this) }
        if (key.isNullOrEmpty()) {
            resultView.text = "Avval API kalitni kiriting."
            return
        }
        val model = modelInput.text.toString().trim().ifEmpty { Prefs.DEFAULT_MODEL }
        resultView.text = "So'rov yuborilmoqda..."
        Thread {
            val plan = GeminiProvider(key, model).plan(
                "Youtube'dan Sting Shape of My Heart ni qo'y", listOf("telegram", "youtube")
            )
            runOnUiThread {
                resultView.text = plan.error?.let { "XATO: $it" }
                    ?: "OK. Gemini javobi: ${plan.action}"
            }
        }.start()
    }
}
