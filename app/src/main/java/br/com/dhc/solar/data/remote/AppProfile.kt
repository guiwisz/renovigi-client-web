package br.com.dhc.solar.data.remote

/**
 * Identidade de um app white-label da Eybond perante o backend ShineMonitor.
 *
 * O backend é multi-tenant: os equipamentos ficam vinculados ao app que os
 * cadastrou (campo `_app_id_`). Para enxergar a usina cadastrada no
 * RenoClient, as requisições precisam se apresentar como ele.
 */
data class AppProfile(
    val appId: String,
    val appVersion: String,
    val companyKey: String = EYBOND_COMPANY_KEY,
    val locale: String = "pt_BR",
    val source: Int = 1,
    val appClient: String = "android",
) {
    /** Sufixo fixo que todo app Eybond anexa às requisições (e que entra na assinatura). */
    val suffixContext: String
        get() = "&i18n=$locale&lang=$locale&source=$source" +
            "&_app_client_=$appClient&_app_id_=$appId&_app_version_=$appVersion"

    companion object {
        /** Chave de fabricante compartilhada pelos apps WatchPower/RenoClient. */
        const val EYBOND_COMPANY_KEY = "bnrl_frRFjEz8Mkn"

        val RENOCLIENT = AppProfile(appId = "com.eybond.renoclient", appVersion = "1.3.2.0")
    }
}
