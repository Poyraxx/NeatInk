package com.xaser.note.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Elle çizilmiş yamuk bir şekli düzgün şekle çevirir:
 * düz çizgi, ok, daire, elips, üçgen, dikdörtgen/dörtgen, çokgen.
 * Girdi/çıktı: (x, y, basınç) üçlüleri.
 */
object ShapeSnapper {

    enum class Kind { LINE, ARROW, CIRCLE, ELLIPSE, TRIANGLE, RECT, POLYGON }

    class Result(val kind: Kind, val points: FloatArray)

    fun snap(pts: FloatArray): Result? {
        val n = pts.size / 3
        if (n < 4) return null
        val xs = FloatArray(n) { pts[it * 3] }
        val ys = FloatArray(n) { pts[it * 3 + 1] }
        var len = 0f
        for (i in 1 until n) len += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (len < 20f) return null
        val endDist = hypot(xs[n - 1] - xs[0], ys[n - 1] - ys[0])

        // 1) Düz çizgi
        if (endDist / len > 0.9f) {
            return Result(Kind.LINE, line(xs[0], ys[0], xs[n - 1], ys[n - 1]))
        }

        // 2) Ok: düz gövde + uçta kısa bir "V"
        arrow(xs, ys)?.let { return it }

        val closed = endDist < len * 0.22f
        if (!closed) return null

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val rx = (maxX - minX) / 2; val ry = (maxY - minY) / 2
        if (rx < 5 || ry < 5) return null

        // Köşe sayısı (Douglas-Peucker)
        val simplified = cleanClosed(douglasPeucker(xs, ys, len * 0.035f), len)
        val corners = simplified.size / 2

        // Elips uyum hatası
        var err = 0f
        for (i in 0 until n) {
            val dx = (xs[i] - cx) / rx; val dy = (ys[i] - cy) / ry
            err += abs(sqrt(dx * dx + dy * dy) - 1f)
        }
        err /= n

        if (err < 0.09f && corners > 4 || err < 0.06f) {
            val circle = abs(rx - ry) / max(rx, ry) < 0.18f
            return if (circle) {
                val r = (rx + ry) / 2
                Result(Kind.CIRCLE, ellipse(cx, cy, r, r))
            } else Result(Kind.ELLIPSE, ellipse(cx, cy, rx, ry))
        }

        val vx = FloatArray(corners) { simplified[it * 2] }
        val vy = FloatArray(corners) { simplified[it * 2 + 1] }
        return when (corners) {
            3 -> Result(Kind.TRIANGLE, polygon(vx, vy))
            4 -> {
                // Yaklaşık eksene hizalı ise düzgün dikdörtgen
                if (isAxisAligned(vx, vy)) Result(Kind.RECT, polygon(floatArrayOf(minX, maxX, maxX, minX), floatArrayOf(minY, minY, maxY, maxY)))
                else Result(Kind.RECT, polygon(vx, vy))
            }
            in 5..8 -> Result(Kind.POLYGON, polygon(vx, vy))
            else -> if (err < 0.15f) Result(Kind.ELLIPSE, ellipse(cx, cy, rx, ry)) else null
        }
    }

    private fun isAxisAligned(vx: FloatArray, vy: FloatArray): Boolean {
        for (i in vx.indices) {
            val j = (i + 1) % vx.size
            val a = abs(atan2(vy[j] - vy[i], vx[j] - vx[i]) * 180 / PI) % 90
            if (a > 15 && a < 75) return false
        }
        return true
    }

    private fun arrow(xs: FloatArray, ys: FloatArray): Result? {
        val n = xs.size
        // gövdenin bittiği yeri bul: başlangıçtan en uzak nokta
        var far = 0; var fd = 0f
        for (i in 0 until n) { val d = hypot(xs[i] - xs[0], ys[i] - ys[0]); if (d > fd) { fd = d; far = i } }
        if (far < n * 0.4f || far > n - 3) return null
        var bodyLen = 0f
        for (i in 1..far) bodyLen += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (fd / bodyLen < 0.92f) return null
        var tailLen = 0f
        for (i in far + 1 until n) tailLen += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (tailLen > fd * 0.7f || tailLen < fd * 0.1f) return null
        val tx = xs[far]; val ty = ys[far]
        val ang = atan2(ty - ys[0], tx - xs[0])
        val head = min(fd * 0.25f, 40f)
        val out = ArrayList<Float>()
        fun add(x: Float, y: Float) { out.add(x); out.add(y); out.add(0.6f) }
        line(xs[0], ys[0], tx, ty).let { a -> for (i in a.indices step 3) add(a[i], a[i + 1]) }
        val a1 = ang + PI.toFloat() * 0.82f; val a2 = ang - PI.toFloat() * 0.82f
        val h1x = tx + head * cos(a1); val h1y = ty + head * sin(a1)
        val h2x = tx + head * cos(a2); val h2y = ty + head * sin(a2)
        line(tx, ty, h1x, h1y).let { a -> for (i in a.indices step 3) add(a[i], a[i + 1]) }
        line(h1x, h1y, tx, ty).let { a -> for (i in a.indices step 3) add(a[i], a[i + 1]) }
        line(tx, ty, h2x, h2y).let { a -> for (i in a.indices step 3) add(a[i], a[i + 1]) }
        return Result(Kind.ARROW, out.toFloatArray())
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val d = hypot(x1 - x0, y1 - y0)
        val steps = max(2, (d / 6f).toInt())
        val out = FloatArray((steps + 1) * 3)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            out[i * 3] = x0 + (x1 - x0) * t; out[i * 3 + 1] = y0 + (y1 - y0) * t; out[i * 3 + 2] = 0.6f
        }
        return out
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): FloatArray {
        val steps = max(36, ((rx + ry) / 3).toInt())
        val out = FloatArray((steps + 1) * 3)
        for (i in 0..steps) {
            val a = 2 * PI * i / steps - PI / 2
            out[i * 3] = cx + rx * cos(a).toFloat(); out[i * 3 + 1] = cy + ry * sin(a).toFloat(); out[i * 3 + 2] = 0.6f
        }
        return out
    }

    fun polygon(vx: FloatArray, vy: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        for (i in vx.indices) {
            val j = (i + 1) % vx.size
            val seg = line(vx[i], vy[i], vx[j], vy[j])
            val start = if (i == 0) 0 else 3
            for (k in start until seg.size) out.add(seg[k])
        }
        return out.toFloatArray()
    }

    /** Kapalı çokgende tekrar eden uç noktayı ve neredeyse düz (180°) köşeleri atar */
    private fun cleanClosed(poly: FloatArray, len: Float): FloatArray {
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>()
        for (i in poly.indices step 2) { vx.add(poly[i]); vy.add(poly[i + 1]) }
        if (vx.size >= 2 && hypot(vx[0] - vx.last(), vy[0] - vy.last()) < len * 0.12f) {
            vx.removeAt(vx.size - 1); vy.removeAt(vy.size - 1)
        }
        var changed = true
        while (changed && vx.size > 3) {
            changed = false
            var bestI = -1; var bestTurn = Float.MAX_VALUE
            for (i in vx.indices) {
                val p = (i - 1 + vx.size) % vx.size; val nx = (i + 1) % vx.size
                val a1 = atan2(vy[i] - vy[p], vx[i] - vx[p]); val a2 = atan2(vy[nx] - vy[i], vx[nx] - vx[i])
                var d = abs(a2 - a1); if (d > PI) d = (2 * PI - d).toFloat()
                val shortEdge = hypot(vx[i] - vx[p], vy[i] - vy[p]) < len * 0.04f
                val turn = if (shortEdge) 0f else d
                if (turn < bestTurn) { bestTurn = turn; bestI = i }
            }
            if (bestTurn < 0.45f) { vx.removeAt(bestI); vy.removeAt(bestI); changed = true }
        }
        val out = FloatArray(vx.size * 2)
        for (i in vx.indices) { out[i * 2] = vx[i]; out[i * 2 + 1] = vy[i] }
        return out
    }

    /** Köşe noktalarını (x,y çiftleri) döndürür */
    private fun douglasPeucker(xs: FloatArray, ys: FloatArray, eps: Float): FloatArray {
        val n = xs.size
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        // Kapalı şekiller için başlangıca en uzak noktadan da böl
        var far = 0; var fd = 0f
        for (i in 0 until n) { val d = hypot(xs[i] - xs[0], ys[i] - ys[0]); if (d > fd) { fd = d; far = i } }
        keep[far] = true
        fun rec(a: Int, b: Int) {
            if (b <= a + 1) return
            var idx = -1; var md = 0f
            val dx = xs[b] - xs[a]; val dy = ys[b] - ys[a]
            val l = hypot(dx, dy)
            for (i in a + 1 until b) {
                val d = if (l < 1e-3f) hypot(xs[i] - xs[a], ys[i] - ys[a])
                else abs(dy * xs[i] - dx * ys[i] + xs[b] * ys[a] - ys[b] * xs[a]) / l
                if (d > md) { md = d; idx = i }
            }
            if (md > eps && idx > 0) { keep[idx] = true; rec(a, idx); rec(idx, b) }
        }
        rec(0, far); rec(far, n - 1)
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out.add(xs[i]); out.add(ys[i]) }
        return out.toFloatArray()
    }
}
