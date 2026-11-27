package com.xaser.note

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xaser.note.model.NoteMeta
import com.xaser.note.model.NoteType
import com.xaser.note.pdf.PdfImporter
import com.xaser.note.ui.AppState
import com.xaser.note.ui.EditorScreen
import com.xaser.note.ui.HomeScreen
import com.xaser.note.ui.NoteTheme
import com.xaser.note.ui.TextNoteScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var incomingPdf by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Lang.init(this)
        AppState.init(this)
        // Önceki oturumdan açık kalmış olabilir
        com.xaser.note.ink.XiaomiPenKeys.disable(this)
        handleIntent(intent)
        setContent { NoteTheme { App(incomingPdf) { incomingPdf = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (com.xaser.note.ink.InkView.current != null) com.xaser.note.ink.XiaomiPenKeys.enable(this)
    }

    override fun onPause() {
        // Uygulamadan çıkınca Xiaomi kalem kısayolları normale dönsün
        com.xaser.note.ink.XiaomiPenKeys.disable(this)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.xaser.note.ink.InkView.current?.handleKey(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) incomingPdf = i.data
    }
}

@Composable
private fun App(incomingPdf: Uri?, consumeIncoming: () -> Unit) {
    val context = LocalContext.current
    val store = AppState.store
    val scope = rememberCoroutineScope()
    // İnternet varsa bir kez güncelleme kontrolü (yoksa hiç denemez)
    LaunchedEffect(Unit) { UpdateChecker.checkOnce(context) }
    // Dil değişince tüm ekran yeniden çizilsin
    @Suppress("UNUSED_EXPRESSION") Lang.current
    var open by remember { mutableStateOf<NoteMeta?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var pendingFolder by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }

    fun importPdf(uri: Uri, folder: String) {
        if (importing) return
        importing = true
        Toast.makeText(context, L("PDF içe aktarılıyor…", "Importing PDF…"), Toast.LENGTH_SHORT).show()
        scope.launch {
            val meta = NoteMeta(title = PdfImporter.displayName(context, uri), type = NoteType.PDF, folder = folder)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val doc = PdfImporter.import(context, uri, store.pdfFile(meta.id))
                    store.saveDoc(meta.id, doc)
                }.isSuccess
            }
            importing = false
            if (ok) { store.add(meta); version++; open = meta }
            else { store.dir(meta.id).deleteRecursively(); Toast.makeText(context, L("PDF açılamadı (şifreli veya bozuk olabilir)", "Could not open PDF (it may be encrypted or damaged)"), Toast.LENGTH_LONG).show() }
        }
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri, pendingFolder)
    }

    LaunchedEffect(incomingPdf) {
        incomingPdf?.let { open = null; importPdf(it, ""); consumeIncoming() }
    }

    val current = open
    if (current == null) {
        HomeScreen(
            version = version,
            onOpen = { open = it },
            onNewInk = { f -> val m = NoteMeta(type = NoteType.INK, folder = f); store.add(m); open = m },
            onNewText = { f -> val m = NoteMeta(type = NoteType.TEXT, folder = f); store.add(m); open = m },
            onImportPdf = { f -> pendingFolder = f; pickPdf.launch(arrayOf("application/pdf")) },
            onChanged = { version++ },
        )
    } else key(current.id) {
        val back: () -> Unit = {
            // Boş metin notlarını tutma
            if (current.type == NoteType.TEXT && current.title.isBlank() && current.body.isBlank()) store.delete(current.id)
            open = null; version++
        }
        when (current.type) {
            NoteType.TEXT -> TextNoteScreen(current, back)
            else -> EditorScreen(current, back) { open = it; version++ }
        }
    }
}
