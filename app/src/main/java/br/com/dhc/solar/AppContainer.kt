package br.com.dhc.solar

import android.content.Context
import br.com.dhc.solar.data.auth.CredentialStore
import br.com.dhc.solar.data.auth.SessionManager
import br.com.dhc.solar.data.local.SnapshotCache
import br.com.dhc.solar.data.remote.ResilientDns
import br.com.dhc.solar.data.remote.RetryInterceptor
import br.com.dhc.solar.data.remote.ShineMonitorApi
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.widget.SolarWidgets
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Injeção de dependências manual: poucas peças, e funciona igual em Activity, Worker e Widget. */
class AppContainer(context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .addInterceptor(RetryInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api = ShineMonitorApi(httpClient)
    val credentials = CredentialStore(context)
    val sessions = SessionManager(api, credentials)
    val cache = SnapshotCache(context)
    val repository = SolarRepository(
        api, sessions, cache,
        onSnapshotChanged = { SolarWidgets.updateAll(context.applicationContext) },
    )
}

val Context.container: AppContainer
    get() = (applicationContext as SolarApplication).container
