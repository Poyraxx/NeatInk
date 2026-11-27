package com.xaser.note.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.xaser.note.L
import com.xaser.note.data.Mistake

/** Hazır ders listesi */
fun subjectList(): List<String> = listOf(
    L("Matematik", "Math"), L("Geometri", "Geometry"), L("Fizik", "Physics"), L("Kimya", "Chemistry"),
    L("Biyoloji", "Biology"), L("Türkçe", "Turkish"), L("Edebiyat", "Literature"), L("Tarih", "History"),
    L("Coğrafya", "Geography"), L("Felsefe", "Philosophy"), L("Din", "Religion"), L("İngilizce", "English"),
    L("Diğer", "Other"),
)

/**
 * Hata defteri ekranı.
 * onOpenSource: kaynağa git (not kimliği, sayfa). onSolve: soruyu yeni notta çöz.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MistakeScreen(onBack: () -> Unit, onOpenSource: (String, Int) -> Unit, onSolve: (Mistake) -> Unit) {
    val ms = AppState.mistakes
    var version by remember { mutableIntStateOf(0) }
    var subject by remember { mutableStateOf("") }
    var showLearned by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Mistake?>(null) }
    var review by remember { mutableStateOf<List<Mistake>?>(null) }
    var reviewIndex by remember { mutableIntStateOf(0) }

    BackHandler {
        when {
            review != null -> review = null
            viewing != null -> viewing = null
            else -> onBack()
        }
    }

    @Suppress("UNUSED_EXPRESSION") version
    val list = ms.items
        .filter { subject.isEmpty() || it.subject == subject }
        .filter { showLearned || !it.learned }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, L("Geri", "Back")) }
            Text(L("Hata Defteri", "Mistake Notebook"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    // Tekrar: öğrenilmemişler, en az tekrar edilen önce, karışık
                    val pool = list.filter { !it.learned }.shuffled().sortedBy { it.reviews }
                    if (pool.isNotEmpty()) { review = pool; reviewIndex = 0 }
                },
                enabled = list.any { !it.learned },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Icon(Icons.Outlined.Replay, null); Spacer(Modifier.width(6.dp)); Text(L("Tekrar et", "Review")) }
        }
        Text(
            L("${ms.openCount} soru tekrar bekliyor", "${ms.openCount} questions to review"),
            color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(L("Tümü", "All"), subject.isEmpty()) { subject = "" }
            ms.subjects.forEach { s -> Chip(s, subject == s) { subject = s } }
            Spacer(Modifier.width(12.dp))
            Chip(if (showLearned) L("Öğrenilenler gösteriliyor", "Showing learned") else L("Öğrenilenleri göster", "Show learned"), showLearned) { showLearned = !showLearned }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
                    Icon(Icons.Outlined.School, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        L("Henüz soru yok. Bir PDF'te ✂ Soruyu kes aracıyla soruyu seç ve \"Hata defterine ekle\"ye bas ya da deneme modunda yanlışlarını ekle.",
                            "No questions yet. Select a question with ✂ Cut question on a PDF and tap \"Add to mistakes\", or add your wrong answers from exam mode."),
                        color = TextSecondary, fontSize = 15.sp,
                    )
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(260.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp, top = 4.dp),
                verticalItemSpacing = 14.dp, horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { m -> MistakeCard(m) { viewing = m } }
            }
        }
    }

    viewing?.let { m ->
        MistakeDetail(m, inReview = false,
            onClose = { viewing = null },
            onLearned = { m.learned = !m.learned; ms.save(); version++; viewing = null },
            onAgain = { viewing = null },
            onDelete = { ms.delete(m); version++; viewing = null },
            onOpenSource = { onOpenSource(m.sourceNoteId, m.sourcePage) },
            onSolve = { onSolve(m) },
            onEdit = { s, t -> m.subject = s; m.topic = t; ms.save(); version++ },
        )
    }

    review?.let { pool ->
        val m = pool.getOrNull(reviewIndex)
        if (m == null) {
            AlertDialog(
                onDismissRequest = { review = null },
                title = { Text(L("Tekrar bitti 🎉", "Review done 🎉")) },
                text = { Text(L("${pool.size} soruyu tekrar ettin.", "You reviewed ${pool.size} questions.")) },
                confirmButton = { TextButton({ review = null; version++ }) { Text(L("Tamam", "OK"), color = Accent) } },
            )
        } else {
            MistakeDetail(m, inReview = true, progress = "${reviewIndex + 1} / ${pool.size}",
                onClose = { review = null; version++ },
                onLearned = { m.learned = true; m.reviews++; m.lastReview = System.currentTimeMillis(); ms.save(); reviewIndex++ },
                onAgain = { m.reviews++; m.lastReview = System.currentTimeMillis(); ms.save(); reviewIndex++ },
                onDelete = { ms.delete(m); reviewIndex++ },
                onOpenSource = { review = null; onOpenSource(m.sourceNoteId, m.sourcePage) },
                onSolve = { review = null; onSolve(m) },
                onEdit = { s, t -> m.subject = s; m.topic = t; ms.save() },
            )
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (on) Color.White else TextPrimary, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Accent else Color(0xFFEBEBEB))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun MistakeCard(m: Mistake, onClick: () -> Unit) {
    val bmp = remember(m.id) { runCatching { BitmapFactory.decodeFile(AppState.mistakes.imageFile(m).absolutePath) }.getOrNull() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).clickable(onClick = onClick).padding(12.dp)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.subject.ifBlank { L("Ders yok", "No subject") }, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            if (m.learned) Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF43A047), modifier = Modifier.size(18.dp))
        }
        if (m.topic.isNotBlank()) Text(m.topic, color = TextPrimary, fontSize = 13.sp)
        Text(
            listOfNotNull(
                m.sourceTitle.takeIf { it.isNotBlank() },
                if (m.sourcePage >= 0) L("s. ${m.sourcePage + 1}", "p. ${m.sourcePage + 1}") else null,
                if (m.questionNo > 0) L("Soru ${m.questionNo}", "Q${m.questionNo}") else null,
            ).joinToString(" · "),
            color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (m.reviews > 0) Text(L("${m.reviews} kez tekrar edildi", "Reviewed ${m.reviews}×"), color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun MistakeDetail(
    m: Mistake, inReview: Boolean, progress: String = "",
    onClose: () -> Unit, onLearned: () -> Unit, onAgain: () -> Unit, onDelete: () -> Unit,
    onOpenSource: () -> Unit, onSolve: () -> Unit, onEdit: (String, String) -> Unit,
) {
    val bmp = remember(m.id) { runCatching { BitmapFactory.decodeFile(AppState.mistakes.imageFile(m).absolutePath) }.getOrNull() }
    var showAnswer by remember(m.id) { mutableStateOf(false) }
    var editing by remember(m.id) { mutableStateOf(false) }
    var confirmDelete by remember(m.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xF0F7F7F7)).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, L("Kapat", "Close")) }
                Column(Modifier.weight(1f)) {
                    Text(m.subject.ifBlank { L("Ders yok", "No subject") } + if (m.topic.isNotBlank()) " · ${m.topic}" else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.clickable { editing = true })
                    if (inReview) Text(L("Tekrar ", "Review ") + progress, color = TextSecondary, fontSize = 13.sp)
                }
                if (m.sourceNoteId.isNotBlank()) IconButton(onClick = onOpenSource) { Icon(Icons.Outlined.OpenInNew, L("Kaynağa git", "Open source")) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, L("Sil", "Delete")) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), contentAlignment = Alignment.TopCenter) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White))
            }
            if (m.correctAnswer.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showAnswer) Text(L("Doğru cevap: ", "Correct answer: ") + m.correctAnswer, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    else OutlinedButton(onClick = { showAnswer = true }) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(6.dp)); Text(L("Cevabı göster", "Show answer")) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(onClick = onSolve) { Icon(Icons.Outlined.Draw, null); Spacer(Modifier.width(6.dp)); Text(L("Yeni notta çöz", "Solve in a new note")) }
                if (inReview) OutlinedButton(onClick = onAgain) { Icon(Icons.Outlined.Replay, null); Spacer(Modifier.width(6.dp)); Text(L("Yine sor", "Ask again")) }
                Button(onClick = onLearned, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))) {
                    Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(6.dp))
                    Text(if (m.learned && !inReview) L("Öğrenilmedi say", "Mark as not learned") else L("Öğrendim", "Learned"))
                }
            }
        }
    }
    if (editing) SubjectDialog(L("Ders ve konu", "Subject and topic"), m.subject, m.topic, { editing = false }) { s, t -> onEdit(s, t); editing = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(L("Bu soru silinsin mi?", "Delete this question?")) },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text(L("Sil", "Delete"), color = Color(0xFFD32F2F)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(L("İptal", "Cancel")) } },
    )
}

/** Ders seçimi + konu girişi */
@Composable
fun SubjectDialog(title: String, initialSubject: String, initialTopic: String, onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var subject by remember { mutableStateOf(initialSubject) }
    var topic by remember { mutableStateOf(initialTopic) }
    val all = (subjectList() + AppState.mistakes.subjects).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(L("Ders", "Subject"), color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                // Basit akışlı ızgara
                all.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { s -> Chip(s, subject == s) { subject = s } }
                    }
                }
                OutlinedTextField(subject, { subject = it }, singleLine = true, label = { Text(L("veya yaz", "or type")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it }, singleLine = true, label = { Text(L("Konu (isteğe bağlı)", "Topic (optional)")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onOk(subject.trim(), topic.trim()) }) { Text(L("Kaydet", "Save"), color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text(L("İptal", "Cancel")) } },
    )
}
