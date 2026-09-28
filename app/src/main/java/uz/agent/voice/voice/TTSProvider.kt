package uz.agent.voice.voice

/** Text-to-speech provider interfeysi. */
interface TTSProvider {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
