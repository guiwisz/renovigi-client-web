package br.com.dhc.solar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.dhc.solar.domain.EnergyPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Barras que crescem em sequência (mola, uma após a outra). Tocar numa barra
 * destaca e mostra o valor; a linha tracejada marca a média.
 */
@Composable
fun BarChart(
    points: List<EnergyPoint>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    highlightIndex: Int? = null,
    labelEvery: Int = 1,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val growth = remember(points) { points.map { Animatable(0f) } }
    LaunchedEffect(points) {
        growth.forEachIndexed { i, anim ->
            launch {
                delay(18L * i)
                anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    var selected by remember(points) { mutableStateOf(highlightIndex) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val index = (offset.x / (size.width / points.size.coerceAtLeast(1))).toInt().coerceIn(0, points.lastIndex)
                    selected = if (selected == index) null else index
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val labelSpace = 20.dp.toPx()
        val top = 30.dp.toPx()
        val chartHeight = size.height - labelSpace - top
        val maxV = (points.maxOf { it.kwh }).coerceAtLeast(0.1) * 1.08
        val slot = size.width / points.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val radius = CornerRadius(barWidth / 2.6f)
        val nonZero = points.filter { it.kwh > 0 }
        val average = if (nonZero.isEmpty()) null else nonZero.map { it.kwh }.average()

        points.forEachIndexed { i, p ->
            val h = (chartHeight * (p.kwh / maxV).toFloat() * growth[i].value).coerceAtLeast(if (p.kwh > 0) 3.dp.toPx() else 0f)
            val left = slot * i + (slot - barWidth) / 2
            val isSelected = selected == i
            val dim = selected != null && !isSelected
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(colors.primary.copy(alpha = if (dim) 0.35f else 1f), colors.primary.copy(alpha = if (dim) 0.18f else 0.55f)),
                    startY = top + chartHeight - h, endY = top + chartHeight,
                ),
                topLeft = Offset(left, top + chartHeight - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
            if (i % labelEvery == 0 || isSelected) {
                val label = measurer.measure(p.label, TextStyle(fontSize = 10.sp, color = if (isSelected) colors.onSurface else colors.onSurfaceVariant))
                drawText(label, topLeft = Offset(left + barWidth / 2 - label.size.width / 2, top + chartHeight + 5.dp.toPx()))
            }
        }

        average?.let { avg ->
            val ay = top + chartHeight * (1f - (avg / maxV).toFloat())
            drawLine(
                colors.onSurfaceVariant.copy(alpha = 0.6f), Offset(0f, ay), Offset(size.width, ay), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        selected?.let { i ->
            val p = points[i]
            val layout = measurer.measure("${p.label}: ${format(p.kwh)}", TextStyle(fontSize = 12.sp, color = colors.onPrimary))
            val padding = 8.dp.toPx()
            val w = layout.size.width + padding * 2
            val h = layout.size.height + padding
            val cx = slot * i + slot / 2
            val left = (cx - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(colors.primary, Offset(left, 0f), Size(w, h), CornerRadius(h / 2))
            drawText(layout, topLeft = Offset(left + padding, padding / 2))
        }
    }
}
