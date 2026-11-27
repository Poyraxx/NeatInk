package com.xaser.note.ui

import android.graphics.BitmapFactory
import com.xaser.note.L
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Calculate
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.model.NoteMeta
import com.xaser.note.model.NoteType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    version: Int,
    onOpen: (NoteMeta) -> Unit,
    onNewInk: (folder: String) -> Unit,
    onNewText: (folder: String) -> Unit,
    onImportPdf: (folder: String) -> Unit,
    onChanged: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenYks: () -> Unit,
) {
    val store = AppState.store
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var fabMenu by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun toast(s: String) = android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_LONG).show()
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.xaser.note.data.Backup.export(store, it) } }.isSuccess
            }
            toast(if (ok) L("Yedek kaydedildi", "Backup saved") else L("Yedek alınamadı", "Backup failed"))
        }
    }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { com.xaser.note.data.Backup.import(store, AppState.mistakes, it, ctx.cacheDir) } }
            }
            r.onSuccess { toast(L("${it.notes} not ve ${it.mistakes} hata sorusu geri yüklendi", "Restored ${it.notes} notes and ${it.mistakes} mistakes")); onChanged() }
             .onFailure { toast(L("Geri yüklenemedi: ", "Restore failed: ") + (it.message ?: "")) }
        }
    }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()

    @Suppress("UNUSED_EXPRESSION") version // yeniden çizim tetikleyicisi
    val list = store.notes
        .filter { folder.isEmpty() || it.folder == folder }
        .filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
        .sortedWith(compareByDescending<NoteMeta> { it.pinned }.thenByDescending { it.updated })

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Başlık
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Outlined.Close, L("İptal", "Cancel")) }
                    Text(L("${selected.size} öğe seçildi", "${selected.size} selected"), fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (selected.size == list.size) selected.clear() else { selected.clear(); selected.addAll(list.map { it.id }) }
                    }) { Text(if (selected.size == list.size) L("Seçimi kaldır", "Deselect all") else L("Tümünü seç", "Select all"), color = Accent) }
                } else {
                    Text(L("Notlar", "Notes"), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { settingsDialog = true }) { Icon(Icons.Outlined.Settings, L("Ayarlar", "Settings"), tint = TextPrimary) }
                }
            }
            if (!selecting) {
                Text(L("${store.notes.size} not", "${store.notes.size} notes"), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 30.dp, bottom = 10.dp))
                UpdateBanner(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            }
            // Arama
            Row(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(46.dp)
                    .clip(RoundedCornerShape(23.dp)).background(Color(0xFFEBEBEB)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = TextSecondary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(L("Notlarda ara", "Search notes"), color = TextSecondary, fontSize = 16.sp)
                    BasicTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = TextPrimary))
                }
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, L("Temizle", "Clear"), tint = TextSecondary) }
            }
            // Klasörler
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hata defteri girişi
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFE0B2))
                        .combinedClickable(onClick = onOpenMistakes).padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.School, null, tint = AccentDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val open = AppState.mistakes.openCount
                    Text(L("Hata Defteri", "Mistakes") + if (open > 0) " ($open)" else "", color = TextPrimary, fontSize = 15.sp)
                }
                // YKS net / puan / sıralama (yalnızca Türkçe arayüzde)
                if (!com.xaser.note.Lang.isEnglish) Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFE3F2FD))
                        .combinedClickable(onClick = onOpenYks).padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Calculate, null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("YKS Hesaplama", color = TextPrimary, fontSize = 15.sp)
                }
                FolderChip(L("Tümü", "All"), folder.isEmpty()) { folder = "" }
                store.folders.forEach { f -> FolderChip(f, folder == f) { folder = f } }
                IconButton(onClick = { newFolderDialog = true }) { Icon(Icons.Outlined.CreateNewFolder, L("Yeni klasör", "New folder"), tint = TextSecondary) }
            }

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.TextSnippet, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (query.isBlank()) L("Henüz not yok", "No notes yet") else L("Sonuç bulunamadı", "No results"), color = TextSecondary)
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(210.dp),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 110.dp),
                    verticalItemSpacing = 14.dp,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { n ->
                        NoteCard(n, version, selecting, n.id in selected,
                            onClick = {
                                if (selecting) { if (n.id in selected) selected.remove(n.id) else selected.add(n.id) } else onOpen(n)
                            },
                            onLong = { if (n.id !in selected) selected.add(n.id) },
                        )
                    }
                }
            }
        }

        if (selecting) {
            // Alt işlem çubuğu
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                BottomAction(Icons.Outlined.PushPin, L("Sabitle", "Pin")) {
                    val pin = selected.any { id -> store.get(id)?.pinned == false }
                    selected.forEach { store.get(it)?.pinned = pin }
                    store.saveIndex(); selected.clear(); onChanged()
                }
                BottomAction(Icons.Outlined.DriveFileMove, L("Taşı", "Move")) { moveDialog = true }
                BottomAction(Icons.Outlined.Delete, L("Sil", "Delete")) { confirmDelete = true }
            }
        } else {
            Box(Modifier.align(Alignment.BottomEnd).padding(28.dp)) {
                FloatingActionButton(onClick = { fabMenu = true }, containerColor = Accent, contentColor = Color.White, shape = CircleShape,
                    modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Outlined.Add, L("Yeni", "New"), modifier = Modifier.size(32.dp))
                }
                DropdownMenu(fabMenu, { fabMenu = false }) {
                    DropdownMenuItem({ Text(L("El yazısı notu", "Handwritten note")) }, { fabMenu = false; onNewInk(folder) }, leadingIcon = { Icon(Icons.Outlined.Draw, null) })
                    DropdownMenuItem({ Text(L("Metin notu", "Text note")) }, { fabMenu = false; onNewText(folder) }, leadingIcon = { Icon(Icons.Outlined.TextSnippet, null) })
                    DropdownMenuItem({ Text(L("PDF içe aktar", "Import PDF")) }, { fabMenu = false; onImportPdf(folder) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) })
                }
            }
        }
    }

    if (settingsDialog) SettingsDialog(
        onDismiss = { settingsDialog = false },
        onBackup = { settingsDialog = false; backupLauncher.launch("NeatInk-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip") },
        onRestore = { settingsDialog = false; restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
    )
    if (newFolderDialog) {
        NameDialog(L("Yeni klasör", "New folder"), "", { newFolderDialog = false }) { name ->
            if (name.isNotBlank() && name !in store.folders) { store.folders.add(name.trim()); store.saveIndex() }
            newFolderDialog = false; onChanged()
        }
    }
    if (moveDialog) {
        AlertDialog(
            onDismissRequest = { moveDialog = false },
            title = { Text(L("Klasöre taşı", "Move to folder")) },
            text = {
                Column {
                    (listOf("") + store.folders).forEach { f ->
                        Text(if (f.isEmpty()) L("Klasörsüz", "No folder") else f, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                            .combinedClickable(onClick = {
                                selected.forEach { store.get(it)?.folder = f }
                                store.saveIndex(); selected.clear(); moveDialog = false; onChanged()
                            }).padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton({ moveDialog = false }) { Text(L("Kapat", "Close")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("${selected.size} not silinsin mi?", "Delete ${selected.size} note(s)?")) },
            text = { Text(L("Bu işlem geri alınamaz.", "This cannot be undone.")) },
            confirmButton = {
                TextButton({ selected.toList().forEach { store.delete(it) }; selected.clear(); confirmDelete = false; onChanged() }) {
                    Text(L("Sil", "Delete"), color = Color(0xFFD32F2F))
                }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(L("İptal", "Cancel")) } },
        )
    }
}

@Composable
private fun FolderChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        color = if (selected) Color.White else TextPrimary,
        fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Accent else Color(0xFFEBEBEB))
            .combinedClickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp))
        .combinedClickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, label, tint = TextPrimary)
        Text(label, fontSize = 12.sp, color = TextPrimary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(n: NoteMeta, version: Int, selecting: Boolean, checked: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val thumb = remember(n.id, n.updated, version) {
        if (n.type == NoteType.TEXT) null
        else AppState.store.thumbFile(n.id).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg)
            .border(if (checked) 2.dp else 0.dp, if (checked) Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(14.dp),
    ) {
        if (thumb != null) {
            Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.pinned) Icon(Icons.Outlined.PushPin, null, tint = Accent, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            if (n.type == NoteType.PDF) Icon(Icons.Outlined.PictureAsPdf, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp).padding(end = 4.dp))
            Text(n.title.ifBlank { if (n.type == NoteType.TEXT) n.body.lineSequence().firstOrNull().orEmpty().ifBlank { L("Metin notu", "Text note") } else L("El yazısı notu", "Handwritten note") },
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (selecting) Icon(if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (checked) Accent else TextSecondary)
        }
        if (n.type == NoteType.TEXT && n.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(n.body, color = Color(0xFF555555), fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(formatDate(n.updated), color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton({ onOk(text) }) { Text(L("Tamam", "OK"), color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text(L("İptal", "Cancel")) } },
    )
}
