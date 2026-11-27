package com.xaser.note.model

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.xaser.note.R

/** Düzeltilmiş yazılarda kullanılabilecek yazı tipleri (hepsi Türkçe harfleri destekler, OFL lisanslı) */
class NoteFont(
    val id: String,
    private val nameTr: String,
    /** res/font kaynağı; null ise sistem yazı tipi */
    val res: Int?,
    val system: Typeface?,
    val handwriting: Boolean,
    private val nameEn: String = nameTr,
) {
    val name: String get() = com.xaser.note.L(nameTr, nameEn)
}

object NoteFonts {
    const val DEFAULT_FIX = "kalam"
    const val PLAIN = "sans"

    val all = listOf(
        NoteFont("kalam", "Kalam", R.font.kalam, null, true),
        NoteFont("caveat", "Caveat", R.font.caveat, null, true),
        NoteFont("patrick_hand", "Patrick Hand", R.font.patrick_hand, null, true),
        NoteFont("architects_daughter", "Architects Daughter", R.font.architects_daughter, null, true),
        NoteFont("shadows_two", "Shadows Into Light", R.font.shadows_two, null, true),
        NoteFont("sriracha", "Sriracha", R.font.sriracha, null, true),
        NoteFont("dancing_script", "Dancing Script (bitişik)", R.font.dancing_script, null, true, "Dancing Script (cursive)"),
        NoteFont("sans", "Düz yazı", null, Typeface.SANS_SERIF, false, "Sans"),
        NoteFont("serif", "Kitap yazısı", null, Typeface.SERIF, false, "Serif"),
        NoteFont("mono", "Daktilo", null, Typeface.MONOSPACE, false, "Typewriter"),
    )

    fun byId(id: String?): NoteFont = all.firstOrNull { it.id == id } ?: all.first { it.id == PLAIN }

    private val cache = HashMap<String, Typeface>()

    fun typeface(ctx: Context, id: String?): Typeface {
        val f = byId(id)
        cache[f.id]?.let { return it }
        val tf = f.system ?: f.res?.let { runCatching { ResourcesCompat.getFont(ctx, it) }.getOrNull() } ?: Typeface.DEFAULT
        cache[f.id] = tf
        return tf
    }
}
