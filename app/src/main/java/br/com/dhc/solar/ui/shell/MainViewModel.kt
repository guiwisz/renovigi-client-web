package br.com.dhc.solar.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.dhc.solar.data.local.ThemeMode
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class MainUiState(
    val snapshot: PlantSnapshot? = null,
    val tariff: Double = 0.93,
    val investment: Double = 26_000.0,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class MainViewModel(private val repository: SolarRepository) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<MainUiState> =
        combine(repository.snapshot, repository.tariff, repository.investment, refreshing, error, ::MainUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val themeMode: StateFlow<ThemeMode> =
        repository.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        if (refreshing.value) return
        refreshing.value = true
        try {
            repository.refresh()
            error.value = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // sair da tela cancela a atualização: não é erro
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        } finally {
            refreshing.value = false
        }
    }

    /**
     * Atualiza "conforme o sistema permite": o coletor envia uma leitura a cada
     * ~5 min; buscamos logo depois do próximo envio esperado e, se ainda não
     * chegou, tentamos de novo a cada minuto. Rodar só com a tela visível.
     */
    suspend fun autoRefresh() {
        while (true) {
            refreshNow()
            delay(nextRefreshDelayMs(state.value.snapshot))
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }

    private fun nextRefreshDelayMs(snapshot: PlantSnapshot?): Long {
        val reading = snapshot?.lastReadingAt ?: return RETRY_MS
        val last = runCatching { LocalDateTime.parse(reading, TS) }.getOrNull() ?: return RETRY_MS
        val offset = ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds)
        val interval = (snapshot.collector?.intervalSeconds ?: 300).toLong()
        val expectedMs = last.plusSeconds(interval + GRACE_SECONDS).toInstant(offset).toEpochMilli()
        return (expectedMs - System.currentTimeMillis()).coerceIn(RETRY_MS, MAX_WAIT_MS)
    }

    private companion object {
        const val RETRY_MS = 60_000L
        const val MAX_WAIT_MS = 10 * 60_000L
        const val GRACE_SECONDS = 45L
        val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
