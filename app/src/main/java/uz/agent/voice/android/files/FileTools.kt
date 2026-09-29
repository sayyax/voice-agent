package uz.agent.voice.android.files

import android.os.Environment
import java.io.File

/**
 * Fayl boshqaruvi (topish, ko'chirish, nomini o'zgartirish).
 * MANAGE_EXTERNAL_STORAGE ruxsati kerak (Sozlamalarda qo'lda beriladi).
 * Faqat ulashilgan xotirada (Download/Documents/DCIM/Pictures) ishlaydi -
 * boshqa ilovalarning (masalan Termuxning) shaxsiy papkasiga to'g'ridan-to'g'ri kira olmaydi.
 */
object FileTools {
    private val searchDirs: List<File>
        get() = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        )

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    fun find(nameQuery: String): File? {
        val q = nameQuery.lowercase()
        for (dir in searchDirs) {
            val hit = dir.listFiles()?.firstOrNull { it.isFile && it.name.lowercase().contains(q) }
            if (hit != null) return hit
        }
        return null
    }

    /** Download papkasi ichida (kerak bo'lsa) pastki papka yaratib, faylni shu yerga ko'chiradi. */
    fun moveToDownloadSubfolder(file: File, subfolder: String?): File? {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destDir = if (subfolder.isNullOrBlank()) base else File(base, subfolder)
        if (!destDir.exists() && !destDir.mkdirs()) return null
        val dest = File(destDir, file.name)
        return try {
            file.copyTo(dest, overwrite = true)
            if (dest.exists()) {
                file.delete()
                dest
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun rename(file: File, newName: String): File? {
        val dest = File(file.parentFile, newName)
        return if (file.renameTo(dest)) dest else null
    }
}
