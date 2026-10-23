package br.com.dhc.solar.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.dhc.solar.data.auth.SessionManager
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !loading
}

class LoginViewModel(
    private val sessions: SessionManager,
    private val repository: SolarRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    /** Entra com dados fictícios, sem conta (para conhecer o app). */
    fun startDemo() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.startDemo() }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }

    fun onUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { sessions.signIn(current.username, current.password) }
                .onSuccess {
                    // Primeira carga já com o login; se falhar, o painel tenta de novo.
                    runCatching { repository.refresh() }
                    _state.update { it.copy(loading = false, password = "") }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }
}
