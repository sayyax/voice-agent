package uz.agent.voice.voice

/** Speech-to-text provider interfeysi. Keyinchalik Whisper/Muxlisa bilan almashtiriladi. */
interface STTProvider {
    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    fun isAvailable(): Boolean
    fun start(listener: Listener)
    fun stop()
    fun release()
}
