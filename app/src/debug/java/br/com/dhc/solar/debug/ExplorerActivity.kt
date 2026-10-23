package br.com.dhc.solar.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import br.com.dhc.solar.container
import br.com.dhc.solar.data.remote.ShineMonitorApi
import br.com.dhc.solar.data.remote.ShineMonitorSigner.encodeURIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.time.LocalDate

/**
 * Explorador de endpoints (só debug). Usa a sessão salva, chama os endpoints
 * que o portal usa para donos de usina e grava as respostas *mascaradas* em
 * Android/data/br.com.dhc.solar/files/explorer/. Disparo:
 *   adb shell am start -n br.com.dhc.solar/.debug.ExplorerActivity
 */
class ExplorerActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = File(getExternalFilesDir(null), "explorer").apply { deleteRecursively(); mkdirs() }
        val app = applicationContext
        scope.launch {
            runCatching { explore(app.container.sessions, app.container.api, out) }
                .onFailure { Log.e(TAG, "explorer falhou", it) }
            Log.i(TAG, "explorer concluído: ${out.listFiles()?.size} arquivos")
            runOnUiThread { finish() }
        }
    }

    private suspend fun explore(sessions: br.com.dhc.solar.data.auth.SessionManager, api: ShineMonitorApi, out: File) =
        sessions.withSession { s ->
            fun save(name: String, body: String) {
                val masked = runCatching { mask(ShineMonitorApi.json.parseToJsonElement(body)).toString() }.getOrDefault(body)
                File(out, "$name.json").writeText(masked)
            }
            suspend fun call(name: String, action: String, vararg p: Pair<String, Any>): String =
                api.raw(s, action, *p).also { save(name, it) }

            val devices = ShineMonitorApi.json.parseToJsonElement(call("webQueryDeviceEs", "webQueryDeviceEs"))
            val dev = devices.jsonObject["dat"]!!.jsonObject["device"]!!.jsonArray.first().jsonObject
            val pid = dev["pid"]!!.jsonPrimitive.long
            val pn = dev["pn"]!!.jsonPrimitive.content
            val sn = encodeURIComponent(dev["sn"]!!.jsonPrimitive.content)
            val devcode = dev["devcode"]!!.jsonPrimitive.int
            val devaddr = dev["devaddr"]!!.jsonPrimitive.int
            val d = arrayOf("pn" to pn, "devcode" to devcode, "devaddr" to devaddr, "sn" to sn)
            val today = LocalDate.now().toString()
            val year = LocalDate.now().year
            val ago = LocalDate.now().minusDays(180).toString()
            val pars = "ENERGY_TODAY,ENERGY_MONTH,ENERGY_YEAR,ENERGY_TOTAL,ENERGY_PROCEEDS,ENERGY_CO2,CURRENT_TEMP," +
                "CURRENT_RADIANT,BATTERY_SOC,ENERGY_COAL,ENERGY_SO2,CURRENT_POWER"

            val calls: List<Pair<String, suspend () -> Unit>> = listOf(
                "accountInfo" to { call("queryAccountInfo", "queryAccountInfo") },
                "plantInfo" to { call("queryPlantInfo", "queryPlantInfo", "plantid" to pid) },
                "plantCurrent" to { call("queryPlantCurrentData", "queryPlantCurrentData", "plantid" to pid, "par" to pars) },
                "plantCurrent2" to { call("plantCurrentData", "plantCurrentData", "id" to pid, "par" to pars) },
                "webQueryDevices" to { call("webQueryDevices", "webQueryDevices", "plantid" to pid, "devcode" to devcode, "page" to 0, "pagesize" to 50) },
                "deviceInfo" to { call("queryDeviceInfo", "queryDeviceInfo", *d) },
                "deviceLast" to { call("queryDeviceLastData", "queryDeviceLastData", *d) },
                "deviceLastSP" to { call("querySPDeviceLastData", "querySPDeviceLastData", *d) },
                "deviceStatus" to { call("queryDeviceStatus", "queryDeviceStatus", *d) },
                "deviceDayPaging" to { call("queryDeviceDataOneDayPaging", "queryDeviceDataOneDayPaging", *d, "date" to today, "page" to 0, "pagesize" to 3) },
                "deviceKeyParDay" to { call("queryDeviceKeyParameterOneDay", "queryDeviceKeyParameterOneDay", *d, "date" to today) },
                "devicePowerDay" to { call("queryDeviceActiveOuputPowerOneDay", "queryDeviceActiveOuputPowerOneDay", *d, "date" to today) },
                "deviceEnergyPerDay" to { call("queryDeviceEnergyMonthPerDay", "queryDeviceEnergyMonthPerDay", *d, "date" to today.substring(0, 7)) },
                "deviceWarning" to { call("queryDeviceWarning", "queryDeviceWarning", *d, "sdate" to "$ago%2000:00:00", "edate" to "$today%2023:59:59", "page" to 0, "pagesize" to 20) },
                "plantsWarning" to { call("webQueryPlantsWarning", "webQueryPlantsWarning", "sdate" to "$ago%2000:00:00", "edate" to "$today%2023:59:59", "page" to 0, "pagesize" to 20) },
                "deviceCtrlField" to { call("queryDeviceCtrlField", "queryDeviceCtrlField", *d) },
                "collectorInfo" to { call("queryCollectorInfo", "queryCollectorInfo", "pn" to pn) },
                "collectorStatus" to { call("queryCollectorStatus", "queryCollectorStatus", "pn" to pn) },
                "plantDeviceStatus" to { call("queryPlantDeviceStatus", "queryPlantDeviceStatus", "plantid" to pid) },
                "weather" to { call("getWeatherByPlant", "getWeatherByPlant", "id" to pid) },
                "electricMeter" to { call("queryPlantElectricmeter", "queryPlantElectricmeter", "pid" to pid) },
                "chartFields" to { call("queryPlantDeviceChartsFields", "queryPlantDeviceChartsFields", "plantid" to pid, "devtype" to 512, "pns" to pn, "devcodes" to devcode) },
                "inverterInfo" to { call("queryPlantDeviceDesignatedInformation", "queryPlantDeviceDesignatedInformation", "plantid" to pid, "devtype" to 512, "parameter" to "energy_today,energy_total") },
                "pvCharts" to { call("queryTodayDevicePvCharts", "queryTodayDevicePvCharts", "plantid" to pid, "pns" to pn, "sort" to 0, "devcodes" to devcode, "sns" to sn, "devaddrs" to devaddr) },
                "runningTime" to { call("queryPlantRunningNormalTime", "queryPlantRunningNormalTime", "plantid" to pid) },
                "yearPerMonth" to { call("queryPlantEnergyYearPerMonth", "queryPlantEnergyYearPerMonth", "plantid" to pid, "date" to year) },
                "totalPerYear" to { call("queryPlantEnergyTotalPerYear", "queryPlantEnergyTotalPerYear", "plantid" to pid) },
            )
            val yesterday = LocalDate.now().minusDays(1).toString()
            val extra: List<Pair<String, suspend () -> Unit>> = listOf(
                "chartFieldsDat" to {
                    call("queryDeviceChartFieldsDat", "queryDeviceChartFieldsDat", *d, "field" to "inverter_temperature,pv_voltage1,output_power",
                        "precision" to 5, "sdate" to "$today%2000:00:00", "edate" to "$today%2023:59:59")
                },
                "sameParManyDay" to {
                    call("queryPlantSameDeviceParManyDay", "queryPlantSameDeviceParManyDay", "stime" to "03:00:00", "etime" to "20:00:00",
                        "chart_field" to "output_power", "pn" to pn, "devcode" to devcode, "sn" to sn, "dates" to "$yesterday,$today")
                },
                "chartFieldsByType" to {
                    call("queryPlantDeviceChartsFieldsByType", "queryPlantDeviceChartsFieldsByType", "plantid" to pid, "devtype" to 512, "devcode" to devcode, "type" to 1)
                },
            )
            for ((name, block) in calls + extra) {
                runCatching { block() }.onFailure { File(out, "$name.error.txt").writeText(it.toString()) }
            }
            // Tabela completa do dia: guarda só cabeçalho + 3 linhas para ver as colunas.
            runCatching {
                val day = ShineMonitorApi.json.parseToJsonElement(api.raw(s, "queryDeviceDataOneDay", *d, "date" to today)).jsonObject
                val dat = day["dat"]?.jsonObject
                val rows = dat?.get("row")?.jsonArray
                val trimmed = if (dat != null && rows != null) {
                    JsonObject(day + ("dat" to JsonObject(dat + ("row" to JsonArray(rows.take(1) + rows.takeLast(2))) + ("rowCount" to JsonPrimitive(rows.size)))))
                } else day
                save("queryDeviceDataOneDay", trimmed.toString())
            }
        }

    /** Mascara identificadores pessoais (série, conta, contato, localização). */
    private fun mask(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> {
            val title = (e["title"] as? JsonPrimitive)?.content.orEmpty()
            JsonObject(e.mapValues { (k, v) ->
                when {
                    SENSITIVE_KEY.matches(k) -> JsonPrimitive("***")
                    k == "val" && SENSITIVE_TITLE.containsMatchIn(title) -> JsonPrimitive("***")
                    else -> mask(v)
                }
            })
        }
        is JsonArray -> JsonArray(e.map(::mask))
        // Números de série também aparecem soltos (linhas de tabela, chaves de gráfico).
        is JsonPrimitive -> if (e.isString && SERIAL_VALUE.matches(e.content)) JsonPrimitive("***") else e
        else -> e
    }

    private companion object {
        const val TAG = "SolarExplorer"
        val SENSITIVE_KEY = Regex("(?i)^(sn|pn|sns|pns|usr|email|mobile|phone|uid|address|lon|lat|devalias|collalias|alias|calias|cpn|village|town|county|wifiName|ssid|ip|mac|token|secret|picBig|picSmall)$")
        /** Sequências longas de dígitos/letras maiúsculas (formato de SN/PN), exceto ids hex de registro e datas. */
        val SERIAL_VALUE = Regex("^(?=.*\\d)[A-Z0-9]{12,20}$")
        val SENSITIVE_TITLE = Regex("(?i)serial|\\bsn\\b|\\bpn\\b|序列|IP|MAC|SSID")
    }
}
