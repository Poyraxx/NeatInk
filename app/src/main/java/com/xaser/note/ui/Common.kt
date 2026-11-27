package com.xaser.note.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xaser.note.data.NoteStore
import com.xaser.note.ink.PageRenderer
import com.xaser.note.model.PenType
import com.xaser.note.recognition.InkRecognizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Uygulama genelinde paylaşılan nesneler */
object AppState {
    lateinit var store: NoteStore
    lateinit var renderer: PageRenderer
    private val recognizerTr by lazy { InkRecognizer("tr") }
    private val recognizerEn by lazy { InkRecognizer("en") }
    /** Uygulama diline göre el yazısı modeli (Türkçe / İngilizce) */
    val recognizer: InkRecognizer get() = if (com.xaser.note.Lang.isEnglish) recognizerEn else recognizerTr
    val mistakes by lazy { com.xaser.note.data.MistakeStore(store.root) }
    val study by lazy { com.xaser.note.data.StudyStore(store.root) }

    fun init(context: Context) {
        if (!::store.isInitialized) store = NoteStore(context.applicationContext)
        if (!::renderer.isInitialized) renderer = PageRenderer(context.applicationContext)
    }
}

val Accent = Color(0xFFFF9800)
val AccentDark = Color(0xFFF57C00)
val Bg = Color(0xFFF7F7F7)
val CardBg = Color.White
val TextPrimary = Color(0xFF1C1C1C)
val TextSecondary = Color(0xFF8A8A8A)

private val scheme: ColorScheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White, secondary = AccentDark,
    background = Bg, surface = Color.White, onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEDEDED), primaryContainer = Color(0xFFFFE0B2),
)

@Composable
fun NoteTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

val Palette = listOf(
    0xFF222222, 0xFF757575, 0xFFD32F2F, 0xFFF57C00, 0xFFFBC02D, 0xFF388E3C,
    0xFF00897B, 0xFF1976D2, 0xFF1A237E, 0xFF7B1FA2, 0xFFE91E63, 0xFF795548,
).map { it.toInt() }

val HighlighterPalette = listOf(
    0xFFFFEB3B, 0xFF8BC34A, 0xFF4DD0E1, 0xFFFF80AB, 0xFFFFB74D, 0xFFB39DDB,
).map { it.toInt() }

fun formatDate(t: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val tr = com.xaser.note.Lang.locale
    return when {
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", tr).format(Date(t))
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", tr).format(Date(t))
        else -> SimpleDateFormat("d MMM yyyy", tr).format(Date(t))
    }
}

/**
 * Kalem simgesi: dik duran kalem gövdesi, ucu seçili renkte.
 * Seçili kalem yukarı kalkık görünür (not uygulamalarındaki kalem kutusu gibi).
 */
@Composable
fun PenIcon(type: PenType, color: Color, selected: Boolean, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val lift = if (selected) 0f else h * 0.16f
        val bodyW = w * 0.34f
        val left = (w - bodyW) / 2
        val bodyColor = Color(0xFFF2F2F2)
        val outline = Color(0xFFBDBDBD)
        val tipTop = h * 0.30f + lift
        val bottom = h * 1.05f
        when (type) {
            PenType.BALLPOINT, PenType.PENCIL, PenType.FOUNTAIN -> {
                val body = Path().apply {
                    moveTo(left, bottom); lineTo(left, tipTop + h * 0.12f)
                    lineTo(w / 2, tipTop - h * 0.14f); lineTo(left + bodyW, tipTop + h * 0.12f)
                    lineTo(left + bodyW, bottom); close()
                }
                drawPath(body, bodyColor); drawPath(body, outline, style = Stroke(1.2f))
                // uç
                val tip = Path().apply {
                    val ty = tipTop - h * 0.14f
                    moveTo(w / 2, ty)
                    lineTo(w / 2 - bodyW * (if (type == PenType.FOUNTAIN) 0.30f else 0.22f), ty + h * 0.11f)
                    lineTo(w / 2 + bodyW * (if (type == PenType.FOUNTAIN) 0.30f else 0.22f), ty + h * 0.11f); close()
                }
                drawPath(tip, if (type == PenType.PENCIL) Color(0xFF5D5D5D) else color)
                drawRect(color, Offset(left, tipTop + h * 0.22f), Size(bodyW, h * 0.07f))
                if (type == PenType.FOUNTAIN) drawLine(Color.White, Offset(w / 2, tipTop - h * 0.1f), Offset(w / 2, tipTop - h * 0.04f), 1.5f)
                if (type == PenType.PENCIL) drawRect(Color(0xFFFFE0B2), Offset(left, tipTop + h * 0.12f), Size(bodyW, h * 0.05f))
            }
            PenType.MARKER, PenType.HIGHLIGHTER -> {
                val bw = bodyW * 1.25f
                val l = (w - bw) / 2
                drawRoundRect(bodyColor, Offset(l, tipTop + h * 0.08f), Size(bw, bottom - tipTop), CornerRadius(4f))
                drawRoundRect(outline, Offset(l, tipTop + h * 0.08f), Size(bw, bottom - tipTop), CornerRadius(4f), style = Stroke(1.2f))
                val tip = Path().apply {
                    if (type == PenType.HIGHLIGHTER) {
                        moveTo(l + bw * 0.2f, tipTop + h * 0.08f); lineTo(l + bw * 0.2f, tipTop - h * 0.06f)
                        lineTo(l + bw * 0.8f, tipTop - h * 0.12f); lineTo(l + bw * 0.8f, tipTop + h * 0.08f)
                    } else {
                        moveTo(l + bw * 0.25f, tipTop + h * 0.08f); lineTo(l + bw * 0.38f, tipTop - h * 0.1f)
                        lineTo(l + bw * 0.62f, tipTop - h * 0.1f); lineTo(l + bw * 0.75f, tipTop + h * 0.08f)
                    }
                    close()
                }
                drawPath(tip, color)
                drawRect(color, Offset(l, tipTop + h * 0.2f), Size(bw, h * 0.07f))
            }
        }
    }
}

@Composable
fun EraserIcon(selected: Boolean, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val lift = if (selected) 0f else h * 0.16f
        val bw = w * 0.42f; val l = (w - bw) / 2
        val top = h * 0.2f + lift
        drawRoundRect(Color(0xFFFFCDD2), Offset(l, top), Size(bw, h * 0.28f), CornerRadius(6f))
        drawRoundRect(Color(0xFFF2F2F2), Offset(l, top + h * 0.24f), Size(bw, h), CornerRadius(4f))
        drawRoundRect(Color(0xFFBDBDBD), Offset(l, top), Size(bw, h), CornerRadius(6f), style = Stroke(1.2f))
    }
}
