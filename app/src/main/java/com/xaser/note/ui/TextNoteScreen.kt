package com.xaser.note.ui

import androidx.activity.compose.BackHandler
import com.xaser.note.L
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaser.note.model.NoteMeta

@Composable
fun TextNoteScreen(meta: NoteMeta, onBack: () -> Unit) {
    val store = AppState.store
    var title by remember { mutableStateOf(meta.title) }
    var body by remember { mutableStateOf(TextFieldValue(meta.body)) }

    fun save() {
        if (meta.title != title || meta.body != body.text) {
            meta.title = title; meta.body = body.text; store.touch(meta)
        }
    }
    DisposableEffect(Unit) { onDispose { save() } }
    BackHandler { save(); onBack() }

    /** Satır başına işaret ekle/kaldır */
    fun toggleLinePrefix(prefix: String) {
        val t = body.text
        val pos = body.selection.start
        val ls = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0 || pos == 0) 0 else it + 1 }
        val newText: String; val delta: Int
        if (t.startsWith(prefix, ls)) { newText = t.removeRange(ls, ls + prefix.length); delta = -prefix.length }
        else { newText = t.substring(0, ls) + prefix + t.substring(ls); delta = prefix.length }
        body = TextFieldValue(newText, TextRange((pos + delta).coerceIn(0, newText.length)))
    }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, L("Geri", "Back")) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { toggleLinePrefix("☐ ") }) { Icon(Icons.Outlined.CheckBox, L("Yapılacak", "To-do")) }
            IconButton(onClick = { toggleLinePrefix("• ") }) { Icon(Icons.Outlined.FormatListBulleted, L("Madde", "Bullet")) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
                Text(formatDate(meta.updated), color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Box {
                    if (title.isEmpty()) Text(L("Başlık", "Title"), fontSize = 28.sp, color = Color(0xFFBDBDBD), fontWeight = FontWeight.Bold)
                    BasicTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary))
                }
                Spacer(Modifier.height(16.dp))
                Box {
                    if (body.text.isEmpty()) Text(L("Not yazmaya başla", "Start writing"), fontSize = 18.sp, color = Color(0xFFBDBDBD))
                    BasicTextField(body, { v ->
                        // ☐ satırında Enter'a basınca yeni satır da ☐ ile başlasın
                        val old = body.text
                        var nv = v
                        if (v.text.length == old.length + 1 && v.selection.start > 0 && v.text[v.selection.start - 1] == '\n') {
                            val prevStart = v.text.lastIndexOf('\n', v.selection.start - 2).let { if (it < 0) 0 else it + 1 }
                            val prev = v.text.substring(prevStart, v.selection.start - 1)
                            val pre = listOf("☐ ", "☑ ", "• ").firstOrNull { prev.startsWith(it) }
                            if (pre != null && prev.length > pre.length) {
                                val p = if (pre == "☑ ") "☐ " else pre
                                val s = v.selection.start
                                nv = TextFieldValue(v.text.substring(0, s) + p + v.text.substring(s), TextRange(s + p.length))
                            }
                        }
                        body = nv
                    }, modifier = Modifier.fillMaxWidth().height(2000.dp),
                        textStyle = TextStyle(fontSize = 18.sp, color = TextPrimary, lineHeight = 28.sp))
                }
            }
        }
    }
}
