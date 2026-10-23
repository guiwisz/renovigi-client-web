package br.com.dhc.solar.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vetores gerados com as mesmas fórmulas do shinemonitor-api (python/shinemonitor_api/_protocol.py). */
class ShineMonitorSignerTest {
    private val profile = AppProfile.RENOCLIENT // locale pt_BR
    private val salt = "1700000000000"

    @Test
    fun `sha1 de senha bate com a referencia`() {
        assertEquals("4c01a5225713870f8ee27630d8dbc166e752ac26", ShineMonitorSigner.sha1Hex("s3nh@"))
    }

    @Test
    fun `login assina SHA1(salt + SHA1(senha) + baseAction) e troca espaco por +`() {
        val query = ShineMonitorSigner.loginQuery(profile, "joao silva", ShineMonitorSigner.sha1Hex("s3nh@"), salt)
        assertEquals(
            "?sign=7e06e44d830cfd3da9d50162da88f03a2c74ccd2&salt=1700000000000" +
                "&action=authSource&usr=joao+silva&company-key=bnrl_frRFjEz8Mkn" +
                "&i18n=pt_BR&lang=pt_BR&source=1&_app_client_=android" +
                "&_app_id_=com.eybond.renoclient&_app_version_=1.3.2.0",
            query,
        )
    }

    @Test
    fun `encodeURIComponent igual ao do navegador`() {
        assertEquals("a%2Bb%40x.com", ShineMonitorSigner.encodeURIComponent("a+b@x.com"))
        assertEquals("Jo%C3%A3o%20Silva%27s", ShineMonitorSigner.encodeURIComponent("João Silva's"))
    }

    @Test
    fun `requisicao autenticada assina SHA1(salt + secret + token + baseAction)`() {
        val baseAction = ShineMonitorSigner.baseAction("queryPlants", listOf("page" to 0, "pagesize" to 50), profile)
        val query = ShineMonitorSigner.authedQuery("tok123", "sec456", baseAction, salt)
        assertEquals(
            "?sign=6bc50ed848c3fadeab8141094ec7ec37bc503708&salt=1700000000000&token=tok123$baseAction",
            query,
        )
    }
}
