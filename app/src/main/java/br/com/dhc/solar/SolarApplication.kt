package br.com.dhc.solar

import android.app.Application
import br.com.dhc.solar.widget.SolarWidgets
import br.com.dhc.solar.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SolarApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RefreshWorker.schedule(this)
        publishWidgetPreviewsOnce()
    }

    /** Prévias do seletor de widgets: só quando a versão do app muda (o sistema limita a frequência). */
    private fun publishWidgetPreviewsOnce() {
        val prefs = getSharedPreferences("widgets", MODE_PRIVATE)
        val version = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        if (prefs.getLong("previews_for", 0) == version) return
        appScope.launch {
            SolarWidgets.publishPreviews(this@SolarApplication)
            prefs.edit().putLong("previews_for", version).apply()
        }
    }
}
