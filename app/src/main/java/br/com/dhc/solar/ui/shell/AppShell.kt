package br.com.dhc.solar.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.dhc.solar.container
import br.com.dhc.solar.ui.alerts.AlertsScreen
import br.com.dhc.solar.ui.components.SolarMotion
import br.com.dhc.solar.ui.components.frostedGlass
import br.com.dhc.solar.ui.history.HistoryScreen
import br.com.dhc.solar.ui.inverter.InverterScreen
import br.com.dhc.solar.ui.now.NowScreen
import br.com.dhc.solar.ui.settings.SettingsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

enum class Tab(val label: String, val icon: ImageVector) {
    NOW("Agora", Icons.Rounded.Bolt),
    INVERTER("Inversor", Icons.Rounded.Memory),
    HISTORY("Histórico", Icons.Rounded.Insights),
    ALERTS("Alertas", Icons.Rounded.NotificationsNone),
}

/** Estado de vidro compartilhado: as telas marcam o conteúdo como fonte e a barra desfoca. */
val LocalHazeState = staticCompositionLocalOf<HazeState> { error("HazeState não fornecido") }

/** Espaço reservado no fim de cada tela para a barra inferior flutuante. */
val BottomBarSpace = 112.dp

@Composable
fun AppShell(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val haze = rememberHazeState()

    // Atualização automática só com o app visível.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.autoRefresh() }
    }

    BackHandler(enabled = settingsOpen || tab != Tab.NOW) {
        if (settingsOpen) settingsOpen = false else tab = Tab.NOW
    }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(SolarMotion.gentle()) + scaleIn(SolarMotion.snappy(), initialScale = 0.98f)) togetherWith fadeOut(SolarMotion.gentle())
                },
                label = "tab",
            ) { current ->
                val container = androidx.compose.ui.platform.LocalContext.current.container
                when (current) {
                    Tab.NOW -> NowScreen(state, onRefresh = viewModel::refresh, onOpenSettings = { settingsOpen = true },
                        onOpenAlerts = { tab = Tab.ALERTS })
                    Tab.INVERTER -> InverterScreen(state, onRefresh = viewModel::refresh)
                    Tab.HISTORY -> HistoryScreen(container.repository, state.tariff)
                    Tab.ALERTS -> AlertsScreen(container.repository)
                }
            }

            GlassTabBar(
                selected = tab,
                alertBadge = state.snapshot?.recentAlarms?.any { it.active } == true,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically(SolarMotion.snappy()) { it } + fadeIn(),
                exit = slideOutVertically(SolarMotion.snappy()) { it } + fadeOut(),
            ) {
                SettingsScreen(state, onClose = { settingsOpen = false }, onSignOut = viewModel::signOut)
            }
        }
    }
}

/** Barra de abas flutuante em vidro fosco, com pílula que desliza até a aba ativa. */
@Composable
private fun GlassTabBar(selected: Tab, alertBadge: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .frostedGlass(LocalHazeState.current, veilAlpha = 0.55f)
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape),
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val bg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, label = "tabBg",
                )
                val tint by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabTint",
                )
                val lift by animateFloatAsState(if (active) 1f else 0f, SolarMotion.snappy(), label = "lift")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!active) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(
                            tab.icon, contentDescription = null, tint = tint,
                            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = 1f + 0.12f * lift; scaleY = 1f + 0.12f * lift },
                        )
                        if (tab == Tab.ALERTS && alertBadge) {
                            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        }
                    }
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
