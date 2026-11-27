package com.xaser.note.ink

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Xiaomi HyperOS, kalem tuşuna basılı tutulunca kendi not ekranını açar / ekran görüntüsü alır
 * (MiuiStylusShortcutManager). Bu davranış yalnızca Xiaomi Notlar'da veya "oyun modunda"
 * (Settings.Secure "gb_boosting" = 1) devre dışıdır. Not açıkken oyun modu bayrağını açarak
 * tuşları uygulamaya alırız; nottan çıkınca kapatırız.
 *
 * Gerekli izin bir kez adb ile verilir:
 *   adb shell pm grant com.xaser.note android.permission.WRITE_SECURE_SETTINGS
 */
object XiaomiPenKeys {
    private const val KEY = "gb_boosting"
    private var active = false

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun enable(ctx: Context) {
        if (!hasPermission(ctx)) return
        runCatching { Settings.Secure.putInt(ctx.contentResolver, KEY, 1); active = true }
    }

    fun disable(ctx: Context) {
        if (!hasPermission(ctx)) return
        runCatching {
            // Yalnızca bizim açtığımızı veya önceki çökmeden kalanı kapat
            if (active || Settings.Secure.getInt(ctx.contentResolver, KEY, 0) == 1) Settings.Secure.putInt(ctx.contentResolver, KEY, 0)
            active = false
        }
    }
}
