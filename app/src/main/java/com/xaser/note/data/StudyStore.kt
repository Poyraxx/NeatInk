package com.xaser.note.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

/** Bir çalışma kaydı: Pomodoro oturumu ya da elle eklenen soru sayısı */
class StudyEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val subject: String,
    val minutes: Int,
    var questions: Int = 0,
    var correct: Int = 0,
    var wrong: Int = 0,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("t", time); put("s", subject); put("m", minutes); put("q", questions); put("d", correct); put("y", wrong)
    }

    companion object {
        fun fromJson(o: JSONObject) = StudyEntry(o.getString("id"), o.getLong("t"), o.optString("s"), o.optInt("m"),
            o.optInt("q"), o.optInt("d"), o.optInt("y"))
    }
}

/** files/notes/_study/study.json — yedeklemeye otomatik dahil */
class StudyStore(notesRoot: File) {
    val dir = File(notesRoot, "_study").apply { mkdirs() }
    private val file = File(dir, "study.json")
    val entries = mutableListOf<StudyEntry>()

    init { load() }

    fun load() {
        entries.clear()
        runCatching {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) entries.add(StudyEntry.fromJson(a.getJSONObject(i)))
        }
    }

    @Synchronized
    fun save() {
        val tmp = File(dir, "study.json.tmp")
        tmp.writeText(JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun add(e: StudyEntry) { entries.add(e); save() }

    fun remove(e: StudyEntry) { entries.remove(e); save() }

    /** Yedekten gelen kayıtları ekler (aynı kimliktekileri atlar) */
    fun merge(other: List<StudyEntry>): Int {
        val have = entries.map { it.id }.toHashSet()
        val add = other.filter { it.id !in have }
        entries.addAll(add); entries.sortBy { it.time }; save()
        return add.size
    }

    companion object {
        fun dayStart(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun readFile(f: File): List<StudyEntry> = runCatching {
            val a = JSONArray(f.readText()); (0 until a.length()).map { StudyEntry.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Belirli günün toplamları */
    fun dayTotals(day: Long): Pair<Int, Int> {
        val s = dayStart(day); val e = s + 86_400_000L
        val list = entries.filter { it.time in s until e }
        return list.sumOf { it.minutes } to list.sumOf { it.questions }
    }

    /** Ardışık çalışılan gün sayısı (bugün veya dün dahil) */
    fun streak(now: Long = System.currentTimeMillis()): Int {
        val days = entries.filter { it.minutes > 0 || it.questions > 0 }.map { dayStart(it.time) }.toHashSet()
        var d = dayStart(now)
        if (d !in days) d -= 86_400_000L
        var n = 0
        while (d in days) { n++; d -= 86_400_000L }
        return n
    }
}
