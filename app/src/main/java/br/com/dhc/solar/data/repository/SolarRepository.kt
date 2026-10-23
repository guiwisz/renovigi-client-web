package br.com.dhc.solar.data.repository

import br.com.dhc.solar.data.auth.NotLoggedInException
import br.com.dhc.solar.data.auth.SessionManager
import br.com.dhc.solar.data.demo.DemoData
import br.com.dhc.solar.data.local.SnapshotCache
import br.com.dhc.solar.data.local.ThemeMode
import br.com.dhc.solar.data.remote.Session
import br.com.dhc.solar.data.remote.ShineMonitorApi
import br.com.dhc.solar.data.remote.ShineMonitorException
import br.com.dhc.solar.data.remote.dto.ChartFieldDto
import br.com.dhc.solar.data.remote.dto.DeviceDto
import br.com.dhc.solar.data.remote.dto.KeyValue
import br.com.dhc.solar.data.remote.dto.TimedValue
import br.com.dhc.solar.data.remote.dto.WarningDto
import br.com.dhc.solar.domain.Alarm
import br.com.dhc.solar.domain.CollectorInfo
import br.com.dhc.solar.domain.EnergyPoint
import br.com.dhc.solar.domain.InverterLive
import br.com.dhc.solar.domain.InverterStatus
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.domain.PowerSample
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Um ponto de série de parâmetro: minuto do dia e valor (null = sem leitura). */
data class SeriesPoint(val minuteOfDay: Int, val value: Double)

/** Parâmetro do inversor com histórico. */
data class ChartField(val id: String, val label: String, val unit: String)

class SolarRepository(
    private val api: ShineMonitorApi,
    private val sessions: SessionManager,
    private val cache: SnapshotCache,
    /** Chamado após gravar um snapshot novo (ex.: redesenhar os widgets). */
    private val onSnapshotChanged: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val snapshot: Flow<PlantSnapshot?> = cache.snapshot
    val tariff: Flow<Double> = cache.snapshot.map { it?.tariffPerKwh ?: SnapshotCache.DEFAULT_TARIFF }
    val investment: Flow<Double> = cache.investment
    val themeMode: Flow<ThemeMode> = cache.themeMode

    private val deviceLock = Mutex()
    @Volatile private var device: DeviceDto? = null
    @Volatile private var fields: List<ChartField>? = null

    val demoMode: Flow<Boolean> = cache.demoMode

    private suspend fun isDemo(): Boolean = cache.demoMode.first()

    /** Entra no modo demonstração (dados fictícios, sem conta). */
    suspend fun startDemo() {
        cache.setDemo(true)
        refresh()
    }

    /** Busca tudo em paralelo, grava no cache e devolve o snapshot novo. */
    suspend fun refresh(): PlantSnapshot = if (isDemo()) {
        DemoData.snapshot(cache.demoTariff.first(), cache.demoName.first()).also {
            cache.save(it)
            onSnapshotChanged()
        }
    } else refreshFromServer()

    private suspend fun refreshFromServer(): PlantSnapshot = sessions.withSession { session ->
        val plantId = sessions.plantId() ?: throw NotLoggedInException()
        val plant = api.plantInfo(session, plantId)
        val offset = plant.address?.timezone ?: BRASILIA_OFFSET
        val today = LocalDateTime.now(ZoneOffset.ofTotalSeconds(offset)).toLocalDate()
        val dev = device(session)
        coroutineScope {
            val current = async { api.plantCurrentData(session, plantId) }
            val curve = async { orEmpty { api.powerOneDay(session, plantId, today).outputPower } }
            val inverter = async { dev?.let { d -> optional { parseInverter(api.deviceLastData(session, d)) } } }
            val collector = async { dev?.let { d -> optional { api.collector(session, d.pn) } } }
            val running = async { optional { api.runningMinutes(session, plantId) } }
            val alarms = async {
                dev?.let { d -> optional { api.deviceWarnings(session, d, today.minusDays(30), today, pageSize = 10).warning.map(::toAlarm) } }
            }
            val month = async { optional { api.energyPerDay(session, plantId, YearMonth.from(today)).perday } }
            val recentDays = async { optional { daysBetween(session, plantId, weekStart(today).minusWeeks(4), today) } }
            val months = async {
                optional {
                    listOf(today.year - 1, today.year).flatMap { year ->
                        api.energyPerMonth(session, plantId, year).permonth.mapNotNull { p ->
                            val date = runCatching { LocalDateTime.parse(p.ts, TS).toLocalDate() }.getOrNull() ?: return@mapNotNull null
                            YearMonth.from(date) to (p.value ?: 0.0)
                        }
                    }
                }
            }

            val values = current.await()
            val live = inverter.await()
            val lastReading = live?.readingAt
            val todayKwh = values.number("ENERGY_TODAY") ?: 0.0
            val days = recentDays.await().orEmpty()
            val start = weekStart(today)
            val closedDays = days.filterKeys { it < today && it >= today.minusDays(28) }.values.filter { it > 0 }
            val fullWeeks = (1..4).map { w ->
                val from = start.minusWeeks(w.toLong())
                (0..6L).sumOf { days[from.plusDays(it)] ?: 0.0 }
            }.filter { it > 0 }
            val closedMonths = months.await().orEmpty()
                .filter { (month, kwh) -> month < YearMonth.from(today) && kwh > 0 }
                .sortedBy { it.first }.takeLast(12).map { it.second }
            PlantSnapshot(
                plantId = plantId,
                plantName = plant.name,
                status = InverterStatus.fromCode(plant.status),
                nominalPowerKw = plant.nominalPower,
                currentPowerKw = values.number("CURRENT_POWER") ?: 0.0,
                todayKwh = todayKwh,
                monthKwh = values.number("ENERGY_MONTH") ?: 0.0,
                yearKwh = values.number("ENERGY_YEAR") ?: 0.0,
                totalKwh = values.number("ENERGY_TOTAL") ?: 0.0,
                todayCurve = trimFuture(curve.await().mapNotNull(::toSample), lastReading),
                co2KgPerKwh = plant.profit?.co2,
                updatedAtMillis = clock(),
                tariffPerKwh = plant.profit?.unitProfit,
                proceedsTotal = values.money("ENERGY_PROCEEDS"),
                co2TotalKg = values.number("ENERGY_CO2"),
                coalTotalKg = values.number("ENERGY_COAL"),
                installDate = plant.install,
                latitude = plant.address?.lat,
                longitude = plant.address?.lon,
                utcOffsetSeconds = offset,
                lastReadingAt = lastReading,
                inverter = live,
                collector = collector.await()?.let { CollectorInfo(it.fireware, it.datFetch, online = it.status == 0) },
                runningMinutes = running.await(),
                recentAlarms = alarms.await().orEmpty(),
                monthDays = month.await().orEmpty()
                    .filter { point -> point.ts.take(10) <= today.toString() }
                    .map { it.toPoint(DAY_LABEL) },
                averageDayKwh = closedDays.takeIf { it.isNotEmpty() }?.average(),
                // O total de hoje vem do servidor em tempo real; o "por dia" pode estar atrasado.
                weekKwh = if (recentDays.await() == null) null
                else (0 until (today.toEpochDay() - start.toEpochDay()).toInt()).sumOf { days[start.plusDays(it.toLong())] ?: 0.0 } + todayKwh,
                typicalWeekKwh = fullWeeks.takeIf { it.isNotEmpty() }?.average(),
                typicalMonthKwh = closedMonths.takeIf { it.isNotEmpty() }?.average(),
            )
        }
    }.also {
        cache.save(it)
        onSnapshotChanged()
    }

    // ---------------------------------------------------------- histórico

    suspend fun dailyHistory(month: YearMonth): List<EnergyPoint> = if (isDemo()) DemoData.perDay(month) else withPlant { session, plantId ->
        api.energyPerDay(session, plantId, month).perday.map { it.toPoint(DAY_LABEL) }
    }

    /** Energia por dia num intervalo qualquer (busca cada mês envolvido uma vez). */
    suspend fun energyByDay(from: LocalDate, to: LocalDate): Map<LocalDate, Double> = if (isDemo()) DemoData.byDay(from, to) else withPlant { session, plantId ->
        daysBetween(session, plantId, from, to)
    }

    private suspend fun daysBetween(session: Session, plantId: Long, from: LocalDate, to: LocalDate): Map<LocalDate, Double> {
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(to) }.toList()
        return coroutineScope {
            months.map { month -> async { api.energyPerDay(session, plantId, month).perday } }.flatMap { it.await() }
        }.mapNotNull { point ->
            val day = runCatching { LocalDateTime.parse(point.ts, TS).toLocalDate() }.getOrNull() ?: return@mapNotNull null
            if (day < from || day > to) null else day to (point.value ?: 0.0)
        }.toMap()
    }

    /** Semana de domingo a sábado (calendário brasileiro). */
    private fun weekStart(date: LocalDate): LocalDate = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))

    suspend fun monthlyHistory(year: Int): List<EnergyPoint> = if (isDemo()) DemoData.perMonth(year) else withPlant { session, plantId ->
        api.energyPerMonth(session, plantId, year).permonth.map { it.toPoint(MONTH_LABEL) }
    }

    suspend fun yearlyHistory(): List<EnergyPoint> = if (isDemo()) DemoData.perYear() else withPlant { session, plantId ->
        api.energyPerYear(session, plantId).peryear.map { it.toPoint(YEAR_LABEL) }
    }

    /** Parâmetros do inversor com histórico, com nomes em português. */
    suspend fun chartFields(): List<ChartField> = if (isDemo()) DemoData.chartFields else fields ?: withPlant { session, plantId ->
        val dev = device(session) ?: return@withPlant emptyList()
        api.chartFields(session, plantId, dev).map(::toField).also { fields = it }
    }

    /** Série de um parâmetro num dia, sem os zeros de "sem leitura" (noite/futuro). */
    suspend fun parameterSeries(fieldId: String, day: LocalDate): List<SeriesPoint> = if (isDemo()) DemoData.series(fieldId, day) else sessions.withSession { session ->
        val dev = device(session) ?: return@withSession emptyList()
        val series = api.chartData(session, dev, listOf(fieldId), day).firstOrNull()?.points.orEmpty()
        cleanSeries(series)
    }

    /** O mesmo parâmetro em vários dias (para sobrepor no gráfico). */
    suspend fun compareDays(fieldId: String, days: List<LocalDate>): Map<LocalDate, List<SeriesPoint>> =
        if (isDemo()) days.associateWith { DemoData.series(fieldId, it) } else sessions.withSession { session ->
            val dev = device(session) ?: return@withSession emptyMap()
            api.compareDays(session, dev, fieldId, days).associate { LocalDate.parse(it.date) to cleanSeries(it.points) }
        }

    suspend fun alarms(from: LocalDate, to: LocalDate, page: Int = 0): List<Alarm> = if (isDemo()) DemoData.alarms(to) else sessions.withSession { session ->
        val dev = device(session) ?: return@withSession emptyList()
        try {
            api.deviceWarnings(session, dev, from, to, page = page, pageSize = 50).warning.map(::toAlarm)
        } catch (e: ShineMonitorException) {
            if (e.isNoRecord) emptyList() else throw e
        }
    }

    // ---------------------------------------------------- escrita / ajustes

    /** Grava a tarifa (R$/kWh) na usina, no servidor, e atualiza o snapshot. */
    suspend fun setTariff(value: Double) {
        if (isDemo()) { cache.setDemoTariff(value); refresh(); return }
        withPlant { session, plantId -> api.editPlant(session, plantId, "profit.unitProfit" to "%.4f".format(Locale.US, value)) }
        refresh()
    }

    suspend fun setPlantName(name: String) {
        if (isDemo()) { cache.setDemoName(name.trim()); refresh(); return }
        withPlant { session, plantId -> api.editPlant(session, plantId, "name" to name.trim()) }
        refresh()
    }

    suspend fun currentTariff(): Double = tariff.first()

    suspend fun setInvestment(value: Double) = cache.setInvestment(value)

    suspend fun setThemeMode(mode: ThemeMode) = cache.setThemeMode(mode)

    suspend fun signOut() {
        cache.setDemo(false)
        sessions.signOut()
        cache.clear()
        device = null
        fields = null
        onSnapshotChanged()
    }

    // ------------------------------------------------------------ internos

    private suspend fun <T> withPlant(block: suspend (Session, Long) -> T): T = sessions.withSession { session ->
        block(session, sessions.plantId() ?: throw NotLoggedInException())
    }

    /** O inversor da usina (descoberto uma vez por sessão do app). */
    private suspend fun device(session: Session): DeviceDto? = device ?: deviceLock.withLock {
        device ?: optional { api.devices(session).firstOrNull() }?.also { device = it }
    }

    private fun parseInverter(params: List<br.com.dhc.solar.data.remote.dto.DeviceParamDto>) =
        InverterLive.fromParams(params.map { Triple(it.title, it.unit, it.value) })

    private fun toAlarm(w: WarningDto) = Alarm(
        id = w.id,
        code = w.code,
        rawDescription = w.desc,
        startedAt = w.gts,
        endedAt = w.cts?.takeIf { it.isNotBlank() },
        active = w.status,
    )

    private fun toField(dto: ChartFieldDto) = ChartField(dto.optional, FIELD_LABELS[dto.optional] ?: dto.name, dto.uint.orEmpty())

    private fun cleanSeries(points: List<KeyValue>): List<SeriesPoint> {
        val parsed = points.mapNotNull { kv ->
            val time = runCatching { LocalDateTime.parse(kv.key, TS) }.getOrNull() ?: return@mapNotNull null
            SeriesPoint(time.hour * 60 + time.minute, kv.number ?: return@mapNotNull null)
        }
        // Zeros antes da primeira e depois da última leitura real = inversor desligado/sem dado.
        val first = parsed.indexOfFirst { it.value != 0.0 }
        val last = parsed.indexOfLast { it.value != 0.0 }
        return if (first < 0) emptyList() else parsed.subList(first, last + 1)
    }

    /** A curva do dia vem com os horários futuros zerados: cortamos na última leitura. */
    private fun trimFuture(curve: List<PowerSample>, lastReading: String?): List<PowerSample> {
        val limit = lastReading?.let { runCatching { LocalDateTime.parse(it, TS) }.getOrNull() }
            ?.let { it.hour * 60 + it.minute }
            ?: curve.indexOfLast { it.kw > 0 }.takeIf { it >= 0 }?.let { curve[it].minuteOfDay }
            ?: return curve
        return curve.filter { it.minuteOfDay <= limit }
    }

    private fun toSample(point: TimedValue): PowerSample? {
        val time = runCatching { LocalDateTime.parse(point.ts, TS) }.getOrNull() ?: return null
        return PowerSample(time.hour * 60 + time.minute, point.value ?: 0.0)
    }

    private fun TimedValue.toPoint(label: DateTimeFormatter): EnergyPoint {
        val text = runCatching { LocalDateTime.parse(ts, TS).format(label) }.getOrDefault(ts)
        return EnergyPoint(text, value ?: 0.0)
    }

    private fun Map<String, KeyValue>.number(key: String): Double? = this[key]?.number

    /** "R$24715.68" → 24715.68 */
    private fun Map<String, KeyValue>.money(key: String): Double? =
        this[key]?.text?.filter { it.isDigit() || it == '.' || it == '-' }?.toDoubleOrNull()

    /** Erros de dados opcionais não derrubam o painel inteiro. */
    private suspend fun <T> optional(block: suspend () -> T): T? =
        try { block() } catch (e: ShineMonitorException) { if (e.code in AUTH_CODES) throw e else null }

    private inline fun <T> orEmpty(block: () -> List<T>): List<T> =
        try { block() } catch (e: ShineMonitorException) { if (e.isNoRecord) emptyList() else throw e }

    private companion object {
        const val BRASILIA_OFFSET = -3 * 3600
        val AUTH_CODES = ShineMonitorException.AUTH_ERRORS
        val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd")
        val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MM")
        val YEAR_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")

        val FIELD_LABELS = mapOf(
            "output_power" to "Potência ativa",
            "total_output_power" to "Potência DC",
            "inverter_temperature" to "Temperatura do inversor",
            "pv_voltage1" to "Tensão FV 1", "pv_voltage2" to "Tensão FV 2",
            "pv_voltage3" to "Tensão FV 3", "pv_voltage4" to "Tensão FV 4",
            "pv_current1" to "Corrente FV 1", "pv_current2" to "Corrente FV 2",
            "pv_current3" to "Corrente FV 3", "pv_current4" to "Corrente FV 4",
            "phase_voltage_r" to "Tensão da rede (A)", "phase_voltage_s" to "Tensão da rede (B)",
            "phase_voltage_t" to "Tensão da rede (C)",
            "phase_current_r" to "Corrente da rede (A)", "phase_current_s" to "Corrente da rede (B)",
            "phase_current_t" to "Corrente da rede (C)",
            "grid_frequency" to "Frequência da rede",
            "bus_voltage" to "Tensão do barramento DC", "bus_half_voltage" to "Meia tensão do barramento",
            "apparent_power_value" to "Potência aparente",
            "energy_today" to "Energia do dia", "energy_total" to "Energia total",
            "current_year_generating_capacity" to "Energia do ano",
        )
    }
}
