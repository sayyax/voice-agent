package uz.agent.voice.android.vision

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

data class OcrMatch(val text: String, val centerX: Float, val centerY: Float)

/**
 * Accessibility daraxtida (node) topilmagan matnni skrinshotdan "o'qib" topish uchun.
 * Faqat rasmga chizilgan (custom Canvas/o'yin kabi) matnlar uchun zaxira usul.
 */
object OcrFallback {
    fun findText(bitmap: Bitmap, query: String): OcrMatch? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = Tasks.await(recognizer.process(image), 10, TimeUnit.SECONDS)
            val q = query.lowercase()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    if (line.text.lowercase().contains(q)) {
                        val box = line.boundingBox ?: continue
                        return OcrMatch(line.text, box.exactCenterX(), box.exactCenterY())
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
