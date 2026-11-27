package com.xaser.note.data

import android.content.Context
import android.graphics.Bitmap
import com.xaser.note.model.InkDocument
import com.xaser.note.model.NoteMeta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Basit dosya tabanlı depolama:
 *   files/notes/index.json           -> tüm notların özetleri + klasörler
 *   files/notes/<id>/doc.json        -> sayfalar, çizgiler
 *   files/notes/<id>/source.pdf      -> içe aktarılan PDF
 *   files/notes/<id>/img_*.png       -> eklenen resimler / kırpılan sorular
 *   files/notes/<id>/thumb.png       -> liste önizlemesi
 */
class NoteStore(context: Context) {
    val root = File(context.filesDir, "notes").apply { mkdirs() }
    private val indexFile = File(root, "index.json")

    val notes = mutableListOf<NoteMeta>()
    val folders = mutableListOf<String>()

    init { load() }

    private fun load() {
        notes.clear(); folders.clear()
        if (!indexFile.exists()) return
        runCatching {
            val o = JSONObject(indexFile.readText())
            val a = o.getJSONArray("notes")
            for (i in 0 until a.length()) notes.add(NoteMeta.fromJson(a.getJSONObject(i)))
            o.optJSONArray("folders")?.let { f -> for (i in 0 until f.length()) folders.add(f.getString(i)) }
        }
    }

    @Synchronized
    fun saveIndex() {
        val o = JSONObject()
        o.put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
        o.put("folders", JSONArray().apply { folders.forEach { put(it) } })
        val tmp = File(root, "index.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(indexFile).also { if (!it) { indexFile.delete(); tmp.renameTo(indexFile) } }
    }

    fun dir(id: String) = File(root, id).apply { mkdirs() }
    fun pdfFile(id: String) = File(dir(id), "source.pdf")
    fun thumbFile(id: String) = File(dir(id), "thumb.png")
    fun imageFile(id: String, name: String) = File(dir(id), name)

    fun newImageName() = "img_" + UUID.randomUUID().toString().take(8) + ".png"

    fun saveBitmap(id: String, bmp: Bitmap): String {
        val name = newImageName()
        imageFile(id, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun get(id: String) = notes.firstOrNull { it.id == id }

    fun add(meta: NoteMeta) { notes.add(0, meta); saveIndex() }

    fun touch(meta: NoteMeta) { meta.updated = System.currentTimeMillis(); saveIndex() }

    fun delete(id: String) {
        notes.removeAll { it.id == id }
        File(root, id).deleteRecursively()
        saveIndex()
    }

    fun loadDoc(id: String): InkDocument {
        val f = File(dir(id), "doc.json")
        if (!f.exists()) return InkDocument()
        return runCatching { InkDocument.fromJson(JSONObject(f.readText())) }.getOrElse { InkDocument() }
    }

    @Synchronized
    fun saveDoc(id: String, doc: InkDocument) {
        val f = File(dir(id), "doc.json")
        val tmp = File(dir(id), "doc.json.tmp")
        tmp.writeText(doc.toJson().toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun saveThumb(id: String, bmp: Bitmap) {
        thumbFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
}
