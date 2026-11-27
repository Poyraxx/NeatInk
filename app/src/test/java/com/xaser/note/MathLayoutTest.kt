package com.xaser.note

import com.xaser.note.model.PenType
import com.xaser.note.model.Stroke
import com.xaser.note.recognition.MathLayout
import com.xaser.note.recognition.MathLayout.Box
import com.xaser.note.recognition.MathLayout.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MathLayoutTest {

    private fun stroke(vararg xy: Float): Stroke {
        // Noktaları sıklaştır (gerçek kalem gibi)
        val out = ArrayList<Float>()
        for (i in 0 until xy.size / 2 - 1) {
            val x0 = xy[i * 2]; val y0 = xy[i * 2 + 1]; val x1 = xy[i * 2 + 2]; val y1 = xy[i * 2 + 3]
            for (k in 0 until 8) { val t = k / 8f; out.add(x0 + (x1 - x0) * t); out.add(y0 + (y1 - y0) * t); out.add(0.5f) }
        }
        out.add(xy[xy.size - 2]); out.add(xy[xy.size - 1]); out.add(0.5f)
        return Stroke(PenType.BALLPOINT, 0, 3f, out.toFloatArray())
    }

    /** "2" benzeri eğri bir rakam (40 birim boy) */
    private fun digit(x: Float, y: Float) = stroke(x, y, x + 20, y - 4, x + 26, y + 10, x + 4, y + 36, x + 28, y + 36)

    /** "5" benzeri */
    private fun five(x: Float, y: Float) = stroke(x + 24, y, x + 4, y, x + 2, y + 16, x + 22, y + 18, x + 22, y + 34, x, y + 38)

    private fun dot(x: Float, y: Float) = stroke(x, y, x + 2, y + 1)

    /** "u" harfi (30 birim) */
    private fun u(x: Float, y: Float) = stroke(x, y, x + 2, y + 26, x + 12, y + 30, x + 22, y + 24, x + 22, y, x + 24, y + 30)

    @Test fun fractionIsSplitIntoNumeratorBarAndDenominator() {
        val num = digit(100f, 40f)
        val bar = stroke(90f, 90f, 115f, 92f, 140f, 91f)   // hafif yamuk
        val den = five(102f, 100f)
        val plan = MathLayout.analyze(listOf(num, bar, den))
        assertEquals(1, plan.straightened.size)
        val fixed = plan.straightened[0].second.points
        assertEquals("kesir çizgisi dümdüz", fixed[1], fixed[fixed.size - 2], 0.01f)
        assertEquals(2, plan.groups.size)
        assertTrue(plan.groups[0].strokes.contains(num))
        assertTrue(plan.groups[1].strokes.contains(den))
    }

    @Test fun barUnderExistingTextIsAFraction() {
        val bar = stroke(90f, 90f, 140f, 91f)
        val den = five(102f, 100f)
        val plan = MathLayout.analyze(listOf(bar, den), listOf(Box(98f, 45f, 130f, 80f)))
        assertEquals(1, plan.straightened.size)
        assertEquals(1, plan.groups.size)
    }

    @Test fun plusMinusEqualsTimes() {
        val h = 40f
        fun sym(vararg s: Stroke) = MathLayout.symbolOf(s.toList(), h, emptyMap())
        assertEquals("+", sym(stroke(0f, 20f, 30f, 21f), stroke(15f, 5f, 16f, 35f)))
        assertEquals("-", sym(stroke(0f, 20f, 30f, 22f)))
        assertEquals("=", sym(stroke(0f, 15f, 30f, 15f), stroke(1f, 27f, 31f, 28f)))
        assertEquals("×", sym(stroke(0f, 0f, 30f, 30f), stroke(30f, 0f, 0f, 30f)))
        assertNull("eğri çizgi işaret değil", sym(digit(0f, 0f)))
    }

    @Test fun longLineIsStraightenedNotRecognized() {
        val letters = listOf(digit(0f, 0f), five(40f, 0f))
        val line = stroke(0f, 200f, 150f, 205f, 300f, 202f)
        val plan = MathLayout.analyze(letters + line)
        assertEquals(1, plan.straightened.size)
        assertTrue(plan.groups.none { it.strokes.contains(line) })
    }

    @Test fun umlautDotsStayWithTheLetter() {
        val letter = u(0f, 20f)
        val d1 = dot(5f, 8f); val d2 = dot(17f, 8f)
        val plan = MathLayout.analyze(listOf(letter, d1, d2))
        assertEquals(1, plan.groups.size)
        assertEquals(3, plan.groups[0].strokes.size)
    }

    @Test fun lateDotsBecomeAMarkOnExistingText() {
        // "mutlu" zaten metin; sonradan iki nokta eklendi
        val d1 = dot(105f, 8f); val d2 = dot(115f, 8f)
        val plan = MathLayout.analyze(listOf(d1, d2), listOf(Box(0f, 20f, 160f, 50f)))
        assertEquals(1, plan.marks.size)
        assertEquals(Mark.DOTS_ABOVE, plan.marks[0].second)
        assertEquals(0, plan.groups.size)
    }

    @Test fun applyMarks() {
        assertEquals("üzüm", MathLayout.applyMark("uzüm", 0, Mark.DOTS_ABOVE, false))
        assertEquals("İstanbul", MathLayout.applyMark("Istanbul", 0, Mark.DOT_ABOVE, false))
        assertEquals("dağ", MathLayout.applyMark("dag", 2, Mark.BREVE_ABOVE, false))
        assertEquals("kuş", MathLayout.applyMark("kus", 2, Mark.HOOK_BELOW, true))
        assertNull(MathLayout.applyMark("kar", 1, Mark.DOTS_ABOVE, false))
    }

    @Test fun circleIsNotAFractionBar() {
        val pts = (0..40).flatMap { val a = 2 * PI * it / 40; listOf((50 + 20 * cos(a)).toFloat(), (50 + 20 * sin(a)).toFloat()) }
        val o = stroke(*pts.toFloatArray())
        val plan = MathLayout.analyze(listOf(o))
        assertEquals(0, plan.straightened.size)
        assertEquals(1, plan.groups.size)
    }

    @Test fun fractionPartsPreferDigits() {
        assertEquals("5", com.xaser.note.recognition.MathText.pick(listOf("s", "5", "S"), math = true))
        assertEquals("0", com.xaser.note.recognition.MathText.pick(listOf("o"), math = true))
        assertEquals("x+1", com.xaser.note.recognition.MathText.pick(listOf("xtl", "x+1"), math = true))
        assertEquals("s", com.xaser.note.recognition.MathText.pick(listOf("s", "5"), math = false))
    }

    @Test fun lonelyBarUnderTextIsStraightenedNotMinus() {
        val bar = stroke(90f, 90f, 140f, 91f)
        val plan = MathLayout.analyze(listOf(bar), listOf(Box(98f, 45f, 130f, 80f)))
        assertEquals(1, plan.straightened.size)
        assertEquals(0, plan.groups.size)
    }
}
