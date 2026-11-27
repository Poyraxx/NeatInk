package com.xaser.note.recognition

import com.xaser.note.ink.ShapeSnapper
import com.xaser.note.model.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * El yazısını tanımaya göndermeden önce yerleşimini çözer:
 *  - kesir çizgisi (üstünde pay, altında payda) → çizgi düzeltilir, pay ve payda ayrı tanınır
 *  - uzun düz çizgiler → yazı sanılmaz, dümdüz yapılır
 *  - + − = × ÷ işaretleri → şeklinden tanınır (tek başına yazılınca tanıyıcı karıştırıyor)
 *  - nokta, şapka, çengel gibi küçük işaretler → ait oldukları satıra bağlanır (ü, ö, ş, ç, ğ, İ, ?)
 *  - yalnız başına gelen noktalar/şapkalar → zaten metne çevrilmiş harfi düzeltir (u→ü, s→ş …)
 * Saf Kotlin: Android'e bağımlı değil, birim testi yazılabilir.
 */
object MathLayout {

    class Box(var l: Float, var t: Float, var r: Float, var b: Float) {
        val w get() = r - l
        val h get() = b - t
        val cx get() = (l + r) / 2
        val cy get() = (t + b) / 2
        fun union(o: Box) = Box(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
        fun copy() = Box(l, t, r, b)
    }

    /** Tanınacak (ya da şekilden tanınmış) bir öbek */
    class Group(val strokes: List<Stroke>, val box: Box, val symbol: String? = null, var math: Boolean = false) {
        val onlySmall get() = symbol == null && strokes.isNotEmpty()
    }

    /** Var olan bir metin kutusundaki harfe eklenecek işaret */
    enum class Mark { DOTS_ABOVE, DOT_ABOVE, BREVE_ABOVE, HOOK_BELOW }

    class Plan(
        val groups: List<Group>,
        /** düzeltilecek çizgiler: eski → yeni (dümdüz) */
        val straightened: List<Pair<Stroke, Stroke>>,
        /** yalnız işaret öbekleri (metin kutusuna eklenmek üzere) */
        val marks: List<Pair<Group, Mark>>,
        /** yazı birim yüksekliği (tahmini) */
        val unit: Float,
    )

    // ---------------- yardımcılar ----------------

    fun rawBox(s: Stroke): Box {
        val p = s.points
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) { l = min(l, p[i]); r = max(r, p[i]); t = min(t, p[i + 1]); b = max(b, p[i + 1]); i += 3 }
        return Box(l, t, r, b)
    }

    fun pathLength(s: Stroke): Float {
        val p = s.points; var len = 0f; var i = 3
        while (i < p.size) { len += hypot(p[i] - p[i - 3], p[i + 1] - p[i - 2]); i += 3 }
        return len
    }

    fun chord(s: Stroke): Float { val p = s.points; val n = p.size; return if (n < 6) 0f else hypot(p[n - 3] - p[0], p[n - 2] - p[1]) }

    /** 1 = dümdüz */
    fun straightness(s: Stroke): Float { val l = pathLength(s); return if (l < 1e-3f) 0f else chord(s) / l }

    /** Çizginin yatayla açısı (0..90 derece) */
    fun angle(s: Stroke): Float {
        val p = s.points; val n = p.size
        if (n < 6) return 0f
        val a = abs(Math.toDegrees(atan2((p[n - 2] - p[1]).toDouble(), (p[n - 3] - p[0]).toDouble())).toFloat())
        return if (a > 90f) 180f - a else a
    }

    fun isStraight(s: Stroke, min: Float = 0.93f) = s.points.size >= 6 && straightness(s) >= min
    fun isHorizontal(s: Stroke) = isStraight(s) && angle(s) < 16f
    fun isVertical(s: Stroke) = isStraight(s) && angle(s) > 70f

    /** Yazı yüksekliği: yassı çizgiler hariç yüksekliklerin ortancası */
    fun unitHeight(strokes: List<Stroke>): Float {
        val hs = strokes.map { rawBox(it) }.filter { it.h >= it.w * 0.35f && it.h > 3f }.map { it.h }.sorted()
        if (hs.isNotEmpty()) return max(12f, hs[hs.size / 2])
        val ws = strokes.map { rawBox(it).w }.sorted()
        return if (ws.isEmpty()) 40f else max(12f, ws[ws.size / 2] * 0.7f)
    }

    /** Uç noktaları birleştiren düz çizgi; neredeyse yatay/dikeyse tam yatay/dikey */
    fun straighten(s: Stroke): Stroke {
        val p = s.points; val n = p.size
        var x0 = p[0]; var y0 = p[1]; var x1 = p[n - 3]; var y1 = p[n - 2]
        val a = angle(s)
        if (a < 6f) { val y = (y0 + y1) / 2; y0 = y; y1 = y }
        if (a > 84f) { val x = (x0 + x1) / 2; x0 = x; x1 = x }
        return Stroke(s.pen, s.color, s.width, ShapeSnapper.line(x0, y0, x1, y1)).also { it.retry = s.retry }
    }

    // ---------------- ana analiz ----------------

    /**
     * @param textBoxes sayfadaki var olan metin kutularının sınırları (kesir payı önceden metne çevrilmiş olabilir,
     *                  ya da yalnız gelen noktalar bir harfe ait olabilir)
     */
    fun analyze(strokes: List<Stroke>, textBoxes: List<Box> = emptyList()): Plan {
        val boxes = strokes.associateWith { rawBox(it) }
        // Yalnız küçük işaretler geldiyse (sonradan konan nokta/şapka) yükseklik yanındaki metinden alınır
        val textH = textBoxes.map { it.h }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
        val h = if (textH > 0f && boxes.values.all { max(it.w, it.h) < 0.3f * textH }) textH else unitHeight(strokes)
        val used = HashSet<Stroke>()
        val straightened = ArrayList<Pair<Stroke, Stroke>>()
        val groups = ArrayList<Group>()

        // 1) Kesir çizgileri (uzundan kısaya)
        val bars = strokes.filter { isHorizontal(it) && boxes.getValue(it).w >= 0.6f * h }.sortedByDescending { boxes.getValue(it).w }
        for (bar in bars) {
            if (bar in used) continue
            val bb = boxes.getValue(bar)
            val y = bb.cy; val len = bb.w
            val x0 = bb.l - 0.15f * len; val x1 = bb.r + 0.15f * len
            fun inX(b: Box) = b.cx in x0..x1
            val others = strokes.filter { it !== bar && it !in used }
            val above = others.filter { val b = boxes.getValue(it); inX(b) && b.b <= y + 0.25f * h && b.b >= y - 1.8f * h }
            val below = others.filter { val b = boxes.getValue(it); inX(b) && b.t >= y - 0.25f * h && b.t <= y + 1.8f * h }
            val textAbove = textBoxes.any { inX(it) && it.b <= y + 0.3f * h && it.b >= y - 1.8f * h }
            if (below.isNotEmpty() && (above.isNotEmpty() || textAbove)) {
                used.add(bar); straightened.add(bar to straighten(bar))
                if (above.isNotEmpty()) { used.addAll(above); groups.addAll(lineGroups(above, h, boxes).onEach { it.math = true }) }
                used.addAll(below); groups.addAll(lineGroups(below, h, boxes).onEach { it.math = true })
            } else if (textAbove && above.isEmpty()) {
                // Pay önceden metne çevrildi, payda henüz yazılmadı: bu bir kesir çizgisi, "-" değil
                used.add(bar); straightened.add(bar to straighten(bar))
            }
        }

        // 2) Uzun düz çizgiler (yazı değil)
        for (s in strokes) {
            if (s in used || !isStraight(s, 0.95f)) continue
            val len = chord(s)
            val longLimit = if (isHorizontal(s)) 2.4f * h else 3.0f * h
            if (len > longLimit) { used.add(s); straightened.add(s to straighten(s)) }
        }

        // 3) Kalanlar satırlara
        val rest = strokes.filter { it !in used }
        val restGroups = lineGroups(rest, h, boxes)

        // 4) Yalnız işaret öbekleri: var olan metne ait mi?
        val marks = ArrayList<Pair<Group, Mark>>()
        for (g in restGroups) {
            val mk = markOf(g, h, boxes)
            if (mk != null && g.symbol == null && textBoxes.any { near(g.box, it, mk) }) marks.add(g to mk)
            else groups.add(g)
        }
        return Plan(groups, straightened, marks, h)
    }

    /** Öbek, bir metin kutusunun hemen üstünde/altında mı */
    fun near(g: Box, t: Box, mk: Mark): Boolean {
        val overlapX = g.cx >= t.l - t.h * 0.1f && g.cx <= t.r + t.h * 0.1f
        return overlapX && when (mk) {
            Mark.HOOK_BELOW -> g.t >= t.b - t.h * 0.5f && g.t <= t.b + t.h * 0.6f
            else -> g.b <= t.t + t.h * 0.6f && g.b >= t.t - t.h * 0.9f
        }
    }

    /** Yalnız küçük işaretlerden oluşan öbeğin türü */
    fun markOf(g: Group, h: Float, boxes: Map<Stroke, Box>): Mark? {
        if (g.strokes.isEmpty() || g.strokes.size > 2) return null
        val bs = g.strokes.map { boxes[it] ?: rawBox(it) }
        if (bs.any { max(it.w, it.h) > 0.5f * h }) return null
        val dotLike = bs.all { max(it.w, it.h) < 0.22f * h }
        return when {
            g.strokes.size == 2 && dotLike -> Mark.DOTS_ABOVE
            g.strokes.size == 1 && dotLike -> Mark.DOT_ABOVE
            g.strokes.size == 1 && bs[0].w > bs[0].h * 1.3f -> Mark.BREVE_ABOVE
            g.strokes.size == 1 -> Mark.HOOK_BELOW
            else -> null
        }
    }

    /** Var olan metindeki harfe işaret ekler; ekleyemezse null */
    fun applyMark(text: String, index: Int, mk: Mark, below: Boolean): String? {
        if (index !in text.indices) return null
        val c = text[index]
        val map: Map<Char, Char> = when {
            mk == Mark.DOTS_ABOVE -> mapOf('u' to 'ü', 'o' to 'ö', 'U' to 'Ü', 'O' to 'Ö', 'ı' to 'i', 'n' to 'ñ')
            mk == Mark.DOT_ABOVE -> mapOf('I' to 'İ', 'ı' to 'i', 'l' to 'i', '1' to 'i')
            mk == Mark.BREVE_ABOVE -> mapOf('g' to 'ğ', 'G' to 'Ğ', 'q' to 'ğ', '9' to 'ğ')
            below || mk == Mark.HOOK_BELOW -> mapOf('s' to 'ş', 'S' to 'Ş', 'c' to 'ç', 'C' to 'Ç')
            else -> emptyMap()
        }
        val nc = map[c] ?: return null
        return text.substring(0, index) + nc + text.substring(index + 1)
    }

    /**
     * Çizgileri satır öbeklerine ayırır. Küçük çizgiler (nokta, şapka, çengel, eksi) en yakın satıra bağlanır;
     * böylece ü, ö, ş, ç, ğ, İ, ? doğru tanınır.
     */
    fun lineGroups(strokes: List<Stroke>, h: Float, boxes: Map<Stroke, Box>): List<Group> {
        if (strokes.isEmpty()) return emptyList()
        val bx = { s: Stroke -> boxes[s] ?: rawBox(s) }
        val big = strokes.filter { val b = bx(it); max(b.w, b.h) >= 0.45f * h }
        val small = strokes.filter { it !in big }
        val lines = ArrayList<MutableList<Stroke>>()
        val lb = ArrayList<Box>()
        for (s in big.sortedBy { bx(it).cy }) {
            val b = bx(s)
            val idx = lb.indexOfFirst { r ->
                val band = max(r.h, h) * 0.45f
                b.cy >= r.cy - band && b.cy <= r.cy + band
            }
            if (idx >= 0) { lines[idx].add(s); lb[idx] = lb[idx].union(b) } else { lines.add(mutableListOf(s)); lb.add(b.copy()) }
        }
        for (s in small) {
            val b = bx(s)
            var best = -1; var bestD = Float.MAX_VALUE
            for (i in lb.indices) {
                val r = lb[i]
                val inX = b.cx >= r.l - 0.35f * h && b.cx <= r.r + 0.35f * h
                if (!inX) continue
                val dy = when { b.cy < r.t -> r.t - b.cy; b.cy > r.b -> b.cy - r.b; else -> 0f }
                if (dy < max(r.h, h) * 0.9f && dy < bestD) { bestD = dy; best = i }
            }
            if (best >= 0) { lines[best].add(s); lb[best] = lb[best].union(b) }
            else { lines.add(mutableListOf(s)); lb.add(b.copy()) }
        }
        return lines.indices.map { i ->
            val list = lines[i].sortedBy { bx(it).l }
            Group(list, lb[i], symbolOf(list, h, boxes))
        }.sortedWith(compareBy({ it.box.t }, { it.box.l }))
    }

    /** Tanıyıcının tek başına karıştırdığı işaretleri şeklinden tanır */
    fun symbolOf(strokes: List<Stroke>, h: Float, boxes: Map<Stroke, Box>): String? {
        val bs = strokes.map { boxes[it] ?: rawBox(it) }
        when (strokes.size) {
            1 -> {
                val s = strokes[0]
                if (isHorizontal(s) && bs[0].w <= 2.4f * h) return "-"
            }
            2 -> {
                val (a, b) = strokes; val ba = bs[0]; val bb = bs[1]
                val la = max(ba.w, ba.h); val lb = max(bb.w, bb.h)
                val centersClose = hypot(ba.cx - bb.cx, ba.cy - bb.cy) < 0.45f * max(la, lb)
                if (isHorizontal(a) && isVertical(b) || isVertical(a) && isHorizontal(b)) {
                    if (centersClose) return "+"
                }
                if (isHorizontal(a) && isHorizontal(b)) {
                    val ov = min(ba.r, bb.r) - max(ba.l, bb.l)
                    val gap = abs(ba.cy - bb.cy)
                    if (ov > 0.5f * min(ba.w, bb.w) && gap < 0.9f * max(ba.w, bb.w) && gap > 0.08f * h) return "="
                }
                val diagA = isStraight(a) && angle(a) in 25f..65f
                val diagB = isStraight(b) && angle(b) in 25f..65f
                if (diagA && diagB && centersClose && slopeSign(a) != slopeSign(b)) return "×"
            }
            3 -> {
                // ÷ : yatay çizgi + üstte ve altta birer nokta
                val i = strokes.indexOfFirst { isHorizontal(it) }
                if (i >= 0) {
                    val bar = bs[i]
                    val dots = bs.filterIndexed { k, _ -> k != i }
                    if (dots.all { max(it.w, it.h) < 0.35f * bar.w } && dots.any { it.cy < bar.cy } && dots.any { it.cy > bar.cy }) return "÷"
                }
            }
        }
        return null
    }

    private fun slopeSign(s: Stroke): Boolean {
        val p = s.points; val n = p.size
        return (p[n - 3] - p[0]) * (p[n - 2] - p[1]) > 0
    }
}


/** Kesir pay/paydasında harfle karışan rakamları tercih et */
object MathText {
    private val lookAlike = mapOf('o' to '0', 'O' to '0', 'D' to '0', 'l' to '1', 'I' to '1', 'i' to '1', '|' to '1',
        'z' to '2', 'Z' to '2', 's' to '5', 'S' to '5', 'b' to '6', 'g' to '9', 'q' to '9', 'B' to '8', 'T' to '7')
    private val mathChars = Regex("^[0-9+\\-=×÷()\\[\\].,/xyzabcnπ√^ ]+$")

    /** Adaylardan matematik yazısına en uygun olanı seçer */
    fun pick(cands: List<String>, math: Boolean): String? {
        if (cands.isEmpty()) return null
        if (!math) return cands.first()
        cands.firstOrNull { it.all { c -> c.isDigit() } }?.let { return it }
        cands.firstOrNull { mathChars.matches(it) }?.let { return it }
        val top = cands.first()
        // Tek karakterlik harf yanılgısı: rakama çevir
        if (top.length <= 3 && top.all { it.isDigit() || it in lookAlike }) return top.map { lookAlike[it] ?: it }.joinToString("")
        return top
    }
}
