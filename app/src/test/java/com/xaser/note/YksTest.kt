package com.xaser.note

import com.xaser.note.model.Yks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YksTest {
    private fun full(type: String): Map<String, Double> =
        (Yks.TYT_TESTS + (Yks.AYT_TESTS[type] ?: emptyList())).associate { it.key to it.questions.toDouble() }

    @Test fun fullNetsGiveAbout500() {
        assertEquals(500.0, Yks.rawScore("TYT", full("TYT")), 1.0)
        assertEquals(500.0, Yks.rawScore("SAY", full("SAY")), 1.0)
        // EA ve SÖZ katsayıları tam netle 500'ü biraz aşıyor (ÖSYM standartlaştırması nedeniyle)
        assertTrue(Yks.rawScore("EA", full("EA")) in 495.0..510.0)
        assertTrue(Yks.rawScore("SÖZ", full("SÖZ")) in 495.0..510.0)
    }

    @Test fun zeroNetsGiveBase() = assertEquals(145.47, Yks.rawScore("TYT", emptyMap()), 1e-9)

    @Test fun netFormula() = assertEquals(27.5, Yks.net(30, 10), 1e-9)

    @Test fun obp() {
        assertEquals(48.0, Yks.obpBonus(80.0, false), 1e-9)
        assertEquals(24.0, Yks.obpBonus(80.0, true), 1e-9)
    }

    @Test fun rankIsMonotonic() {
        for (type in listOf("SAY", "EA", "SÖZ")) {
            var last = 0L
            var s = 550.0
            while (s > 170) {
                val r = Yks.estimateRank(type, s).rank
                assertTrue("$type $s", r >= last)
                last = r; s -= 2.5
            }
        }
    }

    @Test fun rankMatchesDataPoint() {
        // 2025: SAY 400,016 puan ≈ 98.967. sıra
        assertEquals(98967.0, Yks.estimateRank("SAY", 400.016).rank.toDouble(), 50.0)
    }

    @Test fun inverseRoundTrip() {
        val score = Yks.scoreForRank("SAY", 50_000)!!
        assertEquals(50_000.0, Yks.estimateRank("SAY", score).rank.toDouble(), 500.0)
        assertNull(Yks.scoreForRank("SAY", 5_000_000))
    }
}
