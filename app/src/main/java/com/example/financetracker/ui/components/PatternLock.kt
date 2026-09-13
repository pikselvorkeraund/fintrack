package com.example.financetracker.ui.components
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sqrt
@Composable
fun PatternLock(modifier: Modifier = Modifier, isError: Boolean = false, onDone: (List<Int>) -> Unit) {
    val dots = remember { mutableStateListOf<Int>() }
    val drawing = remember { mutableStateOf(false) }
    val finger = remember { mutableStateOf(Offset.Zero) }
    val lc = if (isError) Color(0xFFF44336) else Color(0xFF4CAF50)
    Canvas(modifier = modifier.size(300.dp).pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { dots.clear(); drawing.value = true },
            onDrag = { ch, _ ->
                ch.consume(); finger.value = ch.position
                val cs = size.width / 3
                for (i in 0 until 9) {
                    val cx = (i % 3) * cs + cs / 2; val cy = (i / 3) * cs + cs / 2
                    if (sqrt((ch.position.x - cx).pow(2) + (ch.position.y - cy).pow(2)) < 40.dp.toPx() && !dots.contains(i)) dots.add(i)
                }
            },
            onDragEnd = { drawing.value = false; if (dots.isNotEmpty()) { onDone(dots.toList()); dots.clear() } }
        )
    }) {
        val cs = size.width / 3
        fun c(i: Int) = Offset((i % 3) * cs + cs / 2, (i / 3) * cs + cs / 2)
        if (dots.size > 1) for (i in 0 until dots.size - 1) drawLine(lc, c(dots[i]), c(dots[i + 1]), 8f, cap = StrokeCap.Round)
        if (drawing.value && dots.isNotEmpty()) drawLine(lc.copy(alpha = 0.5f), c(dots.last()), finger.value, 4f, cap = StrokeCap.Round)
        for (i in 0 until 9) drawCircle(if (dots.contains(i)) lc else Color.White.copy(alpha = 0.4f), if (dots.contains(i)) 18f else 12f, c(i))
    }
}