package br.com.dhc.solar.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.ui.components.BarChart
import br.com.dhc.solar.ui.components.CardLabel
import br.com.dhc.solar.ui.components.CardRow
import br.com.dhc.solar.ui.components.InsetCard
import br.com.dhc.solar.ui.components.LineChart
import br.com.dhc.solar.ui.components.LineSeries
import br.com.dhc.solar.ui.components.SegmentedControl
import br.com.dhc.solar.ui.components.SquareIconButton
import br.com.dhc.solar.ui.components.TabScaffold
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.components.pressable
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.theme.SolarPalette

@Composable
fun HistoryScreen(repository: SolarRepository, tariff: Double) {
    val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val state by viewModel.state.collectAsStateWithLifecycle()

    TabScaffold(title = "Histórico") {
        SegmentedControl(
            options = Period.entries.map { it.label },
            selected = state.period.ordinal,
            onSelect = { viewModel.selectPeriod(Period.entries[it]) },
            modifier = Modifier.appear(0),
        )
        if (state.period != Period.TOTAL) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(1)) {
                SquareIconButton(Icons.Rounded.ChevronLeft, "Anterior", { viewModel.shift(-1) })
                AnimatedContent(viewModel.title(state), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "date",
                    modifier = Modifier.weight(1f)) { title ->
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.alpha(if (state.canGoForward) 1f else 0.3f)) {
                    SquareIconButton(Icons.Rounded.ChevronRight, "Próximo", { if (state.canGoForward) viewModel.shift(1) })
                }
            }
        }

        when (state.period) {
            Period.DAY -> DayContent(state, viewModel)
            else -> BarsContent(state, tariff)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun DayContent(state: HistoryUiState, viewModel: HistoryViewModel) {
    val field = state.selectedField
    val unit = field?.unit ?: "kW"
    val digits = if (unit == "kW" || unit == "Hz") 2 else 1
    // Chips de parâmetros (atalhos + "comparar com ontem").
    Row(
        Modifier.horizontalScroll(rememberScrollState()).appear(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val featured = FEATURED_FIELDS.mapNotNull { id -> state.fields.firstOrNull { it.id == id } }
            .ifEmpty { listOf(br.com.dhc.solar.data.repository.ChartField("output_power", "Potência ativa", "kW")) }
        featured.forEach { f -> Chip(f.label, selected = f.id == state.fieldId) { viewModel.selectField(f.id) } }
        Chip("Comparar com ontem", selected = state.compare, onClick = viewModel::toggleCompare)
    }
    InsetCard(Modifier.fillMaxWidth().appear(3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(field?.label ?: "Potência ativa", Icons.AutoMirrored.Rounded.ShowChart, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            state.series.maxOfOrNull { it.value }?.let {
                Text("máx ${Formatters.number(it, digits)} $unit", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val series = buildList {
                if (state.compareSeries.isNotEmpty()) {
                    add(LineSeries(state.compareSeries.map { it.minuteOfDay to it.value }, MaterialTheme.colorScheme.onSurfaceVariant,
                        "Ontem", filled = false, dashed = true))
                }
                add(LineSeries(state.series.map { it.minuteOfDay to it.value }, MaterialTheme.colorScheme.primary,
                    if (state.compare) "Dia" else (field?.label ?: ""), filled = state.fieldId == "output_power"))
            }
            LineChart(
                series = series,
                format = { "${Formatters.number(it, digits)} $unit" },
                height = 230.dp,
                startMinute = 5 * 60,
                endMinute = 19 * 60,
                minY = if (state.fieldId == "output_power" || unit == "A") 0.0 else null,
                liveEnd = state.date == java.time.LocalDate.now() && state.compareSeries.isEmpty(),
            )
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else if (state.series.isEmpty()) Text("Sem leituras neste dia", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.series.isNotEmpty()) {
        val values = state.series.map { it.value }
        CardRow(Modifier.appear(4)) {
            Stat("Mínimo", "${Formatters.number(values.min(), digits)} $unit", Modifier.weight(1f).fillMaxHeight())
            Stat("Média", "${Formatters.number(values.average(), digits)} $unit", Modifier.weight(1f).fillMaxHeight())
            Stat("Máximo", "${Formatters.number(values.max(), digits)} $unit", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun BarsContent(state: HistoryUiState, tariff: Double) {
    val total = state.bars.sumOf { it.kwh }
    val best = state.bars.maxByOrNull { it.kwh }
    val nonZero = state.bars.filter { it.kwh > 0 }
    val unitLabel = when (state.period) { Period.WEEK, Period.MONTH -> "dia"; Period.YEAR -> "mês"; else -> "ano" }
    InsetCard(Modifier.fillMaxWidth().appear(2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Energia por $unitLabel", Icons.Rounded.BarChart, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            Text("— média", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
            BarChart(
                points = state.bars,
                format = Formatters::kwh,
                height = 230.dp,
                labelEvery = if (state.bars.size > 16) 5 else 1,
            )
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp)
        }
        Text("Toque numa barra para ver o valor", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
    if (!state.loading && state.bars.isNotEmpty()) {
        CardRow(Modifier.appear(3)) {
            Stat("Total", Formatters.kwh(total), Modifier.weight(1f).fillMaxHeight(), Formatters.money(total * tariff))
            Stat("Média por $unitLabel", if (nonZero.isEmpty()) "—" else Formatters.kwh(nonZero.map { it.kwh }.average()), Modifier.weight(1f).fillMaxHeight())
        }
        best?.takeIf { it.kwh > 0 }?.let {
            val name = when (state.period) {
                Period.WEEK, Period.MONTH -> "Melhor dia: ${it.label}"
                Period.YEAR -> "Melhor mês: ${it.label}"
                else -> "Melhor ano: ${it.label}"
            }
            Stat(name, Formatters.kwh(it.kwh), Modifier.fillMaxWidth().appear(4), Formatters.money(it.kwh * tariff))
        }
        if (state.period == Period.WEEK) WeekComparison(state, Modifier.appear(5))
    }
}

/** "+12% em relação à semana anterior" (mesmos dias da semana). */
@Composable
private fun WeekComparison(state: HistoryUiState, modifier: Modifier) {
    val previous = state.previousWeekKwh ?: return
    val current = state.currentWeekComparableKwh ?: return
    val change = (current - previous) / previous
    val up = change >= 0
    InsetCard(modifier.fillMaxWidth()) {
        Text("EM RELAÇÃO À SEMANA ANTERIOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${if (up) "▲" else "▼"} ${Formatters.percent(kotlin.math.abs(change))}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (up) SolarPalette.Leaf else SolarPalette.Ember,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "${Formatters.kwh(current)} contra ${Formatters.kwh(previous)} nos mesmos dias da semana passada",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, caption: String? = null) {
    InsetCard(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SolarPalette.Leaf) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .pressable(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

