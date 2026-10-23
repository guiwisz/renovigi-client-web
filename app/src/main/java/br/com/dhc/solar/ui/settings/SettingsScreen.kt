package br.com.dhc.solar.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.dhc.solar.container
import br.com.dhc.solar.data.local.ThemeMode
import br.com.dhc.solar.data.repository.SolarRepository
import br.com.dhc.solar.ui.components.InsetCard
import br.com.dhc.solar.ui.components.SectionTitle
import br.com.dhc.solar.ui.components.SegmentedControl
import br.com.dhc.solar.ui.components.SquareIconButton
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.components.pressable
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.shell.MainUiState
import br.com.dhc.solar.ui.theme.SolarPalette
import br.com.dhc.solar.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado de uma gravação, mostrado embaixo do campo. */
data class SaveStatus(val saving: Boolean = false, val message: String? = null, val failed: Boolean = false)

class SettingsViewModel(private val repository: SolarRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = repository.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val tariffStatus = MutableStateFlow(SaveStatus())
    val nameStatus = MutableStateFlow(SaveStatus())

    fun saveTariff(value: Double) = save(tariffStatus, "Tarifa gravada na usina") { repository.setTariff(value) }
    fun saveName(name: String) = save(nameStatus, "Nome atualizado") { repository.setPlantName(name) }
    fun saveInvestment(value: Double) = viewModelScope.launch { repository.setInvestment(value) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    private fun save(status: MutableStateFlow<SaveStatus>, success: String, block: suspend () -> Unit) {
        status.value = SaveStatus(saving = true)
        viewModelScope.launch {
            status.value = try {
                block()
                SaveStatus(message = success)
            } catch (e: Exception) {
                SaveStatus(message = e.toUserMessage(), failed = true)
            }
        }
    }
}

/** Aceita "0,93", "0.93" e "R$ 0,93". */
private fun parseDecimal(text: String): Double? =
    text.replace("R$", "").replace(".", if (text.contains(',')) "" else ".").replace(',', '.').trim().toDoubleOrNull()

@Composable
fun SettingsScreen(state: MainUiState, onClose: () -> Unit, onSignOut: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(context.container.repository) } })
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    SquareIconButton(Icons.Rounded.Close, "Fechar", onClose)
                }

                SectionTitle("Usina", Modifier.appear(0))
                NameCard(snapshot?.plantName.orEmpty(), viewModel, Modifier.appear(1))

                SectionTitle("Tarifa de energia", Modifier.appear(2))
                TariffCard(state.tariff, viewModel, Modifier.appear(3), demo = snapshot?.isDemo == true)

                SectionTitle("Retorno do investimento", Modifier.appear(4))
                InvestmentCard(state.investment, viewModel, Modifier.appear(5))

                SectionTitle("Aparência", Modifier.appear(6))
                InsetCard(Modifier.fillMaxWidth().appear(7)) {
                    SegmentedControl(
                        options = listOf("Sistema", "Claro", "Escuro"),
                        selected = theme.ordinal,
                        onSelect = { viewModel.setTheme(ThemeMode.entries[it]) },
                    )
                }

                SectionTitle(if (snapshot?.isDemo == true) "Demonstração" else "Conta", Modifier.appear(8))
                SignOutCard(onSignOut, Modifier.appear(9), demo = snapshot?.isDemo == true)

                Text(
                    "Dados da nuvem ShineMonitor (a mesma do RenoClient). Leituras do inversor a cada 5 minutos.",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun NameCard(current: String, viewModel: SettingsViewModel, modifier: Modifier) {
    val status by viewModel.nameStatus.collectAsStateWithLifecycle()
    var name by remember(current) { mutableStateOf(if (current == "PLANT") "" else current) }
    InsetCard(modifier.fillMaxWidth()) {
        Text("Nome da usina", style = MaterialTheme.typography.titleMedium)
        Text("Aparece no app, nos widgets e no portal.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(64) }, singleLine = true,
            placeholder = { Text("Ex.: Casa") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        SaveRow(
            enabled = name.isNotBlank() && name.trim() != current && !status.saving,
            status = status,
            onSave = { viewModel.saveName(name) },
        )
    }
}

@Composable
private fun TariffCard(current: Double, viewModel: SettingsViewModel, modifier: Modifier, demo: Boolean = false) {
    val status by viewModel.tariffStatus.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var text by remember(current) { mutableStateOf(Formatters.number(current, 4)) }
    var bill by remember { mutableStateOf("") }
    var kwh by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Double?>(null) }
    val calculated = parseDecimal(bill)?.let { b -> parseDecimal(kwh)?.takeIf { it > 0 }?.let { b / it } }

    InsetCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${Formatters.money(current)} por kWh", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (demo) "Na demonstração, a tarifa fica só neste celular."
                    else "Gravada na usina (servidor) — vale também no RenoClient e no portal.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (editing) "Cancelar" else "Alterar tarifa",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp).pressable { editing = !editing },
        )
        AnimatedVisibility(editing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("R$ por kWh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text("Calcular pela conta de luz", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Divida o valor total da conta pelo consumo em kWh. Use uma conta com pouca compensação de créditos " +
                        "para chegar perto do preço real do kWh com impostos.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(bill, { bill = it }, singleLine = true, label = { Text("Total (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(kwh, { kwh = it }, singleLine = true, label = { Text("Consumo (kWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                calculated?.let { value ->
                    Text(
                        "≈ ${Formatters.money(value)}/kWh · usar este valor",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.pressable { text = Formatters.number(value, 4) },
                    )
                }
                Text(
                    "Sistemas instalados a partir de 2023 (Lei 14.300) pagam uma parte do \"Fio B\" sobre a energia " +
                        "injetada, então a economia real por kWh fica um pouco abaixo da tarifa cheia. Se quiser ser " +
                        "conservador, use um valor ~10% menor.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SaveRow(
                    enabled = parseDecimal(text)?.let { it in 0.1..5.0 && kotlin.math.abs(it - current) > 0.00005 } == true && !status.saving,
                    status = status,
                    onSave = { confirm = parseDecimal(text) },
                    label = "Gravar na usina",
                )
            }
        }
        if (!editing && status.message != null) StatusText(status)
    }

    confirm?.let { value ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Gravar nova tarifa?") },
            text = {
                Text("A tarifa da usina passará de ${Formatters.money(current)} para ${Formatters.money(value)} por kWh. " +
                    "Isso muda a economia calculada aqui, no RenoClient e no portal.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveTariff(value); confirm = null; editing = false }) { Text("Gravar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun InvestmentCard(current: Double, viewModel: SettingsViewModel, modifier: Modifier) {
    var text by remember(current) { mutableStateOf(Formatters.number(current, 2)) }
    var saved by remember { mutableStateOf(false) }
    InsetCard(modifier.fillMaxWidth()) {
        Text("Valor investido no sistema", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se foi financiado, o mais realista é usar o total pago com juros. Fica salvo só no seu celular.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it; saved = false }, singleLine = true,
            label = { Text("R$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        SaveRow(
            enabled = parseDecimal(text)?.let { it > 0 && kotlin.math.abs(it - current) > 0.005 } == true,
            status = SaveStatus(message = if (saved) "Salvo" else null),
            onSave = { parseDecimal(text)?.let { viewModel.saveInvestment(it); saved = true } },
        )
    }
}

@Composable
private fun SignOutCard(onSignOut: () -> Unit, modifier: Modifier, demo: Boolean = false) {
    var confirm by remember { mutableStateOf(false) }
    InsetCard(modifier.fillMaxWidth(), onClick = { if (demo) onSignOut() else confirm = true }) {
        Text(if (demo) "Sair da demonstração" else "Sair da conta", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error)
        Text(
            if (demo) "Volta para a tela de login para entrar com a sua conta." else "Remove as credenciais salvas neste celular.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sair da conta?") },
            text = { Text("Você vai precisar entrar de novo com usuário e senha.") },
            confirmButton = { TextButton(onClick = { confirm = false; onSignOut() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SaveRow(enabled: Boolean, status: SaveStatus, onSave: () -> Unit, label: String = "Salvar") {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { StatusText(status) }
        Box(
            Modifier
                .pressable(if (enabled) onSave else null)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status.saving) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp,
                modifier = Modifier.height(18.dp).widthIn(max = 18.dp))
            else Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun StatusText(status: SaveStatus) {
    status.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = if (status.failed) MaterialTheme.colorScheme.error else SolarPalette.Leaf)
    } ?: Spacer(Modifier.height(1.dp))
}
