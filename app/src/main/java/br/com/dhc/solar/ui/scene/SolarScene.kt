package br.com.dhc.solar.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.dhc.solar.R
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/**
 * Cena da usina: céu do ciclo do dia (Lottie posicionado no horário real),
 * nuvens (mais densas quando a geração está abaixo do esperado), ondas do sol
 * indo às placas e energia correndo pelos cabos até a casa e a rede. Sem
 * geração (noite), o fluxo se inverte: a casa consome da rede.
 *
 * @param dayProgress 0..1 (ver SolarClock.dayProgress)
 * @param powerFraction potência atual / potência instalada
 * @param cloudiness 0 (céu limpo) .. 1 (muito nublado)
 * @param timelapseIntro ao aparecer, "acelera" o dia desde a madrugada até agora
 */
@Composable
fun SolarScene(
    dayProgress: Float,
    powerFraction: Float,
    cloudiness: Float,
    modifier: Modifier = Modifier,
    timelapseIntro: Boolean = true,
    /** Se definido, a base da cena se dissolve nesta cor (fundo da tela). */
    fadeTo: Color? = null,
) {
    val sky by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sky_cycle))
    val clouds by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.clouds))
    val flow by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.energy_flow))
    val gridImport by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.energy_import))

    val progress = remember { Animatable(if (timelapseIntro) 0.02f else dayProgress) }
    LaunchedEffect(dayProgress) {
        progress.animateTo(dayProgress, tween(if (progress.value < 0.05f) 2600 else 900, easing = FastOutSlowInEasing))
    }
    val generating = powerFraction > 0.005f
    val flowAlpha by animateFloatAsState(if (generating) 1f else 0f, tween(800), label = "flow")
    val importAlpha by animateFloatAsState(if (generating) 0f else 0.9f, tween(800), label = "import")
    val daylight = (1f - kotlin.math.abs(progress.value - 0.5f) * 4f).coerceIn(0f, 1f) // 0 à noite, 1 de dia
    val cloudAlpha by animateFloatAsState(0.15f + daylight * (0.35f + 0.6f * cloudiness), tween(1200), label = "clouds")

    Box(modifier.fillMaxWidth().aspectRatio(SceneGeometry.WIDTH / SceneGeometry.HEIGHT)) {
        LottieAnimation(sky, progress = { progress.value }, modifier = Modifier.matchParentSize())
        LottieAnimation(clouds, iterations = LottieConstants.IterateForever, modifier = Modifier.matchParentSize().alpha(cloudAlpha))
        // Com qualquer geração as ondas ficam bem visíveis; crescem com a potência.
        val waveIntensity = if (generating) (0.55f + powerFraction * 0.6f).coerceIn(0f, 1f) else 0f
        SunWaves(progress.value, intensity = waveIntensity, modifier = Modifier.matchParentSize())
        LottieAnimation(
            flow,
            iterations = LottieConstants.IterateForever,
            isPlaying = generating,
            speed = 0.5f + 2.5f * powerFraction.coerceIn(0f, 1f),
            modifier = Modifier.matchParentSize().alpha(flowAlpha),
        )
        // Sem geração a casa consome da rede: pulsos azuis do poste para a casa.
        LottieAnimation(
            gridImport,
            iterations = LottieConstants.IterateForever,
            isPlaying = !generating,
            speed = 0.6f,
            modifier = Modifier.matchParentSize().alpha(importAlpha),
        )
        if (fadeTo != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0.72f to fadeTo.copy(alpha = 0f), 1f to fadeTo)),
            )
        }
    }
}

/** "Ondinhas" que saem do sol e viajam até as placas. */
@Composable
private fun SunWaves(progress: Float, intensity: Float, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    val sun = SceneGeometry.sun(progress) ?: return
    // Sol baixo (atrás dos morros) quase não manda ondas.
    val elevation = sin(PI * ((progress - 0.25f) / 0.5f)).toFloat().coerceIn(0f, 1f)
    val strength = intensity * ((elevation - 0.15f) / 0.35f).coerceIn(0f, 1f)
    if (strength <= 0.02f) return
    Canvas(modifier) {
        val scale = size.width / SceneGeometry.WIDTH
        val from = sun * scale
        val to = SceneGeometry.panelCenter * scale
        val angle = Math.toDegrees(atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())).toFloat()
        val waves = 4
        for (i in 0 until waves) {
            val t = (phase + i.toFloat() / waves) % 1f
            if (t < 0.18f) continue // nasce já fora do disco do sol
            val center = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            val radius = (12f + 12f * t) * scale
            val alpha = sin(PI * t).toFloat() * 0.85f * strength
            drawArc(
                color = Color(0xFFFFE08A).copy(alpha = alpha),
                startAngle = angle - 40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3f * scale, cap = StrokeCap.Round),
            )
        }
    }
}
