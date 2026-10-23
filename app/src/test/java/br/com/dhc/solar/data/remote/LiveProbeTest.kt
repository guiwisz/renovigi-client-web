package br.com.dhc.solar.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Sondagem contra a API real, com a sua conta do RenoClient. Só roda se as
 * variáveis SHINE_USER e SHINE_PASS existirem (veja o README). Serve para
 * conferir unidades e quais endpoints respondem para a sua usina.
 */
class LiveProbeTest {
    @Test
    fun probe() = runBlocking {
        val user = System.getenv("SHINE_USER")
        val pass = System.getenv("SHINE_PASS")
        assumeTrue("Defina SHINE_USER e SHINE_PASS para rodar", !user.isNullOrBlank() && !pass.isNullOrBlank())

        val api = ShineMonitorApi(OkHttpClient())
        val session = api.login(user!!, ShineMonitorSigner.sha1Hex(pass!!))
        println("✔ login ok (token expira em ${(session.expiresAtMillis - System.currentTimeMillis()) / 3_600_000} h)")

        val plants = api.plants(session)
        println("✔ usinas: ${plants.map { "${it.pid} '${it.name}' status=${it.status} nominal=${it.nominalPower}" }}")
        val plant = plants.firstOrNull() ?: return@runBlocking println("✘ nenhuma usina na conta")
        val id = plant.pid
        val today = LocalDate.now()

        suspend fun step(name: String, block: suspend () -> Any?) {
            val result = runCatching { block() }
            println(if (result.isSuccess) "✔ $name: ${result.getOrNull()}" else "✘ $name: ${result.exceptionOrNull()?.message}")
        }
        step("plantInfo") { api.plantInfo(session, id) }
        step("potência atual") { api.currentPower(session, id) }
        step("energia hoje") { api.energyDay(session, id, today) }
        step("energia mês") { api.energyMonth(session, id, YearMonth.from(today)) }
        step("energia ano") { api.energyYear(session, id, today.year) }
        step("energia total") { api.energyTotal(session, id) }
        step("curva hoje") {
            api.powerOneDay(session, id, today).outputPower.let { "${it.size} pontos, últimos: ${it.takeLast(3)}" }
        }
        step("por dia no mês") { api.energyPerDay(session, id, YearMonth.from(today)).perday.takeLast(3) }
        step("por mês no ano") { api.energyPerMonth(session, id, today.year).permonth.take(3) }
        step("por ano") { api.energyPerYear(session, id).peryear }
    }
}
