package br.com.dhc.solar.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.domain.Alarm
import br.com.dhc.solar.domain.AlarmKind
import br.com.dhc.solar.ui.components.InsetCard
import br.com.dhc.solar.ui.components.SectionTitle
import br.com.dhc.solar.ui.components.TabScaffold
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.theme.SolarPalette
import br.com.dhc.solar.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class AlertsUiState(val loading: Boolean = true, val alarms: List<Alarm> = emptyList(), val error: String? = null)

class AlertsViewModel(private val repository: SolarRepository) : ViewModel() {
    private val _state = MutableStateFlow(AlertsUiState())
    val state: StateFlow<AlertsUiState> = _state

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                val today = LocalDate.now()
                AlertsUiState(loading = false, alarms = repository.alarms(today.minusMonths(12), today).sortedByDescending { it.startedAt })
            } catch (e: Exception) {
                AlertsUiState(loading = false, error = e.toUserMessage())
            }
        }
    }
}

@Composable
fun AlertsScreen(repository: SolarRepository) {
    val viewModel: AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(repository) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    TabScaffold(
        title = "Alertas",
        subtitle = if (state.loading) null else "${state.alarms.size} nos últimos 12 meses",
        refreshing = state.loading,
        onRefresh = viewModel::load,
    ) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.alarms.isEmpty() && state.error == null) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp).appear(0), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CheckCircle, null, tint = SolarPalette.Leaf, modifier = Modifier.size(52.dp))
                Text("Nenhum alerta no último ano", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Seu sistema está funcionando sem interrupções.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        if (state.loading && state.alarms.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
        }
        var index = 0
        state.alarms.groupBy { alarm -> alarm.start()?.let { YearMonth.from(it) } }.forEach { (month, alarms) ->
            SectionTitle(month?.let { Formatters.monthYear(it) } ?: "Sem data", Modifier.appear(index++))
            alarms.forEach { AlarmRow(it, Modifier.appear(index++)) }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, modifier: Modifier) {
    var expanded by rememberSaveable(alarm.id) { mutableStateOf(false) }
    val info = alarm.info
    val (icon, color) = when (info.kind) {
        AlarmKind.GRID -> Icons.Rounded.ElectricalServices to SolarPalette.Sky
        AlarmKind.PANELS -> Icons.Rounded.SolarPower to SolarPalette.Sun
        AlarmKind.INVERTER -> Icons.Rounded.Memory to SolarPalette.Alert
        AlarmKind.OTHER -> Icons.Rounded.WarningAmber to SolarPalette.Sun
    }
    InsetCard(modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    alarm.start()?.let { "${Formatters.dayMonth(it)}, ${"%02d:%02d".format(it.hour, it.minute)}" }.orEmpty() +
                        (alarm.duration?.let { " · ${Formatters.duration(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alarm.active) {
                Text(
                    "Ativo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(SolarPalette.Alert).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp)) {
                Text(info.explanation, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Código ${alarm.code} · ${alarm.rawDescription}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
