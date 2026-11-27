package com.xaser.note.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.xaser.note.ink.PageRenderer
import com.xaser.note.model.InkDocument
import com.xaser.note.model.PAGE_WIDTH
import com.xaser.note.model.Page
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object PdfImporter {
    /** PDF'i not klasörüne kopyalar ve her PDF sayfası için bir not sayfası oluşturur. */
    fun import(context: Context, uri: Uri, target: File): InkDocument {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(target).use { input.copyTo(it) }
        }
        val doc = InkDocument()
        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p ->
                        doc.pages.add(Page(pdfIndex = i, baseHeight = PAGE_WIDTH * p.height / p.width.toFloat()))
                    }
                }
            }
        }
        return doc
    }

    fun displayName(context: Context, uri: Uri): String {
        var name = "PDF"
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
        }
        return name.removeSuffix(".pdf").removeSuffix(".PDF")
    }
}

/**
 * PDF sayfalarını arka planda, yakınlaştırma düzeyine uygun çözünürlükte bitmap'e çizer.
 * PdfRenderer iş parçacığı güvenli olmadığı için tek bir iş parçacığı kullanılır.
 */
class PdfPageCache(file: File, private val onReady: () -> Unit) {
    private val thread = HandlerThread("pdf-render").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pending = HashSet<String>()

    /** Sayfa no -> en son üretilmiş bitmap (çözünürlük fark etmeksizin) */
    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    init {
        handler.post {
            runCatching {
                fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fd!!)
            }
        }
    }

    /** İstenen genişlikte (piksel) bitmap; yoksa eldeki en iyi bitmap döner ve arka planda üretilir. */
    fun get(index: Int, widthPx: Int): Bitmap? {
        val want = bucket(widthPx)
        val have = cache.get(index)
        if (have == null || have.width != want) request(index, want)
        return have
    }

    private fun bucket(w: Int): Int {
        // 256 piksellik basamaklar, en fazla 3000 piksel
        return ((w / 256f).roundToInt().coerceIn(2, 12)) * 256
    }

    private fun request(index: Int, width: Int) {
        val key = "$index:$width"
        synchronized(pending) { if (!pending.add(key)) return }
        handler.post {
            val bmp = renderSync(index, width)
            synchronized(pending) { pending.remove(key) }
            if (bmp != null) main.post { cache.put(index, bmp); onReady() }
        }
    }

    /** Sadece render iş parçacığında çağrılmalı */
    private fun renderSync(index: Int, width: Int): Bitmap? {
        val r = renderer ?: return null
        return runCatching {
            r.openPage(index).use { p ->
                val h = max(1, (width * p.height / p.width.toFloat()).roundToInt())
                val b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                b
            }
        }.getOrNull()
    }

    /** Dışa aktarma/kırpma için eşzamanlı (çağıranı bekletir) render */
    fun renderBlocking(index: Int, width: Int): Bitmap? {
        if (Looper.myLooper() == thread.looper) return renderSync(index, width)
        var out: Bitmap? = null
        val lock = Object()
        var done = false
        handler.post {
            out = renderSync(index, width)
            synchronized(lock) { done = true; lock.notifyAll() }
        }
        synchronized(lock) { while (!done) lock.wait() }
        return out
    }

    /** Orijinal PDF sayfa boyutu (pt) */
    fun pageSizePt(index: Int): Pair<Int, Int>? {
        var out: Pair<Int, Int>? = null
        val lock = Object(); var done = false
        handler.post {
            out = runCatching { renderer!!.openPage(index).use { it.width to it.height } }.getOrNull()
            synchronized(lock) { done = true; lock.notifyAll() }
        }
        synchronized(lock) { while (!done) lock.wait() }
        return out
    }

    fun close() {
        handler.post {
            runCatching { renderer?.close(); fd?.close() }
            thread.quitSafely()
        }
    }
}

object PdfExporter {
    /**
     * Notu PDF olarak yazar: PDF arka planı (yüksek çözünürlük) + çizgiler vektör olarak + genişletilmiş kenarlar.
     * Uzun sürebilir: arka plan iş parçacığında çağırın.
     */
    fun export(
        doc: InkDocument,
        noteDir: File,
        pdfCache: PdfPageCache?,
        renderer: PageRenderer,
        out: File,
        pageRange: IntRange = doc.pages.indices,
    ) {
        val pdf = PdfDocument()
        try {
            for ((n, i) in pageRange.withIndex()) {
                val page = doc.pages[i]
                // Ölçek: sayfa birimi -> pt. PDF sayfasıysa orijinal boyutu koru, değilse A4 (595pt)
                val ptPerUnit = if (page.pdfIndex >= 0 && pdfCache != null) {
                    val sz = pdfCache.pageSizePt(page.pdfIndex)
                    (sz?.first ?: 595) / PAGE_WIDTH
                } else 595f / PAGE_WIDTH
                val wPt = (page.width * ptPerUnit).roundToInt()
                val hPt = (page.height * ptPerUnit).roundToInt()
                val info = PdfDocument.PageInfo.Builder(wPt, hPt, n + 1).create()
                val p = pdf.startPage(info)
                val c: Canvas = p.canvas
                c.drawColor(Color.WHITE)
                c.save()
                c.scale(ptPerUnit, ptPerUnit)
                if (page.pdfIndex >= 0 && pdfCache != null) {
                    // 2x çözünürlük (≈150 dpi) net yazdırma için yeterli
                    val bmp = pdfCache.renderBlocking(page.pdfIndex, (wPt * 2.2f).roundToInt().coerceAtMost(2600))
                    if (bmp != null) c.drawBitmap(bmp, null, RectF(0f, 0f, PAGE_WIDTH, page.baseHeight), null)
                }
                renderer.drawPage(c, page, noteDir)
                c.restore()
                pdf.finishPage(p)
            }
            FileOutputStream(out).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }
}
