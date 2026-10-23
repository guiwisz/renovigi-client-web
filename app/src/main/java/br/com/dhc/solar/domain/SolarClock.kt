package br.com.dhc.solar.domain

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Horários do sol num dia, em minutos desde 00:00 no fuso da usina. */
data class SunTimes(val sunrise: Double, val solarNoon: Double, val sunset: Double)

/**
 * Nascer/pôr do sol pela aproximação da NOAA (erro de ~1 min), a partir da
 * latitude/longitude da usina. Sem coordenadas, assume 06:00–18:00.
 */
object SolarClock {

    fun sunTimes(date: LocalDate, lat: Double?, lon: Double?, utcOffsetSeconds: Int): SunTimes {
        if (lat == null || lon == null) return SunTimes(360.0, 720.0, 1080.0)
        val gamma = 2 * PI / 365 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latRad = Math.toRadians(lat)
        val cosHa = (cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)).coerceIn(-1.0, 1.0)
        val haDeg = Math.toDegrees(acos(cosHa))
        val offsetMin = utcOffsetSeconds / 60.0
        val noon = 720 - 4 * lon - eqTime + offsetMin
        return SunTimes(sunrise = noon - 4 * haDeg, solarNoon = noon, sunset = noon + 4 * haDeg)
    }

    /**
     * Progresso da animação do ciclo do dia (0..1): 0.25 = nascer, 0.5 =
     * meio-dia solar, 0.75 = pôr do sol, com a noite dividida igualmente.
     */
    fun dayProgress(minuteOfDay: Double, sun: SunTimes): Float {
        val m = minuteOfDay
        val p = when {
            m < sun.sunrise -> 0.25 * m / sun.sunrise
            m < sun.solarNoon -> 0.25 + 0.25 * (m - sun.sunrise) / (sun.solarNoon - sun.sunrise)
            m < sun.sunset -> 0.5 + 0.25 * (m - sun.solarNoon) / (sun.sunset - sun.solarNoon)
            else -> 0.75 + 0.25 * (m - sun.sunset) / (1440 - sun.sunset)
        }
        return p.toFloat().coerceIn(0f, 1f)
    }

    /** Potência esperada com céu limpo (kW), para estimar nebulosidade. */
    fun clearSkyPowerKw(minuteOfDay: Double, sun: SunTimes, nominalKw: Double): Double {
        if (minuteOfDay <= sun.sunrise || minuteOfDay >= sun.sunset) return 0.0
        val x = (minuteOfDay - sun.sunrise) / (sun.sunset - sun.sunrise)
        return nominalKw * 0.82 * Math.pow(sin(PI * x), 1.3)
    }
}
