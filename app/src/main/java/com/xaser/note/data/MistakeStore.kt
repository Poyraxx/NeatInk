package com.xaser.note.data

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Hata defterindeki bir soru */
class Mistake(
    val id: String = UUID.randomUUID().toString(),
    /** Sorunun resmi (çözümsüz hali), files/notes/_mistakes/ altında */
    val image: String,
    var subject: String,
    var topic: String = "",
    val sourceNoteId: String = "",
    val sourceTitle: String = "",
    /** Kaynak nottaki sayfa (0'dan başlar), yoksa -1 */
    val sourcePage: Int = -1,
    /** Denemeden geldiyse soru numarası ve doğru cevap */
    val questionNo: Int = 0,
    val correctAnswer: String = "",
    val created: Long = System.currentTimeMillis(),
    var learned: Boolean = false,
    var reviews: Int = 0,
    var lastReview: Long = 0,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("img", image); put("subj", subject); put("topic", topic)
        put("src", sourceNoteId); put("srcTitle", sourceTitle); put("page", sourcePage)
        put("q", questionNo); put("ans", correctAnswer); put("created", created)
        put("learned", learned); put("reviews", reviews); put("last", lastReview)
    }

    companion object {
        fun fromJson(o: JSONObject) = Mistake(
            o.getString("id"), o.getString("img"), o.optString("subj"), o.optString("topic"),
            o.optString("src"), o.optString("srcTitle"), o.optInt("page", -1), o.optInt("q"), o.optString("ans"),
            o.optLong("created"), o.optBoolean("learned"), o.optInt("reviews"), o.optLong("last"),
        )
    }
}

/** Hata defteri: files/notes/_mistakes/mistakes.json + resimler */
class MistakeStore(notesRoot: File) {
    val dir = File(notesRoot, "_mistakes").apply { mkdirs() }
    private val file = File(dir, "mistakes.json")
    val items = mutableListOf<Mistake>()

    init { load() }

    fun load() {
        items.clear()
        if (!file.exists()) return
        runCatching {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) items.add(Mistake.fromJson(a.getJSONObject(i)))
        }
    }

    @Synchronized
    fun save() {
        val a = JSONArray().apply { items.forEach { put(it.toJson()) } }
        val tmp = File(dir, "mistakes.json.tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun imageFile(m: Mistake) = File(dir, m.image)

    fun add(bmp: Bitmap, subject: String, topic: String, sourceNoteId: String, sourceTitle: String,
            sourcePage: Int, questionNo: Int = 0, correctAnswer: String = ""): Mistake {
        val name = "m_" + UUID.randomUUID().toString().take(10) + ".png"
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        val m = Mistake(image = name, subject = subject, topic = topic, sourceNoteId = sourceNoteId,
            sourceTitle = sourceTitle, sourcePage = sourcePage, questionNo = questionNo, correctAnswer = correctAnswer)
        items.add(0, m)
        save()
        return m
    }

    fun delete(m: Mistake) {
        items.remove(m)
        imageFile(m).delete()
        save()
    }

    val subjects: List<String> get() = items.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()
    val openCount: Int get() = items.count { !it.learned }
}
