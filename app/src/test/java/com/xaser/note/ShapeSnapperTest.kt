package com.xaser.note

import com.xaser.note.ink.ShapeSnapper
import com.xaser.note.ink.ShapeSnapper.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShapeSnapperTest {
    private val rnd = Random(1)
    private fun jitter() = (rnd.nextFloat() - 0.5f) * 6f

    private fun pts(list: List<Pair<Float, Float>>) = FloatArray(list.size * 3).also { a ->
        list.forEachIndexed { i, (x, y) -> a[i * 3] = x + jitter(); a[i * 3 + 1] = y + jitter(); a[i * 3 + 2] = 0.5f }
    }

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 30) =
        (0 until n).map { val t = it / n.toFloat(); (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t) }

    @Test fun line() = assertEquals(Kind.LINE, ShapeSnapper.snap(pts(seg(0f, 0f, 300f, 120f)))!!.kind)

    @Test fun circle() {
        val p = (0..80).map { val a = 2 * PI * it / 80; (200 + 100 * cos(a)).toFloat() to (200 + 100 * sin(a)).toFloat() }
        assertEquals(Kind.CIRCLE, ShapeSnapper.snap(pts(p))!!.kind)
    }

    @Test fun ellipse() {
        val p = (0..80).map { val a = 2 * PI * it / 80; (200 + 180 * cos(a)).toFloat() to (200 + 80 * sin(a)).toFloat() }
        assertEquals(Kind.ELLIPSE, ShapeSnapper.snap(pts(p))!!.kind)
    }

    @Test fun rectFromCorner() {
        val p = seg(0f, 0f, 300f, 0f) + seg(300f, 0f, 300f, 200f) + seg(300f, 200f, 0f, 200f) + seg(0f, 200f, 0f, 0f) + listOf(0f to 0f)
        assertEquals(Kind.RECT, ShapeSnapper.snap(pts(p))!!.kind)
    }

    @Test fun rectFromMiddle() {
        val p = seg(150f, 0f, 300f, 0f, 15) + seg(300f, 0f, 300f, 200f) + seg(300f, 200f, 0f, 200f) + seg(0f, 200f, 0f, 0f) + seg(0f, 0f, 150f, 0f, 15)
        assertEquals(Kind.RECT, ShapeSnapper.snap(pts(p))!!.kind)
    }

    @Test fun triangle() {
        val p = seg(0f, 300f, 150f, 0f) + seg(150f, 0f, 300f, 300f) + seg(300f, 300f, 0f, 300f) + listOf(0f to 300f)
        assertEquals(Kind.TRIANGLE, ShapeSnapper.snap(pts(p))!!.kind)
    }

    @Test fun arrow() {
        val p = seg(0f, 0f, 400f, 0f, 60) + seg(400f, 0f, 360f, -30f, 8)
        assertEquals(Kind.ARROW, ShapeSnapper.snap(pts(p))!!.kind)
    }
}
