package com.xaser.note.ink

import android.annotation.SuppressLint
import com.xaser.note.L
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.RenderNode
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import com.xaser.note.model.ImageItem
import com.xaser.note.model.InkDocument
import com.xaser.note.model.PAGE_WIDTH
import com.xaser.note.model.Page
import com.xaser.note.model.PageTemplate
import com.xaser.note.model.PenType
import com.xaser.note.model.Stroke
import com.xaser.note.model.TextItem
import com.xaser.note.pdf.PdfPageCache
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool { PEN, ERASER, LASSO, SHAPE, TEXT, CROP }
enum class EraserMode { STROKE, AREA }

/** Uygulama içi pano: kement kopyası veya kırpılmış soru */
object InkClipboard {
    var strokes: List<Stroke> = emptyList()
    var texts: List<TextItem> = emptyList()
    var images: List<Pair<File, ImageItem>> = emptyList()
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty() && images.isEmpty()
    fun clear() { strokes = emptyList(); texts = emptyList(); images = emptyList() }
}

class Selection(val page: Int) {
    val strokes = LinkedHashSet<Stroke>()
    val texts = LinkedHashSet<TextItem>()
    val images = LinkedHashSet<ImageItem>()
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty() && images.isEmpty()
}

@SuppressLint("ViewConstructor")
class InkView(
    context: Context,
    val renderer: PageRenderer,
    val noteDir: File,
    val pdfCache: PdfPageCache?,
) : View(context) {

    interface Listener {
        fun onContentChanged()
        fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean)
        fun onSelectionChanged(sel: Selection?)
        fun onCropSelected(page: Int, rect: RectF)
        fun onTextRequest(page: Int, x: Float, y: Float, existing: TextItem?)
        fun onAutoBeautify(page: Int, strokes: List<Stroke>)
        fun onCurrentPageChanged(page: Int, count: Int)
        /** Kalem tuşuyla araç değişti: araç çubuğu güncellensin */
        fun onToolChanged() {}
    }

    var listener: Listener? = null
    var doc = InkDocument()
        private set

    // ---- Araç ayarları ----
    var tool = Tool.PEN
        set(v) { field = v; if (v != Tool.LASSO) clearSelection(); cropRect = null; invalidate() }
    var penType = PenType.BALLPOINT
        set(v) { if (v != field) prevPenType = field; field = v }
    /** Kalemin ikinci tuşu bir önceki kaleme döndürür */
    var prevPenType = PenType.HIGHLIGHTER
    val penColors = mutableMapOf(
        PenType.BALLPOINT to 0xFF222222.toInt(), PenType.FOUNTAIN to 0xFF1A237E.toInt(),
        PenType.PENCIL to 0xFF555555.toInt(), PenType.MARKER to 0xFFD32F2F.toInt(),
        PenType.HIGHLIGHTER to 0xFFFFEB3B.toInt(),
    )
    val penWidths = mutableMapOf(
        PenType.BALLPOINT to 3.5f, PenType.FOUNTAIN to 4.5f, PenType.PENCIL to 3f,
        PenType.MARKER to 8f, PenType.HIGHLIGHTER to 26f,
    )
    var eraserMode = EraserMode.STROKE
    /** Ekran pikseli cinsinden silgi çapı */
    var eraserSize = 40f
    /** false: sadece kalem çizer, parmak kaydırır (avuç içi reddi) */
    var fingerDraws = false
    var autoBeautify = false
    /** Yazmayı bıraktıktan sonra düzeltmeye kadar bekleme */
    var autoDelayMs = 550L

    // ---- Yerleşim ve görünüm ----
    private val gap = 36f
    private var pageTops = FloatArray(0)
    private var docWidth = PAGE_WIDTH
    private var docHeight = 0f
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var initialized = false
    private val density = resources.displayMetrics.density

    private val nodes = HashMap<Page, RenderNode>()
    private val dirty = HashSet<Page>()

    // ---- Geçmiş ----
    private sealed class Edit {
        class PageEdit(val page: Page, val before: Page) : Edit()
        class PagesEdit(val before: List<Page>) : Edit()
    }
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private val snapshotted = HashSet<Page>()

    // ---- Çizim durumu ----
    private var activePage = -1
    private var active: Stroke? = null
    private val activePts = FloatArrayList()
    private var snapped = false
    private var holdX = 0f; private var holdY = 0f
    private val holdRunnable = Runnable { trySnapActive() }

    private var lassoPath: Path? = null
    private val lassoPts = FloatArrayList()
    private var lassoPage = -1
    var selection: Selection? = null
        private set
    private var selBounds: RectF? = null
    private enum class SelDrag { NONE, MOVE, RESIZE }
    private var selDrag = SelDrag.NONE
    private var dragLastX = 0f; private var dragLastY = 0f
    private var resizeAnchorX = 0f; private var resizeAnchorY = 0f; private var resizeStartW = 1f; private var resizeStartH = 1f

    private var cropRect: RectF? = null
    private var cropPage = -1
    private var cropStartX = 0f; private var cropStartY = 0f

    private var eraserX = -1f; private var eraserY = -1f
    private var drawingPointer = -1
    private var tempEraser = false

    // ---- Kalem tuşları ----
    // Basılı tut: geçici silgi. Tek bas (ekrana dokunmadan): 1. tuş kalem↔silgi, 2. tuş önceki kalem.
    private var btnState = 0
    private var keyHeld = false
    private var btnPressTime = 0L
    private var btnUsed = false
    private var btnPrimary = true

    private val autoPending = mutableListOf<Stroke>()
    private var autoPage = -1
    private val autoRunnable = Runnable { flushAuto() }

    // ---- Hareket ----
    private val scroller = OverScroller(context)
    private var fingerPanning = false
    private var lastFx = 0f; private var lastFy = 0f
    private var velocityTracker: android.view.VelocityTracker? = null
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomBy(d.scaleFactor, d.focusX, d.focusY); return true
        }
    })

    // ---- Boya ----
    private val bgColor = 0xFFEDEEF0.toInt()
    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFF2196F3.toInt(); strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x112196F3 }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2196F3.toInt() }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF888888.toInt(); strokeWidth = 2f }
    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFF6D00.toInt(); strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    private val cropShade = Paint().apply { color = 0x33000000 }
    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBBBBBB.toInt(); textSize = 28f; textAlign = Paint.Align.CENTER }

    init {
        isFocusable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // =====================================================================
    // Belge
    // =====================================================================

    fun setDocument(d: InkDocument) {
        doc = d
        if (doc.pages.isEmpty()) doc.pages.add(Page())
        undoStack.clear(); redoStack.clear(); snapshotted.clear()
        nodes.clear(); dirty.clear()
        relayout()
        initialized = false
        requestLayout(); invalidate()
        notifyHistory()
    }

    private fun relayout() {
        pageTops = FloatArray(doc.pages.size)
        var y = gap
        docWidth = PAGE_WIDTH
        for ((i, p) in doc.pages.withIndex()) {
            pageTops[i] = y
            y += p.height + gap
            docWidth = max(docWidth, p.width)
        }
        docHeight = y
    }

    val currentPage: Int
        get() {
            val cy = (height / 2f - ty) / scale
            for (i in pageTops.indices.reversed()) if (cy >= pageTops[i] - gap) return i
            return 0
        }

    private var lastReportedPage = -1
    private fun reportPage() {
        val cp = currentPage
        if (cp != lastReportedPage) { lastReportedPage = cp; listener?.onCurrentPageChanged(cp, doc.pages.size) }
    }

    fun scrollToPage(i: Int) {
        if (i !in pageTops.indices) return
        ty = -pageTops[i] * scale + 16 * density
        clamp(); invalidate(); reportPage()
    }

    // ---- geçmiş ----
    private fun snapshot(page: Page) {
        if (!snapshotted.add(page)) return
        undoStack.addLast(Edit.PageEdit(page, page.deepCopy()))
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun snapshotPages() {
        undoStack.addLast(Edit.PagesEdit(doc.pages.toList()))
        redoStack.clear()
    }

    /** Bir kullanıcı hareketi bitince çağrılır: sonraki hareket yeni bir geri alma adımı olsun */
    private fun commit() {
        snapshotted.clear()
        notifyHistory()
        listener?.onContentChanged()
    }

    private fun notifyHistory() = listener?.onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())

    fun undo() { swapHistory(undoStack, redoStack) }
    fun redo() { swapHistory(redoStack, undoStack) }

    private fun swapHistory(from: ArrayDeque<Edit>, to: ArrayDeque<Edit>) {
        val e = from.removeLastOrNull() ?: return
        clearSelection()
        when (e) {
            is Edit.PageEdit -> {
                to.addLast(Edit.PageEdit(e.page, e.page.deepCopy()))
                e.page.restoreFrom(e.before)
                markDirty(e.page)
            }
            is Edit.PagesEdit -> {
                to.addLast(Edit.PagesEdit(doc.pages.toList()))
                doc.pages.clear(); doc.pages.addAll(e.before)
                nodes.clear()
            }
        }
        relayout(); clamp(); invalidate()
        notifyHistory(); listener?.onContentChanged()
    }

    private fun markDirty(p: Page) { dirty.add(p) }
    private fun markAllDirty() { dirty.addAll(doc.pages) }

    // ---- sayfa işlemleri ----
    fun insertPage(after: Int, template: PageTemplate) {
        snapshotPages()
        val base = doc.pages.getOrNull(after)
        val h = base?.height?.takeIf { base.pdfIndex >= 0 } ?: com.xaser.note.model.A4_HEIGHT
        doc.pages.add((after + 1).coerceIn(0, doc.pages.size), Page(template = template, baseHeight = h))
        relayout(); clamp(); invalidate(); commit()
    }

    fun deletePage(i: Int) {
        if (doc.pages.size <= 1 || i !in doc.pages.indices) return
        snapshotPages()
        doc.pages.removeAt(i)
        relayout(); clamp(); invalidate(); commit()
    }

    fun setTemplate(i: Int, t: PageTemplate, all: Boolean) {
        val targets = if (all) doc.pages else listOfNotNull(doc.pages.getOrNull(i))
        targets.forEach { snapshot(it); it.template = t; markDirty(it) }
        invalidate(); commit()
    }

    /** PDF sorusunun yanına/altına çözüm alanı açar */
    fun extendMargin(i: Int, right: Float, bottom: Float) {
        val p = doc.pages.getOrNull(i) ?: return
        snapshot(p)
        p.extraRight = max(0f, right); p.extraBottom = max(0f, bottom)
        markDirty(p); relayout(); clamp(); invalidate(); commit()
    }

    // =====================================================================
    // Ölçü ve dönüşümler
    // =====================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized && w > 0) {
            scale = (w - 32 * density) / docWidth
            tx = 0f; ty = 0f
            initialized = true
        }
        clamp()
    }

    private fun minScale() = width * 0.3f / docWidth
    private fun maxScale() = width * 6f / PAGE_WIDTH

    private fun zoomBy(f: Float, fx: Float, fy: Float) {
        val ns = (scale * f).coerceIn(minScale(), maxScale())
        val k = ns / scale
        tx = fx - (fx - tx) * k
        ty = fy - (fy - ty) * k
        scale = ns
        clamp(); invalidate()
    }

    private fun clamp() {
        if (width == 0) return
        val cw = docWidth * scale
        tx = if (cw < width) (width - cw) / 2 else tx.coerceIn(width - cw, 0f)
        val ch = docHeight * scale
        ty = if (ch < height) 0f else ty.coerceIn(height - ch, 0f)
    }

    /** Ekran → belge */
    private fun docX(sx: Float) = (sx - tx) / scale
    private fun docY(sy: Float) = (sy - ty) / scale

    private fun pageAt(dy: Float, dx: Float, slack: Float = 0f): Int {
        for (i in pageTops.indices) {
            val p = doc.pages[i]
            if (dy >= pageTops[i] - slack && dy <= pageTops[i] + p.height + slack && dx >= -slack && dx <= p.width + slack) return i
        }
        return -1
    }

    private fun pageNearest(dy: Float): Int {
        for (i in pageTops.indices) if (dy <= pageTops[i] + doc.pages[i].height + gap / 2) return i
        return pageTops.size - 1
    }

    // =====================================================================
    // Çizim
    // =====================================================================

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            tx = scroller.currX.toFloat(); ty = scroller.currY.toFloat()
            clamp(); reportPage()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(bgColor)
        if (pageTops.size != doc.pages.size) relayout()
        val viewTop = docY(0f); val viewBottom = docY(height.toFloat())
        for ((i, page) in doc.pages.withIndex()) {
            val top = pageTops[i]
            if (top > viewBottom || top + page.height < viewTop) continue
            c.save()
            c.translate(tx, ty)
            c.scale(scale, scale)
            c.translate(0f, top)
            val r = 3f / scale
            c.drawRect(r, r, page.width + r, page.height + r * 2, shadowPaint)
            c.drawRect(0f, 0f, page.width, page.height, pagePaint)
            if (page.pdfIndex >= 0) {
                val bmp = pdfCache?.get(page.pdfIndex, (PAGE_WIDTH * scale).roundToInt())
                if (bmp != null) c.drawBitmap(bmp, null, RectF(0f, 0f, PAGE_WIDTH, page.baseHeight), bmpPaint)
                else c.drawText(L("Yükleniyor…", "Loading…"), PAGE_WIDTH / 2, page.baseHeight / 2, loadingPaint)
                if (page.extraRight > 0 || page.extraBottom > 0) {
                    // genişletilmiş alanın sınırı
                    c.drawLine(PAGE_WIDTH, 0f, PAGE_WIDTH, page.baseHeight, shadowPaint)
                    c.drawLine(0f, page.baseHeight, PAGE_WIDTH, page.baseHeight, shadowPaint)
                }
            }
            drawPageContent(c, i, page)
            if (activePage == i) active?.let { renderer.drawStroke(c, it) }
            selection?.takeIf { it.page == i }?.let { drawSelection(c, it) }
            if (lassoPage == i) lassoPath?.let { c.drawPath(it, selPaint.apply { strokeWidth = 2f / scale }) }
            if (cropPage == i) cropRect?.let { cr ->
                c.save()
                c.clipOutRect(cr)
                c.drawRect(0f, 0f, page.width, page.height, cropShade)
                c.restore()
                cropPaint.strokeWidth = 3f / scale
                c.drawRect(cr, cropPaint)
            }
            c.restore()
        }
        if (eraserX >= 0) {
            c.drawCircle(eraserX, eraserY, eraserSize / 2, eraserPaint)
        }
    }

    private fun drawPageContent(c: Canvas, i: Int, page: Page) {
        val moving = selection?.takeIf { it.page == i && selDrag != SelDrag.NONE }
        if (c.isHardwareAccelerated && moving == null) {
            var node = nodes[page]
            if (node == null || page in dirty) {
                if (node == null) node = RenderNode("page").also { nodes[page] = it }
                node.setPosition(0, 0, page.width.toInt() + 1, page.height.toInt() + 1)
                node.clipToBounds = false
                val rc = node.beginRecording()
                renderer.drawPage(rc, page, noteDir)
                node.endRecording()
                dirty.remove(page)
            }
            c.drawRenderNode(node)
        } else {
            renderer.drawPage(c, page, noteDir)
            dirty.add(page)
        }
    }

    private fun drawSelection(c: Canvas, sel: Selection) {
        val b = selBounds ?: return
        selPaint.strokeWidth = 2f / scale
        c.drawRect(b, selFill)
        c.drawRect(b, selPaint)
        c.drawCircle(b.right, b.bottom, 12f / scale, handlePaint)
    }

    // =====================================================================
    // Dokunma
    // =====================================================================

    private fun isStylus(e: MotionEvent, idx: Int): Boolean {
        val t = e.getToolType(idx)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val stylus = isStylus(e, idx)
                if (drawingPointer == -1 && (stylus || (fingerDraws && e.pointerCount == 1))) {
                    if (stylus) {
                        // Kalem gelince parmak hareketini iptal et (avuç içi reddi)
                        fingerPanning = false
                        requestUnbufferedDispatch(e)
                    }
                    scroller.forceFinished(true)
                    drawingPointer = e.getPointerId(idx)
                    trackButtons(e)
                    tempEraser = wantsEraser(e, idx)
                    if (btnState != 0 || keyHeld) btnUsed = true
                    beginInput(e.getX(idx), e.getY(idx), pressureOf(e, idx))
                    return true
                }
                if (!stylus && drawingPointer != -1 && fingerDraws && !isStylus(e, e.findPointerIndex(drawingPointer))) {
                    // Parmakla çizerken ikinci parmak: çizimi iptal edip yakınlaştırmaya geç
                    cancelInput()
                }
                if (drawingPointer == -1 && !stylus) {
                    scroller.forceFinished(true)
                    fingerPanning = true
                    lastFx = focalX(e); lastFy = focalY(e)
                    velocityTracker?.recycle(); velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(e)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawingPointer != -1) {
                    val pi = e.findPointerIndex(drawingPointer)
                    if (pi < 0) return true
                    trackButtons(e)
                    // Yazarken tuşa basılırsa/bırakılırsa: o ana kadarki çizgiyi bitir, silgiye (veya geri kaleme) geç
                    val want = wantsEraser(e, pi)
                    if (want != tempEraser) {
                        endInput()
                        tempEraser = want
                        if (want) btnUsed = true
                        beginInput(e.getX(pi), e.getY(pi), pressureOf(e, pi))
                        return true
                    }
                    for (h in 0 until e.historySize) {
                        moveInput(e.getHistoricalX(pi, h), e.getHistoricalY(pi, h), e.getHistoricalPressure(pi, h).let { if (isStylus(e, pi)) it else 0.5f })
                    }
                    moveInput(e.getX(pi), e.getY(pi), pressureOf(e, pi))
                    return true
                }
                if (fingerPanning) {
                    velocityTracker?.addMovement(e)
                    scaleDetector.onTouchEvent(e)
                    val fx = focalX(e); val fy = focalY(e)
                    tx += fx - lastFx; ty += fy - lastFy
                    lastFx = fx; lastFy = fy
                    clamp(); invalidate(); reportPage()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(idx) == drawingPointer) { endInput(); drawingPointer = -1 }
                else if (fingerPanning) {
                    scaleDetector.onTouchEvent(e)
                    // odak noktası değişir, sıçramayı önle
                    lastFx = focalX(e, idx); lastFy = focalY(e, idx)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (drawingPointer != -1) { endInput(); drawingPointer = -1 }
                else if (fingerPanning) {
                    velocityTracker?.addMovement(e)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f; val vy = velocityTracker?.yVelocity ?: 0f
                    if (hypot(vx, vy) > 300) {
                        val cw = docWidth * scale; val ch = docHeight * scale
                        scroller.fling(tx.toInt(), ty.toInt(), vx.toInt(), vy.toInt(),
                            min(0, (width - cw).toInt()), 0, min(0, (height - ch).toInt()), 0)
                        postInvalidateOnAnimation()
                    }
                }
                fingerPanning = false
                velocityTracker?.recycle(); velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawingPointer != -1) { cancelInput(); drawingPointer = -1 }
                fingerPanning = false
            }
        }
        if (fingerPanning && e.actionMasked != MotionEvent.ACTION_MOVE) scaleDetector.onTouchEvent(e)
        return true
    }

    private fun stylusButtons(e: MotionEvent) =
        e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY or
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY)

    private fun wantsEraser(e: MotionEvent, i: Int) =
        e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER || stylusButtons(e) != 0 || keyHeld

    /** Tuş basma/bırakma geçişlerini izler; dokunmadan kısa basış = tıklama */
    private fun trackButtons(e: MotionEvent) {
        val st = stylusButtons(e)
        if (st == btnState) return
        if (btnState == 0 && st != 0) {
            btnPressTime = System.currentTimeMillis(); btnUsed = drawingPointer != -1
            btnPrimary = (st and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY)) != 0
        } else if (st == 0) {
            if (!btnUsed && System.currentTimeMillis() - btnPressTime < 450) penKeyTap()
        }
        btnState = st
    }

    /** Şu an kalem tuşu basılı mı (araç çubuğunda silgiyi göstermek için) */
    val penKeyErasing get() = keyHeld

    private fun prevPen() {
        if (tool != Tool.PEN) tool = Tool.PEN
        else {
            val all = PenType.entries
            penType = all[(all.indexOf(penType) - 1 + all.size) % all.size]
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listener?.onToolChanged(); invalidate()
    }

    /** Tek basış: kalemler sırayla (tükenmez → dolma → kurşun → işaretleyici → fosforlu → …) */
    private fun nextPen() {
        if (tool != Tool.PEN) tool = Tool.PEN
        else {
            val all = PenType.entries
            penType = all[(all.indexOf(penType) + 1) % all.size]
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listener?.onToolChanged()
        invalidate()
    }

    private fun stylusClick(primary: Boolean) {
        if (primary) {
            tool = if (tool == Tool.ERASER) Tool.PEN else Tool.ERASER
        } else {
            tool = Tool.PEN
            penType = prevPenType
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listener?.onToolChanged()
        invalidate()
    }

    /**
     * Bazı kalemler tuşları klavye tuşu olarak gönderir (Android 14+: STYLUS_BUTTON_*).
     * Aktivite bu fonksiyona yönlendirir.
     */
    fun handleKey(e: android.view.KeyEvent): Boolean {
        val devName = e.device?.name.orEmpty()
        // Xiaomi/Redmi kalem tuşları Xiaomi servisinden sanal Page Up / Page Down olarak gelir.
        val fromPen = devName.contains("pen", true) || devName.contains("stylus", true)
        val primary = when {
            e.keyCode in PRIMARY_KEYS -> true
            e.keyCode in SECONDARY_KEYS -> false
            fromPen && e.keyCode != android.view.KeyEvent.KEYCODE_POWER -> true
            else -> return false
        }
        when (e.action) {
            // Basılı tutulduğu sürece silgi: kalem ekrana değince wantsEraser() keyHeld'e bakar
            android.view.KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                keyHeld = true; btnPressTime = e.eventTime; btnUsed = drawingPointer != -1
                listener?.onToolChanged()
            }
            android.view.KeyEvent.ACTION_UP -> {
                val hadDown = keyHeld
                keyHeld = false
                val canceled = (e.flags and android.view.KeyEvent.FLAG_CANCELED) != 0
                // Kısa basış (silmeden): alt tuş = sıradaki kalem, üst tuş = önceki kalem.
                // Bazen ilk basışın DOWN olayı gelmez; o durumda da kısa basış say.
                val short = !hadDown || e.eventTime - btnPressTime < 450
                if (!canceled && !btnUsed && short) { if (primary && e.keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) prevPen() else nextPen() }
                listener?.onToolChanged()
                invalidate()
            }
        }
        return true
    }

    // Xiaomi basılı tutmayı yalnızca kendi Notlar uygulamasına bırakır; bu yüzden silgi = çift basış.
    private val singleTapRunnable = Runnable { nextPen() }
    private var lastTapTime = 0L

    private fun penKeyTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 350) {
            removeCallbacks(singleTapRunnable)
            lastTapTime = 0L
            tool = if (tool == Tool.ERASER) Tool.PEN else Tool.ERASER
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onToolChanged(); invalidate()
        } else {
            lastTapTime = now
            postDelayed(singleTapRunnable, 350)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Xiaomi kendi ekranını açınca tuş bırakma olayı bize gelmez: silgide takılı kalmasın
        if (!hasWindowFocus) { keyHeld = false; btnState = 0 }
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        trackButtons(e)
        return super.onGenericMotionEvent(e)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); current = this }
    override fun onDetachedFromWindow() { if (current === this) current = null; super.onDetachedFromWindow() }

    companion object {
        /** Ekrandaki düzenleyici (kalem tuşu olayları için) */
        var current: InkView? = null
        /** Kalemin üst (birinci) tuşu: Page Up, STYLUS_PRIMARY/TERTIARY/TAIL, BUTTON_7, F13 */
        private val PRIMARY_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_PAGE_UP, 308, 310, 311,
            android.view.KeyEvent.KEYCODE_BUTTON_7, android.view.KeyEvent.KEYCODE_F13,
        )
        /** Kalemin alt (ikinci) tuşu: Page Down, STYLUS_SECONDARY, BUTTON_8, F14 */
        private val SECONDARY_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_PAGE_DOWN, 309,
            android.view.KeyEvent.KEYCODE_BUTTON_8, android.view.KeyEvent.KEYCODE_F14,
        )
    }

    override fun onHoverEvent(e: MotionEvent): Boolean {
        trackButtons(e)
        val showEraser = tool == Tool.ERASER || stylusButtons(e) != 0 || keyHeld
        if (showEraser && e.actionMasked != MotionEvent.ACTION_HOVER_EXIT) { eraserX = e.x; eraserY = e.y; invalidate(); return super.onHoverEvent(e) }
        if (tool == Tool.ERASER && e.actionMasked != MotionEvent.ACTION_HOVER_EXIT) { eraserX = e.x; eraserY = e.y } else eraserX = -1f
        invalidate()
        return super.onHoverEvent(e)
    }

    private fun pressureOf(e: MotionEvent, i: Int) = if (isStylus(e, i)) e.getPressure(i).coerceIn(0f, 1f) else 0.5f

    private fun focalX(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getX(i); n++ }
        return if (n == 0) 0f else s / n
    }
    private fun focalY(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getY(i); n++ }
        return if (n == 0) 0f else s / n
    }

    private val effectiveTool get() = if (tempEraser) Tool.ERASER else tool

    private fun beginInput(sx: Float, sy: Float, p: Float) {
        val dx = docX(sx); val dy = docY(sy)
        when (effectiveTool) {
            Tool.PEN, Tool.SHAPE -> {
                val pi = pageAt(dy, dx, 20f)
                if (pi < 0) return
                if (autoPage != -1 && autoPage != pi) flushAuto()
                removeCallbacks(autoRunnable)
                activePage = pi
                val px = dx; val py = dy - pageTops[pi]
                activePts.clear(); activePts.add(px, py, p)
                active = Stroke(penType, penColors[penType]!!, penWidths[penType]!!, activePts.toArray())
                snapped = false
                holdX = sx; holdY = sy
                postDelayed(holdRunnable, 650)
                invalidate()
            }
            Tool.ERASER -> {
                activePage = pageAt(dy, dx, 20f)
                eraserX = sx; eraserY = sy
                eraseAt(dx, dy)
            }
            Tool.LASSO -> {
                val sel = selection
                val b = selBounds
                if (sel != null && b != null) {
                    val py = dy - pageTops[sel.page]
                    val hr = 28f / scale
                    if (hypot(dx - b.right, py - b.bottom) < hr) {
                        selDrag = SelDrag.RESIZE
                        resizeAnchorX = b.left; resizeAnchorY = b.top
                        resizeStartW = max(1f, b.width()); resizeStartH = max(1f, b.height())
                        dragLastX = dx; dragLastY = py
                        snapshot(doc.pages[sel.page]); invalidate(); return
                    }
                    if (RectF(b).apply { inset(-12f / scale, -12f / scale) }.contains(dx, py)) {
                        selDrag = SelDrag.MOVE
                        dragLastX = dx; dragLastY = py
                        snapshot(doc.pages[sel.page]); invalidate(); return
                    }
                    clearSelection()
                }
                val pi = pageAt(dy, dx, 20f)
                if (pi < 0) return
                lassoPage = pi
                lassoPts.clear(); lassoPts.add(dx, dy - pageTops[pi], 0f)
                lassoPath = Path().apply { moveTo(dx, dy - pageTops[pi]) }
            }
            Tool.CROP -> {
                val pi = pageAt(dy, dx, 20f)
                if (pi < 0) return
                cropPage = pi
                cropStartX = dx; cropStartY = dy - pageTops[pi]
                cropRect = RectF(cropStartX, cropStartY, cropStartX, cropStartY)
                invalidate()
            }
            Tool.TEXT -> {
                val pi = pageAt(dy, dx)
                if (pi < 0) return
                val py = dy - pageTops[pi]
                val hit = doc.pages[pi].texts.lastOrNull { renderer.textBounds(it).apply { inset(-10f, -10f) }.contains(dx, py) }
                listener?.onTextRequest(pi, dx, py, hit)
            }
        }
    }

    private fun moveInput(sx: Float, sy: Float, p: Float) {
        val dx = docX(sx); val dy = docY(sy)
        when (effectiveTool) {
            Tool.PEN, Tool.SHAPE -> {
                val s = active ?: return
                if (snapped) return
                val px = dx; val py = dy - pageTops[activePage]
                val n = activePts.size
                if (n >= 3 && hypot(activePts[n - 3] - px, activePts[n - 2] - py) * scale < 0.8f) return
                activePts.add(px, py, p)
                s.points = activePts.toArray()
                if (hypot(sx - holdX, sy - holdY) > 8 * density) {
                    holdX = sx; holdY = sy
                    removeCallbacks(holdRunnable); postDelayed(holdRunnable, 650)
                }
                invalidate()
            }
            Tool.ERASER -> {
                // araya nokta ekleyerek hızlı harekette boşluk kalmasın
                val steps = max(1, (hypot(sx - eraserX, sy - eraserY) / (eraserSize / 3)).toInt())
                val ox = eraserX; val oy = eraserY
                for (k in 1..steps) {
                    val ix = ox + (sx - ox) * k / steps; val iy = oy + (sy - oy) * k / steps
                    eraseAt(docX(ix), docY(iy))
                }
                eraserX = sx; eraserY = sy
                invalidate()
            }
            Tool.LASSO -> {
                val sel = selection
                if (sel != null && selDrag != SelDrag.NONE) {
                    val py = dy - pageTops[sel.page]
                    if (selDrag == SelDrag.MOVE) translateSelection(dx - dragLastX, py - dragLastY)
                    else {
                        val nw = max(10f, dx - resizeAnchorX); val nh = max(10f, py - resizeAnchorY)
                        val k = min(nw / resizeStartW, nh / resizeStartH).coerceIn(0.05f, 20f)
                        val b = selBounds!!
                        val cur = b.width() / resizeStartW
                        scaleSelection(k / cur, resizeAnchorX, resizeAnchorY)
                    }
                    dragLastX = dx; dragLastY = py
                    invalidate(); return
                }
                if (lassoPage < 0) return
                val py = dy - pageTops[lassoPage]
                lassoPts.add(dx, py, 0f)
                lassoPath?.lineTo(dx, py)
                invalidate()
            }
            Tool.CROP -> {
                if (cropPage < 0) return
                val page = doc.pages[cropPage]
                val px = dx.coerceIn(0f, page.width); val py = (dy - pageTops[cropPage]).coerceIn(0f, page.height)
                cropRect = RectF(min(cropStartX, px), min(cropStartY, py), max(cropStartX, px), max(cropStartY, py))
                invalidate()
            }
            Tool.TEXT -> {}
        }
    }

    private fun endInput() {
        removeCallbacks(holdRunnable)
        when (effectiveTool) {
            Tool.PEN, Tool.SHAPE -> {
                val s = active
                if (s != null && activePage >= 0) {
                    if (effectiveTool == Tool.SHAPE && !snapped) {
                        ShapeSnapper.snap(s.points)?.let { s.points = it.points; snapped = true }
                    }
                    val page = doc.pages[activePage]
                    snapshot(page)
                    page.strokes.add(s)
                    markDirty(page)
                    if (autoBeautify && !snapped && tool == Tool.PEN && penType != PenType.HIGHLIGHTER) {
                        autoPage = activePage
                        autoPending.add(s)
                        postDelayed(autoRunnable, autoDelayMs)
                    }
                    commit()
                }
                active = null; activePage = -1
                invalidate()
            }
            Tool.ERASER -> {
                eraserX = -1f
                if (snapshotted.isNotEmpty()) commit()
                activePage = -1
                invalidate()
            }
            Tool.LASSO -> {
                if (selDrag != SelDrag.NONE) {
                    selDrag = SelDrag.NONE
                    selection?.let { markDirty(doc.pages[it.page]) }
                    commit(); invalidate(); return
                }
                if (lassoPage >= 0) finishLasso()
                lassoPath = null; lassoPage = -1
                invalidate()
            }
            Tool.CROP -> {
                val r = cropRect
                if (r != null && r.width() > 20 && r.height() > 20) listener?.onCropSelected(cropPage, RectF(r))
                else { cropRect = null; cropPage = -1 }
                invalidate()
            }
            Tool.TEXT -> {}
        }
        tempEraser = false
    }

    private fun cancelInput() {
        removeCallbacks(holdRunnable)
        active = null; activePage = -1
        lassoPath = null; lassoPage = -1
        selDrag = SelDrag.NONE
        tempEraser = false
        invalidate()
    }

    fun clearCrop() { cropRect = null; cropPage = -1; invalidate() }

    /** Basılı tut → düzgün şekle çevir */
    private fun trySnapActive() {
        val s = active ?: return
        if (snapped || penType == PenType.HIGHLIGHTER && tool != Tool.SHAPE) return
        val r = ShapeSnapper.snap(s.points) ?: return
        s.points = r.points
        snapped = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    // ---- silgi ----
    private fun eraseAt(dx: Float, dy: Float) {
        val pi = pageAt(dy, dx, 20f)
        if (pi < 0) return
        val page = doc.pages[pi]
        val x = dx; val y = dy - pageTops[pi]
        val r = eraserSize / 2 / scale
        var changed = false
        // metin kutuları her iki modda da bütün olarak silinir
        val tHit = page.texts.filter { renderer.textBounds(it).contains(x, y) }
        if (tHit.isNotEmpty()) { snapshot(page); page.texts.removeAll(tHit.toSet()); changed = true }
        if (eraserMode == EraserMode.STROKE) {
            val hit = page.strokes.filter { strokeHits(it, x, y, r) }
            if (hit.isNotEmpty()) { snapshot(page); page.strokes.removeAll(hit.toSet()); changed = true }
        } else {
            val out = ArrayList<Stroke>(page.strokes.size)
            var any = false
            for (s in page.strokes) {
                val b = s.bounds()
                if (x + r < b[0] || x - r > b[2] || y + r < b[1] || y - r > b[3]) { out.add(s); continue }
                val pieces = splitStroke(s, x, y, r + s.width / 2)
                if (pieces == null) out.add(s) else { any = true; out.addAll(pieces) }
            }
            if (any) { snapshot(page); page.strokes.clear(); page.strokes.addAll(out); changed = true }
        }
        if (changed) { markDirty(page); invalidate() }
    }

    private fun strokeHits(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val b = s.bounds()
        if (x + r < b[0] || x - r > b[2] || y + r < b[1] || y - r > b[3]) return false
        val p = s.points
        val rr = r + s.width / 2
        if (p.size == 3) return hypot(p[0] - x, p[1] - y) < rr
        var i = 0
        while (i + 3 < p.size) {
            if (segDist(x, y, p[i], p[i + 1], p[i + 3], p[i + 4]) < rr) return true
            i += 3
        }
        return false
    }

    /** Silginin içinde kalan kısmı çıkarıp parçaları döndürür; değişiklik yoksa null */
    private fun splitStroke(s: Stroke, x: Float, y: Float, r: Float): List<Stroke>? {
        val p = s.points
        val n = p.size / 3
        val inside = BooleanArray(n) { hypot(p[it * 3] - x, p[it * 3 + 1] - y) < r }
        var cutSeg = false
        val breakAfter = BooleanArray(n)
        for (i in 0 until n - 1) {
            if (!inside[i] && !inside[i + 1] && segDist(x, y, p[i * 3], p[i * 3 + 1], p[i * 3 + 3], p[i * 3 + 4]) < r) {
                breakAfter[i] = true; cutSeg = true
            }
        }
        if (inside.none { it } && !cutSeg) return null
        val pieces = mutableListOf<Stroke>()
        val cur = FloatArrayList()
        fun flush() {
            if (cur.size >= 6) pieces.add(Stroke(s.pen, s.color, s.width, cur.toArray()))
            cur.clear()
        }
        for (i in 0 until n) {
            if (inside[i]) { flush(); continue }
            cur.add(p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
            if (breakAfter[i]) flush()
        }
        flush()
        return pieces
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-6f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    // ---- kement ----
    private fun finishLasso() {
        val page = doc.pages[lassoPage]
        val pts = lassoPts.toArray()
        val sel = Selection(lassoPage)
        val poly = Path()
        var len = 0f
        for (i in pts.indices step 3) {
            if (i == 0) poly.moveTo(pts[0], pts[1]) else { poly.lineTo(pts[i], pts[i + 1]); len += hypot(pts[i] - pts[i - 3], pts[i + 1] - pts[i - 2]) }
        }
        poly.close()
        if (len < 30f) {
            // dokunma: altındaki nesneyi seç
            val x = pts[0]; val y = pts[1]
            page.texts.lastOrNull { renderer.textBounds(it).contains(x, y) }?.let { sel.texts.add(it) }
                ?: page.images.lastOrNull { RectF(it.x, it.y, it.x + it.w, it.y + it.h).contains(x, y) }?.let { sel.images.add(it) }
                ?: page.strokes.lastOrNull { strokeHits(it, x, y, 12f / scale) }?.let { sel.strokes.add(it) }
        } else {
            val bounds = RectF(); poly.computeBounds(bounds, true)
            val region = Region().apply {
                setPath(poly, Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 1, bounds.bottom.toInt() + 1))
            }
            for (s in page.strokes) {
                val p = s.points
                var inside = 0; var total = 0
                var i = 0
                while (i < p.size) { total++; if (region.contains(p[i].toInt(), p[i + 1].toInt())) inside++; i += 3 }
                if (total > 0 && inside >= total * 0.6f) sel.strokes.add(s)
            }
            for (t in page.texts) { val b = renderer.textBounds(t); if (region.contains(b.centerX().toInt(), b.centerY().toInt())) sel.texts.add(t) }
            for (im in page.images) if (region.contains((im.x + im.w / 2).toInt(), (im.y + im.h / 2).toInt())) sel.images.add(im)
        }
        if (sel.isEmpty) { clearSelection(); return }
        selection = sel
        updateSelBounds()
        listener?.onSelectionChanged(sel)
    }

    private fun updateSelBounds() {
        val sel = selection ?: run { selBounds = null; return }
        val r = PageRenderer.boundsOf(sel.strokes) ?: RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (t in sel.texts) r.union(renderer.textBounds(t))
        for (im in sel.images) r.union(im.x, im.y, im.x + im.w, im.y + im.h)
        selBounds = r
    }

    fun clearSelection() {
        if (selection == null) return
        selection = null; selBounds = null; selDrag = SelDrag.NONE
        listener?.onSelectionChanged(null)
        invalidate()
    }

    private fun translateSelection(ddx: Float, ddy: Float) {
        val sel = selection ?: return
        for (s in sel.strokes) { val p = s.points; var i = 0; while (i < p.size) { p[i] += ddx; p[i + 1] += ddy; i += 3 } }
        for (t in sel.texts) { t.x += ddx; t.y += ddy }
        for (im in sel.images) { im.x += ddx; im.y += ddy }
        selBounds?.offset(ddx, ddy)
    }

    private fun scaleSelection(k: Float, ax: Float, ay: Float) {
        val sel = selection ?: return
        for (s in sel.strokes) {
            val p = s.points; var i = 0
            while (i < p.size) { p[i] = ax + (p[i] - ax) * k; p[i + 1] = ay + (p[i + 1] - ay) * k; i += 3 }
            s.width *= k
        }
        for (t in sel.texts) { t.x = ax + (t.x - ax) * k; t.y = ay + (t.y - ay) * k; t.size *= k }
        for (im in sel.images) { im.x = ax + (im.x - ax) * k; im.y = ay + (im.y - ay) * k; im.w *= k; im.h *= k }
        updateSelBounds()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        val page = doc.pages[sel.page]
        snapshot(page)
        page.strokes.removeAll(sel.strokes); page.texts.removeAll(sel.texts); page.images.removeAll(sel.images)
        markDirty(page); clearSelection(); commit(); invalidate()
    }

    /** Seçili metin kutularının yazı tipini değiştirir */
    fun setFontForSelection(font: String) {
        val sel = selection ?: return
        if (sel.texts.isEmpty()) return
        val page = doc.pages[sel.page]
        snapshot(page)
        sel.texts.forEach { it.font = font }
        updateSelBounds(); markDirty(page); commit(); invalidate()
    }

    fun recolorSelection(color: Int) {
        val sel = selection ?: return
        val page = doc.pages[sel.page]
        snapshot(page)
        sel.strokes.forEach { if (it.pen != PenType.HIGHLIGHTER) it.color = color }
        sel.texts.forEach { it.color = color }
        markDirty(page); commit(); invalidate()
    }

    fun copySelection(cut: Boolean) {
        val sel = selection ?: return
        InkClipboard.strokes = sel.strokes.map { it.copy() }
        InkClipboard.texts = sel.texts.map { it.copy() }
        InkClipboard.images = sel.images.map { File(noteDir, it.file) to it.copy() }
        if (cut) deleteSelection()
    }

    /** Panodakini mevcut sayfanın görünen ortasına yapıştırır */
    fun paste(store: (File) -> String) {
        if (InkClipboard.isEmpty) return
        val pi = currentPage
        val page = doc.pages[pi]
        snapshot(page)
        val strokes = InkClipboard.strokes.map { it.copy() }
        val texts = InkClipboard.texts.map { it.copy() }
        val images = InkClipboard.images.map { (f, im) ->
            val name = if (f.parentFile == noteDir) im.file else store(f)
            ImageItem(name, im.x, im.y, im.w, im.h)
        }
        // içerik sınırları
        val b = PageRenderer.boundsOf(strokes) ?: RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        texts.forEach { b.union(renderer.textBounds(it)) }
        images.forEach { b.union(it.x, it.y, it.x + it.w, it.y + it.h) }
        val cx = docX(width / 2f).coerceIn(0f, page.width); val cy = (docY(height / 2f) - pageTops[pi]).coerceIn(0f, page.height)
        // Sayfaya sığmayan resmi küçült
        val maxW = page.width * 0.9f
        val k = if (b.width() > maxW) maxW / b.width() else 1f
        val ox = cx - b.centerX() * k; val oy = cy - b.centerY() * k
        strokes.forEach { s -> val p = s.points; var i = 0; while (i < p.size) { p[i] = p[i] * k + ox; p[i + 1] = p[i + 1] * k + oy; i += 3 }; s.width *= k }
        texts.forEach { it.x = it.x * k + ox; it.y = it.y * k + oy; it.size *= k }
        images.forEach { it.x = it.x * k + ox; it.y = it.y * k + oy; it.w *= k; it.h *= k }
        page.strokes.addAll(strokes); page.texts.addAll(texts); page.images.addAll(images)
        markDirty(page); commit()
        // yapıştırılanı seçili bırak
        tool = Tool.LASSO
        selection = Selection(pi).apply { this.strokes.addAll(strokes); this.texts.addAll(texts); this.images.addAll(images) }
        updateSelBounds(); listener?.onSelectionChanged(selection)
        invalidate()
    }

    fun selectedStrokes(): List<Stroke> = selection?.strokes?.filter { it.pen != PenType.HIGHLIGHTER } ?: emptyList()

    /** El yazısı çizgilerini tanınan metin kutularıyla değiştirir (geri alınabilir) */
    fun replaceStrokesWithTexts(pageIndex: Int, strokes: Collection<Stroke>, texts: List<TextItem>) {
        val page = doc.pages.getOrNull(pageIndex) ?: return
        snapshot(page)
        page.strokes.removeAll(strokes.toSet())
        page.texts.addAll(texts)
        markDirty(page)
        if (selection?.page == pageIndex) clearSelection()
        commit(); invalidate()
    }

    fun addText(pageIndex: Int, item: TextItem) {
        val page = doc.pages.getOrNull(pageIndex) ?: return
        snapshot(page); page.texts.add(item); markDirty(page); commit(); invalidate()
    }

    fun updateText(pageIndex: Int, item: TextItem, newText: String) {
        val page = doc.pages.getOrNull(pageIndex) ?: return
        snapshot(page)
        // snapshot kopyaladığı için orijinal nesneyi güncellemek güvenli
        if (newText.isBlank()) page.texts.remove(item) else item.text = newText
        markDirty(page); commit(); invalidate()
    }

    fun addImage(name: String, bw: Int, bh: Int) {
        val pi = currentPage
        val page = doc.pages[pi]
        snapshot(page)
        val w = min(page.width * 0.7f, bw.toFloat()); val h = w * bh / max(1, bw)
        val cx = docX(width / 2f).coerceIn(0f, page.width); val cy = (docY(height / 2f) - pageTops[pi]).coerceIn(0f, page.height)
        page.images.add(ImageItem(name, cx - w / 2, cy - h / 2, w, h))
        markDirty(page); commit(); invalidate()
    }

    // ---- otomatik düzeltme ----
    private fun flushAuto() {
        removeCallbacks(autoRunnable)
        if (autoPending.isEmpty() || autoPage < 0) { autoPending.clear(); return }
        val page = doc.pages.getOrNull(autoPage)
        val list = autoPending.filter { page?.strokes?.contains(it) == true }
        val p = autoPage
        autoPending.clear(); autoPage = -1
        if (list.isNotEmpty()) listener?.onAutoBeautify(p, list)
    }

    // ---- görüntü üretimi ----
    /** Bir sayfa bölgesini (PDF arka planı dahil) bitmap'e çizer: kırp → nota at */
    fun renderRegion(pageIndex: Int, rect: RectF, maxPx: Int = 1800): Bitmap {
        val page = doc.pages[pageIndex]
        val k = min(3f, maxPx / max(1f, max(rect.width(), rect.height())))
        val bw = max(1, (rect.width() * k).roundToInt()); val bh = max(1, (rect.height() * k).roundToInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(k, k)
        c.translate(-rect.left, -rect.top)
        if (page.pdfIndex >= 0 && pdfCache != null) {
            pdfCache.renderBlocking(page.pdfIndex, (PAGE_WIDTH * k).roundToInt().coerceAtMost(4000))?.let {
                c.drawBitmap(it, null, RectF(0f, 0f, PAGE_WIDTH, page.baseHeight), bmpPaint)
            }
        }
        renderer.drawPage(c, page, noteDir)
        return bmp
    }

    fun renderThumbnail(widthPx: Int = 360): Bitmap? {
        val page = doc.pages.firstOrNull() ?: return null
        val k = widthPx / page.width
        val h = min(page.height, page.width * 1.3f)
        val bmp = Bitmap.createBitmap(widthPx, max(1, (h * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(k, k)
        if (page.pdfIndex >= 0) pdfCache?.get(page.pdfIndex, 512)?.let { c.drawBitmap(it, null, RectF(0f, 0f, PAGE_WIDTH, page.baseHeight), bmpPaint) }
        renderer.drawPage(c, page, noteDir)
        return bmp
    }

    fun pageCount() = doc.pages.size
    fun pageAtIndex(i: Int) = doc.pages[i]
    fun redrawAll() { markAllDirty(); invalidate() }
}

/** Kutulama yapmayan basit float listesi */
class FloatArrayList {
    private var a = FloatArray(384)
    var size = 0
        private set
    fun add(x: Float, y: Float, p: Float) {
        if (size + 3 > a.size) a = a.copyOf(a.size * 2)
        a[size++] = x; a[size++] = y; a[size++] = p
    }
    operator fun get(i: Int) = a[i]
    fun clear() { size = 0 }
    fun toArray(): FloatArray = a.copyOf(size)
}

@Suppress("unused")
private fun Matrix.noop() = this
