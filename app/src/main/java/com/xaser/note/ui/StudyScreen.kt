package com.xaser.note.ui

import android.app.DatePickerDialog
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.L
import com.xaser.note.Lang
import com.xaser.note.data.StudyEntry
import com.xaser.note.data.StudyStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// ================= Geri sayım =================

/** ÖSYM 2027 takvimi: TYT 19 Haziran 2027 */
private val DEFAULT_EXAM: Long = Calendar.getInstance().apply { set(2027, Calendar.JUNE, 19, 10, 15, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

object Countdown {
    private fun p(ctx: Context) = ctx.getSharedPreferences("study", 0)
    fun date(ctx: Context): Long = p(ctx).getLong("examDate", DEFAULT_EXAM)
    fun title(ctx: Context): String = p(ctx).getString("examTitle", null) ?: "YKS"
    fun set(ctx: Context, date: Long, title: String) = p(ctx).edit().putLong("examDate", date).putString("examTitle", title).apply()

    /** Sınava kalan gün (bugün sınavsa 0, geçtiyse negatif) */
    fun daysLeft(ctx: Context, now: Long = System.currentTimeMillis()): Long =
        (StudyStore.dayStart(date(ctx)) - StudyStore.dayStart(now)) / 86_400_000L
}

// ================= Pomodoro durumu =================

/** Uygulama kapansa da doğru kalsın diye bitiş zamanı saklanır */
private class Pomo(ctx: Context) {
    private val sp = ctx.getSharedPreferences("study", 0)
    var focusMin = sp.getInt("focusMin", 25)
    var shortMin = sp.getInt("shortMin", 5)
    var longMin = sp.getInt("longMin", 15)
    var phase = sp.getString("phase", "focus")!! // focus | short | long
    var running = sp.getBoolean("running", false)
    var endAt = sp.getLong("endAt", 0L)
    var leftMs = sp.getLong("leftMs", focusMin * 60_000L)
    var cycle = sp.getInt("cycle", 0)
    var subject = sp.getString("subject", "")!!

    fun save() = sp.edit().putInt("focusMin", focusMin).putInt("shortMin", shortMin).putInt("longMin", longMin)
        .putString("phase", phase).putBoolean("running", running).putLong("endAt", endAt).putLong("leftMs", leftMs)
        .putInt("cycle", cycle).putString("subject", subject).apply()

    fun phaseMin() = when (phase) { "short" -> shortMin; "long" -> longMin; else -> focusMin }
    fun remaining(now: Long) = if (running) endAt - now else leftMs
}

private fun alarm(ctx: Context) {
    runCatching {
        ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }
    runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 700) }
}

private fun mmss(ms: Long): String { val s = (ms.coerceAtLeast(0) + 999) / 1000; return "%02d:%02d".format(s / 60, s % 60) }

// ================= Ekran =================

@Composable
fun StudyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    val study = AppState.study
    val pomo = remember { Pomo(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var askQuestions by remember { mutableStateOf<StudyEntry?>(null) }
    var addManual by remember { mutableStateOf(false) }
    var editCountdown by remember { mutableStateOf(false) }
    var pomoSettings by remember { mutableStateOf(false) }

    // Ekran açık kalsın (sayaç çalışırken)
    val view = LocalView.current
    DisposableEffect(pomo.running, tick) { view.keepScreenOn = pomo.running; onDispose { view.keepScreenOn = false } }

    fun finishPhase() {
        if (pomo.phase == "focus") {
            val e = StudyEntry(subject = pomo.subject.ifBlank { L("Genel", "General") }, minutes = pomo.focusMin)
            study.add(e)
            askQuestions = e
            pomo.cycle++
            pomo.phase = if (pomo.cycle % 4 == 0) "long" else "short"
        } else pomo.phase = "focus"
        pomo.leftMs = pomo.phaseMin() * 60_000L
        // Mola otomatik başlasın; odak için kullanıcı başlatsın
        if (pomo.phase != "focus") { pomo.running = true; pomo.endAt = System.currentTimeMillis() + pomo.leftMs } else pomo.running = false
        pomo.save(); alarm(ctx); tick++
    }

    LaunchedEffect(pomo.running, tick) {
        while (pomo.running) {
            now = System.currentTimeMillis()
            if (pomo.remaining(now) <= 0) { finishPhase(); break }
            delay(300)
        }
    }

    @Suppress("UNUSED_EXPRESSION") tick
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, L("Geri", "Back")) }
            Text(L("Çalışma", "Study"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            val st = study.streak()
            if (st > 0) Text(L("🔥 $st gün üst üste", "🔥 $st-day streak"), color = AccentDark, fontWeight = FontWeight.SemiBold)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 820.dp
            val left: @Composable () -> Unit = {
                CountdownCard { editCountdown = true }
                Spacer(Modifier.height(14.dp))
                PomodoroCard(pomo, now, tick,
                    onStartPause = {
                        val t = System.currentTimeMillis()
                        if (pomo.running) { pomo.leftMs = pomo.endAt - t; pomo.running = false }
                        else { pomo.endAt = t + pomo.leftMs; pomo.running = true; now = t }
                        pomo.save(); tick++
                    },
                    onSkip = { pomo.running = false; if (pomo.phase == "focus") { pomo.phase = "short" } else pomo.phase = "focus"; pomo.leftMs = pomo.phaseMin() * 60_000L; pomo.save(); tick++ },
                    onReset = { pomo.running = false; pomo.phase = "focus"; pomo.leftMs = pomo.focusMin * 60_000L; pomo.save(); tick++ },
                    onSubject = { pomo.subject = it; pomo.save(); tick++ },
                    onSettings = { pomoSettings = true },
                )
            }
            val right: @Composable () -> Unit = {
                TodayCard(tick) { addManual = true }
                Spacer(Modifier.height(14.dp))
                WeekCard(tick)
                Spacer(Modifier.height(14.dp))
                NetChartCard()
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { left() }
                Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { right() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                left(); Spacer(Modifier.height(14.dp)); right()
            }
        }
    }

    askQuestions?.let { e ->
        QuestionsDialog(L("Oturum bitti 🎉 Kaç soru çözdün?", "Session done 🎉 How many questions?"), e.subject, fixedSubject = true,
            onDismiss = { askQuestions = null }) { _, q, d, y -> e.questions = q; e.correct = d; e.wrong = y; study.save(); askQuestions = null; tick++ }
    }
    if (addManual) QuestionsDialog(L("Çözdüğün soruları ekle", "Add solved questions"), pomo.subject, fixedSubject = false,
        onDismiss = { addManual = false }) { s, q, d, y ->
        study.add(StudyEntry(subject = s.ifBlank { L("Genel", "General") }, minutes = 0, questions = q, correct = d, wrong = y)); addManual = false; tick++
    }
    if (editCountdown) {
        var title by remember { mutableStateOf(Countdown.title(ctx)) }
        var date by remember { mutableLongStateOf(Countdown.date(ctx)) }
        AlertDialog(
            onDismissRequest = { editCountdown = false },
            title = { Text(L("Geri sayım", "Countdown")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text(L("Sınav adı", "Exam name")) }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    TextButton({
                        val c = Calendar.getInstance().apply { timeInMillis = date }
                        DatePickerDialog(ctx, { _, y, m, d -> date = Calendar.getInstance().apply { set(y, m, d, 10, 0, 0) }.timeInMillis },
                            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text(SimpleDateFormat("d MMMM yyyy, EEEE", Lang.locale).format(Date(date)), color = AccentDark, fontSize = 17.sp) }
                    Text(L("Varsayılan: ÖSYM 2027 takvimi — TYT 19 Haziran 2027", "Default: ÖSYM 2027 calendar — TYT 19 June 2027"), color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({ Countdown.set(ctx, date, title.trim().ifBlank { "YKS" }); editCountdown = false; tick++ }) { Text(L("Kaydet", "Save"), color = Accent) } },
            dismissButton = { TextButton({ Countdown.set(ctx, DEFAULT_EXAM, "YKS"); editCountdown = false; tick++ }) { Text(L("Varsayılana dön", "Reset")) } },
        )
    }
    if (pomoSettings) {
        var f by remember { mutableStateOf(pomo.focusMin.toString()) }
        var s by remember { mutableStateOf(pomo.shortMin.toString()) }
        var l by remember { mutableStateOf(pomo.longMin.toString()) }
        AlertDialog(
            onDismissRequest = { pomoSettings = false },
            title = { Text(L("Süreler (dakika)", "Durations (minutes)")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(L("Odak", "Focus"), f) { v: String -> f = v }, Triple(L("Kısa mola", "Short break"), s) { v: String -> s = v },
                        Triple(L("Uzun mola (her 4 oturumda)", "Long break (every 4)"), l) { v: String -> l = v }).forEach { (label, v, set) ->
                        OutlinedTextField(v, { set(it.filter(Char::isDigit).take(3)) }, label = { Text(label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("25/5", "50/10", "90/20").forEach { pr ->
                            Text(pr, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEEEEE)).clickable {
                                val (a, b) = pr.split("/"); f = a; s = b; l = (b.toInt() * 3).toString()
                            }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    pomo.focusMin = (f.toIntOrNull() ?: 25).coerceIn(1, 240); pomo.shortMin = (s.toIntOrNull() ?: 5).coerceIn(1, 60)
                    pomo.longMin = (l.toIntOrNull() ?: 15).coerceIn(1, 120)
                    if (!pomo.running) pomo.leftMs = pomo.phaseMin() * 60_000L
                    pomo.save(); pomoSettings = false; tick++
                }) { Text(L("Kaydet", "Save"), color = Accent) }
            },
            dismissButton = { TextButton({ pomoSettings = false }) { Text(L("İptal", "Cancel")) } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(18.dp)) { content() }
}

@Composable
private fun CountdownCard(onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val days = Countdown.daysLeft(ctx)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Countdown.title(ctx), color = TextSecondary, fontSize = 15.sp)
                Text(
                    when {
                        days > 0 -> L("$days gün kaldı", "$days days left")
                        days == 0L -> L("Bugün! Başarılar 🍀", "Today! Good luck 🍀")
                        else -> L("Sınav geçti", "Exam is over")
                    },
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AccentDark,
                )
                Text(SimpleDateFormat("d MMMM yyyy, EEEE", Lang.locale).format(Date(Countdown.date(ctx))), color = TextSecondary, fontSize = 13.sp)
                if (days > 0) Text(L("≈ ${days / 7} hafta", "≈ ${days / 7} weeks"), color = TextSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, L("Düzenle", "Edit"), tint = TextSecondary) }
        }
    }
}

@Composable
private fun PomodoroCard(
    pomo: Pomo, now: Long, tick: Int, onStartPause: () -> Unit, onSkip: () -> Unit, onReset: () -> Unit,
    onSubject: (String) -> Unit, onSettings: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    val total = pomo.phaseMin() * 60_000L
    val left = pomo.remaining(now).coerceIn(0, total)
    val color = if (pomo.phase == "focus") Accent else Color(0xFF43A047)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when (pomo.phase) { "short" -> L("Kısa mola", "Short break"); "long" -> L("Uzun mola", "Long break"); else -> L("Odak", "Focus") },
                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color, modifier = Modifier.weight(1f))
            Text(L("${pomo.cycle} oturum", "${pomo.cycle} sessions"), color = TextSecondary, fontSize = 13.sp)
            TextButton(onSettings) { Text(L("Süreler", "Times"), color = AccentDark) }
        }
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val sw = 16.dp.toPx()
                drawArc(Color(0xFFEEEEEE), 0f, 360f, false, style = Stroke(sw), topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw))
                drawArc(color, -90f, 360f * (left / total.toFloat()), false, style = Stroke(sw, cap = StrokeCap.Round),
                    topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw))
            }
            Text(mmss(left), fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onReset) { Icon(Icons.Outlined.RestartAlt, L("Sıfırla", "Reset")) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(color).clickable(onClick = onStartPause), contentAlignment = Alignment.Center) {
                Icon(if (pomo.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onSkip) { Icon(Icons.Outlined.SkipNext, L("Atla", "Skip")) }
        }
        Spacer(Modifier.height(10.dp))
        Text(L("Ders", "Subject"), color = TextSecondary, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subjectList().forEach { s ->
                val on = pomo.subject == s
                Text(s, color = if (on) Color.White else TextPrimary, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Accent else Color(0xFFEEEEEE))
                        .clickable { onSubject(if (on) "" else s) }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun TodayCard(tick: Int, onAdd: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
    val study = AppState.study
    val (min, q) = study.dayTotals(System.currentTimeMillis())
    val today = study.entries.filter { StudyStore.dayStart(it.time) == StudyStore.dayStart(System.currentTimeMillis()) }
    val d = today.sumOf { it.correct }; val y = today.sumOf { it.wrong }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Bugün", "Today"), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onAdd) { Text(L("+ Soru ekle", "+ Add questions"), color = AccentDark) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Big(L("Çalışma", "Studied"), if (min >= 60) "${min / 60} sa ${min % 60} dk" else "$min dk".let { if (Lang.isEnglish) "$min min" else it })
            Big(L("Soru", "Questions"), q.toString())
            if (d + y > 0) Big(L("Başarı", "Accuracy"), "%${(100 * d / (d + y))}")
        }
        // Derse göre (bugün)
        val bySubj = today.groupBy { it.subject }.mapValues { (_, l) -> l.sumOf { it.minutes } to l.sumOf { it.questions } }
        if (bySubj.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            bySubj.forEach { (s, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(s, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Text(L("${v.first} dk · ${v.second} soru", "${v.first} min · ${v.second} q"), color = TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun WeekCard(tick: Int) {
    @Suppress("UNUSED_EXPRESSION") tick
    val study = AppState.study
    val today = StudyStore.dayStart(System.currentTimeMillis())
    val days = (6 downTo 0).map { today - it * 86_400_000L }
    val mins = days.map { study.dayTotals(it).first }
    val qs = days.map { study.dayTotals(it).second }
    val maxM = (mins.maxOrNull() ?: 0).coerceAtLeast(30)
    val dayFmt = SimpleDateFormat("EEE", Lang.locale)
    Card {
        Row {
            Text(L("Son 7 gün", "Last 7 days"), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(L("${mins.sum()} dk · ${qs.sum()} soru", "${mins.sum()} min · ${qs.sum()} q"), color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(if (mins[i] > 0) "${mins[i]}" else "", fontSize = 11.sp, color = TextSecondary)
                    Box(Modifier.width(26.dp).height((110f * mins[i] / maxM).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (i == 6) Accent else Color(0xFFFFCC80)))
                    Text(dayFmt.format(Date(d)), fontSize = 12.sp, color = if (i == 6) AccentDark else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun NetChartCard() {
    val all = remember {
        ExamSession.loadAll(AppState.store).filter { it.second.result().keyed > 0 }.sortedBy { it.second.created }
    }
    // Soru sayısına göre grupla (TYT 120, AYT 80, branş 40 ... karışmasın)
    val groups = all.groupBy { it.second.count }.toList().sortedByDescending { it.second.size }
    var sel by remember { mutableIntStateOf(groups.firstOrNull()?.first ?: 0) }
    Card {
        Text(L("Net gelişimi", "Net progress"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (all.isEmpty()) {
            Text(L("Henüz sonuçlanmış deneme yok. Bir PDF'te ⏱ Deneme modunu aç, cevaplarını ve anahtarı gir; netlerin burada grafik olur.",
                "No graded exams yet. Open ⏱ Exam mode on a PDF and enter your answers and the key; your nets will be charted here."),
                color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            return@Card
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { (count, list) ->
                val on = sel == count
                Text(L("$count soru (${list.size})", "$count q (${list.size})"), color = if (on) Color.White else TextPrimary, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Accent else Color(0xFFEEEEEE))
                        .clickable { sel = count }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        val pts = all.filter { it.second.count == sel }
        val nets = pts.map { it.second.result().net }
        val maxY = (nets.maxOrNull() ?: 1.0).coerceAtLeast(sel.toDouble() * 0.25).let { it * 1.1 }
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(Modifier.fillMaxSize().padding(start = 30.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                val w = size.width; val h = size.height
                for (k in 0..4) { val y = h * k / 4f; drawLine(Color(0xFFEEEEEE), Offset(0f, y), Offset(w, y), 1.dp.toPx()) }
                if (nets.isEmpty()) return@Canvas
                fun px(i: Int) = if (nets.size == 1) w / 2 else w * i / (nets.size - 1f)
                fun py(v: Double) = (h - h * (v / maxY)).toFloat()
                val path = Path().apply { nets.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
                drawPath(path, Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                nets.forEachIndexed { i, v -> drawCircle(Color.White, 6.dp.toPx(), Offset(px(i), py(v))); drawCircle(AccentDark, 4.dp.toPx(), Offset(px(i), py(v))) }
            }
            Text("%.0f".format(maxY), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.align(Alignment.TopStart))
            Text("0", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.align(Alignment.BottomStart))
        }
        if (nets.isNotEmpty()) {
            val first = nets.first(); val last = nets.last()
            val diff = last - first
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(L("Son: ", "Last: ") + "%.2f".format(last), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(L("En iyi: ", "Best: ") + "%.2f".format(nets.max()), color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                if (nets.size > 1) Text((if (diff >= 0) "▲ " else "▼ ") + "%.2f".format(kotlin.math.abs(diff)),
                    color = if (diff >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
            }
            Text(pts.takeLast(4).reversed().joinToString("\n") { (title, s) -> "• ${s.name.ifBlank { title }} — net %.2f".format(s.result().net) },
                color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun QuestionsDialog(title: String, subject: String, fixedSubject: Boolean, onDismiss: () -> Unit, onOk: (String, Int, Int, Int) -> Unit) {
    var subj by remember { mutableStateOf(subject) }
    var q by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fixedSubject) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjectList().forEach { s ->
                        val on = subj == s
                        Text(s, color = if (on) Color.White else TextPrimary, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Accent else Color(0xFFEEEEEE))
                                .clickable { subj = s }.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                OutlinedTextField(q, { q = it.filter(Char::isDigit).take(4) }, label = { Text(L("Çözülen soru", "Questions solved")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(d, { d = it.filter(Char::isDigit).take(4) }, label = { Text(L("Doğru (isteğe bağlı)", "Correct (optional)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(y, { y = it.filter(Char::isDigit).take(4) }, label = { Text(L("Yanlış", "Wrong")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton({
                val dd = d.toIntOrNull() ?: 0; val yy = y.toIntOrNull() ?: 0
                val qq = (q.toIntOrNull() ?: 0).coerceAtLeast(dd + yy)
                onOk(subj, qq, dd, yy)
            }) { Text(L("Kaydet", "Save"), color = Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(L("Atla", "Skip")) } },
    )
}
