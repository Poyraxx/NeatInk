package com.xaser.note

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Uygulama dili: "system" (cihaz diline göre), "tr" veya "en".
 * Ekran yazıları L("Türkçe", "English") ile yazılır; dil değişince arayüz kendini yeniler.
 */
object Lang {
    private const val PREFS = "app"
    private const val KEY = "lang"

    /** Kullanıcının seçimi: system / tr / en */
    var mode by mutableStateOf("system")
        private set

    /** Etkin dil: tr veya en */
    var current by mutableStateOf("tr")
        private set

    val isEnglish get() = current == "en"

    fun init(ctx: Context) {
        mode = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "system") ?: "system"
        current = resolve(mode)
    }

    fun set(ctx: Context, newMode: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, newMode).apply()
        mode = newMode
        current = resolve(newMode)
    }

    private fun resolve(m: String) = when (m) {
        "tr", "en" -> m
        else -> if (Locale.getDefault().language == "tr") "tr" else "en"
    }

    val locale: Locale get() = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("tr")
}

/** Türkçe / İngilizce metin seçer */
fun L(tr: String, en: String): String = if (Lang.isEnglish) en else tr
