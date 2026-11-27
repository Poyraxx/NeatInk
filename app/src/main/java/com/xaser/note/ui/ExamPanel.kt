package com.xaser.note.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.L
import com.xaser.note.ink.InkView
import com.xaser.note.model.NoteMeta
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Bir deneme oturumu (nota bağlı, files/notes/<id>/exams.json) */
class ExamSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var count: Int = 40,
    var firstNo: Int = 1,
    /** 4 veya 5 şık */
    var choices: Int = 5,
    /** Kaç yanlış bir doğruyu götürür (0 = götürmez) */
    var penalty: Int = 4,
    var limitMin: Int = 0,
    var elapsedMs: Long = 0,
    val answers: MutableMap<Int, String> = mutableMapOf(),
    val key: MutableMap<Int, String> = mutableMapOf(),
    /** Soru no → işaretlendiği sayfa */
    val pages: MutableMap<Int, Int> = mutableMapOf(),
    val created: Long = System.currentTimeMillis(),
    var addedToMistakes: Boolean = false,
) {
    val numbers get() = (firstNo until firstNo + count).toList()

    class Result(val correct: Int, val wrong: Int, val empty: Int, val net: Double, val wrongNos: List<Int>, val keyed: Int)

    fun result(): Result {
        var d = 0; var y = 0; var b = 0; var keyed = 0
        val wrong = mutableListOf<Int>()
        for (n in numbers) {
            val k = key[n] ?: continue
            keyed++
            val a = answers[n]
            when {
                a == null -> b++
                a == k -> d++
                else -> { y++; wrong.add(n) }
            }
        }
        val net = if (penalty > 0) d - y.toDouble() / penalty else d.toDouble()
        return Result(d, y, b, net, wrong, keyed)
    }

    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("count", count); put("first", firstNo); put("choices", choices)
        put("penalty", penalty); put("limit", limitMin); put("elapsed", elapsedMs); put("created", created); put("added", addedToMistakes)
        put("answers", JSONObject().apply { answers.forEach { (k, v) -> put(k.toString(), v) } })
        put("key", JSONObject().apply { key.forEach { (k, v) -> put(k.toString(), v) } })
        put("pages", JSONObject().apply { pages.forEach { (k, v) -> put(k.toString(), v) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ExamSession {
            val s = ExamSession(o.getString("id"), o.optString("name"), o.optInt("count", 40), o.optInt("first", 1),
                o.optInt("choices", 5), o.optInt("penalty", 4), o.optInt("limit"), o.optLong("elapsed"),
                created = o.optLong("created"), addedToMistakes = o.optBoolean("added"))
            o.optJSONObject("answers")?.let { a -> a.keys().forEach { s.answers[it.toInt()] = a.getString(it) } }
            o.optJSONObject("key")?.let { a -> a.keys().forEach { s.key[it.toInt()] = a.getString(it) } }
            o.optJSONObject("pages")?.let { a -> a.keys().forEach { s.pages[it.toInt()] = a.getInt(it) } }
            return s
        }

        fun load(dir: File): MutableList<ExamSession> = runCatching {
            val a = JSONArray(File(dir, "exams.json").readText())
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }.toMutableList()
        }.getOrElse { mutableListOf() }

        fun save(dir: File, list: List<ExamSession>) {
            runCatching { File(dir, "exams.json").writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun fmtNet(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else "%.2f".format(n)

/** Sağda açılan deneme paneli: süre + optik form + cevap anahtarı + sonuç */
@Composable
fun ExamPanel(view: InkView, meta: NoteMeta, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dir = remember { AppState.store.dir(meta.id) }
    val sessions = remember { ExamSession.load(dir).ifEmpty { mutableListOf(ExamSession(name = L("Deneme 1", "Exam 1"))) } }
    var current by remember { mutableStateOf(sessions.last()) }
    var tick by remember { mutableIntStateOf(0) } // yeniden çizim
    var tab by remember { mutableIntStateOf(0) } // 0 cevaplar, 1 anahtar, 2 sonuç
    var running by remember { mutableStateOf(false) }
    var runStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionMenu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var keyText by remember(current.id) { mutableStateOf(current.numbers.joinToString("") { current.key[it] ?: "-" }.trimEnd('-')) }
    var askSubject by remember { mutableStateOf(false) }

    fun persist() { ExamSession.save(dir, sessions) }
    fun elapsed() = current.elapsedMs + if (running) now - runStart else 0
    fun pause() { if (running) { current.elapsedMs += System.currentTimeMillis() - runStart; running = false; persist() } }

    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(250) } }
    DisposableEffect(Unit) { onDispose { pause(); persist() } }

    val letters = "ABCDE".take(current.choices).map { it.toString() }

    Column(
        modifier.width(320.dp).fillMaxHeight().shadow(12.dp).background(Color.White).padding(top = 8.dp),
    ) {
        // Başlık + oturum seçimi
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(current.name.ifBlank { L("Deneme", "Exam") } + " ▾", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.clickable { sessionMenu = true }.padding(vertical = 6.dp))
                DropdownMenu(sessionMenu, { sessionMenu = false }) {
                    sessions.reversed().forEach { s ->
                        val r = s.result()
                        DropdownMenuItem({ Text(s.name + if (r.keyed > 0) "  ·  " + L("net ", "net ") + fmtNet(r.net) else "") },
                            { pause(); current = s; sessionMenu = false; tick++ })
                    }
                    DropdownMenuItem({ Text(L("+ Yeni deneme", "+ New exam"), color = AccentDark) }, {
                        pause()
                        val s = ExamSession(name = L("Deneme ${sessions.size + 1}", "Exam ${sessions.size + 1}"),
                            count = current.count, firstNo = current.firstNo, choices = current.choices, penalty = current.penalty, limitMin = current.limitMin)
                        sessions.add(s); current = s; tab = 0; persist(); sessionMenu = false; tick++
                    })
                }
            }
            TextButton({ settings = true }) { Text(L("Ayarlar", "Setup"), color = AccentDark) }
            IconButton(onClick = { pause(); onClose() }) { Icon(Icons.Outlined.Close, L("Kapat", "Close")) }
        }

        // Süre
        val el = elapsed()
        val over = current.limitMin > 0 && el > current.limitMin * 60_000L
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt(el), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = if (over) Color(0xFFD32F2F) else TextPrimary)
                if (current.limitMin > 0) {
                    val left = current.limitMin * 60_000L - el
                    Text(if (left >= 0) L("Kalan ", "Left ") + fmt(left) else L("Süre doldu", "Time is up"),
                        color = if (over) Color(0xFFD32F2F) else TextSecondary, fontSize = 13.sp)
                }
            }
            IconButton(onClick = {
                if (running) pause() else { runStart = System.currentTimeMillis(); now = runStart; running = true }
            }) { Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = AccentDark, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { running = false; current.elapsedMs = 0; persist(); tick++ }) { Icon(Icons.Outlined.RestartAlt, L("Sıfırla", "Reset")) }
        }

        // Sekmeler
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(L("Cevaplarım", "Answers"), L("Anahtar", "Key"), L("Sonuç", "Result")).forEachIndexed { i, t ->
                Text(t, color = if (tab == i) Color.White else TextPrimary, fontSize = 14.sp,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (tab == i) Accent else Color(0xFFEEEEEE))
                        .clickable { tab = i }.padding(vertical = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        @Suppress("UNUSED_EXPRESSION") tick
        when (tab) {
            0, 1 -> {
                if (tab == 1) {
                    OutlinedTextField(
                        keyText, { v ->
                            keyText = v.uppercase().filter { it in "ABCDE-" }
                            current.key.clear()
                            keyText.forEachIndexed { i, c -> if (c != '-') current.key[current.firstNo + i] = c.toString() }
                            persist(); tick++
                        },
                        label = { Text(L("Anahtarı yapıştır: ABCDA…", "Paste key: ABCDA…")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
                val map = if (tab == 0) current.answers else current.key
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                    items(current.numbers, key = { it }) { n ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$n", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.width(36.dp))
                            letters.forEach { l ->
                                val on = map[n] == l
                                val col = if (tab == 1) Color(0xFF2E7D32) else Color(0xFF222222)
                                Box(
                                    Modifier.padding(horizontal = 3.dp).size(38.dp).clip(CircleShape)
                                        .background(if (on) col else Color.Transparent)
                                        .border(1.5.dp, if (on) col else Color(0xFFBDBDBD), CircleShape)
                                        .clickable {
                                            if (on) map.remove(n) else map[n] = l
                                            if (tab == 0) current.pages[n] = view.currentPage
                                            else keyText = current.numbers.joinToString("") { current.key[it] ?: "-" }.trimEnd('-')
                                            persist(); tick++
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(l, color = if (on) Color.White else Color(0xFF757575), fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
                if (tab == 0) Text(
                    L("${current.answers.size} / ${current.count} işaretlendi", "${current.answers.size} / ${current.count} marked"),
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(12.dp),
                )
            }
            else -> {
                val r = current.result()
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (r.keyed == 0) {
                        Text(L("Sonuç için önce \"Anahtar\" sekmesinden cevap anahtarını gir.", "Enter the answer key in the \"Key\" tab to see results."),
                            color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat(L("Doğru", "Correct"), r.correct.toString(), Color(0xFF2E7D32))
                            Stat(L("Yanlış", "Wrong"), r.wrong.toString(), Color(0xFFD32F2F))
                            Stat(L("Boş", "Blank"), r.empty.toString(), TextSecondary)
                        }
                        Text(L("Net: ", "Net: ") + fmtNet(r.net), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AccentDark,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(L("Süre: ", "Time: ") + fmt(elapsed()), color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(10.dp))
                        if (r.wrongNos.isNotEmpty()) {
                            Text(L("Yanlışlar", "Wrong answers"), fontWeight = FontWeight.SemiBold)
                            LazyColumn(Modifier.weight(1f)) {
                                items(r.wrongNos) { n ->
                                    Row(Modifier.fillMaxWidth().clickable { current.pages[n]?.let { view.scrollToPage(it) } }.padding(vertical = 5.dp)) {
                                        Text("$n.", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                                        Text(L("Senin: ", "Yours: ") + current.answers[n], color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                                        Text(L("Doğru: ", "Right: ") + current.key[n], color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                            Button(
                                onClick = { askSubject = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            ) { Text(if (current.addedToMistakes) L("Tekrar hata defterine ekle", "Add to mistakes again") else L("Yanlışları hata defterine ekle", "Add wrong answers to mistakes")) }
                        } else Text(L("Hiç yanlış yok 🎉", "No wrong answers 🎉"), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }

    if (settings) {
        var count by remember { mutableStateOf(current.count.toString()) }
        var first by remember { mutableStateOf(current.firstNo.toString()) }
        var limit by remember { mutableStateOf(if (current.limitMin > 0) current.limitMin.toString() else "") }
        var name by remember { mutableStateOf(current.name) }
        var choices by remember { mutableIntStateOf(current.choices) }
        var penalty by remember { mutableIntStateOf(current.penalty) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(L("Deneme ayarları", "Exam setup")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(L("Ad", "Name")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text(L("Soru sayısı", "Questions")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(first, { first = it.filter(Char::isDigit) }, label = { Text(L("İlk soru no", "First no.")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text(L("Süre sınırı (dk, isteğe bağlı)", "Time limit (min, optional)")) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(L("Şık sayısı", "Choices"), color = TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5 to "A–E", 4 to "A–D").forEach { (v, t) -> Pill(t, choices == v) { choices = v } }
                    }
                    Text(L("Yanlış doğruyu götürür", "Wrong answers cancel a right one"), color = TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4 to L("4 yanlış (YKS)", "4 wrong (YKS)"), 3 to L("3 yanlış (LGS)", "3 wrong (LGS)"), 0 to L("Götürmez", "None"))
                            .forEach { (v, t) -> Pill(t, penalty == v) { penalty = v } }
                    }
                    Text(L("Hazır: TYT 120 soru / 165 dk, AYT 80 soru / 180 dk", "Presets: TYT 120 q / 165 min, AYT 80 q / 180 min"), color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("TYT", false) { count = "120"; first = "1"; limit = "165"; penalty = 4; choices = 5 }
                        Pill("AYT", false) { count = "80"; first = "1"; limit = "180"; penalty = 4; choices = 5 }
                        Pill("LGS", false) { count = "90"; first = "1"; limit = "155"; penalty = 3; choices = 4 }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    current.name = name.trim()
                    current.count = (count.toIntOrNull() ?: 40).coerceIn(1, 300)
                    current.firstNo = (first.toIntOrNull() ?: 1).coerceIn(1, 999)
                    current.limitMin = limit.toIntOrNull() ?: 0
                    current.choices = choices; current.penalty = penalty
                    keyText = current.numbers.joinToString("") { current.key[it] ?: "-" }.trimEnd('-')
                    persist(); settings = false; tick++
                }) { Text(L("Kaydet", "Save"), color = Accent) }
            },
            dismissButton = { TextButton({ settings = false }) { Text(L("İptal", "Cancel")) } },
        )
    }

    if (askSubject) {
        SubjectDialog(L("Yanlışlar hangi dersten?", "Which subject?"), "", "", { askSubject = false }) { subject, topic ->
            askSubject = false
            val r = current.result()
            val cache = HashMap<Int, android.graphics.Bitmap?>()
            var added = 0
            for (n in r.wrongNos) {
                val page = current.pages[n] ?: view.currentPage
                val bmp = cache.getOrPut(page) { view.renderPageClean(page) } ?: continue
                AppState.mistakes.add(bmp, subject, topic, meta.id, meta.title.ifBlank { current.name }, page, n, current.key[n] ?: "")
                added++
            }
            current.addedToMistakes = true; persist(); tick++
            Toast.makeText(ctx, L("$added soru hata defterine eklendi", "$added questions added to mistakes"), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, color = if (on) Color.White else TextPrimary, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Accent else Color(0xFFEEEEEE))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}
