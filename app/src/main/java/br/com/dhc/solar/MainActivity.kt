package br.com.dhc.solar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import br.com.dhc.solar.data.local.ThemeMode
import br.com.dhc.solar.ui.shell.AppShell
import br.com.dhc.solar.ui.shell.MainViewModel
import br.com.dhc.solar.ui.login.LoginScreen
import br.com.dhc.solar.ui.login.LoginViewModel
import br.com.dhc.solar.ui.theme.SolarTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = container
        // Segura a splash até sabermos se já existe conta salva (evita "piscar" a tela de login).
        val ready = MutableStateFlow(false)
        splash.setKeepOnScreenCondition { !ready.value }
        // Conta salva ou modo demonstração ativo: entra direto no app.
        val loggedIn = combine(container.credentials.isLoggedIn, container.cache.demoMode) { logged, demo -> logged || demo }
            .onEach { ready.value = true }

        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container.repository) } })
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SolarTheme(darkTheme = dark) {
                // Surface na raiz: define a cor de conteúdo padrão (texto claro no tema escuro).
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    val isLoggedIn by loggedIn.collectAsStateWithLifecycle(initialValue = null)
                    AnimatedContent(isLoggedIn, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { logged ->
                        when (logged) {
                            null -> Unit
                            true -> AppShell(mainViewModel)
                            false -> LoginScreen(
                                viewModel(factory = viewModelFactory { initializer { LoginViewModel(container.sessions, container.repository) } }),
                            )
                        }
                    }
                }
            }
        }
    }
}
