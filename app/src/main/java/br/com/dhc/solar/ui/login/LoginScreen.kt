package br.com.dhc.solar.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.dhc.solar.domain.SolarClock
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.components.pressable
import br.com.dhc.solar.ui.scene.SolarScene
import br.com.dhc.solar.ui.theme.SolarPalette
import java.time.LocalDate
import java.time.LocalTime

/** Portal oficial, onde existe a recuperação de senha da conta ShineMonitor. */
private const val PORTAL_URL = "https://www.shinemonitor.com/"

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var showPassword by remember { mutableStateOf(false) }

    // Antes do login não sabemos onde fica a usina: céu pelo relógio do celular (nascer 6h, pôr 18h).
    val now = LocalTime.now()
    val progress = SolarClock.dayProgress(now.hour * 60 + now.minute.toDouble(), SolarClock.sunTimes(LocalDate.now(), null, null, 0))

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SolarScene(
                dayProgress = progress,
                powerFraction = if (progress in 0.27f..0.73f) 0.6f else 0f,
                cloudiness = 0.2f,
                fadeTo = MaterialTheme.colorScheme.background,
            )
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-36).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Bem-vindo", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.appear(0))
                Text(
                    "Acesse sua usina e acompanhe sua energia em tempo real.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp).appear(1),
                )

                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .appear(2)
                        .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Use a mesma conta do RenoClient",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RoundedField(
                        value = state.username,
                        onValueChange = viewModel::onUsername,
                        placeholder = "Usuário ou e-mail",
                        icon = Icons.Rounded.PersonOutline,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    RoundedField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        placeholder = "Sua senha",
                        icon = Icons.Rounded.Lock,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                        trailing = {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.pressable { showPassword = !showPassword },
                            )
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Esqueceu sua senha?",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.pressable { uriHandler.openUri(PORTAL_URL) },
                        )
                    }
                    AnimatedVisibility(state.error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    SubmitButton(enabled = state.canSubmit, loading = state.loading, onClick = viewModel::submit)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Ainda não tem conta? Ver demonstração",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pressable(if (state.loading) null else viewModel::startDemo).padding(vertical = 6.dp).appear(3),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Seus dados ficam só neste celular. A senha não é guardada.",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.appear(3),
                )
            }
        }
    }
}

@Composable
private fun RoundedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val transparent = Color.Transparent
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = transparent,
            unfocusedIndicatorColor = transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SubmitButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val active = enabled || loading
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(58.dp)
            .pressable(if (enabled) onClick else null)
            .clip(RoundedCornerShape(29.dp))
            .background(Brush.horizontalGradient(listOf(SolarPalette.Sun, SolarPalette.Ember)))
            .background(Color.White.copy(alpha = if (active) 0f else 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text("Entrar", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
            )
        }
    }
}
