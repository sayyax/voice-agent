package uz.agent.voice.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Termux RUN_COMMAND natijasini (stdout/stderr/exitCode) qabul qiladi. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stdout = intent.getStringExtra("stdout").orEmpty()
        val stderr = intent.getStringExtra("stderr").orEmpty()
        val exit = if (intent.hasExtra("exitCode")) intent.getIntExtra("exitCode", -1) else null
        TermuxBridge.deliver(stdout, stderr, exit)
    }
}
