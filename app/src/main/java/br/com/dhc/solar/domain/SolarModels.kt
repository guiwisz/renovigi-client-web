package br.com.dhc.solar.domain

import kotlinx.serialization.Serializable

enum class InverterStatus { ONLINE, OFFLINE, WARNING, ATTENTION, UNKNOWN;

    companion object {
        /** Códigos PLANT_STATUS_* da ShineMonitor. */
        fun fromCode(code: Int): InverterStatus = when (code) {
            0x00 -> ONLINE
            0x01 -> OFFLINE
            0x04 -> WARNING
            0x07 -> ATTENTION
            else -> UNKNOWN
        }
    }
}

/** Uma amostra da curva do dia: minuto desde 00:00 (fuso da usina) e potência em kW. */
@Serializable
data class PowerSample(val minuteOfDay: Int, val kw: Double)

/** Um ponto de série histórica (dia do mês, mês do ano ou ano) com energia em kWh. */
@Serializable
data class EnergyPoint(val label: String, val kwh: Double)

/** Tudo o que a tela inicial e os widgets precisam, num único snapshot serializável. */
@Serializable
data class PlantSnapshot(
    val plantId: Long,
    val plantName: String,
    val status: InverterStatus,
    val nominalPowerKw: Double?,
    val currentPowerKw: Double,
    val todayKwh: Double,
    val monthKwh: Double,
    val yearKwh: Double,
    val totalKwh: Double,
    val todayCurve: List<PowerSample>,
    val co2KgPerKwh: Double?,
    val updatedAtMillis: Long,
    /** Tarifa configurada na usina (R$/kWh), gravada no servidor. */
    val tariffPerKwh: Double? = null,
    /** Receita acumulada calculada pelo servidor (R$). */
    val proceedsTotal: Double? = null,
    val co2TotalKg: Double? = null,
    val coalTotalKg: Double? = null,
    val installDate: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val utcOffsetSeconds: Int = -3 * 3600,
    /** Horário da última leitura do inversor ("yyyy-MM-dd HH:mm:ss", fuso da usina). */
    val lastReadingAt: String? = null,
    val inverter: InverterLive? = null,
    val collector: CollectorInfo? = null,
    val runningMinutes: Long? = null,
    val recentAlarms: List<Alarm> = emptyList(),
    /** Energia por dia do mês corrente (para média e "dia típico"). */
    val monthDays: List<EnergyPoint> = emptyList(),
    /** Média dos últimos 28 dias fechados (kWh). */
    val averageDayKwh: Double? = null,
    /** Semana corrente (domingo até hoje) e média das 4 semanas completas anteriores. */
    val weekKwh: Double? = null,
    val typicalWeekKwh: Double? = null,
    /** Média dos últimos 12 meses fechados. */
    val typicalMonthKwh: Double? = null,
    /** Dados fictícios do modo demonstração. */
    val isDemo: Boolean = false,
) {
    fun savings(tariffPerKwh: Double, kwh: Double = todayKwh): Double = kwh * tariffPerKwh

    /** Dia típico: média dos últimos 28 dias; sem ela, a dos dias já fechados do mês. */
    val typicalDayKwh: Double?
        get() {
            averageDayKwh?.let { return it }
            val closed = monthDays.dropLast(1).map { it.kwh }.filter { it > 0 }
            return if (closed.isEmpty()) null else closed.average()
        }
}
