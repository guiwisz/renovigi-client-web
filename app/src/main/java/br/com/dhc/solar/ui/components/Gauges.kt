package br.com.dhc.solar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Medidor em semicírculo (0..1) com ponta marcada; anima ao mudar. */
@Composable
fun SemiGauge(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val stroke = 14.dp.toPx()
        val diameter = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(colors.surfaceVariant, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val sweep = 180f * progress.value
        drawArc(colors.primary, 180f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val angle = Math.toRadians((180f + sweep).toDouble())
        val radius = diameter / 2
        val center = Offset(topLeft.x + radius, topLeft.y + radius)
        val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        drawCircle(colors.surfaceContainerHigh, stroke * 0.55f, tip)
        drawCircle(colors.primary, stroke * 0.32f, tip)
    }
}

/** Barra segmentada em blocos (como na referência), preenchendo da esquerda. */
@Composable
fun BlockProgress(fraction: Float, modifier: Modifier = Modifier, blocks: Int = 12) {
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing)) }
    Row(modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(blocks) { i ->
            // Cada bloco acende progressivamente, com transição suave no bloco "da vez".
            val fill = (progress.value * blocks - i).coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(lerp(colors.surfaceVariant, colors.primary, fill)),
            )
        }
    }
}
