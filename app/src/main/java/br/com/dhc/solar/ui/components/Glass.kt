package br.com.dhc.solar.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Vidro fosco: desfoca o que estiver *atrás* (conteúdo marcado com
 * `hazeSource(state)`) e aplica um véu translúcido da cor de fundo.
 * Em aparelhos sem RenderEffect a Haze cai para um véu sólido.
 */
@Composable
fun Modifier.frostedGlass(state: HazeState, veilAlpha: Float = 0.62f): Modifier {
    val veil = MaterialTheme.colorScheme.surface.copy(alpha = veilAlpha)
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            blurRadius(28.dp)
            noiseFactor(0.06f)
            colorEffects(listOf(HazeColorEffect.tint(veil)))
        },
    )
}
