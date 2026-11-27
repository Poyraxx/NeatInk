package com.xaser.note.ui

import android.content.Intent
import com.xaser.note.L
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.xaser.note.ink.EraserMode
import com.xaser.note.ink.InkClipboard
import com.xaser.note.ink.InkView
import com.xaser.note.ink.Selection
import com.xaser.note.ink.Tool
import com.xaser.note.model.ImageItem
import com.xaser.note.model.InkDocument
import com.xaser.note.model.NoteMeta
import com.xaser.note.model.NoteType
import com.xaser.note.model.NoteFont
import com.xaser.note.model.NoteFonts
import com.xaser.note.model.Page
import com.xaser.note.model.PageTemplate
import com.xaser.note.model.PenType
import com.xaser.note.model.Stroke
import com.xaser.note.model.TextItem
import com.xaser.note.pdf.PdfExporter
import com.xaser.note.pdf.PdfPageCache
import com.xaser.note.recognition.Beautifier
import com.xaser.note.recognition.MathLayout
import com.xaser.note.recognition.MathText
import kotlin.math.abs
import com.xaser.note.recognition.InkRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS = "editor"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(meta: NoteMeta, initialPage: Int = -1, onBack: () -> Unit, onOpenNote: (NoteMeta) -> Unit) {
    val context = LocalContext.current
    val store = AppState.store
    val renderer = AppState.renderer
    val recognizer = AppState.recognizer
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, 0) }

    val noteDir = remember { store.dir(meta.id) }
    var ui by remember { mutableIntStateOf(0) } // yeniden çizim tetikleyicisi
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var pageInfo by remember { mutableStateOf(0 to 1) }
    var penPopup by remember { mutableStateOf(false) }
    var eraserPopup by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf<Pair<Int, RectF>?>(null) }
    var textReq by remember { mutableStateOf<Triple<Int, Pair<Float, Float>, TextItem?>?>(null) }
    var candidates by remember { mutableStateOf<CandidateRequest?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var titleDialog by remember { mutableStateOf(false) }
    var recolorPopup by remember { mutableStateOf(false) }
    var fontPopup by remember { mutableStateOf(false) }
    var selFontPopup by remember { mutableStateOf(false) }
    /** Düzeltilen yazıların yazı tipi (✨ simgesine basılı tutunca seçilir) */
    var fixFont by remember { mutableStateOf(prefs.getString("fixFont", NoteFonts.DEFAULT_FIX)!!) }
    var marginDialog by remember { mutableStateOf(false) }
    var examOpen by remember { mutableStateOf(false) }
    var retryEndDialog by remember { mutableStateOf(false) }
    var mistakeCrop by remember { mutableStateOf<Pair<Int, RectF>?>(null) }
    /** Eklenmeyi bekleyen resim: dosya adı, genişlik, yükseklik */
    var pendingImage by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    val pdfCache = remember {
        if (meta.type == NoteType.PDF && store.pdfFile(meta.id).exists()) PdfPageCache(store.pdfFile(meta.id)) { ui++ } else null
    }

    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    val view = remember {
        InkView(context, renderer, noteDir, pdfCache).apply {
            fingerDraws = prefs.getBoolean("finger", false)
            autoBeautify = prefs.getBoolean("auto", false)
            eraserMode = EraserMode.valueOf(prefs.getString("eraser", "STROKE")!!)
            PenType.entries.forEach { t ->
                penColors[t] = prefs.getInt("c_$t", penColors[t]!!)
                penWidths[t] = prefs.getFloat("w_$t", penWidths[t]!!)
            }
            penType = PenType.valueOf(prefs.getString("pen", "BALLPOINT")!!)
        }
    }
    LaunchedEffect(pdfCache) { pdfCache?.let { view.invalidate() } }
    // PDF sayfası hazır olunca yeniden çiz
    LaunchedEffect(ui) { view.invalidate() }

    fun savePrefs() {
        prefs.edit().apply {
            putBoolean("finger", view.fingerDraws); putBoolean("auto", view.autoBeautify)
            putString("eraser", view.eraserMode.name); putString("pen", view.penType.name)
            PenType.entries.forEach { putInt("c_$it", view.penColors[it]!!); putFloat("w_$it", view.penWidths[it]!!) }
        }.apply()
    }

    fun saveNow() {
        val snapshot = InkDocument(view.doc.pages.map { it.deepCopy() }.toMutableList())
        val thumb = view.renderThumbnail()
        scope.launch(Dispatchers.IO) {
            store.saveDoc(meta.id, snapshot)
            thumb?.let { store.saveThumb(meta.id, it) }
            store.touch(meta)
        }
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(1500); saveNow() }
    }

    /** Seçili çizgileri tanıyıp metne / düzgün el yazısına çevirir */
    fun recognizeAndReplace(pageIndex: Int, strokes: List<Stroke>, font: String, auto: Boolean) {
        if (strokes.isEmpty()) return
        val page = view.pageAtIndex(pageIndex)
        val visibleTexts = page.texts.filter { !view.hideInk || it.retry }
        fun boxOf(t: TextItem) = renderer.textBounds(t).let { MathLayout.Box(it.left, it.top, it.right, it.bottom) }
        val textBoxes = visibleTexts.map { boxOf(it) }
        // 1) Yerleşim: kesir, düz çizgi, işaret, nokta/şapka
        val plan = MathLayout.analyze(strokes, textBoxes)
        val rec = recognizer
        scope.launch {
            val mlGroups = plan.groups.filter { it.symbol == null }
            if (mlGroups.isNotEmpty() && rec.state != InkRecognizer.State.READY)
                busy = L("Tanıma modeli hazırlanıyor… (ilk sefer internet gerekir)", "Preparing recognition model… (internet needed the first time)")
            // 2) Tanıma (aynı satırda soldaki metin bağlam olarak verilir)
            val results = HashMap<MathLayout.Group, List<String>>()
            for (g in mlGroups) {
                val left = visibleTexts.filter { t -> val b = boxOf(t); b.r <= g.box.l + 4 && abs(b.cy - g.box.cy) < plan.unit && g.box.l - b.r < plan.unit * 3 }
                    .maxByOrNull { boxOf(it).r }?.text.orEmpty()
                results[g] = rec.recognizeAwait(g.strokes, RectF(g.box.l, g.box.t, g.box.r, g.box.b), left)
            }
            busy = null
            val color = strokes.first().color
            // Tek satır, birden çok aday ve başka değişiklik yok: kullanıcı seçsin
            if (!auto && plan.groups.size == 1 && plan.groups[0].symbol == null && plan.marks.isEmpty() && plan.straightened.isEmpty()) {
                val g = plan.groups[0]; val c = results[g].orEmpty()
                if (c.size > 1) {
                    candidates = CandidateRequest(pageIndex, g.strokes, Beautifier.Line(g.strokes, RectF(g.box.l, g.box.t, g.box.r, g.box.b)), c, color, font)
                    return@launch
                }
            }
            val removed = ArrayList<Stroke>()
            val newTexts = ArrayList<TextItem>()
            for (g in plan.groups) {
                val text = g.symbol ?: MathText.pick(results[g].orEmpty(), g.math) ?: continue
                // İşaretlerin boyu, el yazısının satır yüksekliğine göre
                if (g.symbol != null) {
                    // İşaret: genişliği ve dikey ortası çizilen şekle eşit olsun
                    val t = TextItem(text, g.box.l, g.box.cy, 40f, color, font)
                    val r = android.graphics.Rect()
                    renderer.textPaintFor(t).getTextBounds(text, 0, text.length, r)
                    val target = if (text == "-" || text == "=") g.box.w else maxOf(g.box.w, g.box.h)
                    t.size = (40f * target / maxOf(1, r.width())).coerceIn(14f, maxOf(60f, plan.unit * 2.5f))
                    renderer.textPaintFor(t).getTextBounds(text, 0, text.length, r)
                    t.x = g.box.cx - (r.left + r.right) / 2f
                    t.y = g.box.cy - (r.top + r.bottom) / 2f
                    newTexts.add(t)
                } else {
                    newTexts.add(Beautifier.place(text, Beautifier.Line(g.strokes, RectF(g.box.l, g.box.t, g.box.r, g.box.b)), color, font, renderer))
                }
                removed.addAll(g.strokes)
            }
            // 3) Sonradan konan nokta/şapka/çengel → var olan harfi düzelt
            val edits = ArrayList<Pair<TextItem, String>>()
            for ((g, mk) in plan.marks) {
                val t = visibleTexts.filter { MathLayout.near(g.box, boxOf(it), mk) }.minByOrNull { abs(boxOf(it).cx - g.box.cx) } ?: continue
                val current = edits.lastOrNull { it.first === t }?.second ?: t.text
                val paint = renderer.textPaintFor(t)
                var x = t.x; var idx = -1
                for (i in current.indices) {
                    val w = paint.measureText(current, i, i + 1)
                    if (g.box.cx in x..(x + w)) { idx = i; break }
                    x += w
                }
                if (idx < 0) idx = if (g.box.cx < t.x) 0 else current.length - 1
                val below = mk == MathLayout.Mark.HOOK_BELOW
                val fixed = MathLayout.applyMark(current, idx, mk, below)
                    ?: MathLayout.applyMark(current, idx - 1, mk, below) ?: MathLayout.applyMark(current, idx + 1, mk, below)
                if (fixed != null) { edits.add(t to fixed); removed.addAll(g.strokes) }
            }
            removed.addAll(plan.straightened.map { it.first })
            if (newTexts.isEmpty() && edits.isEmpty() && plan.straightened.isEmpty()) {
                if (!auto) toast(rec.lastError ?: L("Yazı tanınamadı", "Could not recognize the writing"))
                return@launch
            }
            view.applyRecognition(pageIndex, removed, plan.straightened.map { it.second }, newTexts, edits)
        }
    }

    DisposableEffect(Unit) {
        view.listener = object : InkView.Listener {
            override fun onContentChanged() { scheduleSave() }
            override fun onHistoryChanged(u: Boolean, r: Boolean) { canUndo = u; canRedo = r }
            override fun onSelectionChanged(sel: Selection?) { selection = sel; ui++ }
            override fun onCropSelected(page: Int, rect: RectF) { crop = page to rect }
            override fun onTextRequest(page: Int, x: Float, y: Float, existing: TextItem?) { textReq = Triple(page, x to y, existing) }
            override fun onAutoBeautify(page: Int, strokes: List<Stroke>) {
                recognizeAndReplace(page, strokes, fixFont, auto = true)
            }
            override fun onCurrentPageChanged(page: Int, count: Int) { pageInfo = page to count }
            override fun onToolChanged() { ui++ }
        }
        val doc = store.loadDoc(meta.id)
        if (doc.pages.isEmpty()) doc.pages.add(Page(template = PageTemplate.valueOf(prefs.getString("tpl", "BLANK")!!)))
        view.setDocument(doc)
        // Xiaomi: not açıkken kalem tuşları sisteme değil bize gelsin (basılı tut = silgi)
        com.xaser.note.ink.XiaomiPenKeys.enable(context)
        pageInfo = 0 to doc.pages.size
        // Hata defterinden "kaynağa git": ilgili sayfaya kaydır
        if (initialPage >= 0) view.post { view.scrollToPage(initialPage) }
        // Otomatik düzeltme açıksa modeli hemen ısıt: ilk düzeltme beklemesin
        if (view.autoBeautify) recognizer.warmUp()
        onDispose {
            // Tekrar çözümü açık kaldıysa çizilenleri kaybetme
            view.endRetry(keep = true)
            com.xaser.note.ink.XiaomiPenKeys.disable(context)
            saveJob?.cancel()
            savePrefs()
            val snap = InkDocument(view.doc.pages.map { it.deepCopy() }.toMutableList())
            val thumb = view.renderThumbnail()
            store.saveDoc(meta.id, snap)
            thumb?.let { store.saveThumb(meta.id, it) }
            store.touch(meta)
            pdfCache?.close()
        }
    }

    BackHandler { onBack() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val name = store.newImageName()
                    val f = store.imageFile(meta.id, name)
                    context.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, o)
                    Triple(name, o.outWidth, o.outHeight)
                }.getOrNull()
            }
            if (res != null) pendingImage = res else toast(L("Resim eklenemedi", "Could not add image"))
        }
    }

    var exportFile by remember { mutableStateOf<File?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = exportFile ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            withContext(Dispatchers.Main) { toast(L("PDF kaydedildi", "PDF saved")) }
        }
    }

    fun exportPdf(share: Boolean) {
        busy = L("PDF hazırlanıyor…", "Preparing PDF…")
        val snap = InkDocument(view.doc.pages.map { it.deepCopy() }.toMutableList())
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "export").apply { mkdirs() }
                    val name = (meta.title.ifBlank { "NeatInk" }).replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".pdf"
                    val out = File(dir, name)
                    PdfExporter.export(snap, noteDir, pdfCache, renderer, out)
                    out
                }.getOrNull()
            }
            busy = null
            if (f == null) { toast(L("PDF oluşturulamadı", "Could not create PDF")); return@launch }
            exportFile = f
            if (share) {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, L("PDF paylaş", "Share PDF")))
            } else savePdf.launch(f.name)
        }
    }

    // ================= Arayüz =================
    Column(Modifier.fillMaxSize().background(Color.White)) {
        // ---- Üst araç çubuğu ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, L("Geri", "Back")) }
            IconButton(onClick = { view.undo() }, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, L("Geri al", "Undo")) }
            IconButton(onClick = { view.redo() }, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, L("Yinele", "Redo")) }

            // Araçlar (ortada)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom,
            ) {
                @Suppress("UNUSED_EXPRESSION") ui
                PenType.entries.forEach { t ->
                    val sel = view.tool == Tool.PEN && view.penType == t && !view.penKeyErasing
                    Box(Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        if (sel) penPopup = true else { view.tool = Tool.PEN; view.penType = t; ui++ }
                    }, contentAlignment = Alignment.BottomCenter) {
                        PenIcon(t, Color(view.penColors[t]!!), sel, 44.dp)
                        if (sel) PenSettingsPopup(penPopup, view, t, { penPopup = false; savePrefs() }) { ui++ }
                    }
                }
                Box(Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(8.dp)).clickable {
                    if (view.tool == Tool.ERASER) eraserPopup = true else { view.tool = Tool.ERASER; ui++ }
                }, contentAlignment = Alignment.BottomCenter) {
                    EraserIcon(view.tool == Tool.ERASER || view.penKeyErasing, 44.dp)
                    EraserPopup(eraserPopup, view, { eraserPopup = false; savePrefs() }) { ui++ }
                }
                Spacer(Modifier.width(8.dp))
                ToolButton(Icons.Outlined.HighlightAlt, L("Kement", "Lasso"), view.tool == Tool.LASSO) { view.tool = Tool.LASSO; ui++ }
                // Otomatik düzeltme: açık/kapalı anahtar. Açıkken yazdığın kelime ve rakamlar kendiliğinden düzeltilir.
                // Basılı tut: düzeltilen yazının yazı tipini seç
                Box {
                    ToolButton(Icons.Outlined.AutoFixHigh, L("Otomatik düzelt", "Auto-fix"), view.autoBeautify,
                        onLongClick = { fontPopup = true }) {
                        view.autoBeautify = !view.autoBeautify
                        if (view.autoBeautify) {
                            toast(L("Otomatik düzeltme AÇIK (", "Auto-fix ON (") + NoteFonts.byId(fixFont).name + L(") — yazı tipi için basılı tut", ") — long-press for fonts"))
                            recognizer.ensureModel { ok -> if (!ok) toast(recognizer.lastError ?: L("Model indirilemedi", "Model could not be downloaded")) else recognizer.warmUp() }
                        } else toast(L("Otomatik düzeltme kapalı", "Auto-fix off"))
                        savePrefs(); ui++
                    }
                    DropdownMenu(fontPopup, { fontPopup = false }) {
                        FontPickerList(fixFont, L("Düzeltme yazı tipi", "Fix font")) { id ->
                            fixFont = id; prefs.edit().putString("fixFont", id).apply()
                            fontPopup = false
                            toast(L("Düzeltme yazı tipi: ", "Fix font: ") + NoteFonts.byId(id).name)
                        }
                    }
                }
                ToolButton(Icons.Outlined.Category, L("Şekil", "Shape"), view.tool == Tool.SHAPE) { view.tool = Tool.SHAPE; ui++ }
                ToolButton(Icons.Outlined.TextFields, L("Metin", "Text"), view.tool == Tool.TEXT) { view.tool = Tool.TEXT; ui++ }
                ToolButton(Icons.Outlined.Crop, L("Soruyu kes", "Cut question"), view.tool == Tool.CROP) { view.tool = Tool.CROP; ui++ }
                ToolButton(Icons.Outlined.Image, L("Resim", "Image"), false) {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.width(6.dp))
                // Çözümleri gizle → temiz sayfada tekrar çöz
                ToolButton(if (view.hideInk) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    L("Çözümleri gizle", "Hide solutions"), view.hideInk) {
                    if (!view.hideInk) {
                        view.startRetry()
                        toast(L("Çözümler gizlendi — temiz sayfada tekrar çöz", "Solutions hidden — solve again on a clean page"))
                    } else if (view.hasRetryInk()) retryEndDialog = true
                    else view.endRetry(keep = true)
                    ui++
                }
                // Deneme modu: süre + optik + net
                ToolButton(Icons.Outlined.Timer, L("Deneme modu", "Exam mode"), examOpen) { examOpen = !examOpen }
                if (!InkClipboard.isEmpty) ToolButton(Icons.Outlined.ContentPaste, L("Yapıştır", "Paste"), false) {
                    view.paste { src ->
                        val name = store.newImageName(); src.copyTo(store.imageFile(meta.id, name), true); name
                    }
                    ui++
                }
            }

            ToolButton(Icons.Outlined.PanTool, L("Parmakla çiz", "Draw with finger"), view.fingerDraws) {
                view.fingerDraws = !view.fingerDraws
                toast(if (view.fingerDraws) L("Parmakla çizim açık", "Finger drawing on") else L("Sadece kalem çizer, parmak kaydırır", "Only the pen draws, fingers scroll"))
                savePrefs(); ui++
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, L("Diğer", "More")) }
                MoreMenu(moreMenu, { moreMenu = false }, view, meta,
                    onRename = { titleDialog = true },
                    onMargin = { marginDialog = true },
                    onExport = { exportPdf(it) },
                    onDownloadModel = {
                        busy = L("Türkçe el yazısı modeli indiriliyor…", "Downloading Turkish handwriting model…")
                        recognizer.ensureModel { ok -> busy = null; toast(if (ok) L("Model hazır, internetsiz çalışır", "Model ready, works offline") else recognizer.lastError ?: L("İndirilemedi", "Download failed")) }
                    },
                    onTemplateDefault = { prefs.edit().putString("tpl", it.name).apply() },
                )
            }
        }
        HorizontalDivider(color = Color(0xFFEEEEEE))

        // ---- Tuval ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ view }, Modifier.fillMaxSize())

            if (view.hideInk) {
                Text(
                    L("Tekrar çözüm modu — eski çözümler gizli", "Re-solve mode — old solutions hidden"),
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xCC1565C0)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            if (examOpen) ExamPanel(view, meta, { examOpen = false }, Modifier.align(Alignment.CenterEnd))

            // Sayfa göstergesi
            Text(
                "${pageInfo.first + 1} / ${pageInfo.second}",
                color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 5.dp),
            )

            // Kement seçim menüsü
            val sel = selection
            if (sel != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp)
                        .clip(RoundedCornerShape(26.dp)).background(Color(0xFF2B2B2B)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasInk = sel.strokes.isNotEmpty()
                    if (hasInk) {
                        SelAction(Icons.Outlined.Title, L("Metne çevir", "To text")) {
                            recognizeAndReplace(sel.page, view.selectedStrokes(), NoteFonts.PLAIN, auto = false)
                        }
                        SelAction(Icons.Outlined.AutoFixHigh, L("Düzelt", "Fix")) {
                            recognizeAndReplace(sel.page, view.selectedStrokes(), fixFont, auto = false)
                        }
                    }
                    if (sel.texts.isNotEmpty()) Box {
                        SelAction(Icons.Outlined.FontDownload, L("Yazı tipi", "Font")) { selFontPopup = true }
                        DropdownMenu(selFontPopup, { selFontPopup = false }) {
                            FontPickerList(sel.texts.first().font, L("Seçili yazının yazı tipi", "Font of selected text")) { id ->
                                view.setFontForSelection(id); selFontPopup = false
                            }
                        }
                    }
                    Box {
                        SelAction(Icons.Outlined.Palette, L("Renk", "Color")) { recolorPopup = true }
                        DropdownMenu(recolorPopup, { recolorPopup = false }) {
                            ColorGrid(Palette, null) { view.recolorSelection(it); recolorPopup = false }
                        }
                    }
                    SelAction(Icons.Outlined.ContentCopy, L("Kopyala", "Copy")) { view.copySelection(false); toast(L("Kopyalandı", "Copied")); ui++ }
                    SelAction(Icons.Outlined.ContentCut, L("Kes", "Cut")) { view.copySelection(true); ui++ }
                    SelAction(Icons.Outlined.Delete, L("Sil", "Delete")) { view.deleteSelection() }
                }
            }

            busy?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xCC000000)).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }

    // ================= Diyaloglar =================
    pendingImage?.let { (name, bw, bh) ->
        ImageInsertDialog(
            file = store.imageFile(meta.id, name),
            prefs = prefs,
            onCancel = { store.imageFile(meta.id, name).delete(); pendingImage = null },
        ) { digitize, withText, withShapes, keepImage ->
            pendingImage = null
            if (!digitize) { view.tool = Tool.LASSO; view.addImage(name, bw, bh); return@ImageInsertDialog }
            busy = L("Resim dijitale çevriliyor…", "Digitizing image…")
            scope.launch {
                val file = store.imageFile(meta.id, name)
                val result = runCatching {
                    val bmp = withContext(Dispatchers.IO) {
                        // EXIF yönünü uygular (telefon fotoğrafları yan gelmesin)
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { d, _, _ ->
                            d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    com.xaser.note.ink.ImageDigitizer.digitize(bmp, view.digitizeWidth(), view.penColors[PenType.BALLPOINT]!!,
                        NoteFonts.PLAIN, withText, withShapes)
                }.getOrNull()
                busy = null
                when {
                    result == null -> { toast(L("Dijitale çevrilemedi, resim olarak eklendi", "Could not digitize, added as image")); view.addImage(name, bw, bh) }
                    result.isEmpty -> { toast(L("Resimde yazı veya şekil bulunamadı, resim olarak eklendi", "No text or shapes found, added as image")); view.addImage(name, bw, bh) }
                    else -> {
                        view.addDigitized(result, if (keepImage) name else null)
                        if (!keepImage) file.delete()
                        toast(L("${result.texts.size} satır yazı, ${result.strokes.size} çizgi/şekil eklendi", "Added ${result.texts.size} text lines, ${result.strokes.size} lines/shapes"))
                    }
                }
            }
        }
    }

    mistakeCrop?.let { (pi, rect) ->
        SubjectDialog(L("Hata defterine ekle", "Add to mistakes"), prefs.getString("lastSubject", "") ?: "", "", { mistakeCrop = null }) { subject, topic ->
            // Sorunun temiz hali (senin çözümün olmadan)
            val bmp = view.renderRegion(pi, rect, includeInk = false)
            AppState.mistakes.add(bmp, subject, topic, meta.id, meta.title, pi)
            prefs.edit().putString("lastSubject", subject).apply()
            mistakeCrop = null
            toast(L("Hata defterine eklendi", "Added to mistakes"))
        }
    }

    if (retryEndDialog) {
        AlertDialog(
            onDismissRequest = { retryEndDialog = false },
            title = { Text(L("Tekrar çözüm bitti", "Re-solve finished")) },
            text = { Text(L("Gizliyken yazdığın yeni çözüm ne olsun? Eski çözümlerin geri gelecek.", "What should happen to the new solution you wrote? Your old solutions will come back.")) },
            confirmButton = { TextButton({ view.endRetry(keep = true); retryEndDialog = false; ui++ }) { Text(L("Sakla", "Keep"), color = Accent) } },
            dismissButton = { TextButton({ view.endRetry(keep = false); retryEndDialog = false; ui++ }) { Text(L("Sil", "Discard"), color = Color(0xFFD32F2F)) } },
        )
    }

    crop?.let { (pi, rect) ->
        AlertDialog(
            onDismissRequest = { crop = null; view.clearCrop() },
            title = { Text(L("Seçilen bölge", "Selected area")) },
            text = { Text(L("Bu soruyu/bölgeyi ne yapmak istersin?", "What do you want to do with this question/area?")) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({
                        mistakeCrop = pi to RectF(rect)
                        crop = null; view.clearCrop()
                    }) { Text(L("Hata defterine ekle", "Add to mistakes"), color = AccentDark, fontWeight = FontWeight.SemiBold) }
                    TextButton({
                        val bmp = view.renderRegion(pi, rect)
                        val name = store.saveBitmap(meta.id, bmp)
                        InkClipboard.clear()
                        InkClipboard.images = listOf(store.imageFile(meta.id, name) to ImageItem(name, 0f, 0f, rect.width(), rect.height()))
                        crop = null; view.clearCrop(); ui++
                        toast(L("Panoya alındı — istediğin nota gidip Yapıştır'a bas", "Copied — open any note and tap Paste"))
                    }) { Text(L("Panoya kopyala", "Copy to clipboard"), color = Accent) }
                    TextButton({
                        val bmp = view.renderRegion(pi, rect)
                        val newMeta = NoteMeta(title = meta.title.ifBlank { L("Soru", "Question") } + L(" – soru", " – question"), type = NoteType.INK, folder = meta.folder)
                        store.add(newMeta)
                        val name = store.saveBitmap(newMeta.id, bmp)
                        val page = Page(template = PageTemplate.valueOf(prefs.getString("tpl", "BLANK")!!))
                        val w = rect.width().coerceAtMost(900f); val h = w * rect.height() / rect.width()
                        page.images.add(ImageItem(name, 50f, 50f, w, h))
                        store.saveDoc(newMeta.id, InkDocument(mutableListOf(page)))
                        crop = null; view.clearCrop()
                        toast(L("Yeni nota eklendi", "Added to a new note"))
                        onOpenNote(newMeta)
                    }) { Text(L("Yeni nota gönder", "Send to a new note"), color = Accent) }
                    TextButton({
                        val bmp = view.renderRegion(pi, rect)
                        val name = store.saveBitmap(meta.id, bmp)
                        // Bu notun sonuna yeni sayfa olarak ekle
                        view.insertPage(view.pageCount() - 1, PageTemplate.valueOf(prefs.getString("tpl", "BLANK")!!))
                        val last = view.pageAtIndex(view.pageCount() - 1)
                        val w = rect.width().coerceAtMost(900f); val h = w * rect.height() / rect.width()
                        last.images.add(ImageItem(name, 50f, 50f, w, h))
                        view.redrawAll(); scheduleSave()
                        crop = null; view.clearCrop()
                        view.scrollToPage(view.pageCount() - 1)
                    }) { Text(L("Bu notun sonuna ekle", "Add to the end of this note"), color = Accent) }
                }
            },
            dismissButton = { TextButton({ crop = null; view.clearCrop() }) { Text(L("İptal", "Cancel")) } },
        )
    }

    textReq?.let { (pi, pos, existing) ->
        var text by remember(textReq) { mutableStateOf(existing?.text ?: "") }
        var font by remember(textReq) { mutableStateOf(existing?.font ?: fixFont) }
        AlertDialog(
            onDismissRequest = { textReq = null },
            title = { Text(if (existing == null) L("Metin ekle", "Add text") else L("Metni düzenle", "Edit text")) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoteFonts.all.forEach { f ->
                            val on = f.id == font
                            Text(f.name, fontFamily = composeFamily(f), fontSize = 16.sp, maxLines = 1,
                                color = if (on) Color.White else TextPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Accent else Color(0xFFEEEEEE))
                                    .clickable { font = f.id }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (existing != null) { existing.font = font; view.updateText(pi, existing, text) }
                    else if (text.isNotBlank()) view.addText(pi, TextItem(text, pos.first, pos.second, 36f, view.penColors[PenType.BALLPOINT]!!, font))
                    textReq = null
                }) { Text(L("Tamam", "OK"), color = Accent) }
            },
            dismissButton = { TextButton({ textReq = null }) { Text(L("İptal", "Cancel")) } },
        )
    }

    candidates?.let { req ->
        AlertDialog(
            onDismissRequest = { candidates = null },
            title = { Text(if (req.font == NoteFonts.PLAIN) L("Metne çevir", "To text") else L("Düzeltilmiş hali", "Fixed handwriting")) },
            text = {
                Column {
                    Text(L("Doğru olanı seç:", "Pick the right one:"), color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    req.options.forEach { opt ->
                        Text(opt, fontSize = 22.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable {
                                view.replaceStrokesWithTexts(req.page, req.strokes,
                                    listOf(Beautifier.place(opt, req.line, req.color, req.font, renderer)))
                                candidates = null
                            }.padding(vertical = 10.dp, horizontal = 8.dp),
                            fontFamily = composeFamily(NoteFonts.byId(req.font)))
                    }
                }
            },
            confirmButton = { TextButton({ candidates = null }) { Text(L("İptal", "Cancel")) } },
        )
    }

    if (titleDialog) {
        NameDialog(L("Not başlığı", "Note title"), meta.title, { titleDialog = false }) { meta.title = it.trim(); store.touch(meta); titleDialog = false; ui++ }
    }

    if (marginDialog) {
        val pi = view.currentPage
        val page = view.pageAtIndex(pi)
        var right by remember { mutableFloatStateOf(page.extraRight) }
        var bottom by remember { mutableFloatStateOf(page.extraBottom) }
        AlertDialog(
            onDismissRequest = { marginDialog = false },
            title = { Text(L("Çözüm alanı (sayfa ${pi + 1})", "Solution space (page ${pi + 1})")) },
            text = {
                Column {
                    Text(L("Sağa boşluk: ${(right / 10).toInt()}%", "Space on the right: ${(right / 10).toInt()}%"))
                    Slider(right, { right = it }, valueRange = 0f..1000f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                    Text(L("Alta boşluk: ${(bottom / 10).toInt()}%", "Space below: ${(bottom / 10).toInt()}%"))
                    Slider(bottom, { bottom = it }, valueRange = 0f..1500f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                }
            },
            confirmButton = { TextButton({ view.extendMargin(pi, right, bottom); marginDialog = false }) { Text(L("Uygula", "Apply"), color = Accent) } },
            dismissButton = { TextButton({ marginDialog = false }) { Text(L("İptal", "Cancel")) } },
        )
    }
}

class CandidateRequest(
    val page: Int, val strokes: List<Stroke>, val line: Beautifier.Line,
    val options: List<String>, val color: Int, val font: String,
)

@Composable
private fun ToolButton(icon: ImageVector, desc: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 1.dp).size(42.dp).clip(CircleShape)
            .background(if (selected) Color(0x1F000000) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (selected) Color(0xFF111111) else Color(0xFF444444))
        // Basılı tutunca menü açılan simgelerde küçük köşe işareti
        if (onLongClick != null) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(5.dp).clip(CircleShape).background(Color(0xFF888888)))
    }
}

@Composable
private fun SelAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun ColorGrid(colors: List<Int>, selected: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Accent else Color(0x22000000), CircleShape)
                        .clickable { onPick(c) })
                }
            }
        }
    }
}

@Composable
private fun PenSettingsPopup(open: Boolean, view: InkView, t: PenType, onDismiss: () -> Unit, onChange: () -> Unit) {
    DropdownMenu(open, onDismiss) {
        var w by remember(open) { mutableFloatStateOf(view.penWidths[t]!!) }
        var col by remember(open) { mutableIntStateOf(view.penColors[t]!!) }
        val range = when (t) { PenType.HIGHLIGHTER -> 10f..60f; PenType.MARKER -> 3f..30f; else -> 1f..16f }
        Column(Modifier.width(280.dp).padding(horizontal = 12.dp)) {
            Text(when (t) {
                PenType.BALLPOINT -> L("Tükenmez kalem", "Ballpoint pen"); PenType.FOUNTAIN -> L("Dolma kalem", "Fountain pen"); PenType.PENCIL -> L("Kurşun kalem", "Pencil")
                PenType.MARKER -> L("İşaretleyici", "Marker"); PenType.HIGHLIGHTER -> L("Fosforlu kalem", "Highlighter")
            }, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((w / range.endInclusive * 30).coerceAtLeast(3f).dp).clip(CircleShape).background(Color(col)))
                }
                Slider(w, { w = it; view.penWidths[t] = it; onChange() }, valueRange = range,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
            }
        }
        ColorGrid(if (t == PenType.HIGHLIGHTER) HighlighterPalette else Palette, col) { col = it; view.penColors[t] = it; onChange() }
    }
}

@Composable
private fun EraserPopup(open: Boolean, view: InkView, onDismiss: () -> Unit, onChange: () -> Unit) {
    DropdownMenu(open, onDismiss) {
        var mode by remember(open) { mutableStateOf(view.eraserMode) }
        var size by remember(open) { mutableFloatStateOf(view.eraserSize) }
        Column(Modifier.width(260.dp).padding(12.dp)) {
            Text(L("Silgi", "Eraser"), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(EraserMode.STROKE to L("Çizgi silgisi", "Stroke eraser"), EraserMode.AREA to L("Alan silgisi", "Area eraser")).forEach { (m, label) ->
                    Text(label, color = if (mode == m) Color.White else TextPrimary, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (mode == m) Accent else Color(0xFFEEEEEE))
                            .clickable { mode = m; view.eraserMode = m; onChange() }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(L("Boyut", "Size"), fontSize = 13.sp, color = TextSecondary)
            Slider(size, { size = it; view.eraserSize = it }, valueRange = 12f..160f,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
            Text(L("İpucu: kalem tuşuna basılı tutarak da silebilirsin.", "Tip: hold the pen button to erase."), fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun MoreMenu(
    open: Boolean, onDismiss: () -> Unit, view: InkView, meta: NoteMeta, onRename: () -> Unit,
    onMargin: () -> Unit, onExport: (share: Boolean) -> Unit,
    onDownloadModel: () -> Unit, onTemplateDefault: (PageTemplate) -> Unit,
) {
    var tplPicker by remember { mutableStateOf<Boolean?>(null) } // true: sayfa ekle, false: şablonu değiştir
    DropdownMenu(open, onDismiss) {
        DropdownMenuItem({ Text(L("Başlık: ", "Title: ") + meta.title.ifBlank { L("Başlıksız", "Untitled") }) }, { onDismiss(); onRename() })
        HorizontalDivider()
        DropdownMenuItem({ Text(L("Sonraya sayfa ekle…", "Add page after…")) }, { onDismiss(); tplPicker = true })
        DropdownMenuItem({ Text(L("Bu sayfayı sil", "Delete this page")) }, { view.deletePage(view.currentPage); onDismiss() })
        DropdownMenuItem({ Text(L("Sayfa şablonu…", "Page template…")) }, { onDismiss(); tplPicker = false })
        if (meta.type == NoteType.PDF) DropdownMenuItem({ Text(L("Çözüm alanı aç (kenar boşluğu)", "Add solution space (margin)")) }, { onDismiss(); onMargin() })
        HorizontalDivider()
        DropdownMenuItem({ Text(L("PDF olarak paylaş", "Share as PDF")) }, { onDismiss(); onExport(true) })
        DropdownMenuItem({ Text(L("PDF olarak kaydet", "Save as PDF")) }, { onDismiss(); onExport(false) })
        HorizontalDivider()
        DropdownMenuItem({ Text(L("El yazısı modelini indir (Türkçe)", "Download handwriting model (Turkish)")) }, { onDismiss(); onDownloadModel() })
    }
    tplPicker?.let { adding ->
        val p = view.currentPage
        val cur = view.pageAtIndex(p).template
        TemplatePickerDialog(adding, cur, onDismiss = { tplPicker = null }) { t, all ->
            if (adding) { view.insertPage(p, t); view.scrollToPage(p + 1) }
            else if (all) { view.setTemplate(0, t, true); onTemplateDefault(t) }
            else view.setTemplate(p, t, false)
            tplPicker = null
        }
    }
}


/** Bir NoteFont'u Compose yazı ailesine çevirir (önizleme için) */
fun composeFamily(f: NoteFont): androidx.compose.ui.text.font.FontFamily = when {
    f.res != null -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(f.res))
    f.id == "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    f.id == "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
    else -> androidx.compose.ui.text.font.FontFamily.SansSerif
}

/** Yazı tipi listesi: her satır kendi yazı tipiyle örnek gösterir */
@Composable
fun FontPickerList(selected: String, title: String, onPick: (String) -> Unit) {
    Column(Modifier.width(320.dp).padding(vertical = 4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        listOf(true to L("El yazısı", "Handwriting"), false to L("Düz yazı", "Plain")).forEach { (hand, header) ->
            Text(header, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
            NoteFonts.all.filter { it.handwriting == hand }.forEach { f ->
                val on = f.id == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(f.id) }
                        .background(if (on) Color(0x14000000) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Merhaba çağış 123", "Hello çağış 123"), fontFamily = composeFamily(f), fontSize = 22.sp, maxLines = 1, color = TextPrimary)
                        Text(f.name, fontSize = 11.sp, color = TextSecondary)
                    }
                    if (on) Icon(Icons.Outlined.Check, null, tint = AccentDark)
                }
            }
        }
    }
}


/** Resim eklerken: "Dijitale çevir" seçeneği */
@Composable
private fun ImageInsertDialog(
    file: File, prefs: android.content.SharedPreferences, onCancel: () -> Unit,
    onOk: (digitize: Boolean, withText: Boolean, withShapes: Boolean, keepImage: Boolean) -> Unit,
) {
    val preview = remember(file) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
    }
    var digitize by remember { mutableStateOf(prefs.getBoolean("dig", false)) }
    var withText by remember { mutableStateOf(prefs.getBoolean("digText", true)) }
    var withShapes by remember { mutableStateOf(prefs.getBoolean("digShapes", true)) }
    var keep by remember { mutableStateOf(prefs.getBoolean("digKeep", false)) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(L("Resim ekle", "Add image")) },
        text = {
            Column {
                preview?.let {
                    androidx.compose.foundation.Image(it.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F2)))
                    Spacer(Modifier.height(10.dp))
                }
                CheckRow(digitize, L("Dijitale çevir", "Digitize"), L("Yazılar, rakamlar ve şekiller düzenlenebilir hale gelir (internetsiz)",
                    "Text, numbers and shapes become editable (works offline)")) { digitize = it }
                if (digitize) Column(Modifier.padding(start = 28.dp)) {
                    CheckRow(withText, L("Yazılar ve rakamlar", "Text and numbers"), null) { withText = it }
                    CheckRow(withShapes, L("Şekiller ve çizgiler", "Shapes and lines"), null) { withShapes = it }
                    CheckRow(keep, L("Orijinal resmi arkada tut", "Keep the original image behind"), null) { keep = it }
                }
            }
        },
        confirmButton = {
            TextButton({
                prefs.edit().putBoolean("dig", digitize).putBoolean("digText", withText).putBoolean("digShapes", withShapes).putBoolean("digKeep", keep).apply()
                onOk(digitize && (withText || withShapes), withText, withShapes, keep)
            }) { Text(L("Ekle", "Add"), color = Accent) }
        },
        dismissButton = { TextButton(onCancel) { Text(L("İptal", "Cancel")) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, title: String, sub: String?, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked, onChange, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Accent))
        Column {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = TextSecondary)
        }
    }
}


/** Şablon adları */
fun templateName(t: PageTemplate) = when (t) {
    PageTemplate.BLANK -> L("Boş", "Blank")
    PageTemplate.LINED -> L("Çizgili", "Lined")
    PageTemplate.GRID -> L("Kareli", "Grid")
    PageTemplate.GRID_SMALL -> L("Küçük kareli", "Small grid")
    PageTemplate.DOTS -> L("Noktalı", "Dotted")
    PageTemplate.CORNELL -> L("Cornell not", "Cornell notes")
}

/** Resimli şablon seçici (sayfa eklerken / şablonu değiştirirken) */
@Composable
fun TemplatePickerDialog(adding: Boolean, current: PageTemplate, onDismiss: () -> Unit, onPick: (PageTemplate, Boolean) -> Unit) {
    var sel by remember { mutableStateOf(current) }
    var all by remember { mutableStateOf(false) }
    val previews = remember { PageTemplate.entries.associateWith { AppState.renderer.templatePreview(it, 220).asImageBitmap() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) L("Yeni sayfa", "New page") else L("Sayfa şablonu", "Page template")) },
        text = {
            Column {
                PageTemplate.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        row.forEach { t ->
                            val on = t == sel
                            Column(
                                Modifier.width(104.dp).clip(RoundedCornerShape(10.dp)).clickable { sel = t }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.Image(
                                    previews.getValue(t), templateName(t),
                                    modifier = Modifier.width(92.dp).height(130.dp).clip(RoundedCornerShape(6.dp))
                                        .border(if (on) 3.dp else 1.dp, if (on) Accent else Color(0xFFDDDDDD), RoundedCornerShape(6.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(templateName(t), fontSize = 13.sp, color = if (on) AccentDark else TextPrimary,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
                if (!adding) Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { all = !all }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(all, { all = it }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Accent))
                    Text(L("Tüm sayfalara uygula (varsayılan yap)", "Apply to all pages (make default)"), fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton({ onPick(sel, all) }) { Text(if (adding) L("Sayfa ekle", "Add page") else L("Uygula", "Apply"), color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text(L("İptal", "Cancel")) } },
    )
}
