package br.com.dhc.solar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Uma série do gráfico de linha: x em minutos do dia. */
data class LineSeries(
    val points: List<Pair<Int, Double>>,
    val color: Color,
    val label: String,
    val filled: Boolean = true,
    val dashed: Boolean = false,
)

/**
 * Gráfico de linha ao longo do dia:
 * - as linhas "se desenham" (draw-on) e o preenchimento aparece em seguida;
 * - tocar e arrastar mostra um marcador com o valor de cada série;
 * - eixo X fixo [startMinute, endMinute] para o dia "andar" sem esticar.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    startMinute: Int = 5 * 60,
    endMinute: Int = 19 * 60,
    minY: Double? = 0.0,
    /** Marca o último ponto da primeira série como "agora" (dia em andamento). */
    liveEnd: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val visible = remember(series) { series.map { s -> s.copy(points = s.points.filter { it.first in startMinute..endMinute }) } }

    val draw = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        draw.snapTo(0f)
        draw.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    var scrubMinute by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(visible) {
                fun minuteAt(x: Float) = (startMinute + (x / size.width) * (endMinute - startMinute)).toInt()
                detectDragGestures(
                    onDragStart = { scrubMinute = minuteAt(it.x); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragEnd = { scrubMinute = null },
                    onDragCancel = { scrubMinute = null },
                    onDrag = { change, _ ->
                        val next = minuteAt(change.position.x)
                        if (next / 15 != (scrubMinute ?: next) / 15) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        scrubMinute = next
                    },
                )
            }
            .pointerInput(visible) { detectTapGestures(onTap = { scrubMinute = null }) },
    ) {
        val labelSpace = 22.dp.toPx()
        val top = 34.dp.toPx()
        val chartHeight = size.height - labelSpace - top
        val all = visible.flatMap { it.points.map { p -> p.second } }
        val rawMax = all.maxOrNull() ?: 1.0
        val rawMin = minY ?: (all.minOrNull() ?: 0.0)
        val pad = (rawMax - rawMin).coerceAtLeast(0.1) * 0.1
        val maxV = rawMax + pad
        val minV = if (minY != null) minY else rawMin - pad
        fun x(minute: Int) = (minute - startMinute).toFloat() / (endMinute - startMinute) * size.width
        fun y(v: Double) = top + chartHeight * (1f - ((v - minV) / (maxV - minV)).toFloat())
        val baseline = top + chartHeight

        val firstHour = ((startMinute / 60 + 2) / 3) * 3
        for (hour in firstHour..(endMinute / 60) step 3) {
            val gx = x(hour * 60)
            if (gx > size.width - 12.dp.toPx()) continue
            drawLine(colors.outline.copy(alpha = 0.3f), Offset(gx, top), Offset(gx, baseline), 1.dp.toPx())
            val label = measurer.measure("%02d:00".format(hour), TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant))
            drawText(label, topLeft = Offset(gx - label.size.width / 2f, baseline + 6.dp.toPx()))
        }

        visible.forEachIndexed { index, s ->
            if (s.points.size < 2) return@forEachIndexed
            val line = Path().apply {
                moveTo(x(s.points.first().first), y(s.points.first().second))
                for (i in 1 until s.points.size) {
                    val (pm, pv) = s.points[i - 1]
                    val (cm, cv) = s.points[i]
                    val mid = (x(pm) + x(cm)) / 2
                    cubicTo(mid, y(pv), mid, y(cv), x(cm), y(cv))
                }
            }
            // Draw-on: desenha só o trecho [0, comprimento * progresso] da linha.
            val measure = PathMeasure().apply { setPath(line, false) }
            val partial = Path()
            val stagger = (draw.value * 1.15f - index * 0.15f).coerceIn(0f, 1f)
            measure.getSegment(0f, measure.length * stagger, partial, true)
            if (s.filled) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(s.points.last().first), baseline)
                    lineTo(x(s.points.first().first), baseline)
                    close()
                }
                val revealX = x(s.points.first().first) + (x(s.points.last().first) - x(s.points.first().first)) * stagger
                val endX = x(s.points.last().first)
                val fade = size.width * 0.1f
                clipRect(right = revealX) {
                    // Camada própria: a borda direita da área se dissolve em vez de terminar num corte reto.
                    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint())
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.45f), s.color.copy(alpha = 0.02f)), startY = top, endY = baseline))
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = endX - fade, endX = endX),
                        topLeft = Offset(endX - fade, 0f), size = Size(fade + 1f, size.height),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                    )
                    drawContext.canvas.restore()
                }
            }
            drawPath(
                partial, s.color,
                style = Stroke(
                    width = 2.5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (s.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
                ),
            )
        }

        // Ponto do "agora" na ponta da curva, quando ela já terminou de se desenhar.
        val live = visible.firstOrNull()?.points?.lastOrNull()
        if (liveEnd && live != null && draw.value > 0.98f && scrubMinute == null) {
            val c = Offset(x(live.first), y(live.second))
            drawCircle(visible.first().color.copy(alpha = 0.25f), 9.dp.toPx(), c)
            drawCircle(colors.surfaceContainerHigh, 5.5.dp.toPx(), c)
            drawCircle(visible.first().color, 4.dp.toPx(), c)
        }

        scrubMinute?.let { minute ->
            val px = x(minute)
            drawLine(colors.onSurface.copy(alpha = 0.5f), Offset(px, top - 4.dp.toPx()), Offset(px, baseline), 1.dp.toPx())
            val texts = visible.mapNotNull { s ->
                val nearest = s.points.minByOrNull { abs(it.first - minute) } ?: return@mapNotNull null
                if (abs(nearest.first - minute) > 20) return@mapNotNull null
                drawCircle(colors.surfaceContainerHigh, 6.dp.toPx(), Offset(x(nearest.first), y(nearest.second)))
                drawCircle(s.color, 4.dp.toPx(), Offset(x(nearest.first), y(nearest.second)))
                (if (visible.size > 1) "${s.label} " else "") + format(nearest.second)
            }
            if (texts.isNotEmpty()) {
                val text = "%02d:%02d  ".format(minute / 60, minute % 60) + texts.joinToString("  ·  ")
                val layout = measurer.measure(text, TextStyle(fontSize = 12.sp, color = colors.onPrimary))
                val padding = 8.dp.toPx()
                val w = layout.size.width + padding * 2
                val h = layout.size.height + padding
                val left = (px - w / 2).coerceIn(0f, size.width - w)
                drawRoundRect(colors.primary, Offset(left, 0f), Size(w, h), CornerRadius(h / 2))
                drawText(layout, topLeft = Offset(left + padding, padding / 2))
            }
        }
    }
}
