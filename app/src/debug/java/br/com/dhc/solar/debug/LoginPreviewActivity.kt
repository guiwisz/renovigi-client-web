package br.com.dhc.solar.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import br.com.dhc.solar.container
import br.com.dhc.solar.ui.login.LoginScreen
import br.com.dhc.solar.ui.login.LoginViewModel
import br.com.dhc.solar.ui.theme.SolarTheme

/** Pré-visualização da tela de login sem sair da conta (só debug). */
class LoginPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = LoginViewModel(container.sessions, container.repository)
        setContent {
            SolarTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
                Surface(color = MaterialTheme.colorScheme.background) { LoginScreen(viewModel) }
            }
        }
    }
}
