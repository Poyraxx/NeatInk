package com.xaser.note.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NoteType { TEXT, INK, PDF }

enum class PageTemplate { BLANK, LINED, GRID, DOTS }

enum class PenType { BALLPOINT, FOUNTAIN, PENCIL, MARKER, HIGHLIGHTER }

/** Sayfa koordinatları: sayfa genişliği her zaman PAGE_WIDTH birimdir. */
const val PAGE_WIDTH = 1000f
const val A4_HEIGHT = 1414f

class Stroke(
    val pen: PenType,
    var color: Int,
    var width: Float,
    /** x, y, basınç üçlüleri */
    var points: FloatArray,
) {
    val pointCount get() = points.size / 3

    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var i = 0
        while (i < points.size) {
            val x = points[i]; val y = points[i + 1]
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            i += 3
        }
        val pad = width
        return floatArrayOf(l - pad, t - pad, r + pad, b + pad)
    }

    fun copy() = Stroke(pen, color, width, points.copyOf())

    fun toJson(): JSONObject = JSONObject().apply {
        put("pen", pen.name); put("color", color); put("w", width.toDouble())
        val a = JSONArray()
        points.forEach { a.put(Math.round(it * 100) / 100.0) }
        put("p", a)
    }

    companion object {
        fun fromJson(o: JSONObject): Stroke {
            val a = o.getJSONArray("p")
            val pts = FloatArray(a.length()) { a.getDouble(it).toFloat() }
            return Stroke(PenType.valueOf(o.getString("pen")), o.getInt("color"), o.getDouble("w").toFloat(), pts)
        }
    }
}

class ImageItem(
    val file: String,
    var x: Float, var y: Float, var w: Float, var h: Float,
) {
    fun copy() = ImageItem(file, x, y, w, h)
    fun toJson() = JSONObject().apply {
        put("file", file); put("x", x.toDouble()); put("y", y.toDouble()); put("w", w.toDouble()); put("h", h.toDouble())
    }
    companion object {
        fun fromJson(o: JSONObject) = ImageItem(
            o.getString("file"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
            o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
        )
    }
}

class TextItem(
    var text: String,
    var x: Float, var y: Float,
    var size: Float,
    var color: Int,
    /** Yazı tipi kimliği (NoteFonts) */
    var font: String,
) {
    fun copy() = TextItem(text, x, y, size, color, font)
    fun toJson() = JSONObject().apply {
        put("t", text); put("x", x.toDouble()); put("y", y.toDouble()); put("s", size.toDouble())
        put("c", color); put("f", font)
    }
    companion object {
        fun fromJson(o: JSONObject) = TextItem(
            o.getString("t"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
            o.getDouble("s").toFloat(), o.getInt("c"),
            // eski kayıtlar: hw=true → Kalam, değilse düz yazı
            o.optString("f").ifEmpty { if (o.optBoolean("hw", false)) NoteFonts.DEFAULT_FIX else NoteFonts.PLAIN },
        )
    }
}

class Page(
    var template: PageTemplate = PageTemplate.BLANK,
    /** PDF sayfası ise o PDF'teki sayfa no, değilse -1 */
    var pdfIndex: Int = -1,
    /** PDF sayfasının (veya boş sayfanın) içerik yüksekliği, sayfa biriminde */
    var baseHeight: Float = A4_HEIGHT,
    /** Kenar genişletme: sağa ve alta eklenen boşluk */
    var extraRight: Float = 0f,
    var extraBottom: Float = 0f,
    val strokes: MutableList<Stroke> = mutableListOf(),
    val images: MutableList<ImageItem> = mutableListOf(),
    val texts: MutableList<TextItem> = mutableListOf(),
) {
    val width get() = PAGE_WIDTH + extraRight

    fun deepCopy() = Page(
        template, pdfIndex, baseHeight, extraRight, extraBottom,
        strokes.map { it.copy() }.toMutableList(),
        images.map { it.copy() }.toMutableList(),
        texts.map { it.copy() }.toMutableList(),
    )

    fun restoreFrom(o: Page) {
        template = o.template; pdfIndex = o.pdfIndex; baseHeight = o.baseHeight
        extraRight = o.extraRight; extraBottom = o.extraBottom
        strokes.clear(); strokes.addAll(o.strokes)
        images.clear(); images.addAll(o.images)
        texts.clear(); texts.addAll(o.texts)
    }
    val height get() = baseHeight + extraBottom

    fun toJson() = JSONObject().apply {
        put("tpl", template.name); put("pdf", pdfIndex); put("bh", baseHeight.toDouble())
        put("er", extraRight.toDouble()); put("eb", extraBottom.toDouble())
        put("strokes", JSONArray().apply { strokes.forEach { put(it.toJson()) } })
        put("images", JSONArray().apply { images.forEach { put(it.toJson()) } })
        put("texts", JSONArray().apply { texts.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Page {
            val p = Page(
                PageTemplate.valueOf(o.optString("tpl", "BLANK")), o.optInt("pdf", -1),
                o.optDouble("bh", A4_HEIGHT.toDouble()).toFloat(),
                o.optDouble("er", 0.0).toFloat(), o.optDouble("eb", 0.0).toFloat(),
            )
            o.optJSONArray("strokes")?.let { a -> for (i in 0 until a.length()) p.strokes.add(Stroke.fromJson(a.getJSONObject(i))) }
            o.optJSONArray("images")?.let { a -> for (i in 0 until a.length()) p.images.add(ImageItem.fromJson(a.getJSONObject(i))) }
            o.optJSONArray("texts")?.let { a -> for (i in 0 until a.length()) p.texts.add(TextItem.fromJson(a.getJSONObject(i))) }
            return p
        }
    }
}

/** Liste ekranında gösterilen özet bilgi */
data class NoteMeta(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var type: NoteType = NoteType.INK,
    var folder: String = "",
    var created: Long = System.currentTimeMillis(),
    var updated: Long = System.currentTimeMillis(),
    var pinned: Boolean = false,
    /** Metin notları için içerik; diğerleri için kısa önizleme */
    var body: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("type", type.name); put("folder", folder)
        put("created", created); put("updated", updated); put("pinned", pinned); put("body", body)
    }
    companion object {
        fun fromJson(o: JSONObject) = NoteMeta(
            o.getString("id"), o.optString("title"), NoteType.valueOf(o.optString("type", "INK")),
            o.optString("folder"), o.optLong("created"), o.optLong("updated"), o.optBoolean("pinned"),
            o.optString("body"),
        )
    }
}

class InkDocument(val pages: MutableList<Page> = mutableListOf()) {
    fun toJson() = JSONObject().apply { put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } }) }
    companion object {
        fun fromJson(o: JSONObject): InkDocument {
            val d = InkDocument()
            val a = o.getJSONArray("pages")
            for (i in 0 until a.length()) d.pages.add(Page.fromJson(a.getJSONObject(i)))
            return d
        }
    }
}
