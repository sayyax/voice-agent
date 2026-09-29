package uz.agent.voice.android.contacts

import android.content.Context
import android.provider.ContactsContract

data class Contact(val name: String, val number: String)

/** Telefon kitobidan ism bo'yicha qidirish. READ_CONTACTS ruxsati kerak. */
object ContactsLookup {
    fun find(ctx: Context, query: String): List<Contact> {
        val out = LinkedHashMap<String, Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        val args = arrayOf("%$query%")
        try {
            ctx.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx) ?: continue
                    val number = c.getString(numIdx) ?: continue
                    out["$name|$number"] = Contact(name, number)
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out.values.toList()
    }
}
