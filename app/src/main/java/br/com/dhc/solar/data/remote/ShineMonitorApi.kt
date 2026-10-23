package br.com.dhc.solar.data.remote

import android.util.Log
import br.com.dhc.solar.data.remote.ShineMonitorSigner.LoginMethod
import br.com.dhc.solar.data.remote.dto.AuthDat
import br.com.dhc.solar.data.remote.dto.ChartDataDat
import br.com.dhc.solar.data.remote.dto.ChartFieldDto
import br.com.dhc.solar.data.remote.dto.ChartSeriesDto
import br.com.dhc.solar.data.remote.dto.CollectorDto
import br.com.dhc.solar.data.remote.dto.CollectorsDat
import br.com.dhc.solar.data.remote.dto.CompareDayDto
import br.com.dhc.solar.data.remote.dto.CurrentPowerDat
import br.com.dhc.solar.data.remote.dto.DeviceDto
import br.com.dhc.solar.data.remote.dto.DeviceParamDto
import br.com.dhc.solar.data.remote.dto.DevicesDat
import br.com.dhc.solar.data.remote.dto.EnergyDat
import br.com.dhc.solar.data.remote.dto.Envelope
import br.com.dhc.solar.data.remote.dto.KeyValue
import br.com.dhc.solar.data.remote.dto.MonthPerDayDat
import br.com.dhc.solar.data.remote.dto.PlantDto
import br.com.dhc.solar.data.remote.dto.PlantsDat
import br.com.dhc.solar.data.remote.dto.PowerOneDayDat
import br.com.dhc.solar.data.remote.dto.RunningTimeDat
import br.com.dhc.solar.data.remote.dto.TotalPerYearDat
import br.com.dhc.solar.data.remote.dto.WarningsDat
import br.com.dhc.solar.data.remote.dto.YearPerMonthDat
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Token emitido no login. Expira; o [SessionManager] cuida de renovar.
 *
 * A forma de login define o contexto das chamadas seguintes: contas
 * acessadas pelo login do portal (`auth`/`authEmail`) só enxergam a usina
 * quando as requisições vão *sem* o sufixo `_app_id_` do RenoClient.
 */
data class Session(
    val token: String,
    val secret: String,
    val expiresAtMillis: Long,
    val method: LoginMethod = LoginMethod.APP_SOURCE,
)

/**
 * Cliente de baixo nível da API ShineMonitor (mesmo backend do RenoClient).
 *
 * Usa os endpoints de **usina** (capítulo 3 da documentação oficial), que já
 * entregam potência em kW e energia em kWh agregadas, sem depender do layout
 * de campos específico de cada modelo de inversor.
 */
class ShineMonitorApi(
    private val httpClient: OkHttpClient,
    private val profile: AppProfile = AppProfile.RENOCLIENT,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Última forma de login que funcionou; tentada primeiro nas próximas vezes. */
    @Volatile
    private var preferredLogin: LoginMethod? = null

    /**
     * Tenta as formas de login em ordem. Só passa para a próxima quando o
     * servidor diz "usuário não encontrado"; senha errada encerra na hora.
     */
    suspend fun login(username: String, passwordSha1: String): Session {
        val methods = listOfNotNull(preferredLogin) + LoginMethod.entries.filter { it != preferredLogin }
        var notFound: ShineMonitorException? = null
        for (method in methods) {
            val query = ShineMonitorSigner.loginQuery(profile, username, passwordSha1, salt(), method)
            val dat = try {
                get(query, AuthDat.serializer())
            } catch (e: ShineMonitorException) {
                if (e.code != ShineMonitorException.ERR_NOT_FOUND_USR) throw e
                notFound = e
                continue
            }
            preferredLogin = method
            Log.i(TAG, "Login aceito via $method")
            return Session(dat.token, dat.secret, expiresAtMillis = clock() + dat.expire * 1000, method = method)
        }
        throw notFound ?: IllegalStateException("Nenhuma forma de login disponível")
    }

    suspend fun plants(session: Session): List<PlantDto> =
        authed(session, "queryPlants", PlantsDat.serializer(), "page" to 0, "pagesize" to 50).plant

    suspend fun plantInfo(session: Session, plantId: Long): PlantDto =
        authed(session, "queryPlantInfo", PlantDto.serializer(), "plantid" to plantId)

    /** Potência ativa atual da usina, em kW. */
    suspend fun currentPower(session: Session, plantId: Long): Double? =
        authed(session, "queryPlantActiveOuputPowerCurrent", CurrentPowerDat.serializer(), "plantid" to plantId)
            .outputPower

    /** Curva do dia, uma amostra a cada 5 minutos (kW). */
    suspend fun powerOneDay(session: Session, plantId: Long, day: LocalDate): PowerOneDayDat =
        authed(
            session, "queryPlantActiveOuputPowerOneDay", PowerOneDayDat.serializer(),
            "plantid" to plantId, "date" to day.format(ISO_DAY),
        )

    suspend fun energyDay(session: Session, plantId: Long, day: LocalDate): Double? =
        energy(session, "queryPlantEnergyDay", plantId, day.format(ISO_DAY))

    suspend fun energyMonth(session: Session, plantId: Long, month: YearMonth): Double? =
        energy(session, "queryPlantEnergyMonth", plantId, month.format(ISO_MONTH))

    suspend fun energyYear(session: Session, plantId: Long, year: Int): Double? =
        energy(session, "queryPlantEnergyYear", plantId, year.toString())

    suspend fun energyTotal(session: Session, plantId: Long): Double? =
        authed(session, "queryPlantEnergyTotal", EnergyDat.serializer(), "plantid" to plantId).energy

    suspend fun energyPerDay(session: Session, plantId: Long, month: YearMonth): MonthPerDayDat =
        authed(
            session, "queryPlantEnergyMonthPerDay", MonthPerDayDat.serializer(),
            "plantid" to plantId, "date" to month.format(ISO_MONTH),
        )

    suspend fun energyPerMonth(session: Session, plantId: Long, year: Int): YearPerMonthDat =
        authed(
            session, "queryPlantEnergyYearPerMonth", YearPerMonthDat.serializer(),
            "plantid" to plantId, "date" to year.toString(),
        )

    suspend fun energyPerYear(session: Session, plantId: Long): TotalPerYearDat =
        authed(session, "queryPlantEnergyTotalPerYear", TotalPerYearDat.serializer(), "plantid" to plantId)

    /**
     * Totais da usina num único pedido (o mesmo que o portal usa):
     * ENERGY_TODAY/MONTH/YEAR/TOTAL (kWh), CURRENT_POWER (kW), ENERGY_PROCEEDS
     * ("R$24715.68"), ENERGY_CO2/COAL/SO2 (kg).
     */
    suspend fun plantCurrentData(session: Session, plantId: Long): Map<String, KeyValue> =
        authed(
            session, "queryPlantCurrentData", ListSerializer(KeyValue.serializer()),
            "plantid" to plantId, "par" to CURRENT_DATA_KEYS,
        ).associateBy { it.key }

    suspend fun devices(session: Session): List<DeviceDto> =
        authed(session, "queryDevices", DevicesDat.serializer(), "page" to 0, "pagesize" to 50).device

    /** Os ~30 parâmetros ao vivo do inversor (títulos em inglês, valores em string). */
    suspend fun deviceLastData(session: Session, device: DeviceDto): List<DeviceParamDto> =
        authed(session, "queryDeviceLastData", ListSerializer(DeviceParamDto.serializer()), *device.params())

    suspend fun deviceWarnings(
        session: Session,
        device: DeviceDto,
        from: LocalDate,
        to: LocalDate,
        page: Int = 0,
        pageSize: Int = 50,
    ): WarningsDat = authed(
        session, "queryDeviceWarning", WarningsDat.serializer(), *device.params(),
        "sdate" to "${from.format(ISO_DAY)}%2000:00:00", "edate" to "${to.format(ISO_DAY)}%2023:59:59",
        "page" to page, "pagesize" to pageSize,
    )

    suspend fun collector(session: Session, pn: String): CollectorDto? =
        authed(session, "queryCollectorInfo", CollectorsDat.serializer(), "pn" to pn).collector.firstOrNull()

    /** Minutos de operação contínua sem falhas. */
    suspend fun runningMinutes(session: Session, plantId: Long): Long =
        authed(session, "queryPlantRunningNormalTime", RunningTimeDat.serializer(), "plantid" to plantId).minutes

    /** Parâmetros do inversor que têm histórico (id, nome em inglês, unidade). */
    suspend fun chartFields(session: Session, plantId: Long, device: DeviceDto): List<ChartFieldDto> =
        authed(
            session, "queryPlantDeviceChartsFieldsByType", ListSerializer(ChartFieldDto.serializer()),
            "plantid" to plantId, "devtype" to 512, "devcode" to device.devcode, "type" to 1,
        )

    /** Série de um ou mais parâmetros num dia (uma amostra a cada 5 min, 288 por dia). */
    suspend fun chartData(session: Session, device: DeviceDto, fields: List<String>, day: LocalDate): List<ChartSeriesDto> =
        authed(
            session, "queryDeviceChartFieldsDat", ChartDataDat.serializer(), *device.params(),
            "field" to fields.joinToString(","), "precision" to 5,
            "sdate" to "${day.format(ISO_DAY)}%2000:00:00", "edate" to "${day.format(ISO_DAY)}%2023:59:59",
        ).date

    /** Mesmo parâmetro em vários dias, das 03h às 20h, para comparação. */
    suspend fun compareDays(session: Session, device: DeviceDto, field: String, days: List<LocalDate>): List<CompareDayDto> =
        authed(
            session, "queryPlantSameDeviceParManyDay", ListSerializer(CompareDayDto.serializer()),
            "stime" to "03:00:00", "etime" to "20:00:00", "chart_field" to field,
            "pn" to device.pn, "devcode" to device.devcode, "sn" to ShineMonitorSigner.encodeURIComponent(device.sn),
            "dates" to days.joinToString(",") { it.format(ISO_DAY) },
        )

    /**
     * Altera dados da usina no servidor (edição parcial: só os campos enviados
     * mudam). Usado para tarifa (`profit.unitProfit`) e nome (`name`).
     * Escrita: não passa pela repetição automática.
     */
    suspend fun editPlant(session: Session, plantId: Long, vararg fields: Pair<String, String>) {
        val params = listOf<Pair<String, Any>>("plantid" to plantId) +
            fields.map { (key, value) -> key to ShineMonitorSigner.encodeURIComponent(value) }
        val suffix = if (session.method == LoginMethod.APP_SOURCE) profile.suffixContext else ""
        val baseAction = ShineMonitorSigner.baseAction("editPlant", params, suffix)
        val query = ShineMonitorSigner.authedQuery(session.token, session.secret, baseAction, salt())
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(baseUrl + query).header(RetryInterceptor.NO_RETRY, "1").get().build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            val envelope = json.decodeFromString(Envelope.serializer(), body)
            // 0x010F = "nada mudou" (valor igual ao atual): não é erro para nós.
            if (envelope.err != 0 && envelope.err != ERR_NONE_OPERATION) {
                throw ShineMonitorException.from(envelope.err, envelope.desc)
            }
        }
    }

    private fun DeviceDto.params(): Array<Pair<String, Any>> = arrayOf(
        "pn" to pn, "devcode" to devcode, "devaddr" to devaddr, "sn" to ShineMonitorSigner.encodeURIComponent(sn),
    )

    private suspend fun energy(session: Session, action: String, plantId: Long, date: String): Double? =
        authed(session, action, EnergyDat.serializer(), "plantid" to plantId, "date" to date).energy

    private suspend fun <T> authed(
        session: Session,
        action: String,
        deserializer: DeserializationStrategy<T>,
        vararg params: Pair<String, Any>,
    ): T {
        val suffix = if (session.method == LoginMethod.APP_SOURCE) profile.suffixContext else ""
        val baseAction = ShineMonitorSigner.baseAction(action, params.toList(), suffix)
        val query = ShineMonitorSigner.authedQuery(session.token, session.secret, baseAction, salt())
        return get(query, deserializer)
    }

    private suspend fun <T> get(query: String, deserializer: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(baseUrl + query).get().build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            val envelope = json.decodeFromString(Envelope.serializer(), body)
            if (envelope.err != 0) throw ShineMonitorException.from(envelope.err, envelope.desc)
            val dat = envelope.dat ?: throw IOException("Resposta sem 'dat'")
            json.decodeFromJsonElement(deserializer, dat)
        }

    /** Resposta crua (envelope inteiro), para exploração de endpoints ainda não modelados. */
    suspend fun raw(session: Session, action: String, vararg params: Pair<String, Any>): String {
        val suffix = if (session.method == LoginMethod.APP_SOURCE) profile.suffixContext else ""
        val baseAction = ShineMonitorSigner.baseAction(action, params.toList(), suffix)
        val query = ShineMonitorSigner.authedQuery(session.token, session.secret, baseAction, salt())
        return withContext(Dispatchers.IO) {
            httpClient.newCall(Request.Builder().url(baseUrl + query).get().build()).execute().use { it.body.string() }
        }
    }

    private fun salt(): String = clock().toString()

    companion object {
        const val DEFAULT_BASE_URL = "https://android.shinemonitor.com/public/"
        private const val ERR_NONE_OPERATION = 0x010F
        private const val CURRENT_DATA_KEYS = "ENERGY_TODAY,ENERGY_MONTH,ENERGY_YEAR,ENERGY_TOTAL,ENERGY_PROCEEDS," +
            "ENERGY_CO2,ENERGY_COAL,ENERGY_SO2,CURRENT_POWER"
        private const val TAG = "Solar"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE
        private val ISO_MONTH = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
