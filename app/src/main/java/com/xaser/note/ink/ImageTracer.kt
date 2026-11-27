package com.xaser.note.ink

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Bir resimdeki çizgi ve şekilleri vektör çizgilere çevirir (internetsiz, saf Kotlin).
 * 1) Uyarlamalı eşikleme  2) Yazı bölgelerini maskele  3) İnceltme (Zhang–Suen)
 * 4) İskeleti çoklu çizgilere izle  5) Sadeleştir  6) Düzgün şekil ise ShapeSnapper ile düzelt.
 * Girdi gri tonlu piksel dizisidir (0 = siyah, 255 = beyaz), çıktı piksel koordinatlarında çizgilerdir.
 */
object ImageTracer {

    class Traced(val points: FloatArray /* x,y,basınç üçlüleri */, val thickness: Float, val snapped: Boolean)

    /**
     * @param gray w*h gri tonlar
     * @param maskRects yazı kutuları (izlenmeyecek): [l,t,r,b] piksel
     */
    fun trace(gray: IntArray, w: Int, h: Int, maskRects: List<IntArray> = emptyList()): List<Traced> {
        val ink = threshold(gray, w, h)
        for (r in maskRects) {
            val pad = max(2, (r[3] - r[1]) / 6)
            for (y in max(0, r[1] - pad) until min(h, r[3] + pad)) for (x in max(0, r[0] - pad) until min(w, r[2] + pad)) ink[y * w + x] = false
        }
        removeSmallComponents(ink, w, h, minPixels = max(25, (w * h) / 40000))
        val inkCount = ink.count { it }
        if (inkCount == 0) return emptyList()
        val skel = ink.copyOf()
        thin(skel, w, h)
        val skelCount = skel.count { it }.coerceAtLeast(1)
        // Ortalama kalınlık ≈ mürekkep alanı / iskelet uzunluğu
        val thickness = (inkCount.toFloat() / skelCount).coerceIn(1.5f, 12f)
        val paths = tracePaths(skel, w, h)
        val minLen = max(8f, min(w, h) / 80f)
        val out = ArrayList<Traced>()
        for (p in paths) {
            if (pathLength(p) < minLen) continue
            val simp = simplify(p, 1.2f)
            val pts = FloatArray(simp.size / 2 * 3)
            for (i in 0 until simp.size / 2) { pts[i * 3] = simp[i * 2]; pts[i * 3 + 1] = simp[i * 2 + 1]; pts[i * 3 + 2] = 0.6f }
            if (simp.size == 4) { out.add(Traced(pts, thickness, true)); continue }
            val snap = if (simp.size >= 8) ShapeSnapper.snap(densify(pts)) else null
            // Yalnızca güvenli şekilleri düzelt (daire, elips, üçgen, dikdörtgen, düz çizgi)
            val ok = snap != null && snap.kind != ShapeSnapper.Kind.POLYGON && snap.kind != ShapeSnapper.Kind.ARROW
            out.add(Traced(if (ok) snap!!.points else pts, thickness, ok))
        }
        return out
    }

    /** Yerel ortalamaya göre eşikleme (fotoğraflardaki gölgeye dayanıklı) */
    fun threshold(gray: IntArray, w: Int, h: Int): BooleanArray {
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += gray[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row
            }
        }
        val r = max(8, min(w, h) / 40)
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w - 1, x + r)
                val sum = integral[(y1 + 1) * (w + 1) + x1 + 1] - integral[y0 * (w + 1) + x1 + 1] -
                    integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = sum / ((x1 - x0 + 1) * (y1 - y0 + 1)).toDouble()
                val g = gray[y * w + x]
                out[y * w + x] = g < mean - 18 && g < 170
            }
        }
        return out
    }

    private fun removeSmallComponents(ink: BooleanArray, w: Int, h: Int, minPixels: Int) {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val comp = ArrayList<Int>()
        for (start in ink.indices) {
            if (!ink[start] || seen[start]) continue
            comp.clear()
            var sp = 0; stack[sp++] = start; seen[start] = true
            while (sp > 0) {
                val p = stack[--sp]; comp.add(p)
                val px = p % w; val py = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (ink[q] && !seen[q]) { seen[q] = true; stack[sp++] = q }
                }
            }
            if (comp.size < minPixels) comp.forEach { ink[it] = false }
        }
    }

    /** Zhang–Suen inceltme: 1 piksel kalınlığında iskelet (ayırma yapmayan hızlı sürüm) */
    fun thin(img: BooleanArray, w: Int, h: Int) {
        val del = IntArray(w * h)
        val n = BooleanArray(8)
        var changed = true
        while (changed) {
            changed = false
            for (step in 0..1) {
                var dc = 0
                for (y in 1 until h - 1) {
                    val row = y * w
                    for (x in 1 until w - 1) {
                        if (!img[row + x]) continue
                        n[0] = img[row - w + x]; n[1] = img[row - w + x + 1]; n[2] = img[row + x + 1]; n[3] = img[row + w + x + 1]
                        n[4] = img[row + w + x]; n[5] = img[row + w + x - 1]; n[6] = img[row + x - 1]; n[7] = img[row - w + x - 1]
                        var b = 0
                        for (i in 0 until 8) if (n[i]) b++
                        if (b < 2 || b > 6) continue
                        var a = 0
                        for (i in 0 until 8) if (!n[i] && n[(i + 1) and 7]) a++
                        if (a != 1) continue
                        // n[0]=p2, n[2]=p4, n[4]=p6, n[6]=p8
                        if (step == 0) { if (n[0] && n[2] && n[4]) continue; if (n[2] && n[4] && n[6]) continue }
                        else { if (n[0] && n[2] && n[6]) continue; if (n[0] && n[4] && n[6]) continue }
                        del[dc++] = row + x
                    }
                }
                if (dc > 0) { changed = true; for (i in 0 until dc) img[del[i]] = false }
            }
        }
    }

    /** İskeleti uç noktalardan başlayarak çoklu çizgilere ayırır (x,y çiftleri) */
    fun tracePaths(sk: BooleanArray, w: Int, h: Int): List<FloatArray> {
        val visited = BooleanArray(w * h)
        fun nbs(p: Int): List<Int> {
            val x = p % w; val y = p / w
            val r = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (sk[q]) r.add(q)
            }
            return r
        }
        val paths = ArrayList<FloatArray>()
        fun walk(start: Int) {
            val pts = ArrayList<Float>()
            var cur = start
            visited[cur] = true
            pts.add((cur % w).toFloat()); pts.add((cur / w).toFloat())
            while (true) {
                val next = nbs(cur).filter { !visited[it] }
                    // önce düz komşular (köşegenleri sonra) daha düzgün yol verir
                    .minByOrNull { q -> abs(q % w - cur % w) + abs(q / w - cur / w) } ?: break
                visited[next] = true
                cur = next
                pts.add((cur % w).toFloat()); pts.add((cur / w).toFloat())
            }
            if (pts.size >= 4) paths.add(pts.toFloatArray())
        }
        // 1) uç noktalardan
        for (p in sk.indices) if (sk[p] && !visited[p] && nbs(p).size == 1) walk(p)
        // 2) kalanlar (kapalı döngüler)
        for (p in sk.indices) if (sk[p] && !visited[p]) walk(p)
        // Kapalı döngüyü kapat: son nokta ilk noktaya komşuysa başa ekle
        return paths.map { a ->
            if (a.size >= 8 && hypot(a[0] - a[a.size - 2], a[1] - a[a.size - 1]) <= 2f) a + floatArrayOf(a[0], a[1]) else a
        }
    }

    private fun pathLength(p: FloatArray): Float {
        var l = 0f
        var i = 2
        while (i < p.size) { l += hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]); i += 2 }
        return l
    }

    /** Douglas–Peucker (x,y çiftleri) */
    fun simplify(p: FloatArray, eps: Float): FloatArray {
        val n = p.size / 2
        if (n < 3) return p
        val keep = BooleanArray(n); keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>(); stack.add(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var idx = -1; var md = 0f
            val ax = p[a * 2]; val ay = p[a * 2 + 1]; val bx = p[b * 2]; val by = p[b * 2 + 1]
            val dx = bx - ax; val dy = by - ay; val l = hypot(dx, dy)
            for (i in a + 1 until b) {
                val x = p[i * 2]; val y = p[i * 2 + 1]
                val d = if (l < 1e-3f) hypot(x - ax, y - ay) else abs(dy * x - dx * y + bx * ay - by * ax) / l
                if (d > md) { md = d; idx = i }
            }
            if (md > eps && idx > 0) { keep[idx] = true; stack.add(a to idx); stack.add(idx to b) }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out.add(p[i * 2]); out.add(p[i * 2 + 1]) }
        return out.toFloatArray()
    }

    /** ShapeSnapper sık noktalı girdi bekler */
    private fun densify(pts: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        val n = pts.size / 3
        for (i in 0 until n - 1) {
            val x0 = pts[i * 3]; val y0 = pts[i * 3 + 1]; val x1 = pts[(i + 1) * 3]; val y1 = pts[(i + 1) * 3 + 1]
            val steps = max(1, (hypot(x1 - x0, y1 - y0) / 4f).toInt())
            for (k in 0 until steps) { val t = k / steps.toFloat(); out.add(x0 + (x1 - x0) * t); out.add(y0 + (y1 - y0) * t); out.add(0.6f) }
        }
        out.add(pts[(n - 1) * 3]); out.add(pts[(n - 1) * 3 + 1]); out.add(0.6f)
        return out.toFloatArray()
    }
}
