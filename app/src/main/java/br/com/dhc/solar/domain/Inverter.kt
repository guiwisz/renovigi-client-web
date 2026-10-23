package br.com.dhc.solar.domain

import kotlinx.serialization.Serializable

/** Uma entrada MPPT (string de painéis). */
@Serializable
data class PvString(val index: Int, val voltage: Double, val current: Double) {
    val powerW: Double get() = voltage * current

    /** Entradas sem painéis ligados ficam com tensão residual (poucos volts). */
    val inUse: Boolean get() = voltage >= 50.0
}

/** Leitura ao vivo do inversor (a cada 5 min), interpretada de `queryDeviceLastData`. */
@Serializable
data class InverterLive(
    val readingAt: String? = null,
    val acPowerW: Double? = null,
    val dcPowerW: Double? = null,
    val strings: List<PvString> = emptyList(),
    val gridVoltage: Double? = null,
    val gridCurrent: Double? = null,
    val gridFrequency: Double? = null,
    val apparentPowerKva: Double? = null,
    val temperatureC: Double? = null,
    val busVoltage: Double? = null,
    val firmwareHmi: String? = null,
    val firmwareDsp: String? = null,
    val gridStandard: String? = null,
    val todayKwh: Double? = null,
    val totalKwh: Double? = null,
) {
    /** Eficiência da conversão DC → AC, quando há potência suficiente para ser significativa. */
    val efficiency: Double?
        get() {
            val ac = acPowerW ?: return null
            val dc = dcPowerW ?: return null
            return if (dc > 100) (ac / dc).coerceIn(0.0, 1.0) else null
        }

    companion object {
        /** Monta a partir dos pares título/valor (títulos em inglês, como vêm da API). */
        fun fromParams(params: List<Triple<String, String?, String?>>): InverterLive {
            val byTitle = params.associate { (title, _, value) -> title.trim().lowercase() to value?.trim() }
            fun num(title: String) = byTitle[title]?.toDoubleOrNull()
            fun text(title: String) = byTitle[title]?.takeIf { it.isNotEmpty() }

            val strings = (1..8).mapNotNull { i ->
                val v = num("dc voltage $i") ?: return@mapNotNull null
                PvString(i, v, num("dc current $i") ?: 0.0)
            }
            // Inversor monofásico: só uma das três fases tem valor; pegamos a maior.
            val voltage = listOf("ab voltage/a phase voltage", "bc voltage/b phase voltage", "ca voltage/c phase voltage")
                .mapNotNull(::num).maxOrNull()
            val current = listOf("a phase current", "b phase current", "c phase current").mapNotNull(::num).maxOrNull()
            return InverterLive(
                readingAt = text("timestamp"),
                acPowerW = num("active power"),
                dcPowerW = num("dc output power"),
                strings = strings,
                gridVoltage = voltage,
                gridCurrent = current,
                gridFrequency = num("grid frequency"),
                apparentPowerKva = num("apparent power value"),
                temperatureC = num("inverter temperature"),
                busVoltage = num("dc bus voltage"),
                firmwareHmi = text("hmi version"),
                firmwareDsp = text("dsp version"),
                gridStandard = text("national standard number"),
                todayKwh = num("today energy"),
                totalKwh = num("total energy"),
            )
        }
    }
}

@Serializable
data class CollectorInfo(val firmware: String? = null, val intervalSeconds: Int? = null, val online: Boolean = true)
