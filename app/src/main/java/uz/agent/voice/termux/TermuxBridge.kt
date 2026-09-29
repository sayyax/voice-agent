package uz.agent.voice.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class TermuxRunResult(
    val ranAtAll: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String
)

/**
 * Termux'ga buyruq yuborish (RUN_COMMAND). Termux'da avval bir marta:
 *   mkdir -p ~/.termux
 *   echo "allow-external-apps=true" >> ~/.termux/termux.properties
 *   termux-reload-settings
 * qilingan bo'lishi shart, aks holda Termux buyruqni rad etadi.
 */
object TermuxBridge {
    private const val HOME = "/data/data/com.termux/files/home"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var result: TermuxRunResult? = null

    fun run(ctx: Context, command: String, workdir: String = HOME, timeoutMs: Long = 20000): TermuxRunResult {
        val l = CountDownLatch(1)
        latch = l
        result = null

        return try {
            val resultIntent = Intent(ctx, TermuxResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(ctx, 0, resultIntent, flags)

            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
            }
            ctx.startForegroundService(intent)

            val finished = l.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) TermuxRunResult(false, null, "", "Termux javob bermadi (vaqt tugadi).")
            else result ?: TermuxRunResult(false, null, "", "Natija olinmadi.")
        } catch (e: Exception) {
            TermuxRunResult(
                false, null, "",
                "Termux'ga ulanib bo'lmadi. Termux o'rnatilganmi va allow-external-apps yoqilganmi tekshiring. (${e.message})"
            )
        }
    }

    internal fun deliver(stdout: String, stderr: String, exitCode: Int?) {
        result = TermuxRunResult(true, exitCode, stdout, stderr)
        latch?.countDown()
    }
}
