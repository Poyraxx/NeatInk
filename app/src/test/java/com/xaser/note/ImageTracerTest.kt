package com.xaser.note

import com.xaser.note.ink.ImageTracer
import com.xaser.note.ink.ShapeSnapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ImageTracerTest {
    private val w = 400; private val h = 300

    private fun canvas() = IntArray(w * h) { 245 }

    private fun dot(g: IntArray, x: Float, y: Float, r: Float = 2.5f) {
        for (dy in -4..4) for (dx in -4..4) {
            val px = (x + dx).toInt(); val py = (y + dy).toInt()
            if (px in 0 until w && py in 0 until h && hypot(dx.toFloat(), dy.toFloat()) <= r) g[py * w + px] = 30
        }
    }

    private fun line(g: IntArray, x0: Float, y0: Float, x1: Float, y1: Float) {
        val n = (hypot(x1 - x0, y1 - y0) * 2).toInt()
        for (i in 0..n) { val t = i / n.toFloat(); dot(g, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t) }
    }

    @Test fun circleRectAndLineAreRecognized() {
        val g = canvas()
        for (i in 0..720) { val a = Math.PI * 2 * i / 720; dot(g, (100 + 60 * cos(a)).toFloat(), (100 + 60 * sin(a)).toFloat()) }
        line(g, 220f, 40f, 360f, 40f); line(g, 360f, 40f, 360f, 140f); line(g, 360f, 140f, 220f, 140f); line(g, 220f, 140f, 220f, 40f)
        line(g, 40f, 240f, 360f, 270f)
        val res = ImageTracer.trace(g, w, h)
        val kinds = res.map { t -> ShapeSnapper.snap(t.points)?.kind }
        println("parça: ${res.size}, şekiller: $kinds, kalınlık: ${res.map { it.thickness }}")
        assertTrue("en az 3 parça", res.size >= 3)
        assertTrue("daire", kinds.contains(ShapeSnapper.Kind.CIRCLE))
        assertTrue("dikdörtgen", kinds.contains(ShapeSnapper.Kind.RECT))
        // Düz çizgi sadeleştirmede 2 noktaya iner (zaten kusursuz düz)
        assertTrue("çizgi", kinds.contains(ShapeSnapper.Kind.LINE) || res.any { it.points.size == 6 })
    }

    @Test fun maskedTextIsIgnored() {
        val g = canvas()
        line(g, 50f, 50f, 150f, 50f)
        val res = ImageTracer.trace(g, w, h, listOf(intArrayOf(40, 40, 160, 60)))
        assertEquals(0, res.size)
    }

    @Test fun blankImageGivesNothing() = assertEquals(0, ImageTracer.trace(canvas(), w, h).size)
}
