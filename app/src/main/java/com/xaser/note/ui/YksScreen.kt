package com.xaser.note.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.model.Yks
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private val trNum = NumberFormat.getInstance(Locale.forLanguageTag("tr"))
private fun fmt2(d: Double) = "%.2f".format(Locale.forLanguageTag("tr"), d)
private fun fmtNet(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(Locale.forLanguageTag("tr"), d)

/** Girişleri hatırla (uygulama kapanınca kaybolmasın) */
private fun loadInputs(ctx: Context): Map<String, String> = runCatching {
    val o = JSONObject(ctx.getSharedPreferences("yks", 0).getString("inputs", "{}")!!)
    o.keys().asSequence().associateWith { o.getString(it) }
}.getOrDefault(emptyMap())

private fun saveInputs(ctx: Context, m: Map<String, String>) {
    ctx.getSharedPreferences("yks", 0).edit().putString("inputs", JSONObject(m).toString()).apply()
}

/** YKS net, puan ve tahmini sıralama hesaplayıcı (Türkçe arayüzde gösterilir) */
@Composable
fun YksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    val inp = remember { mutableStateMapOf<String, String>().apply { putAll(loadInputs(ctx)) } }
    var type by remember { mutableStateOf(inp["type"] ?: "SAY") }
    var broken by remember { mutableStateOf(inp["broken"] == "1") }

    fun set(k: String, v: String) { inp[k] = v; saveInputs(ctx, inp.toMap()) }
    fun int(k: String) = inp[k]?.toIntOrNull() ?: 0

    // Netler
    val tests = Yks.TYT_TESTS + (Yks.AYT_TESTS[type] ?: emptyList())
    val nets = tests.associate { t -> t.key to Yks.net(int("${t.key}_d"), int("${t.key}_y")) }
    val diploma = inp["dip"]?.replace(',', '.')?.toDoubleOrNull()
    val bonus = diploma?.let { Yks.obpBonus(it, broken) } ?: 0.0
    val tytRaw = Yks.rawScore("TYT", nets)
    val tytNet = Yks.TYT_TESTS.sumOf { nets[it.key] ?: 0.0 }
    val aytNet = (Yks.AYT_TESTS[type] ?: emptyList()).sumOf { nets[it.key] ?: 0.0 }
    val typeRaw = Yks.rawScore(type, nets)

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri") }
            Text("YKS Hesaplama", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton({ inp.keys.filter { it.endsWith("_d") || it.endsWith("_y") }.forEach { inp.remove(it) }; saveInputs(ctx, inp.toMap()) }) {
                Text("Temizle", color = AccentDark)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SAY", "EA", "SÖZ").forEach { t ->
                Text(t, color = if (type == t) Color.White else TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (type == t) Accent else Color(0xFFEBEBEB))
                        .clickable { type = t; set("type", t) }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 820.dp
            val inputs: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Section("TYT")
                    Yks.TYT_TESTS.forEach { t -> TestRow(t, inp["${t.key}_d"].orEmpty(), inp["${t.key}_y"].orEmpty(), nets[t.key] ?: 0.0,
                        { set("${t.key}_d", it) }, { set("${t.key}_y", it) }) }
                    Section("AYT · $type")
                    Yks.AYT_TESTS[type]!!.forEach { t -> TestRow(t, inp["${t.key}_d"].orEmpty(), inp["${t.key}_y"].orEmpty(), nets[t.key] ?: 0.0,
                        { set("${t.key}_d", it) }, { set("${t.key}_y", it) }) }
                    Section("Diploma notu (OBP)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumField(inp["dip"].orEmpty(), "örn. 85", 90.dp, decimal = true) { set("dip", it) }
                        Spacer(Modifier.width(12.dp))
                        Checkbox(broken, { broken = it; set("broken", if (it) "1" else "0") }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                        Text("Geçen yıl bir programa yerleştim", fontSize = 14.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            val results: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    ResultCard("TYT", tytNet, tytRaw, if (diploma != null) tytRaw + bonus else null, rankType = "TYT", tytOk = true)
                    Spacer(Modifier.height(12.dp))
                    ResultCard(type, aytNet, typeRaw, if (diploma != null) typeRaw + bonus else null, rankType = type, tytOk = tytRaw >= Yks.MIN_TYT)
                    Spacer(Modifier.height(12.dp))
                    TargetCard(type)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bu hesaplama ${Yks.DATA_YEAR} YKS katsayıları ve ${Yks.DATA_YEAR} yerleştirme verileriyle yapılan YAKLAŞIK bir tahmindir. " +
                            "Katsayılar ve sıralamalar her yıl sınavın zorluğuna göre değişir; kesin sonuç ÖSYM'nin açıkladığıdır. " +
                            "Net = doğru − yanlış ÷ 4. Yerleştirme puanı = ham puan + OBP × 0,12 (geçen yıl yerleştiysen 0,06).",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                inputs(Modifier.weight(1.1f))
                results(Modifier.weight(1f))
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Dar ekranda tek sütun: önce sonuçlar görünmesin diye girişler üstte
                Box(Modifier.fillMaxWidth().height(10.dp))
                inputs(Modifier.fillMaxWidth().height(620.dp))
                results(Modifier.fillMaxWidth().height(760.dp))
            }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AccentDark, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun TestRow(t: Yks.Test, d: String, y: String, net: Double, onD: (String) -> Unit, onY: (String) -> Unit) {
    val total = (d.toIntOrNull() ?: 0) + (y.toIntOrNull() ?: 0)
    val bad = total > t.questions
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.name, color = TextPrimary, fontSize = 15.sp)
            Text("${t.questions} soru", color = if (bad) Color(0xFFD32F2F) else TextSecondary, fontSize = 11.sp)
        }
        NumField(d, "D", 64.dp) { onD(it) }
        Spacer(Modifier.width(6.dp))
        NumField(y, "Y", 64.dp) { onY(it) }
        Text(fmtNet(net), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.End,
            color = if (bad) Color(0xFFD32F2F) else TextPrimary, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun NumField(v: String, hint: String, w: androidx.compose.ui.unit.Dp, decimal: Boolean = false, onChange: (String) -> Unit) {
    Box(
        Modifier.width(w).height(42.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (v.isEmpty()) Text(hint, color = Color(0xFFBDBDBD), fontSize = 15.sp)
        BasicTextField(
            v, { s -> onChange(s.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(5)) },
            singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultCard(title: String, net: Double, raw: Double, placement: Double?, rankType: String, tytOk: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("Net ${fmtNet(net)}", color = TextSecondary, fontSize = 15.sp)
        }
        if (!tytOk) {
            Text("TYT ham puanın 150'nin altında olduğu için $title puanı hesaplanmaz.", color = Color(0xFFD32F2F), fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Ham puan", color = TextSecondary, fontSize = 13.sp)
                Text(fmt2(raw), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("Yerleştirme", color = TextSecondary, fontSize = 13.sp)
                Text(placement?.let { fmt2(it) } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AccentDark)
            }
        }
        val forRank = placement ?: raw
        val est = Yks.estimateRank(rankType, forRank)
        Spacer(Modifier.height(8.dp))
        Text("Tahmini sıralama" + if (placement == null) " (diploma notu girilmedi)" else "", color = TextSecondary, fontSize = 13.sp)
        Text(
            when (est.note) {
                Yks.RankEstimate.Note.EXACT_RANGE -> "≈ " + trNum.format(est.rank)
                Yks.RankEstimate.Note.ABOVE_DATA -> if (rankType == "TYT") "İlk ${trNum.format(est.rank)} içinde" else "İlk ${trNum.format(est.rank)} içinde 🎯"
                Yks.RankEstimate.Note.BELOW_DATA -> trNum.format(est.rank) + "+"
                Yks.RankEstimate.Note.NO_DATA -> "Veri yok"
            },
            fontSize = 26.sp, fontWeight = FontWeight.Bold,
        )
        if (rankType == "TYT") Text("TYT sıralaması yalnızca kaba aralıklarla tahmin edilir.", color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun TargetCard(type: String) {
    var target by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF3E0)).padding(16.dp)) {
        Text("Hedef sıralama → gereken puan ($type)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField(target, "örn. 50000", 140.dp) { target = it.take(7) }
            Spacer(Modifier.width(14.dp))
            val r = target.toLongOrNull()
            val s = r?.let { Yks.scoreForRank(type, it) }
            Text(
                when {
                    r == null -> "Sıralama yaz"
                    s == null -> "Bu sıralama için veri yok"
                    else -> "≈ ${fmt2(s)} yerleştirme puanı"
                },
                fontSize = 17.sp, fontWeight = if (s != null) FontWeight.Bold else FontWeight.Normal,
                color = if (s != null) AccentDark else TextSecondary,
            )
        }
    }
}
