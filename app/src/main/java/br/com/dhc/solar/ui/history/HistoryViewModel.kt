package br.com.dhc.solar.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.dhc.solar.data.repository.ChartField
import br.com.dhc.solar.data.repository.SeriesPoint
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.domain.EnergyPoint
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.YearMonth

enum class Period(val label: String) { DAY("Dia"), WEEK("Semana"), MONTH("Mês"), YEAR("Ano"), TOTAL("Total") }

/** Semana de domingo a sábado, como nos calendários brasileiros. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

data class HistoryUiState(
    val period: Period = Period.DAY,
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val error: String? = null,
    // Dia
    val fields: List<ChartField> = emptyList(),
    val fieldId: String = "output_power",
    val compare: Boolean = false,
    val series: List<SeriesPoint> = emptyList(),
    val compareSeries: List<SeriesPoint> = emptyList(),
    // Semana / mês / ano / total
    val bars: List<EnergyPoint> = emptyList(),
    /** Semana: total dos mesmos dias da semana anterior (para comparação justa com a semana em andamento). */
    val previousWeekKwh: Double? = null,
    val currentWeekComparableKwh: Double? = null,
) {
    val selectedField: ChartField? get() = fields.firstOrNull { it.id == fieldId }
    val canGoForward: Boolean
        get() = when (period) {
            Period.DAY -> date < LocalDate.now()
            Period.WEEK -> weekStart(date) < weekStart(LocalDate.now())
            Period.MONTH -> YearMonth.from(date) < YearMonth.now()
            Period.YEAR -> date.year < LocalDate.now().year
            Period.TOTAL -> false
        }
}

/** Parâmetros mostrados como atalho (os demais ficam no seletor completo). */
val FEATURED_FIELDS = listOf("output_power", "inverter_temperature", "pv_voltage1", "pv_voltage2", "phase_voltage_t", "grid_frequency")

class HistoryViewModel(private val repository: SolarRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state
    private var job: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.chartFields() }.onSuccess { fields -> _state.update { it.copy(fields = fields) } }
        }
        load()
    }

    fun selectPeriod(period: Period) {
        _state.update { it.copy(period = period, date = LocalDate.now()) }
        load()
    }

    fun selectField(id: String) {
        _state.update { it.copy(fieldId = id) }
        load()
    }

    fun toggleCompare() {
        _state.update { it.copy(compare = !it.compare) }
        load()
    }

    fun shift(step: Int) {
        _state.update {
            val next = when (it.period) {
                Period.DAY -> it.date.plusDays(step.toLong())
                Period.WEEK -> it.date.plusWeeks(step.toLong())
                Period.MONTH -> it.date.plusMonths(step.toLong())
                Period.YEAR -> it.date.plusYears(step.toLong())
                Period.TOTAL -> it.date
            }
            it.copy(date = minOf(next, LocalDate.now()))
        }
        load()
    }

    fun title(s: HistoryUiState): String = when (s.period) {
        Period.DAY -> if (s.date == LocalDate.now()) "Hoje" else Formatters.dayMonth(s.date)
        Period.WEEK -> {
            val start = weekStart(s.date)
            if (start == weekStart(LocalDate.now())) "Esta semana"
            else "${Formatters.dayMonth(start)} – ${Formatters.dayMonth(start.plusDays(6))}"
        }
        Period.MONTH -> Formatters.monthYear(s.date)
        Period.YEAR -> s.date.year.toString()
        Period.TOTAL -> "Desde a instalação"
    }

    private fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val s = _state.value
            try {
                when (s.period) {
                    Period.DAY -> {
                        val series = repository.parameterSeries(s.fieldId, s.date)
                        val previous = if (s.compare) {
                            repository.compareDays(s.fieldId, listOf(s.date.minusDays(1)))[s.date.minusDays(1)].orEmpty()
                        } else emptyList()
                        _state.update { it.copy(series = series, compareSeries = previous, loading = false) }
                    }
                    Period.WEEK -> {
                        val start = weekStart(s.date)
                        val today = LocalDate.now()
                        val days = repository.energyByDay(start.minusWeeks(1), start.plusDays(6))
                        val bars = (0..6).map { offset ->
                            val day = start.plusDays(offset.toLong())
                            EnergyPoint(Formatters.weekdayDay(day), if (day > today) 0.0 else days[day] ?: 0.0)
                        }
                        // Compara só os dias já decorridos (semana em andamento) com os mesmos dias da anterior.
                        val elapsed = if (start == weekStart(today)) (today.toEpochDay() - start.toEpochDay()).toInt() else 6
                        val current = (0..elapsed).sumOf { days[start.plusDays(it.toLong())] ?: 0.0 }
                        val previous = (0..elapsed).sumOf { days[start.minusWeeks(1).plusDays(it.toLong())] ?: 0.0 }
                        _state.update {
                            it.copy(bars = bars, loading = false, currentWeekComparableKwh = current,
                                previousWeekKwh = previous.takeIf { p -> p > 0 })
                        }
                    }
                    Period.MONTH -> {
                        val month = YearMonth.from(s.date)
                        val days = repository.dailyHistory(month)
                        // Dias futuros vêm zerados: mostramos só até hoje.
                        val visible = if (month == YearMonth.now()) days.take(LocalDate.now().dayOfMonth) else days
                        _state.update { it.copy(bars = visible, loading = false) }
                    }
                    Period.YEAR -> {
                        val months = repository.monthlyHistory(s.date.year).mapIndexed { i, p -> p.copy(label = Formatters.monthShort(i + 1)) }
                        val visible = if (s.date.year == LocalDate.now().year) months.take(LocalDate.now().monthValue) else months
                        _state.update { it.copy(bars = visible, loading = false) }
                    }
                    Period.TOTAL -> _state.update { it.copy(bars = repository.yearlyHistory(), loading = false) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }
}
