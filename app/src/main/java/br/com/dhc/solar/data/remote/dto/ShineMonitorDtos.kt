package br.com.dhc.solar.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/*
 * Respostas da ShineMonitor: {"err": 0, "desc": "ERR_NONE", "dat": {...}}.
 * Valores numéricos costumam vir como string ("29.0001"), por isso o
 * FlexibleDouble. Unidades padronizadas pela API: potência em kW, energia em kWh.
 */

@Serializable
data class Envelope(
    val err: Int,
    val desc: String = "",
    val dat: JsonElement? = null,
)

@Serializable
data class AuthDat(
    val token: String,
    val secret: String,
    /** Validade do token em segundos. */
    val expire: Long,
    val role: Int? = null,
)

@Serializable
data class PlantsDat(
    val total: Int = 0,
    val page: Int = 0,
    val pagesize: Int = 0,
    val plant: List<PlantDto> = emptyList(),
)

@Serializable
data class PlantDto(
    val pid: Long,
    val uid: Long? = null,
    val name: String = "",
    /** 0 normal, 1 offline, 4 alarme, 7 atenção. */
    val status: Int = 0,
    val address: AddressDto? = null,
    val profit: ProfitDto? = null,
    @Serializable(FlexibleDouble::class) val nominalPower: Double? = null,
    @Serializable(FlexibleDouble::class) val energyYearEstimate: Double? = null,
    val designCompany: String? = null,
    val install: String? = null,
)

@Serializable
data class AddressDto(
    val city: String? = null,
    val province: String? = null,
    val country: String? = null,
    /** Fuso horário da usina em segundos (ex.: -10800 para Brasília). */
    val timezone: Int? = null,
    @Serializable(FlexibleDouble::class) val lat: Double? = null,
    @Serializable(FlexibleDouble::class) val lon: Double? = null,
)

@Serializable
data class ProfitDto(
    /** Receita por kWh gerado, configurada na usina. */
    @Serializable(FlexibleDouble::class) val unitProfit: Double? = null,
    val currency: String? = null,
    val currencyCountry: String? = null,
    /** kg evitados por kWh. */
    @Serializable(FlexibleDouble::class) val co2: Double? = null,
    @Serializable(FlexibleDouble::class) val coal: Double? = null,
)

@Serializable
data class CurrentPowerDat(
    @Serializable(FlexibleDouble::class) val outputPower: Double? = null,
)

@Serializable
data class EnergyDat(
    @Serializable(FlexibleDouble::class) val energy: Double? = null,
    val unit: String? = null,
)

@Serializable
data class TimedValue(
    @SerialName("val") @Serializable(FlexibleDouble::class) val value: Double? = null,
    /** "yyyy-MM-dd HH:mm:ss" no fuso da usina. */
    val ts: String,
)

@Serializable
data class PowerOneDayDat(val outputPower: List<TimedValue> = emptyList())

@Serializable
data class MonthPerDayDat(val perday: List<TimedValue> = emptyList())

@Serializable
data class YearPerMonthDat(val permonth: List<TimedValue> = emptyList())

@Serializable
data class TotalPerYearDat(val peryear: List<TimedValue> = emptyList())

/** Par chave/valor genérico (`plantCurrentData`, séries de gráfico). */
@Serializable
data class KeyValue(
    val key: String,
    @SerialName("val") val value: JsonElement? = null,
) {
    val text: String? get() = (value as? JsonPrimitive)?.content
    val number: Double? get() = text?.trim()?.toDoubleOrNull()
}

@Serializable
data class DevicesDat(val total: Int = 0, val device: List<DeviceDto> = emptyList())

@Serializable
data class DeviceDto(
    val pn: String,
    val sn: String,
    val devcode: Int,
    val devaddr: Int,
    val status: Int = 0,
)

/** Um parâmetro de `queryDeviceLastData`: título em inglês, unidade e valor. */
@Serializable
data class DeviceParamDto(
    val title: String,
    val unit: String? = null,
    @SerialName("val") val value: String? = null,
)

@Serializable
data class WarningsDat(val total: Int = 0, val warning: List<WarningDto> = emptyList())

@Serializable
data class WarningDto(
    val id: String,
    val code: String = "",
    val desc: String = "",
    val level: Int = 0,
    /** true enquanto o alarme está ativo. */
    val status: Boolean = false,
    val handle: Boolean = false,
    /** Início ("yyyy-MM-dd HH:mm:ss"). */
    val gts: String = "",
    /** Fim, quando já encerrado. */
    val cts: String? = null,
)

@Serializable
data class CollectorsDat(val collector: List<CollectorDto> = emptyList())

@Serializable
data class CollectorDto(
    val pn: String = "",
    /** Intervalo de envio de dados, em segundos. */
    val datFetch: Int? = null,
    val status: Int = 0,
    val fireware: String? = null,
)

@Serializable
data class ChartFieldDto(
    val optional: String,
    val name: String = "",
    val uint: String? = null,
)

@Serializable
data class ChartDataDat(val date: List<ChartSeriesDto> = emptyList())

@Serializable
data class ChartSeriesDto(
    val par: String,
    @SerialName("paramter") val points: List<KeyValue> = emptyList(),
)

@Serializable
data class CompareDayDto(
    val unit: String? = null,
    val date: String,
    @SerialName("dat") val points: List<KeyValue> = emptyList(),
)

@Serializable
data class RunningTimeDat(val minutes: Long = 0)

/** Aceita número, string numérica ou string vazia. */
object FlexibleDouble : KSerializer<Double?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return (element as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}
