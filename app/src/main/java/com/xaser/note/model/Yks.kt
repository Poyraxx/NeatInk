package com.xaser.note.model

import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * YKS net / puan / sıralama tahmini.
 *
 * Katsayılar: 2025 YKS için yayımlanan katsayılar (ferrumone.com derlemesi). Tüm netler tam olduğunda
 * TYT ≈ 500, SAY ≈ 500 verir. Puan = taban + Σ(net × katsayı); yerleştirme puanı = ham + OBP × 0,12.
 * Sıralama: 2025 YKS'de bölümlerin taban/tavan yerleştirme puanları ve başarı sıraları
 * (Medipol Üniversitesi yayını, YES Akademi). Aralar logaritmik doğrusal enterpolasyonla doldurulur.
 * Hepsi YAKLAŞIK tahmindir; kesin sonuç ÖSYM'nin açıkladığıdır.
 */
object Yks {
    const val DATA_YEAR = 2025

    /** Bir alt test: anahtar, ad, soru sayısı */
    class Test(val key: String, val name: String, val questions: Int)

    val TYT_TESTS = listOf(
        Test("tr", "Türkçe", 40), Test("sos", "Sosyal Bilimler", 20),
        Test("mat", "Temel Matematik", 40), Test("fen", "Fen Bilimleri", 20),
    )

    val AYT_TESTS = mapOf(
        "SAY" to listOf(Test("amat", "Matematik", 40), Test("fiz", "Fizik", 14), Test("kim", "Kimya", 13), Test("biy", "Biyoloji", 13)),
        "EA" to listOf(Test("amat", "Matematik", 40), Test("ede", "Türk Dili ve Edebiyatı", 24), Test("tar1", "Tarih-1", 10), Test("cog1", "Coğrafya-1", 6)),
        "SÖZ" to listOf(
            Test("ede", "Türk Dili ve Edebiyatı", 24), Test("tar1", "Tarih-1", 10), Test("cog1", "Coğrafya-1", 6),
            Test("tar2", "Tarih-2", 11), Test("cog2", "Coğrafya-2", 11), Test("fel", "Felsefe Grubu", 12), Test("din", "Din Kültürü / İlave Felsefe", 6),
        ),
    )

    class Coef(val base: Double, val c: Map<String, Double>)

    val COEF = mapOf(
        "TYT" to Coef(145.47, mapOf("tr" to 2.83, "sos" to 2.99, "mat" to 3.28, "fen" to 2.53)),
        "SAY" to Coef(132.87, mapOf("tr" to 1.20, "sos" to 1.27, "mat" to 1.39, "fen" to 1.07,
            "amat" to 2.89, "fiz" to 2.46, "kim" to 2.53, "biy" to 2.61)),
        "EA" to Coef(129.34, mapOf("tr" to 1.19, "sos" to 1.26, "mat" to 1.38, "fen" to 1.07,
            "amat" to 2.88, "ede" to 2.94, "tar1" to 2.53, "cog1" to 2.85)),
        "SÖZ" to Coef(129.61, mapOf("tr" to 1.13, "sos" to 1.19, "mat" to 1.31, "fen" to 1.01,
            "ede" to 2.79, "tar1" to 2.39, "cog1" to 2.70, "tar2" to 3.80, "cog2" to 2.47, "fel" to 3.76, "din" to 2.36)),
    )

    fun net(correct: Int, wrong: Int) = correct - wrong / 4.0

    /** Ham puan. nets: test anahtarı → net */
    fun rawScore(type: String, nets: Map<String, Double>): Double {
        val c = COEF.getValue(type)
        return c.base + c.c.entries.sumOf { (k, v) -> (nets[k] ?: 0.0).coerceAtLeast(0.0) * v }
    }

    /** OBP = diploma × 5. Yerleştirme puanına OBP × 0,12 eklenir (önceki yıl yerleşenlerde 0,06). */
    fun obpBonus(diploma: Double, broken: Boolean): Double {
        val obp = (diploma.coerceIn(50.0, 100.0)) * 5
        return obp * if (broken) 0.06 else 0.12
    }

    /** TYT ham puanı 150'nin altındaysa AYT puan türleri hesaplanmaz (ÖSYM kuralı) */
    const val MIN_TYT = 150.0

    // ---- 2025 yerleştirme puanı → başarı sırası noktaları ----
    private val RANK = mapOf(
        "SAY" to listOf(
            556.594 to 2, 551.132 to 38, 546.988 to 146, 543.166 to 337, 542.085 to 411, 535.805 to 1028, 533.832 to 1321,
            501.736 to 10336, 490.556 to 15829, 480.548 to 21941, 477.693 to 23836, 474.817 to 25895, 469.390 to 29897,
            465.453 to 32953, 461.603 to 36049, 454.521 to 42051, 451.059 to 45191, 446.528 to 49259, 440.035 to 55359,
            434.703 to 60632, 428.829 to 66616, 425.664 to 69943, 420.115 to 75866, 416.451 to 79999, 409.894 to 87231,
            404.813 to 93090, 400.016 to 98967, 395.387 to 104588, 385.918 to 116931, 375.193 to 131919, 370.407 to 138978,
            360.847 to 154464, 354.614 to 165558, 344.131 to 185928, 337.725 to 199824, 330.342 to 216915, 322.941 to 236238,
            313.717 to 262612, 304.705 to 291937, 270.206 to 448641, 255.282 to 551980, 239.310 to 703231, 223.905 to 886518,
            202.168 to 1133023, 186.331 to 1247930, 174.173 to 1283860, 160.223 to 1291032,
        ),
        "EA" to listOf(
            438.695 to 5403, 430.178 to 8078, 426.639 to 9589, 419.719 to 13371, 412.459 to 18307, 405.984 to 23822,
            399.469 to 30551, 394.448 to 36539, 388.433 to 44731, 383.789 to 51726, 376.821 to 63703, 372.422 to 71980,
            366.708 to 83849, 360.900 to 97040, 355.691 to 109898, 351.175 to 122079, 343.143 to 145941, 336.474 to 168545,
            331.129 to 188459, 324.851 to 214425, 313.488 to 268699, 303.325 to 326766, 290.754 to 413234, 282.588 to 478604,
            274.535 to 549859, 262.117 to 673214, 243.562 to 881911, 220.838 to 1147391, 201.298 to 1343147,
            184.463 to 1453879, 175.101 to 1482226, 158.068 to 1494073,
        ),
        "SÖZ" to listOf(
            501.728 to 41, 475.208 to 191, 444.076 to 961, 432.709 to 1668, 423.338 to 2639, 418.948 to 3229, 409.175 to 5241,
            404.761 to 6449, 401.071 to 7671, 396.907 to 9262, 386.903 to 14287, 378.191 to 20551, 373.695 to 24650,
            369.736 to 28623, 360.206 to 40657, 353.469 to 51467, 332.368 to 101372, 316.871 to 158264, 305.025 to 216437,
            292.123 to 295096, 288.201 to 322045, 256.721 to 593323, 233.515 to 817510, 213.309 to 986650,
            191.747 to 1114542, 167.488 to 1170620,
        ),
        // TYT: yalnızca kaba aralıklar (YES Akademi)
        "TYT" to listOf(389.0 to 200000, 335.0 to 500000, 288.0 to 1000000),
    )

    /** Puan sırasına göre (büyükten küçüğe), sıralaması tutarlı olan noktalar */
    private fun points(type: String): List<Pair<Double, Double>> {
        val raw = RANK[type] ?: return emptyList()
        val sorted = raw.sortedByDescending { it.first }
        val out = mutableListOf<Pair<Double, Double>>()
        var lastRank = 0
        for ((s, r) in sorted) if (r > lastRank) { out.add(s to r.toDouble()); lastRank = r }
        return out
    }

    class RankEstimate(val rank: Long, val note: Note) {
        enum class Note { EXACT_RANGE, ABOVE_DATA, BELOW_DATA, NO_DATA }
    }

    /** Yerleştirme puanından tahmini başarı sırası */
    fun estimateRank(type: String, placementScore: Double): RankEstimate {
        val p = points(type)
        if (p.isEmpty()) return RankEstimate(0, RankEstimate.Note.NO_DATA)
        if (placementScore >= p.first().first) return RankEstimate(p.first().second.roundToLong(), RankEstimate.Note.ABOVE_DATA)
        if (placementScore <= p.last().first) return RankEstimate(p.last().second.roundToLong(), RankEstimate.Note.BELOW_DATA)
        for (i in 0 until p.size - 1) {
            val (s1, r1) = p[i]; val (s2, r2) = p[i + 1]
            if (placementScore <= s1 && placementScore >= s2) {
                val t = (s1 - placementScore) / (s1 - s2)
                val r = exp(ln(r1) + t * (ln(r2) - ln(r1)))
                return RankEstimate(r.roundToLong().coerceAtLeast(1), RankEstimate.Note.EXACT_RANGE)
            }
        }
        return RankEstimate(0, RankEstimate.Note.NO_DATA)
    }

    /** Hedef sıralama için gereken yaklaşık yerleştirme puanı (veri aralığı dışında null) */
    fun scoreForRank(type: String, rank: Long): Double? {
        val p = points(type)
        if (p.size < 2 || rank < p.first().second || rank > p.last().second) return null
        for (i in 0 until p.size - 1) {
            val (s1, r1) = p[i]; val (s2, r2) = p[i + 1]
            if (rank >= r1 && rank <= r2) {
                val t = (ln(rank.toDouble()) - ln(r1)) / (ln(r2) - ln(r1))
                return s1 + t * (s2 - s1)
            }
        }
        return null
    }

    fun hasRankData(type: String) = RANK.containsKey(type)
}
