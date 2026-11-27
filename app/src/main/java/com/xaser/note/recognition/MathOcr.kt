package com.xaser.note.recognition

import kotlin.math.max
import kotlin.math.min

/**
 * Fotodan okunan yazıyı matematiğe uygun hale getirir (saf Kotlin, test edilebilir):
 *  - üs / indis: küçük ve yukarıda duran karakter → ², küçük ve aşağıda duran → ₂
 *  - sembol düzeltme: <= ≥ ≠, √, π
 *  - çoğu rakam olan ifadelerde harf-rakam karışması: O→0, l→1, S→5 …
 *  - yalnız çizgiden oluşan satır (— — —) → kesir çizgisi
 */
object MathOcr {

    /** Tek karakter ve kutusu; word: aynı kelimedeki karakterler aynı numarayı taşır */
    class Sym(val text: String, val l: Float, val t: Float, val r: Float, val b: Float, val word: Int,
              /** Büyütülüp yeniden okundu: küçük karakter olduğu kesin (üs/indis adayı) */
              val script: Boolean = false) {
        val h get() = b - t
        val cy get() = (t + b) / 2
    }

    private val SUP = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
        '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'x' to 'ˣ', 'i' to 'ⁱ')
    private val SUB = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇',
        '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'n' to 'ₙ', 'x' to 'ₓ', 'i' to 'ᵢ')

    private fun median(v: List<Float>) = v.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }

    /**
     * Bir satırın karakterlerinden metni kurar; üs ve indisleri yerleşimden bulur.
     * Üs/indis yapılamayan karakter (eşlemesi yoksa) olduğu gibi kalır.
     */
    fun buildLine(syms: List<Sym>): String {
        if (syms.isEmpty()) return ""
        val sorted = syms.sortedBy { it.l }
        val medH = median(sorted.map { it.h })
        val full = sorted.filter { it.h >= medH * 0.75f }.ifEmpty { sorted }
        val base = median(full.map { it.b })      // taban çizgisi
        val top = median(full.map { it.t })       // büyük harf üstü
        val sb = StringBuilder()
        var prevWord = sorted.first().word
        var prev: Sym? = null
        for (s in sorted) {
            if (s.word != prevWord) { sb.append(' '); prevWord = s.word }
            val small = (s.h < medH * 0.72f || s.script) && s.text.length == 1
            val c = s.text.firstOrNull()
            val out = when {
                // Büyütülüp yeniden okunan (önce noktalama sanılan) karakter: satırın alt yarısında → indis, üstte → üs
                // Üssün alt ucu taban çizgisinin belirgin üstündedir; değilse indis
                s.script && prev != null && s.text.length == 1 -> (if (s.b >= base - medH * 0.2f) SUB[c] else SUP[c])?.toString()
                // Üs: küçük, alt kenarı tabanın belirgin üstünde, öncesinde bir karakter var
                small && prev != null && s.b < base - medH * 0.25f && s.cy < (top + base) / 2 -> SUP[c]?.toString()
                // İndis: küçük, alt kenarı tabanın altında
                small && prev != null && s.b > base + medH * 0.12f && (s.t > top + medH * 0.3f || s.script) -> SUB[c]?.toString()
                else -> null
            }
            sb.append(out ?: s.text)
            prev = s
        }
        return sb.toString()
    }

    /** Bir OCR satırı: kutusu ve karakterleri */
    class LineData(val syms: MutableList<Sym>, var l: Float, var t: Float, var r: Float, var b: Float) {
        val h get() = b - t
        val cx get() = (l + r) / 2
    }

    /**
     * Ayrı satır olarak okunmuş küçük üs/indisleri (x² içindeki "2" gibi) yanındaki satıra katar.
     * Katılan karakterler konumlarını korur; buildLine onları üs/indis olarak tanır.
     */
    fun mergeScripts(lines: List<LineData>): List<LineData> {
        if (lines.size < 2) return lines
        val medH = median(lines.map { it.h })
        val out = lines.toMutableList()
        for (s in lines.sortedBy { it.h }) {
            if (s !in out || s.syms.isEmpty() || s.syms.size > 3 || s.h >= medH * 0.75f) continue
            val target = out.filter { it !== s && it.h >= s.h * 1.25f }.firstOrNull { bl ->
                val inX = s.cx >= bl.l - 0.2f * bl.h && s.l <= bl.r + 0.6f * bl.h
                val sup = s.b <= bl.t + 0.65f * bl.h && s.b >= bl.t - 0.4f * bl.h
                val sub = s.t >= bl.b - 0.65f * bl.h && s.t <= bl.b + 0.4f * bl.h
                inX && (sup || sub)
            } ?: continue
            // Kelime numarası: hemen solundaki karakterin kelimesi (boşluk açılmasın)
            val leftWord = target.syms.filter { it.r <= s.l + 4f }.maxByOrNull { it.r }?.word ?: target.syms.firstOrNull()?.word ?: 0
            s.syms.forEach { target.syms.add(Sym(it.text, it.l, it.t, it.r, it.b, leftWord, it.script)) }
            target.l = min(target.l, s.l); target.r = max(target.r, s.r)
            out.remove(s)
        }
        return out
    }

    /** Satır başındaki başıboş noktalama (",x² + 3" gibi) */
    fun stripLeadingNoise(text: String): String = text.replace(Regex("^[\\s,.'`‚’‘\"„´¸·]+"), "")

    /** Üs/indis yerinde duran ve noktalama sanılmış karakter mi (yeniden okunmalı) */
    fun isSuspiciousScript(s: Sym, lineSyms: List<Sym>): Boolean {
        if (s.text !in setOf(",", ".", "'", "`", "’", "‘")) return false
        val medH = median(lineSyms.map { it.h })
        return s.h >= medH * 0.4f && lineSyms.firstOrNull() !== s
    }

    /** Yaygın OCR karışıklıklarını matematik sembollerine çevirir */
    fun normalize(text: String): String {
        var t = text
        t = t.replace("<=", "≤").replace(">=", "≥").replace("=<", "≤").replace("=>", "≥")
        t = t.replace("!=", "≠").replace("=/=", "≠").replace("=/", "≠")
        t = t.replace("+-", "±").replace("+/-", "±")
        // Rakam, parantez ya da harften önce gelen V / v → kök (V2, V(x+1), Vx gibi); kelime içinde değil
        t = Regex("(^|[\\s=+\\-×÷(])[Vv](?=[0-9(])").replace(t) { it.groupValues[1] + "√" }
        // Tek başına TT / Tt / 7T → π
        t = Regex("(^|[\\s=+\\-×÷(0-9])(TT|Tt|II)(?=$|[\\s=+\\-×÷)0-9])").replace(t) { it.groupValues[1] + "π" }
        // Rakamlar arasındaki x / X → çarpı (2x3 → 2×3); değişken olan x'e dokunmaz (2x tek başına kalır)
        t = Regex("(?<=[0-9])\\s*[xX]\\s*(?=[0-9])").replace(t, "×")
        return t
    }

    private val digitLike = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'l' to '1', 'I' to '1', '|' to '1',
        'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5', 'B' to '8', 'G' to '6', 'b' to '6', 'g' to '9', 'q' to '9')

    /** Çoğu rakam olan kelimelerde harf sanılan rakamları düzeltir (2O19 → 2019, l5 → 15) */
    private const val MATH_CHARS = "+-×÷=().,/²³⁰¹⁴⁵⁶⁷⁸⁹⁺⁻%"

    fun fixDigits(text: String): String = text.split(' ').joinToString(" ") { w ->
        val digits = w.count { it.isDigit() }
        val looks = w.count { it in digitLike }
        // Yalnız rakam, rakama benzeyen harf ve işlem işaretlerinden oluşan, rakamı ağır basan kelimeler
        val onlyMath = w.all { it.isDigit() || it in digitLike || it in MATH_CHARS }
        if (looks > 0 && digits >= 1 && digits >= looks && onlyMath) w.map { digitLike[it] ?: it }.joinToString("") else w
    }

    /** Satır yalnız çizgi karakterlerinden mi oluşuyor (kesir çizgisi okunmuş) */
    fun isBarText(text: String, w: Float, h: Float): Boolean =
        text.isNotBlank() && text.all { it in "-—–_‒―" } && w > h * 2f

    /** Bir kesir çizgisinin üstündeki ve altındaki metinleri çizginin ortasına hizalamak için yeni x değerleri */
    class Item(val l: Float, val t: Float, val r: Float, val b: Float) {
        val cx get() = (l + r) / 2
    }

    /**
     * @param bars yatay çizgiler: x0, x1, y
     * @return metin indeksi → yeni sol x (yalnızca hizalanacaklar)
     */
    fun centerOverBars(items: List<Item>, bars: List<Triple<Float, Float, Float>>, unit: Float): Map<Int, Float> {
        val out = HashMap<Int, Float>()
        for ((x0, x1, y) in bars) {
            val len = x1 - x0
            val near = items.withIndex().filter { (_, it) ->
                it.cx in (x0 - 0.2f * len)..(x1 + 0.2f * len) && (it.r - it.l) < len * 1.6f &&
                    ((it.b <= y + 0.3f * unit && it.b >= y - 1.6f * unit) || (it.t >= y - 0.3f * unit && it.t <= y + 1.6f * unit))
            }
            val above = near.any { it.value.b <= y + 0.3f * unit }
            val below = near.any { it.value.t >= y - 0.3f * unit }
            if (!above || !below) continue   // gerçekten kesir değil
            val mid = (x0 + x1) / 2
            for ((i, it) in near) out[i] = mid - (it.r - it.l) / 2
        }
        return out
    }

    /** Birim yükseklik (satır kutularının ortancası) */
    fun unit(items: List<Item>) = max(10f, median(items.map { it.b - it.t }))

    @Suppress("unused")
    private fun clamp(v: Float, a: Float, b: Float) = min(b, max(a, v))
}
