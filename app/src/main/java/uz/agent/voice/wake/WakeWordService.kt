package uz.agent.voice.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import uz.agent.voice.agent.intent.CommandParser
import uz.agent.voice.agent.tools.Action
import uz.agent.voice.agent.tools.ActionExecutor
import uz.agent.voice.agent.tools.ActionNames
import uz.agent.voice.config.Prefs
import uz.agent.voice.llm.GeminiProvider
import uz.agent.voice.llm.LLMPlan
import uz.agent.voice.ui.MainActivity
import uz.agent.voice.voice.AndroidSttProvider
import uz.agent.voice.voice.AndroidTtsProvider
import uz.agent.voice.voice.STTProvider
import uz.agent.voice.voice.TTSProvider

/**
 * Fonda doim ishlaydigan xizmat: "agent" so'zini eshitguncha qisqa tsikllarda tinglaydi,
 * eshitgach buyruqni to'liq qabul qilib, MainActivity'dagi kabi bajaradi (ovozli javob bilan).
 * Eslatma: bu har-safar SpeechRecognizer'ni qayta ishga tushiradigan oddiy usul - maxsus
 * "wake word" dvigateliga qaraganda batareyani ko'proq sarflaydi va tsikllar orasida
 * qisqa bo'shliq bo'ladi, lekin qo'shimcha kutubxonasiz ishlaydi.
 */
class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIF_ID = 42
        const val WAKE_WORD = "agent"
        const val ACTION_STOP = "uz.agent.voice.wake.STOP"
        @Volatile var isRunning = false
            private set
    }

    private object Mode { const val WAKE = 0; const val COMMAND = 1; const val CONFIRM = 2 }

    private lateinit var executor: ActionExecutor
    private lateinit var stt: STTProvider
    private lateinit var tts: TTSProvider
    private val handler = Handler(Looper.getMainLooper())
    private var mode = Mode.WAKE
    private var pendingSteps: List<Action>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        executor = ActionExecutor(this)
        tts = AndroidTtsProvider(this) { }
        stt = AndroidSttProvider(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Uyg'otuvchi so'zni kutyapman (\"agent\")..."))
        mode = Mode.WAKE
        listenCycle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stt.release()
        tts.shutdown()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Ovozli Agent - doim tinglash", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            flags or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("Ovozli Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPi)
            .addAction(0, "To'xtatish", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))
    }

    private val listener = object : STTProvider.Listener {
        override fun onListening() {}
        override fun onPartial(text: String) {
            if (mode == Mode.WAKE && text.lowercase().contains(WAKE_WORD)) onWakeDetected()
        }
        override fun onFinal(text: String) {
            when (mode) {
                Mode.WAKE -> if (text.lowercase().contains(WAKE_WORD)) onWakeDetected() else listenCycle()
                Mode.COMMAND -> handleCommand(text)
                Mode.CONFIRM -> handleConfirm(text)
                else -> listenCycle()
            }
        }
        override fun onError(message: String) { listenCycle() }
    }

    private fun onWakeDetected() {
        mode = Mode.COMMAND
        updateNotification("Eshityapman...")
        tts.speak("Labbay")
        handler.postDelayed({ listenCycle() }, 900)
    }

    private fun listenCycle() {
        if (!isRunning) return
        if (!stt.isAvailable()) {
            updateNotification("Ovoz tanish xizmati topilmadi")
            return
        }
        try {
            stt.start(listener)
        } catch (e: Exception) {
            handler.postDelayed({ listenCycle() }, 1000)
        }
    }

    private fun handleCommand(text: String) {
        if (text.isBlank()) { backToWake(); return }
        val local = executor.plan(text)
        if (local.name != ActionNames.UNKNOWN) { runSteps(listOf(local)); return }
        val key = Prefs.geminiKey(this)
        if (key == null) { tts.speak("Gemini kaliti sozlanmagan. Sozlamalarda kiriting."); backToWake(); return }
        val model = Prefs.model(this)
        val ids = executor.appIds()
        Thread {
            val plan = GeminiProvider(key, model).plan(text, ids)
            handler.post { handlePlan(plan) }
        }.start()
    }

    private fun handlePlan(plan: LLMPlan) {
        if (plan.error != null) { tts.speak(plan.error); backToWake(); return }
        val steps = plan.steps
        if (steps.isEmpty() || (steps.size == 1 && steps[0].name == ActionNames.UNKNOWN)) {
            tts.speak(plan.say ?: "Buyruqni tushunmadim.")
            backToWake()
            return
        }
        if (steps.size == 1 && steps[0].name == ActionNames.CLARIFY) {
            tts.speak(plan.say ?: "Aniqroq ayting.")
            backToWake()
            return
        }
        if (steps.any { it.name in ActionNames.needsConfirmation }) {
            pendingSteps = steps
            mode = Mode.CONFIRM
            tts.speak(steps.joinToString(", keyin ") { describeStep(it) } + ". Davom etaymi?")
            handler.postDelayed({ listenCycle() }, 900)
            return
        }
        runSteps(steps)
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

    private fun handleConfirm(text: String) {
        val steps = pendingSteps
        pendingSteps = null
        if (steps == null) { backToWake(); return }
        when (CommandParser.parseYesNo(text)) {
            true -> runSteps(steps)
            false -> { tts.speak("Bekor qilindi."); backToWake() }
            null -> { tts.speak("Tushunmadim."); backToWake() }
        }
    }

    private fun runSteps(steps: List<Action>) {
        updateNotification("Bajarilmoqda...")
        Thread {
            val result = executor.executeSteps(steps)
            handler.post {
                tts.speak(result.message)
                backToWake()
            }
        }.start()
    }

    private fun backToWake() {
        mode = Mode.WAKE
        updateNotification("Uyg'otuvchi so'zni kutyapman (\"agent\")...")
        handler.postDelayed({ listenCycle() }, 1200)
    }
}
