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

    class Line(val text: String, val box: Rect)

    private suspend fun ocr(bmp: Bitmap): List<Line> = suspendCancellableCoroutine { cont ->
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val lines = t.textBlocks.flatMap { b -> b.lines.mapNotNull { l -> l.boundingBox?.let { Line(l.text, it) } } }
                client.close(); cont.resume(lines)
            }
            .addOnFailureListener { client.close(); cont.resume(emptyList()) }
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
                         withText: Boolean = true, withShapes: Boolean = true): Result {
        // 1) Yazı tanıma (büyük resimde daha iyi)
        val ocrBmp = scaled(src, 2200)
        val lines = if (withText) ocr(ocrBmp) else emptyList()
        return withContext(Dispatchers.Default) {
            val k = targetWidth / ocrBmp.width
            val texts = lines.filter { it.text.isNotBlank() }.map { l ->
                val hgt = l.box.height() * k
                TextItem(l.text, l.box.left * k, l.box.bottom * k - hgt * 0.18f, (hgt * 0.92f).coerceAtLeast(12f), inkColor, font)
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
            Result(texts, strokes, targetWidth, src.height * targetWidth / src.width)
        }
    }
}
