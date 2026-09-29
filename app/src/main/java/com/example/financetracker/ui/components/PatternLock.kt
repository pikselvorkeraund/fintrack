package com.example.financetracker.ui.components
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sqrt
@Composable
fun PatternLock(modifier: Modifier = Modifier, isError: Boolean = false, onDone: (List<Int>) -> Unit) {
    val dots = remember { mutableStateListOf<Int>() }
    val drawing = remember { mutableStateOf(false) }
    val finger = remember { mutableStateOf(Offset.Zero) }
    // Цвет активной линии: красный при ошибке, зелёный при успехе
    val lc = if (isError) Color(0xFFF44336) else Color(0xFF4CAF50)
    // Нейтральный цвет колец подложки: автоматически подстраивается под
    // светлую/тёмную тему и виден на любом фоне (вместо белого по белому)
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Толщина обводки колец — 3dp в пикселях (вычисляется вне Canvas)
    val ringW = with(LocalDensity.current) { 3.dp.toPx() }
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
        // Нейтральные полые кольца: рисуются до линии, чтобы выбранные
        // точки поверх колец выглядели залитыми и не было швов обводки
        for (i in 0 until 9) drawCircle(baseColor.copy(alpha = 0.5f), 22f, c(i), style = Stroke(width = ringW))
        if (dots.size > 1) for (i in 0 until dots.size - 1) drawLine(lc, c(dots[i]), c(dots[i + 1]), 8f, cap = StrokeCap.Round)
        if (drawing.value && dots.isNotEmpty()) drawLine(lc.copy(alpha = 0.5f), c(dots.last()), finger.value, 4f, cap = StrokeCap.Round)
        // Выбранные точки: залитый круг чуть меньшего радиуса + толстая
        // цветная обводка по контуру — чистый «классический» pattern lock
        dots.forEach { i ->
            drawCircle(lc, 18f, c(i))
            drawCircle(lc.copy(alpha = 0.45f), 22f, c(i), style = Stroke(width = ringW * 1.6f))
        }
    }
}