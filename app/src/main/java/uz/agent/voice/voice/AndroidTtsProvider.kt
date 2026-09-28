package uz.agent.voice.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android TextToSpeech. Avval uz-UZ ni sinaydi. Agar telefonda o'zbekcha ovoz bo'lmasa,
 * turkcha (o'zbek lotin yozuviga eng yaqin) ovozga o'tadi va bu haqda onReady orqali xabar beradi.
 */
class AndroidTtsProvider(
    ctx: Context,
    private val onReady: (String) -> Unit
) : TTSProvider {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                onReady("TTS ishga tushmadi")
            } else {
                val uz = engine.setLanguage(Locale.forLanguageTag("uz-UZ"))
                if (uz >= TextToSpeech.LANG_AVAILABLE) {
                    ready = true
                    onReady("o'zbekcha (uz-UZ)")
                } else {
                    val tr = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
                    if (tr >= TextToSpeech.LANG_AVAILABLE) {
                        ready = true
                        onReady("o'zbekcha ovoz yo'q, turkcha ovoz ishlatilmoqda")
                    } else {
                        onReady("mos ovoz topilmadi (Google Text-to-speech dan uz-UZ yuklang)")
                    }
                }
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
