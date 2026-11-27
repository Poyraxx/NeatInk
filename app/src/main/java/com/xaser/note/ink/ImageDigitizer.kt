package com.xaser.note.ink

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xaser.note.model.PenType
import com.xaser.note.model.Stroke
import com.xaser.note.model.TextItem
import com.xaser.note.recognition.MathOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bir resmi düzenlenebilir içeriğe çevirir:
 *  - yazı ve rakamlar → metin kutuları (ML Kit Text Recognition, cihaz üzerinde, internetsiz)
 *  - şekil ve çizgiler → kalem çizgileri (ImageTracer; düzgün şekiller ShapeSnapper ile düzeltilir)
 * Sonuç, resmin sol üst köşesi (0,0) ve verilen genişlikte sayfa birimlerindedir.
 */
object ImageDigitizer {

    class Result(val texts: List<TextItem>, val strokes: List<Stroke>, val width: Float, val height: Float) {
        val isEmpty get() = texts.isEmpty() && strokes.isEmpty()
    }

    class Line(val text: String, val box: Rect, val syms: List<MathOcr.Sym> = emptyList())

    private suspend fun ocr(bmp: Bitmap): List<Line> = suspendCancellableCoroutine { cont ->
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val lines = t.textBlocks.flatMap { b ->
                    b.lines.mapNotNull { l ->
                        val box = l.boundingBox ?: return@mapNotNull null
                        // Karakter kutuları: üs/indis yerleşimini bulmak için
                        val syms = l.elements.flatMapIndexed { wi, e ->
                            e.symbols.mapNotNull { s -> s.boundingBox?.let { r -> MathOcr.Sym(s.text, r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), wi) } }
                        }
                        Line(l.text, box, syms)
                    }
                }
                client.close(); cont.resume(lines)
            }
            .addOnFailureListener { client.close(); cont.resume(emptyList()) }
    }

    /** Satır metnini matematiğe uygun hale getir: üs/indis, semboller, rakam karışmaları */
    private fun mathText(l: Line): String {
        val built = if (l.syms.isNotEmpty()) MathOcr.buildLine(l.syms) else l.text
        return MathOcr.fixDigits(MathOcr.normalize(MathOcr.stripLeadingNoise(built)))
    }

    /** Tek bir küçük bölgeyi büyütüp yeniden okur (virgül sanılan indis rakamı gibi) */
    private suspend fun reread(bmp: Bitmap, s: MathOcr.Sym): String? {
        val padX = s.h * 0.6f; val padY = s.h * 0.4f
        val l = (s.l - padX).toInt().coerceAtLeast(0); val t = (s.t - padY).toInt().coerceAtLeast(0)
        val r = (s.r + padX).toInt().coerceAtMost(bmp.width); val b = (s.b + padY).toInt().coerceAtMost(bmp.height)
        if (r - l < 2 || b - t < 2) return null
        val crop = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        val scale = (160f / crop.height).coerceIn(1f, 12f)
        // Kenarlara beyaz boşluk: tek karakter daha iyi okunur
        val big = Bitmap.createScaledBitmap(crop, (crop.width * scale).roundToInt(), (crop.height * scale).roundToInt(), true)
        val canvasBmp = Bitmap.createBitmap(big.width + 120, big.height + 120, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(canvasBmp).apply { drawColor(Color.WHITE); drawBitmap(big, 60f, 60f, null) }
        val got = ocr(canvasBmp).joinToString("") { it.text }.trim()
        return got.singleOrNull()?.takeIf { it.isLetterOrDigit() || it in "+-=()" }?.toString()
    }

    /** OCR sonrası: virgül sanılan indis/üsleri yeniden oku, ayrı satır sanılan üs/indisleri birleştir */
    private suspend fun refine(bmp: Bitmap, lines: List<Line>): List<Line> {
        val fixed = lines.map { line ->
            if (line.syms.isEmpty()) return@map line
            val syms = line.syms.map { s ->
                if (MathOcr.isSuspiciousScript(s, line.syms)) reread(bmp, s)?.let { MathOcr.Sym(it, s.l, s.t, s.r, s.b, s.word, script = true) } ?: s else s
            }
            Line(line.text, line.box, syms)
        }
        val data = fixed.map { MathOcr.LineData(it.syms.toMutableList(), it.box.left.toFloat(), it.box.top.toFloat(), it.box.right.toFloat(), it.box.bottom.toFloat()) }
        val merged = MathOcr.mergeScripts(data)
        return merged.map { d ->
            val orig = fixed[data.indexOf(d)]
            if (d.syms.size == orig.syms.size) orig
            else Line(orig.text, Rect(d.l.toInt(), orig.box.top, d.r.toInt(), orig.box.bottom), d.syms)
        }
    }

    private fun scaled(src: Bitmap, maxSide: Int): Bitmap {
        val k = min(1f, maxSide / max(src.width, src.height).toFloat())
        return if (k >= 1f) src else Bitmap.createScaledBitmap(src, (src.width * k).roundToInt(), (src.height * k).roundToInt(), true)
    }

    /**
     * @param targetWidth sayfada kaplayacağı genişlik (sayfa birimi)
     * @param inkColor yazı ve çizgi rengi
     * @param font metin kutularının yazı tipi
     * @param withText yazıları tanı, @param withShapes şekilleri izle
     */
    suspend fun digitize(src: Bitmap, targetWidth: Float, inkColor: Int, font: String,
                         withText: Boolean = true, withShapes: Boolean = true,
                         measure: (TextItem) -> Float = { it.text.length * it.size * 0.55f }): Result {
        // 1) Yazı tanıma (büyük resimde daha iyi)
        val ocrBmp = scaled(src, 2200)
        val lines = if (withText) refine(ocrBmp, ocr(ocrBmp)) else emptyList()
        return withContext(Dispatchers.Default) {
            val k = targetWidth / ocrBmp.width
            val texts = ArrayList<TextItem>()
            val barStrokes = ArrayList<Stroke>()
            for (l in lines) {
                val text = mathText(l)
                if (text.isBlank()) continue
                // "———" diye okunan satır aslında kesir çizgisi: yazı değil düz çizgi
                if (MathOcr.isBarText(text, l.box.width().toFloat(), l.box.height().toFloat())) {
                    val y = l.box.exactCenterY() * k
                    barStrokes.add(Stroke(PenType.BALLPOINT, inkColor, 3f, ShapeSnapper.line(l.box.left * k, y, l.box.right * k, y)))
                    continue
                }
                val hgt = l.box.height() * k
                texts.add(TextItem(text, l.box.left * k, l.box.bottom * k - hgt * 0.18f, (hgt * 0.92f).coerceAtLeast(12f), inkColor, font))
            }
            // 2) Şekil izleme (hız için en çok 1200 piksel)
            val strokes = if (!withShapes) emptyList() else {
                val tb = scaled(src, 1200)
                val w = tb.width; val h = tb.height
                val px = IntArray(w * h); tb.getPixels(px, 0, w, 0, 0, w, h)
                val gray = IntArray(w * h) { i ->
                    val c = px[i]
                    val a = Color.alpha(c)
                    val g = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    if (a < 128) 255 else g // saydam alan = beyaz
                }
                val r = w / ocrBmp.width.toFloat()
                val masks = lines.map { l -> intArrayOf((l.box.left * r).toInt(), (l.box.top * r).toInt(), (l.box.right * r).toInt(), (l.box.bottom * r).toInt()) }
                val kk = targetWidth / w
                ImageTracer.trace(gray, w, h, masks).map { t ->
                    val p = t.points.copyOf()
                    var i = 0
                    while (i < p.size) { p[i] *= kk; p[i + 1] *= kk; i += 3 }
                    Stroke(PenType.BALLPOINT, inkColor, (t.thickness * kk).coerceIn(1.5f, 10f), p)
                }
            }
            val allStrokes = strokes + barStrokes
            // Kesir: yatay çizginin üstündeki ve altındaki yazıları çizginin ortasına hizala
            val bars = allStrokes.filter { s ->
                val n = s.points.size
                n >= 6 && kotlin.math.abs(s.points[n - 2] - s.points[1]) < 0.12f * kotlin.math.abs(s.points[n - 3] - s.points[0]) &&
                    com.xaser.note.recognition.MathLayout.straightness(s) > 0.95f
            }.map { s -> val n = s.points.size; Triple(minOf(s.points[0], s.points[n - 3]), maxOf(s.points[0], s.points[n - 3]), (s.points[1] + s.points[n - 2]) / 2) }
            if (bars.isNotEmpty() && texts.isNotEmpty()) {
                val items = texts.map { t -> MathOcr.Item(t.x, t.y - t.size * 0.8f, t.x + measure(t), t.y + t.size * 0.2f) }
                val moved = MathOcr.centerOverBars(items, bars, MathOcr.unit(items))
                moved.forEach { (i, x) -> texts[i].x = x }
            }
            Result(texts, allStrokes, targetWidth, src.height * targetWidth / src.width)
        }
    }
}
