package com.xaser.note

import com.xaser.note.ui.ExamSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamResultTest {
    private fun session(penalty: Int) = ExamSession(count = 10, firstNo = 1, penalty = penalty).apply {
        "ABCDEABCDE".forEachIndexed { i, c -> key[i + 1] = c.toString() }
        // 1-6 doğru, 7-8 yanlış, 9-10 boş
        for (n in 1..6) answers[n] = key[n]!!
        answers[7] = "E"; answers[8] = "A"
    }

    @Test fun yksNet() {
        val r = session(4).result()
        assertEquals(6, r.correct); assertEquals(2, r.wrong); assertEquals(2, r.empty)
        assertEquals(5.5, r.net, 1e-9)
        assertEquals(listOf(7, 8), r.wrongNos)
    }

    @Test fun lgsNet() = assertEquals(6 - 2 / 3.0, session(3).result().net, 1e-9)

    @Test fun noPenalty() = assertEquals(6.0, session(0).result().net, 1e-9)

    @Test fun onlyKeyedQuestionsCount() {
        val s = session(4); s.key.remove(10); s.key.remove(9)
        val r = s.result()
        assertEquals(8, r.keyed); assertEquals(0, r.empty)
    }
}
