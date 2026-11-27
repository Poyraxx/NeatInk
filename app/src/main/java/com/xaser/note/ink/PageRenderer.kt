package com.xaser.note.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.xaser.note.R
import com.xaser.note.model.Page
import com.xaser.note.model.PageTemplate
import com.xaser.note.model.PenType
import com.xaser.note.model.Stroke
import com.xaser.note.model.TextItem
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Bir sayfanın içeriğini (şablon, resim, çizgi, metin) sayfa koordinatlarında çizer. */
class PageRenderer(private val context: Context) {

    val handTypeface: Typeface = ResourcesCompat.getFont(context, R.font.kalam) ?: Typeface.DEFAULT
    private val plainTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    private val templatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD6DCE4.toInt(); strokeWidth = 1.2f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC2C9D2.toInt() }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val imageCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun drawTemplate(c: Canvas, page: Page, clip: RectF? = null) {
        val w = page.width; val h = page.height
        val top = if (page.pdfIndex >= 0) page.baseHeight else 0f
        val t = page.template
        if (t == PageTemplate.BLANK) return
        val step = if (t == PageTemplate.LINED) 40f else 36f
        val y0 = max(top, clip?.top ?: top)
        when (t) {
            PageTemplate.LINED -> {
                var y = top + 90f
                while (y < h) { if (y >= y0 - step) c.drawLine(40f, y, w - 40f, y, templatePaint); y += step }
            }
            PageTemplate.GRID -> {
                var y = top + step
                while (y < h) { c.drawLine(0f, y, w, y, templatePaint); y += step }
                var x = step
                while (x < w) { c.drawLine(x, top, x, h, templatePaint); x += step }
            }
            PageTemplate.DOTS -> {
                var y = top + step
                while (y < h) {
                    var x = step
                    while (x < w) { c.drawCircle(x, y, 2f, dotPaint); x += step }
                    y += step
                }
            }
            else -> {}
        }
    }

    fun loadImage(file: File): Bitmap? {
        val key = file.absolutePath
        imageCache.get(key)?.let { return it }
        val b = runCatching { BitmapFactory.decodeFile(key) }.getOrNull() ?: return null
        imageCache.put(key, b)
        return b
    }

    fun drawImages(c: Canvas, page: Page, dir: File) {
        for (img in page.images) {
            val b = loadImage(File(dir, img.file)) ?: continue
            c.drawBitmap(b, null, RectF(img.x, img.y, img.x + img.w, img.y + img.h), bmpPaint)
        }
    }

    fun drawStrokes(c: Canvas, strokes: List<Stroke>) {
        // Fosforlu kalem diğerlerinin altında kalsın
        for (s in strokes) if (s.pen == PenType.HIGHLIGHTER) drawStroke(c, s)
        for (s in strokes) if (s.pen != PenType.HIGHLIGHTER) drawStroke(c, s)
    }

    fun drawTexts(c: Canvas, texts: List<TextItem>) {
        for (t in texts) drawText(c, t)
    }

    fun textPaintFor(t: TextItem): Paint = textPaint.apply {
        typeface = com.xaser.note.model.NoteFonts.typeface(context, t.font)
        textSize = t.size
        color = t.color
    }

    /** Metin kutusunun sınırları (y = taban çizgisi) */
    fun textBounds(t: TextItem): RectF {
        val p = textPaintFor(t)
        val lines = t.text.split('\n')
        val lh = p.fontSpacing
        var w = 0f
        lines.forEach { w = max(w, p.measureText(it)) }
        return RectF(t.x, t.y + p.ascent(), t.x + w, t.y + p.descent() + lh * (lines.size - 1))
    }

    fun drawText(c: Canvas, t: TextItem) {
        val p = textPaintFor(t)
        var y = t.y
        for (line in t.text.split('\n')) { c.drawText(line, t.x, y, p); y += p.fontSpacing }
    }

    fun drawPage(c: Canvas, page: Page, dir: File, withTemplate: Boolean = true) {
        if (withTemplate) drawTemplate(c, page)
        drawImages(c, page, dir)
        drawStrokes(c, page.strokes)
        drawTexts(c, page.texts)
    }

    fun drawStroke(c: Canvas, s: Stroke) {
        val pts = s.points
        val n = pts.size / 3
        if (n == 0) return
        when (s.pen) {
            PenType.FOUNTAIN -> drawVariable(c, s, 0.35f, 1.6f)
            PenType.BALLPOINT -> drawSmoothPath(c, s, Paint.Cap.ROUND, 255, null)
            PenType.PENCIL -> drawSmoothPath(c, s, Paint.Cap.ROUND, 200, null)
            PenType.MARKER -> drawSmoothPath(c, s, Paint.Cap.SQUARE, 255, null)
            PenType.HIGHLIGHTER -> drawSmoothPath(c, s, Paint.Cap.BUTT, 90, BlendMode.MULTIPLY)
        }
    }

    private fun drawSmoothPath(c: Canvas, s: Stroke, cap: Paint.Cap, alpha: Int, blend: BlendMode?) {
        val pts = s.points
        val n = pts.size / 3
        strokePaint.color = s.color
        strokePaint.alpha = (Color.alpha(s.color) * alpha / 255)
        strokePaint.strokeCap = cap
        strokePaint.blendMode = blend
        // Basıncın ortalamasına göre hafif kalınlık değişimi (tükenmez kalem hissi)
        var avgP = 0f
        for (i in 0 until n) avgP += pts[i * 3 + 2]
        avgP /= n
        val pf = if (s.pen == PenType.BALLPOINT || s.pen == PenType.PENCIL) 0.8f + 0.4f * avgP else 1f
        strokePaint.strokeWidth = s.width * pf
        if (n == 1) {
            fillPaint.color = strokePaint.color; fillPaint.alpha = strokePaint.alpha; fillPaint.blendMode = blend
            c.drawCircle(pts[0], pts[1], strokePaint.strokeWidth / 2, fillPaint)
            fillPaint.blendMode = null
            strokePaint.blendMode = null
            return
        }
        path.reset()
        path.moveTo(pts[0], pts[1])
        for (i in 1 until n - 1) {
            val x = pts[i * 3]; val y = pts[i * 3 + 1]
            val nx = pts[(i + 1) * 3]; val ny = pts[(i + 1) * 3 + 1]
            path.quadTo(x, y, (x + nx) / 2, (y + ny) / 2)
        }
        path.lineTo(pts[(n - 1) * 3], pts[(n - 1) * 3 + 1])
        c.drawPath(path, strokePaint)
        strokePaint.blendMode = null
    }

    /** Dolma kalem: basınca göre değişen kalınlık */
    private fun drawVariable(c: Canvas, s: Stroke, minF: Float, maxF: Float) {
        val pts = s.points
        val n = pts.size / 3
        strokePaint.color = s.color
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.blendMode = null
        fun wAt(i: Int) = s.width * (minF + (maxF - minF) * pts[i * 3 + 2].coerceIn(0f, 1f))
        if (n == 1) {
            fillPaint.color = s.color
            c.drawCircle(pts[0], pts[1], wAt(0) / 2, fillPaint)
            return
        }
        // Noktalar arası yumuşatılmış orta noktalarla kısa parçalar
        var px = pts[0]; var py = pts[1]
        for (i in 1 until n) {
            val x = pts[i * 3]; val y = pts[i * 3 + 1]
            val mx = if (i < n - 1) (x + pts[(i + 1) * 3]) / 2 else x
            val my = if (i < n - 1) (y + pts[(i + 1) * 3 + 1]) / 2 else y
            strokePaint.strokeWidth = (wAt(i - 1) + wAt(i)) / 2
            path.reset(); path.moveTo(px, py); path.quadTo(x, y, mx, my)
            c.drawPath(path, strokePaint)
            px = mx; py = my
        }
    }

    companion object {
        fun boundsOf(strokes: Collection<Stroke>): RectF? {
            if (strokes.isEmpty()) return null
            val r = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (s in strokes) {
                val b = s.bounds()
                r.left = min(r.left, b[0]); r.top = min(r.top, b[1]); r.right = max(r.right, b[2]); r.bottom = max(r.bottom, b[3])
            }
            return r
        }
    }
}
