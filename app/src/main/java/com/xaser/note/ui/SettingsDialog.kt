package com.xaser.note.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.L
import com.xaser.note.Lang
import com.xaser.note.UpdateChecker

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Ayarlar: dil seçimi, sürüm, GitHub sayfası */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Ayarlar", "Settings")) },
        text = {
            Column {
                Text(L("Dil", "Language"), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                listOf(
                    "system" to L("Cihaz dili", "Device language"),
                    "tr" to "Türkçe",
                    "en" to "English",
                ).forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { Lang.set(ctx, code) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(Lang.mode == code, { Lang.set(ctx, code) }, colors = RadioButtonDefaults.colors(selectedColor = Accent))
                        Text(label, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(L("Yedekleme", "Backup"), fontWeight = FontWeight.SemiBold)
                Text(L("Tüm notlar, PDF'ler ve hata defteri tek bir .zip dosyasına kaydedilir. Geri yüklerken cihazdaki notlar silinmez.",
                    "All notes, PDFs and mistakes are saved to one .zip file. Restoring never deletes notes on this device."),
                    color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onBackup) { Text(L("Yedekle", "Back up"), color = AccentDark) }
                    TextButton(onRestore) { Text(L("Geri yükle", "Restore"), color = AccentDark) }
                }
                Spacer(Modifier.height(8.dp))
                Text(L("Sürüm", "Version") + " " + UpdateChecker.currentVersionName(ctx), color = TextSecondary, fontSize = 14.sp)
                Text(
                    "GitHub: github.com/Poyraxx/NeatInk", color = AccentDark, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { openUrl(ctx, UpdateChecker.REPO_URL) },
                )
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(L("Kapat", "Close"), color = Accent) } },
    )
}

/** Yeni sürüm varsa ana ekranda görünen şerit */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val upd = UpdateChecker.available ?: return
    val ctx = LocalContext.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF3E0))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.SystemUpdate, null, tint = AccentDark)
        Text(
            L("Güncelleme var: sürüm ${upd.versionName}", "Update available: version ${upd.versionName}"),
            color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { openUrl(ctx, upd.url) }) {
            Text(L("GitHub'dan indir", "Download on GitHub"), color = AccentDark, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { UpdateChecker.dismiss() }) {
            Icon(Icons.Outlined.Close, L("Kapat", "Close"), tint = TextSecondary)
        }
        Spacer(Modifier.width(2.dp))
    }
}
