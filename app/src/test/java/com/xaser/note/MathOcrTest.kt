package com.xaser.note

import com.xaser.note.recognition.MathOcr
import com.xaser.note.recognition.MathOcr.Item
import com.xaser.note.recognition.MathOcr.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathOcrTest {
    /** Normal boyda karakter (taban 100, boy 40) */
    private fun ch(c: String, x: Float, word: Int = 0) = Sym(c, x, 60f, x + 20f, 100f, word)
    private fun sup(c: String, x: Float, word: Int = 0) = Sym(c, x, 50f, x + 12f, 72f, word)
    private fun sub(c: String, x: Float, word: Int = 0) = Sym(c, x, 88f, x + 12f, 110f, word)

    @Test fun superscript() = assertEquals("x²", MathOcr.buildLine(listOf(ch("x", 0f), sup("2", 22f))))

    @Test fun subscript() = assertEquals("a₁", MathOcr.buildLine(listOf(ch("a", 0f), sub("1", 22f))))

    @Test fun expression() {
        // x² + 3 = 7   (kelimeler: "x²", "+", "3", "=", "7")
        val s = listOf(ch("x", 0f, 0), sup("2", 22f, 0), ch("+", 50f, 1), ch("3", 80f, 2), ch("=", 110f, 3), ch("7", 140f, 4))
        assertEquals("x² + 3 = 7", MathOcr.buildLine(s))
    }

    @Test fun firstSmallCharIsNotSuperscript() = assertEquals("2x", MathOcr.buildLine(listOf(sup("2", 0f), ch("x", 14f))))

    @Test fun normalizeSymbols() {
        assertEquals("x ≤ 5", MathOcr.normalize("x <= 5"))
        assertEquals("a ≠ b", MathOcr.normalize("a != b"))
        assertEquals("√2 + √(x)", MathOcr.normalize("V2 + V(x)"))
        assertEquals("2π", MathOcr.normalize("2TT"))
        assertEquals("2×3", MathOcr.normalize("2x3"))
        assertEquals("2x + 3", MathOcr.normalize("2x + 3"))
        assertEquals("Very", MathOcr.normalize("Very"))
    }

    @Test fun fixDigits() {
        assertEquals("2019", MathOcr.fixDigits("2O19"))
        assertEquals("15 + 10", MathOcr.fixDigits("l5 + 1O"))
        assertEquals("Sol", MathOcr.fixDigits("Sol"))       // gerçek kelime
        assertEquals("x2", MathOcr.fixDigits("x2"))
    }

    @Test fun barText() {
        assertTrue(MathOcr.isBarText("———", 120f, 10f))
        assertFalse(MathOcr.isBarText("-", 10f, 10f))
        assertFalse(MathOcr.isBarText("a-b", 120f, 10f))
    }

    @Test fun fractionCentering() {
        val num = Item(100f, 50f, 120f, 90f)     // pay solda kaymış
        val den = Item(130f, 110f, 160f, 150f)
        val other = Item(400f, 50f, 440f, 90f)   // uzakta
        val m = MathOcr.centerOverBars(listOf(num, den, other), listOf(Triple(90f, 190f, 100f)), 40f)
        assertEquals(130f, m[0]!!, 0.01f)   // orta 140, genişlik 20
        assertEquals(125f, m[1]!!, 0.01f)   // genişlik 30
        assertTrue(2 !in m)
    }

    @Test fun barWithOnlyTextAboveIsNotAFraction() {
        val m = MathOcr.centerOverBars(listOf(Item(100f, 50f, 120f, 90f)), listOf(Triple(90f, 190f, 100f)), 40f)
        assertTrue(m.isEmpty())
    }

    @Test fun separateSuperscriptLineIsMerged() {
        val main = MathOcr.LineData(mutableListOf(ch("x", 0f, 0), ch("+", 40f, 1), ch("3", 70f, 2)), 0f, 60f, 90f, 100f)
        val two = MathOcr.LineData(mutableListOf(sup("2", 22f, 0)), 22f, 50f, 34f, 72f)
        val merged = MathOcr.mergeScripts(listOf(main, two))
        assertEquals(1, merged.size)
        assertEquals("x² + 3", MathOcr.buildLine(merged[0].syms))
    }

    @Test fun leadingNoise() = assertEquals("x² + 3", MathOcr.stripLeadingNoise(",x² + 3"))

    @Test fun weirdLeadingQuote() {
        assertEquals("x²", MathOcr.stripLeadingNoise("‚x²"))
        assertEquals("-3", MathOcr.stripLeadingNoise("-3"))
        assertEquals("", MathOcr.stripLeadingNoise(","))
    }

    @Test fun rereadScriptBecomesSubscript() {
        // OCR virgül sandı, büyütüp okuyunca "1" çıktı; kutusu normal boyda olsa da indis olmalı
        val line = listOf(ch("a", 0f, 0), Sym("1", 22f, 80f, 36f, 112f, 0, script = true), ch("+", 50f, 1))
        assertEquals("a₁ +", MathOcr.buildLine(line))
    }

    @Test fun suspiciousComma() {
        val line = listOf(ch("a", 0f), Sym(",", 22f, 86f, 32f, 110f, 0))
        assertTrue(MathOcr.isSuspiciousScript(line[1], line))
        val realComma = listOf(ch("0", 0f), Sym(",", 22f, 94f, 28f, 106f, 0), ch("4", 30f))
        assertFalse(MathOcr.isSuspiciousScript(realComma[1], realComma))
    }
}
