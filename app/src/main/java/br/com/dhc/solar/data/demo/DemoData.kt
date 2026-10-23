package br.com.dhc.solar.data.demo

import br.com.dhc.solar.data.repository.ChartField
import br.com.dhc.solar.data.repository.SeriesPoint
import br.com.dhc.solar.domain.Alarm
import br.com.dhc.solar.domain.CollectorInfo
import br.com.dhc.solar.domain.EnergyPoint
import br.com.dhc.solar.domain.InverterLive
import br.com.dhc.solar.domain.InverterStatus
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.domain.PowerSample
import br.com.dhc.solar.domain.PvString
import br.com.dhc.solar.domain.SolarClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Usina fictícia para o modo demonstração (e para os prints do projeto).
 *
 * Os dados são determinísticos por dia (o mesmo dia gera sempre os mesmos
 * números) e coerentes entre si: a curva segue o sol de Brasília, os totais
 * são a soma dos dias e a leitura do inversor bate com a potência atual.
 */
object DemoData {
    const val PLANT_NAME = "Usina demonstração"
    const val DEFAULT_TARIFF = 0.95
    private const val NOMINAL_KW = 6.6
    private const val LAT = -15.79

    /** Fuso do celular, com o sol posicionado para meio-dia local (coerente em qualquer lugar). */
    private val OFFSET: Int = java.time.ZonedDateTime.now().offset.totalSeconds
    private val LON: Double = OFFSET / 3600.0 * 15
    private val INSTALLED: LocalDate = LocalDate.of(2023, 3, 14)
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(): LocalDateTime = LocalDateTime.now(ZoneOffset.ofTotalSeconds(OFFSET))

    /** Energia de um dia inteiro: sazonal (mais no verão) com variação de nuvens. */
    fun dayKwh(date: LocalDate): Double {
        if (date < INSTALLED) return 0.0
        val seasonal = 30.0 + 4.5 * cos(2 * PI * (date.dayOfYear - 15) / 365.0)
        val random = Random(date.toEpochDay())
        // O dia de hoje é sempre bonito (a demo serve para apresentar o app); dias passados variam.
        val today = date == now().toLocalDate()
        val weather = if (!today && random.nextDouble() < 0.18) random.nextDouble(0.35, 0.7) else random.nextDouble(0.9, 1.04)
        return seasonal * weather
    }

    /** Curva do dia (amostras de 5 min) até [untilMinute], com quedas de nuvem determinísticas. */
    fun curve(date: LocalDate, untilMinute: Int = 24 * 60): List<PowerSample> {
        val sun = SolarClock.sunTimes(date, LAT, LON, OFFSET)
        val clear = (sun.sunrise.toInt() / 5 * 5..sun.sunset.toInt() step 5).map { m ->
            m to SolarClock.clearSkyPowerKw(m.toDouble(), sun, NOMINAL_KW)
        }
        val clearKwh = clear.sumOf { it.second } * 5 / 60
        val scale = if (clearKwh > 0) (dayKwh(date) / clearKwh).coerceAtMost(1.05) else 0.0
        val random = Random(date.toEpochDay() * 31)
        // Poucas passagens de nuvem, cada uma com profundidade própria e entrada/saída suaves.
        val clouds = List(random.nextInt(1, 4)) {
            Triple(sun.solarNoon + random.nextDouble(-200.0, 200.0), random.nextDouble(8.0, 18.0), random.nextDouble(0.2, 0.45))
        }
        return clear.filter { it.first <= untilMinute }.map { (m, kw) ->
            val dip = clouds.sumOf { (center, halfWidth, depth) ->
                val d = (m - center) / halfWidth
                if (kotlin.math.abs(d) >= 1) 0.0 else depth * (1 + cos(PI * d)) / 2
            }.coerceAtMost(0.7)
            PowerSample(m, (kw * scale * (1 - dip)).coerceAtLeast(0.0))
        }
    }

    fun snapshot(tariff: Double, name: String?): PlantSnapshot {
        val now = now()
        val today = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val reading = minute / 5 * 5
        val todayCurve = curve(today, reading)
        val todayKwh = todayCurve.sumOf { it.kw } * 5 / 60
        val currentKw = todayCurve.lastOrNull()?.takeIf { it.minuteOfDay >= reading - 5 }?.kw ?: 0.0
        val monthKwh = (1 until today.dayOfMonth).sumOf { dayKwh(today.withDayOfMonth(it)) } + todayKwh
        val yearKwh = (1 until today.dayOfYear).sumOf { dayKwh(today.withDayOfYear(it)) } + todayKwh
        val totalKwh = daysBetween(INSTALLED, today.minusDays(1)).sumOf(::dayKwh) + todayKwh
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val last28 = (1..28).map { dayKwh(today.minusDays(it.toLong())) }
        val weeks = (1..4).map { w -> (0..6L).sumOf { dayKwh(weekStart.minusWeeks(w.toLong()).plusDays(it)) } }
        val months = (1..12).map { m -> monthTotal(YearMonth.from(today).minusMonths(m.toLong())) }
        val readingAt = today.atTime(reading / 60, reading % 60, 12).format(TS)

        return PlantSnapshot(
            plantId = 0,
            plantName = name ?: PLANT_NAME,
            status = InverterStatus.ONLINE,
            nominalPowerKw = NOMINAL_KW,
            currentPowerKw = currentKw,
            todayKwh = todayKwh,
            monthKwh = monthKwh,
            yearKwh = yearKwh,
            totalKwh = totalKwh,
            todayCurve = todayCurve,
            co2KgPerKwh = 0.997,
            updatedAtMillis = System.currentTimeMillis(),
            tariffPerKwh = tariff,
            proceedsTotal = totalKwh * tariff,
            co2TotalKg = totalKwh * 0.997,
            coalTotalKg = totalKwh * 0.4,
            installDate = "${INSTALLED} 10:00:00",
            latitude = LAT,
            longitude = LON,
            utcOffsetSeconds = OFFSET,
            lastReadingAt = readingAt,
            inverter = inverter(currentKw, readingAt, todayKwh, totalKwh),
            collector = CollectorInfo(firmware = "3.94.2.6", intervalSeconds = 300, online = true),
            runningMinutes = 9 * 24 * 60 + 410,
            recentAlarms = alarms(today).take(3),
            monthDays = (1..today.dayOfMonth).map { d ->
                EnergyPoint("%02d".format(d), if (d == today.dayOfMonth) todayKwh else dayKwh(today.withDayOfMonth(d)))
            },
            averageDayKwh = last28.average(),
            weekKwh = daysBetween(weekStart, today.minusDays(1)).sumOf(::dayKwh) + todayKwh,
            typicalWeekKwh = weeks.average(),
            typicalMonthKwh = months.filter { it > 0 }.average(),
            isDemo = true,
        )
    }

    private fun inverter(acKw: Double, readingAt: String, todayKwh: Double, totalKwh: Double): InverterLive {
        val fraction = acKw / NOMINAL_KW
        val dcW = if (acKw > 0.01) acKw * 1000 / 0.976 else 0.0
        val v1 = if (dcW > 0) 292.0 + 14 * fraction else 4.1
        val v2 = if (dcW > 0) 305.0 + 12 * fraction else 3.8
        return InverterLive(
            readingAt = readingAt,
            acPowerW = acKw * 1000,
            dcPowerW = dcW,
            strings = listOf(
                PvString(1, v1, if (dcW > 0) dcW * 0.48 / v1 else 0.0),
                PvString(2, v2, if (dcW > 0) dcW * 0.52 / v2 else 0.0),
                PvString(3, 1.2, 0.0),
                PvString(4, 0.9, 0.0),
            ),
            gridVoltage = 221.4 + 4 * fraction,
            gridCurrent = acKw * 1000 / 222.0,
            gridFrequency = 60.01,
            apparentPowerKva = acKw * 1.002,
            temperatureC = 31.0 + 34 * fraction,
            busVoltage = 352.0,
            firmwareHmi = "009F",
            firmwareDsp = "0026",
            gridStandard = "BRAZIL-H",
            todayKwh = todayKwh,
            totalKwh = totalKwh,
        )
    }

    fun alarms(today: LocalDate): List<Alarm> = listOf(
        Triple(3L, "NO-Grid", 22L), Triple(11L, "UN-G-V", 14L), Triple(26L, "NO-Grid", 6L), Triple(47L, "OV-G-V", 9L),
    ).map { (daysAgo, code, minutes) ->
        val start = today.minusDays(daysAgo).atTime(16, 40)
        Alarm(
            id = "demo-$daysAgo",
            code = if (code == "NO-Grid") "0x00001015" else "0x00001011",
            rawDescription = code,
            startedAt = start.format(TS),
            endedAt = start.plusMinutes(minutes).format(TS),
        )
    }

    fun perDay(month: YearMonth): List<EnergyPoint> {
        val today = now().toLocalDate()
        return (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            EnergyPoint("%02d".format(d), if (date > today) 0.0 else dayKwh(date))
        }
    }

    fun perMonth(year: Int): List<EnergyPoint> =
        (1..12).map { m -> EnergyPoint("%02d".format(m), monthTotal(YearMonth.of(year, m))) }

    fun perYear(): List<EnergyPoint> =
        (INSTALLED.year..now().year).map { y -> EnergyPoint(y.toString(), perMonth(y).sumOf { it.kwh }) }

    fun byDay(from: LocalDate, to: LocalDate): Map<LocalDate, Double> = daysBetween(from, to).associateWith(::dayKwh)

    private fun monthTotal(month: YearMonth): Double {
        val today = now().toLocalDate()
        return (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it <= today }.sumOf(::dayKwh)
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        if (to < from) emptyList() else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()

    val chartFields = listOf(
        ChartField("output_power", "Potência ativa", "kW"),
        ChartField("inverter_temperature", "Temperatura do inversor", "°C"),
        ChartField("pv_voltage1", "Tensão FV 1", "V"),
        ChartField("pv_voltage2", "Tensão FV 2", "V"),
        ChartField("phase_voltage_t", "Tensão da rede (C)", "V"),
        ChartField("grid_frequency", "Frequência da rede", "Hz"),
    )

    /** Série de um parâmetro, derivada da curva do dia. */
    fun series(fieldId: String, date: LocalDate): List<SeriesPoint> {
        val now = now()
        val until = if (date == now.toLocalDate()) now.hour * 60 + now.minute else 24 * 60
        return curve(date, until).filter { it.kw > 0.005 }.map { s ->
            val f = s.kw / NOMINAL_KW
            val value = when (fieldId) {
                "inverter_temperature" -> 31.0 + 34 * f
                "pv_voltage1" -> 292.0 + 14 * f + 6 * sin(s.minuteOfDay / 40.0)
                "pv_voltage2" -> 305.0 + 12 * f + 5 * sin(s.minuteOfDay / 37.0)
                "phase_voltage_t" -> 221.4 + 4 * f + 1.5 * sin(s.minuteOfDay / 23.0)
                "grid_frequency" -> 60.0 + 0.02 * sin(s.minuteOfDay / 11.0)
                else -> s.kw
            }
            SeriesPoint(s.minuteOfDay, value)
        }
    }
}
