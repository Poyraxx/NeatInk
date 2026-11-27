package com.xaser.note

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uygulama açılışında, internet VARSA, GitHub'daki version.json'a bir kez bakar.
 * Güncelse hiçbir şey göstermez; yeni sürüm varsa ana ekranda "Güncelleme var" düğmesi çıkar.
 * İnternet yoksa hiç denemez — uygulama tamamen çevrimdışı çalışır.
 */
object UpdateChecker {
    const val REPO_URL = "https://github.com/Poyraxx/NeatInk"
    const val RELEASES_URL = "$REPO_URL/releases/latest"
    private const val VERSION_URL = "https://raw.githubusercontent.com/Poyraxx/NeatInk/main/version.json"

    class Update(val versionName: String, val url: String)

    /** Yeni sürüm varsa dolu */
    var available by mutableStateOf<Update?>(null)
        private set

    private var checked = false

    fun currentVersionCode(ctx: Context): Long =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode }.getOrDefault(0L)

    fun currentVersionName(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Uygulama başına yalnızca bir kez çalışır; hata olursa sessizce vazgeçer */
    suspend fun checkOnce(ctx: Context) {
        if (checked) return
        checked = true
        if (!isOnline(ctx)) return
        val mine = currentVersionCode(ctx)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(VERSION_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 5000; c.readTimeout = 5000
                c.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (c.responseCode != 200) return@runCatching null
                    val o = JSONObject(c.inputStream.bufferedReader().readText())
                    val code = o.getLong("versionCode")
                    if (code > mine) Update(o.optString("versionName", "?"), o.optString("url", RELEASES_URL)) else null
                } finally { c.disconnect() }
            }.getOrNull()
        }
        available = result
    }

    fun dismiss() { available = null }
}
