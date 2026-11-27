package com.xaser.note.data

import com.xaser.note.model.NoteMeta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Tüm notları (PDF'ler, resimler, hata defteri dahil) tek bir .zip dosyasına yedekler / geri yükler.
 * Geri yüklemede mevcut notların üzerine yazılmaz; yalnızca cihazda olmayan notlar eklenir.
 */
object Backup {

    fun export(store: NoteStore, out: OutputStream) {
        val root = store.root
        ZipOutputStream(out.buffered()).use { zip ->
            root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                val rel = f.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    class Result(val notes: Int, val mistakes: Int)

    fun import(store: NoteStore, mistakes: MistakeStore, input: InputStream, tempDir: File): Result {
        val tmp = File(tempDir, "restore_" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            val canonRoot = tmp.canonicalPath + File.separator
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val target = File(tmp, e.name)
                    // Zip içindeki "../" ile dışarı yazmayı engelle
                    if (!target.canonicalPath.startsWith(canonRoot)) throw SecurityException("Geçersiz yedek dosyası")
                    if (e.isDirectory) target.mkdirs()
                    else { target.parentFile?.mkdirs(); target.outputStream().use { zip.copyTo(it) } }
                    e = zip.nextEntry
                }
            }
            val index = File(tmp, "index.json")
            require(index.exists()) { "Bu bir NeatInk yedeği değil" }
            val o = JSONObject(index.readText())

            // Klasörler
            o.optJSONArray("folders")?.let { f -> for (i in 0 until f.length()) { val n = f.getString(i); if (n !in store.folders) store.folders.add(n) } }

            // Notlar: cihazda olmayanları ekle
            var addedNotes = 0
            val a = o.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until a.length()) {
                val meta = NoteMeta.fromJson(a.getJSONObject(i))
                if (store.get(meta.id) != null) continue
                val src = File(tmp, meta.id)
                if (src.isDirectory) src.copyRecursively(File(store.root, meta.id), overwrite = true)
                store.notes.add(meta)
                addedNotes++
            }
            store.notes.sortByDescending { it.updated }
            store.saveIndex()

            // Hata defteri
            var addedMistakes = 0
            val mf = File(tmp, "_mistakes/mistakes.json")
            if (mf.exists()) {
                val ma = JSONArray(mf.readText())
                val have = mistakes.items.map { it.id }.toHashSet()
                for (i in 0 until ma.length()) {
                    val m = Mistake.fromJson(ma.getJSONObject(i))
                    if (m.id in have) continue
                    File(tmp, "_mistakes/" + m.image).takeIf { it.exists() }?.copyTo(File(mistakes.dir, m.image), overwrite = true)
                    mistakes.items.add(m)
                    addedMistakes++
                }
                mistakes.items.sortByDescending { it.created }
                mistakes.save()
            }
            return Result(addedNotes, addedMistakes)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
