package br.com.dhc.solar.widget

import br.com.dhc.solar.domain.InverterStatus
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.domain.PowerSample
import br.com.dhc.solar.ui.format.Formatters
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Tudo o que os widgets mostram, já calculado (o Glance só desenha). */
data class WidgetData(
    val plantName: String,
    val updatedAt: String?,
    val online: Boolean,
    val nowKw: Double,
    val nominalKw: Double?,
    val todayKwh: Double,
    val typicalDayKwh: Double?,
    val weekKwh: Double?,
    val typicalWeekKwh: Double?,
    val monthKwh: Double,
    val typicalMonthKwh: Double?,
    val savingsToday: Double,
    val curve: List<PowerSample>,
) {
    /** Progresso em relação a um período típico (0..1+). */
    val dayFraction: Float? get() = fraction(todayKwh, typicalDayKwh)
    val weekFraction: Float? get() = fraction(weekKwh, typicalWeekKwh)
    val monthFraction: Float? get() = fraction(monthKwh, typicalMonthKwh)

    private fun fraction(value: Double?, typical: Double?): Float? =
        if (value == null || typical == null || typical <= 0) null else (value / typical).toFloat()

    companion object {
        fun from(s: PlantSnapshot, tariff: Double) = WidgetData(
            plantName = s.plantName.takeIf { it.isNotBlank() && it != "PLANT" } ?: "Minha usina",
            updatedAt = Formatters.readingTime(s.lastReadingAt) ?: Formatters.clock(s.updatedAtMillis),
            online = s.status != InverterStatus.OFFLINE,
            nowKw = s.currentPowerKw,
            nominalKw = s.nominalPowerKw,
            todayKwh = s.todayKwh,
            typicalDayKwh = s.typicalDayKwh,
            weekKwh = s.weekKwh,
            typicalWeekKwh = s.typicalWeekKwh,
            monthKwh = s.monthKwh,
            typicalMonthKwh = s.typicalMonthKwh,
            savingsToday = s.savings(tariff),
            curve = s.todayCurve,
        )

        /** Dados de exemplo para a prévia no seletor de widgets. */
        val sample = WidgetData(
            plantName = "Minha usina",
            updatedAt = "13:45",
            online = true,
            nowKw = 5.8,
            nominalKw = 8.5,
            todayKwh = 27.6,
            typicalDayKwh = 35.4,
            weekKwh = 128.0,
            typicalWeekKwh = 246.0,
            monthKwh = 490.0,
            typicalMonthKwh = 820.0,
            savingsToday = 25.7,
            curve = (66..165).map { i ->
                val minute = i * 5
                val x = (minute - 360) / 720.0
                PowerSample(minute, 6.6 * sin(PI * x.coerceIn(0.0, 1.0)).pow(1.4))
            },
        )
    }
}
