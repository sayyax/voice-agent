package uz.agent.voice.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidSttProvider(
    private val ctx: Context,
    private val lang: String = "uz-UZ"
) : STTProvider {

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    override fun start(listener: STTProvider.Listener) {
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listener.onListening() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                listener.onError(describe(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) listener.onError("Hech narsa eshitilmadi.")
                else listener.onFinal(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) listener.onPartial(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        r.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun release() {
        recognizer?.let {
            try { it.cancel() } catch (_: Exception) {}
            it.destroy()
        }
        recognizer = null
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Gapingizni tushunmadim."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hech narsa eshitilmadi."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet bilan muammo bor."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon ruxsati kerak."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ovoz tanish band, qayta urinib ko'ring."
        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon bilan muammo bor."
        else -> "Ovoz tanishda xato: $code"
    }
}
