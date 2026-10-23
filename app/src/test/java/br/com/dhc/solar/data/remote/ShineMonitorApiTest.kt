package br.com.dhc.solar.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ShineMonitorApiTest {
    private val server = MockWebServer()
    private lateinit var api: ShineMonitorApi
    private val session = Session("tok", "sec", Long.MAX_VALUE)

    @Before
    fun setUp() {
        server.start()
        api = ShineMonitorApi(OkHttpClient(), baseUrl = server.url("/public/").toString(), clock = { 1_000L })
    }

    @After
    fun tearDown() = server.close()

    private fun respond(json: String) = server.enqueue(MockResponse.Builder().body(json).build())

    @Test
    fun `login devolve token e calcula expiracao`() = runTest {
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"token":"t","secret":"s","expire":604800,"role":0}}""")
        val result = api.login("user", "hash")
        assertEquals(Session("t", "s", 1_000L + 604_800_000L), result)
        val url = server.takeRequest().url.toString()
        assertTrue(url.contains("action=authSource&usr=user&company-key="))
        assertTrue(url.contains("_app_id_=com.eybond.renoclient"))
    }

    @Test
    fun `usuario nao encontrado no authSource tenta o login do portal web`() = runTest {
        respond("""{"err":261,"desc":"ERR_NOT_FOUND_USR"}""")
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"token":"t","secret":"s","expire":60}}""")
        api.login("joao@mail.com", "hash")
        assertTrue(server.takeRequest().url.toString().contains("action=authSource"))
        val web = server.takeRequest().url.toString()
        assertTrue(web.contains("action=auth&usr=joao%40mail.com&company-key="))
        assertTrue(!web.contains("_app_id_"))

        // Próximo login já começa pela forma que funcionou.
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"token":"t","secret":"s","expire":60}}""")
        api.login("joao@mail.com", "hash")
        assertTrue(server.takeRequest().url.toString().contains("action=auth&"))
    }

    @Test(expected = ShineMonitorAuthException::class)
    fun `senha errada nao tenta outras formas`() = runTest {
        respond("""{"err":16,"desc":"ERR_PASSWORD_VERIF_FAIL"}""")
        try {
            api.login("user", "hash")
        } finally {
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `sessao do login web faz chamadas sem o sufixo do app`() = runTest {
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"outputPower":"1.0"}}""")
        api.currentPower(session.copy(method = ShineMonitorSigner.LoginMethod.WEB), 1)
        val url = server.takeRequest().url.toString()
        assertTrue(url.endsWith("action=queryPlantActiveOuputPowerCurrent&plantid=1"))
    }

    @Test
    fun `numeros em string sao convertidos`() = runTest {
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"outputPower":"3.4210"}}""")
        assertEquals(3.421, api.currentPower(session, 1)!!, 1e-9)
    }

    @Test
    fun `curva do dia e lida em ordem`() = runTest {
        respond(
            """{"err":0,"desc":"ERR_NONE","dat":{"outputPower":[
                {"val":"0.5","ts":"2026-10-13 06:10:00"},{"val":"1.25","ts":"2026-10-13 06:15:00"}]}}""",
        )
        val curve = api.powerOneDay(session, 1, LocalDate.of(2026, 10, 13)).outputPower
        assertEquals(listOf(0.5, 1.25), curve.map { it.value })
        assertTrue(server.takeRequest().url.toString().contains("date=2026-10-13"))
    }

    @Test
    fun `usina com campos opcionais ausentes`() = runTest {
        respond("""{"err":0,"desc":"ERR_NONE","dat":{"total":1,"plant":[{"pid":42,"name":"Casa","status":0,"nominalPower":""}]}}""")
        val plant = api.plants(session).single()
        assertEquals(42L, plant.pid)
        assertNull(plant.nominalPower)
    }

    @Test(expected = ShineMonitorAuthException::class)
    fun `token expirado vira erro de autenticacao`() = runTest {
        respond("""{"err":270,"desc":"ERR_TOKEN_INVALID"}""")
        api.currentPower(session, 1)
    }

    @Test
    fun `sem registro e identificado`() = runTest {
        respond("""{"err":12,"desc":"ERR_NO_RECORD"}""")
        val error = runCatching { api.energyDay(session, 1, LocalDate.of(2026, 10, 13)) }.exceptionOrNull()
        assertTrue(error is ShineMonitorException && error.isNoRecord)
    }
}
