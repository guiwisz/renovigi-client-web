package br.com.dhc.solar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.dhc.solar.ui.shell.BottomBarSpace
import br.com.dhc.solar.ui.shell.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Estrutura de cada aba: título grande que, ao rolar, dá lugar a um título
 * pequeno numa barra de vidro fosco no topo; conteúdo centralizado com
 * largura máxima (tablet) e espaço para a barra de abas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    subtitle: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * Cabeçalho de ponta a ponta (ex.: cena da usina), desenhado atrás da barra
     * de status. Quando presente, substitui o título grande padrão e as ações
     * ficam por conta dele até a barra de vidro aparecer.
     */
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val haze = LocalHazeState.current
    val threshold = if (header != null) 520 else 90
    val collapse by animateFloatAsState(if (scroll.value > threshold) 1f else 0f, label = "collapse")

    Box(Modifier.fillMaxSize()) {
        val body: @Composable () -> Unit = {
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .verticalScroll(scroll)
                    .then(if (header == null) Modifier.statusBarsPadding() else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header?.invoke()
                Column(
                    Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (header != null) {
                        // Mesmo respiro entre o cabeçalho e o primeiro card que entre os cards (spacedBy + 4 dp).
                        Spacer(Modifier.height(4.dp))
                    } else {
                        Spacer(Modifier.height(52.dp))
                        Column(Modifier.graphicsLayer { alpha = 1f - collapse }) {
                            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    content()
                    Spacer(Modifier.height(BottomBarSpace))
                }
            }
        }
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { body() }
        } else {
            body()
        }

        // Barra superior: transparente no topo, vidro fosco depois de rolar.
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 0.999f }
                .then(if (collapse > 0f) Modifier.frostedGlass(haze, veilAlpha = 0.6f * collapse) else Modifier),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(40.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = collapse },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // Com cabeçalho próprio, as ações só aparecem junto com a barra de vidro.
                    modifier = Modifier.graphicsLayer { alpha = if (header != null) collapse else 1f },
                    content = actions,
                )
            }
        }
    }
}

/** Botão de ícone em "quadrado arredondado", como nas referências. */
@Composable
fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit, iconModifier: Modifier = Modifier) {
    Box(
        Modifier
            .size(40.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = iconModifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Título de seção pequeno, acima de um grupo de cards. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 10.dp, start = 2.dp),
    )
}
