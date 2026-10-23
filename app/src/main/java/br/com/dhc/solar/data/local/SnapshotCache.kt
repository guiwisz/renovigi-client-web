package br.com.dhc.solar.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.dhc.solar.domain.PlantSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Último snapshot da usina e preferências locais. App e widgets leem daqui,
 * então o widget nunca precisa ir à rede para se desenhar.
 */
class SnapshotCache(context: Context) {

    private val dataStore = context.applicationContext.solarDataStore

    val snapshot: Flow<PlantSnapshot?> = dataStore.data.map { prefs -> prefs[SNAPSHOT]?.let(::decode) }

    /** Valor investido no sistema (R$), para o retorno do investimento. */
    val investment: Flow<Double> = dataStore.data.map { it[INVESTMENT] ?: DEFAULT_INVESTMENT }

    /** Modo demonstração: dados fictícios, sem conta nem rede. */
    val demoMode: Flow<Boolean> = dataStore.data.map { it[DEMO] == true }
    val demoTariff: Flow<Double> = dataStore.data.map { it[DEMO_TARIFF] ?: br.com.dhc.solar.data.demo.DemoData.DEFAULT_TARIFF }
    val demoName: Flow<String?> = dataStore.data.map { it[DEMO_NAME] }

    suspend fun setDemo(enabled: Boolean) {
        dataStore.edit {
            if (enabled) it[DEMO] = true else { it.remove(DEMO); it.remove(DEMO_TARIFF); it.remove(DEMO_NAME) }
        }
    }

    suspend fun setDemoTariff(value: Double) { dataStore.edit { it[DEMO_TARIFF] = value } }
    suspend fun setDemoName(value: String) { dataStore.edit { it[DEMO_NAME] = value } }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun latest(): PlantSnapshot? = snapshot.first()

    suspend fun save(snapshot: PlantSnapshot) {
        dataStore.edit { it[SNAPSHOT] = json.encodeToString(PlantSnapshot.serializer(), snapshot) }
    }

    suspend fun setInvestment(value: Double) {
        dataStore.edit { it[INVESTMENT] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(SNAPSHOT) }
    }

    private fun decode(raw: String): PlantSnapshot? =
        runCatching { json.decodeFromString(PlantSnapshot.serializer(), raw) }.getOrNull()

    companion object {
        /** Usada só até o servidor responder com a tarifa configurada na usina. */
        const val DEFAULT_TARIFF = 0.93
        const val DEFAULT_INVESTMENT = 26_000.0

        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val INVESTMENT = doublePreferencesKey("investment")
        private val THEME = stringPreferencesKey("theme")
        private val DEMO = androidx.datastore.preferences.core.booleanPreferencesKey("demo")
        private val DEMO_TARIFF = doublePreferencesKey("demo_tariff")
        private val DEMO_NAME = stringPreferencesKey("demo_name")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private val Context.solarDataStore by preferencesDataStore(name = "solar")
