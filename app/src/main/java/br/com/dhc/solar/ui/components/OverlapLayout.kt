package br.com.dhc.solar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Desenha [card] subindo [overlap] sobre a base de [hero] (como o card de potência sobre a cena). */
@Composable
fun OverlapLayout(
    hero: @Composable () -> Unit,
    card: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlap: Dp = 64.dp,
) {
    Layout(contents = listOf(hero, card), modifier = modifier) { (heroMeasurables, cardMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val heroPlaceable = heroMeasurables.first().measure(loose)
        val cardPlaceable = cardMeasurables.first().measure(loose)
        val overlapPx = overlap.roundToPx().coerceAtMost(heroPlaceable.height)
        val height = heroPlaceable.height - overlapPx + cardPlaceable.height
        layout(constraints.maxWidth, height) {
            heroPlaceable.place(0, 0)
            cardPlaceable.place((constraints.maxWidth - cardPlaceable.width) / 2, heroPlaceable.height - overlapPx)
        }
    }
}
