package com.xaser.note.recognition

import android.graphics.Paint
import com.xaser.note.L
import android.graphics.RectF
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.xaser.note.ink.PageRenderer
import com.xaser.note.model.Stroke
import com.xaser.note.model.TextItem
import kotlin.math.max
import kotlin.math.min

/**
 * Google ML Kit el yazısı tanıma (Türkçe). Model bir kez indirilir (~20MB), sonra internetsiz çalışır.
 */
class InkRecognizer(private val languageTag: String = "tr") {
    enum class State { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    var state = State.NOT_DOWNLOADED
        private set
    var lastError: String? = null
        private set

    private val model: DigitalInkRecognitionModel? = runCatching {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)?.let { DigitalInkRecognitionModel.builder(it).build() }
    }.getOrNull()
    private var recognizer: DigitalInkRecognizer? = null
    private val manager = RemoteModelManager.getInstance()
    private val waiting = mutableListOf<(Boolean) -> Unit>()

    fun ensureModel(onDone: (Boolean) -> Unit) {
        val m = model ?: run { state = State.ERROR; lastError = L("El yazısı modeli bulunamadı ($languageTag)", "Handwriting model not found ($languageTag)"); onDone(false); return }
        if (state == State.READY) { onDone(true); return }
        waiting.add(onDone)
        if (state == State.DOWNLOADING) return
        state = State.DOWNLOADING
        manager.isModelDownloaded(m).addOnSuccessListener { downloaded ->
            if (downloaded) ready(m)
            else manager.download(m, DownloadConditions.Builder().build())
                .addOnSuccessListener { ready(m) }
                .addOnFailureListener { fail(it.message ?: L("İndirme başarısız (internet gerekli, sadece ilk sefer)", "Download failed (internet needed, first time only)")) }
        }.addOnFailureListener { fail(it.message ?: L("Model kontrol edilemedi", "Could not check the model")) }
    }

    private fun ready(m: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
        state = State.READY
        val w = waiting.toList(); waiting.clear(); w.forEach { it(true) }
    }

    private fun fail(msg: String) {
        state = State.ERROR; lastError = msg
        val w = waiting.toList(); waiting.clear(); w.forEach { it(false) }
    }

    /** Verilen çizgileri tanır, en iyi adayları (en fazla 5) döndürür. */
    fun recognize(strokes: List<Stroke>, area: RectF, preContext: String, onResult: (List<String>) -> Unit) {
        ensureModel { ok ->
            val r = recognizer
            if (!ok || r == null) { onResult(emptyList()); return@ensureModel }
            val ink = Ink.builder()
            var t = 0L
            for (s in strokes) {
                val sb = Ink.Stroke.builder()
                var i = 0
                while (i < s.points.size) {
                    sb.addPoint(Ink.Point.create(s.points[i], s.points[i + 1], t)); t += 8; i += 3
                }
                t += 120
                ink.addStroke(sb.build())
            }
            val ctx = RecognitionContext.builder()
                .setPreContext(preContext.takeLast(20))
                .setWritingArea(WritingArea(max(1f, area.width()), max(1f, area.height())))
                .build()
            r.recognize(ink.build(), ctx)
                .addOnSuccessListener { res -> onResult(res.candidates.map { it.text }.distinct().take(5)) }
                .addOnFailureListener { onResult(emptyList()) }
        }
    }

    /** Coroutine içinden tanıma */
    suspend fun recognizeAwait(strokes: List<Stroke>, area: RectF, preContext: String): List<String> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            recognize(strokes, area, preContext) { if (cont.isActive) cont.resume(it) { } }
        }

    private var warmed = false

    /** İlk tanıma yavaştır (model belleğe yüklenir); boş bir tanıma ile önceden yükle */
    fun warmUp() {
        if (warmed) return
        warmed = true
        val s = com.xaser.note.model.Stroke(com.xaser.note.model.PenType.BALLPOINT, 0, 2f,
            floatArrayOf(0f, 0f, .5f, 10f, 10f, .5f, 20f, 0f, .5f))
        recognize(listOf(s), android.graphics.RectF(0f, 0f, 200f, 40f), "") { }
    }

    fun close() { recognizer?.close() }
}

/** El yazısını satırlara ayırır ve tanınan metni yerleştirir. */
object Beautifier {

    class Line(val strokes: List<Stroke>, val bounds: RectF)

    /** Çizgileri dikey örtüşmeye göre satırlara gruplar (yukarıdan aşağı). */
    fun splitLines(strokes: List<Stroke>): List<Line> {
        val items = strokes.map { it to it.bounds() }.sortedBy { (it.second[1] + it.second[3]) / 2 }
        val lines = mutableListOf<MutableList<Pair<Stroke, FloatArray>>>()
        val lb = mutableListOf<RectF>()
        for (it in items) {
            val b = it.second
            val cy = (b[1] + b[3]) / 2
            val idx = lb.indexOfFirst { r ->
                val h = r.height()
                cy > r.top - h * 0.15f && cy < r.bottom + h * 0.15f
            }
            if (idx >= 0 && (b[3] - b[1]) < lb[idx].height() * 2.5f) {
                lines[idx].add(it)
                lb[idx].union(b[0], b[1], b[2], b[3])
            } else {
                lines.add(mutableListOf(it)); lb.add(RectF(b[0], b[1], b[2], b[3]))
            }
        }
        return lines.indices.map { i -> Line(lines[i].map { it.first }.sortedBy { it.bounds()[0] }, lb[i]) }
            .sortedBy { it.bounds.top }
    }

    /**
     * Tanınan metin için, orijinal el yazısının yerini ve boyutunu taklit eden bir metin kutusu oluşturur.
     */
    fun place(text: String, line: Line, color: Int, font: String, renderer: PageRenderer): TextItem {
        val b = line.bounds
        // Kalem kalınlığı payını çıkar
        val pad = line.strokes.maxOfOrNull { it.width } ?: 0f
        val top = b.top + pad; val bottom = b.bottom - pad
        val left = b.left + pad; val right = b.right - pad
        val h = max(10f, bottom - top)
        val item = TextItem(text, left, bottom, h, color, font)
        val p: Paint = renderer.textPaintFor(item)
        // Yükseklik: büyük harf/çıkıntı yüksekliği el yazısı yüksekliğine eşit olsun
        val fm = p.fontMetrics
        val glyphH = (-fm.ascent) * 0.85f
        var size = h * h / max(1f, glyphH)
        item.size = size
        // Genişliği de orantılı tut: aşırı geniş/dar olmasın
        val w = renderer.textPaintFor(item).measureText(text)
        val targetW = max(1f, right - left)
        if (w > targetW * 1.35f) size *= targetW * 1.35f / w
        if (w < targetW * 0.6f) size *= min(1.6f, targetW * 0.6f / w)
        item.size = size.coerceIn(14f, 400f)
        // Taban çizgisi: inen harf (g, y, p) yoksa alt kenar, varsa biraz yukarısı
        val desc = text.any { it in "gjpqyşçğ" }
        item.y = if (desc) bottom - renderer.textPaintFor(item).descent() * 0.8f else bottom
        return item
    }
}
