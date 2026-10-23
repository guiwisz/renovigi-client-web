package br.com.dhc.solar.ui.inverter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import br.com.dhc.solar.domain.InverterLive
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.ui.components.AnimatedNumber
import br.com.dhc.solar.ui.components.CardLabel
import br.com.dhc.solar.ui.components.CardRow
import br.com.dhc.solar.ui.components.InsetCard
import br.com.dhc.solar.ui.components.SemiGauge
import br.com.dhc.solar.ui.components.TabScaffold
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.shell.MainUiState
import br.com.dhc.solar.ui.theme.SolarPalette
import kotlinx.coroutines.delay

@Composable
fun InverterScreen(state: MainUiState, onRefresh: () -> Unit) {
    val snapshot = state.snapshot
    val live = snapshot?.inverter
    TabScaffold(
        title = "Inversor",
        subtitle = Formatters.readingTime(live?.readingAt)?.let {
            "Leitura das $it · atualiza a cada ${(snapshot?.collector?.intervalSeconds ?: 300) / 60} min"
        },
        refreshing = state.refreshing,
        onRefresh = onRefresh,
    ) {
        if (snapshot == null || live == null) {
            Text("Sem leitura do inversor ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TabScaffold
        }
        ConversionCard(live, Modifier.appear(0))
        StringsCard(live, Modifier.appear(1))
        CardRow(Modifier.appear(2)) {
            TemperatureCard(live, Modifier.weight(1f).fillMaxHeight())
            FrequencyCard(live, Modifier.weight(1f).fillMaxHeight())
        }
        GridCard(live, Modifier.appear(3))
        EquipmentCard(snapshot, live, Modifier.appear(4))
    }
}

/** Painéis (DC) → inversor → casa/rede (AC), com eficiência da conversão. */
@Composable
private fun ConversionCard(live: InverterLive, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "conv")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    val active = (live.acPowerW ?: 0.0) > 10
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Conversão", Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.primary)
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PowerColumn("Painéis (DC)", live.dcPowerW, Modifier.weight(1f))
            Box(Modifier.weight(0.8f).height(40.dp), contentAlignment = Alignment.Center) {
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    drawLine(
                        color.copy(alpha = if (active) 1f else 0.25f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                        strokeWidth = size.height, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), phase = if (active) -phase * 26f else 0f),
                    )
                }
                live.efficiency?.let {
                    Text(
                        Formatters.percent(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            PowerColumn("Saída (AC)", live.acPowerW, Modifier.weight(1f), alignEnd = true)
        }
        Text(
            if (live.efficiency != null) "Eficiência do inversor: quanto da energia dos painéis vira energia utilizável."
            else "Sem geração no momento.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PowerColumn(label: String, watts: Double?, modifier: Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedNumber((watts ?: 0.0) / 1000, { "${Formatters.kw(it)} kW" }, MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Cada entrada MPPT com barra de potência que cresce, tensão e corrente. */
@Composable
private fun StringsCard(live: InverterLive, modifier: Modifier) {
    val used = live.strings.filter { it.inUse }
    val unused = live.strings.filterNot { it.inUse }
    val maxW = (used.maxOfOrNull { it.powerW } ?: 1.0).coerceAtLeast(1.0)
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Strings de painéis", Icons.Rounded.SolarPower, SolarPalette.Sky)
        used.forEachIndexed { i, s ->
            val grow = remember { Animatable(0f) }
            LaunchedEffect(s.powerW) {
                delay(120L * i)
                grow.animateTo((s.powerW / maxW).toFloat(), tween(900, easing = FastOutSlowInEasing))
            }
            Column(Modifier.padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("MPPT ${s.index}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${Formatters.number(s.powerW, 0)} W", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(
                    Modifier.padding(vertical = 8.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(grow.value.coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp))
                            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(SolarPalette.Sun, SolarPalette.Ember))),
                    )
                }
                Text(
                    "${Formatters.number(s.voltage, 1)} V · ${Formatters.number(s.current, 1)} A",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (used.size >= 2) {
            val a = used[0].powerW
            val b = used[1].powerW
            val diff = if (maxOf(a, b) > 200) kotlin.math.abs(a - b) / maxOf(a, b) else 0.0
            Text(
                if (diff > 0.25) "As strings estão bem diferentes (${Formatters.percent(diff)}). Pode ser sombra, sujeira ou orientação diferente dos painéis."
                else "Strings equilibradas.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (diff > 0.25) SolarPalette.Sun else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (unused.isNotEmpty()) {
            Text(
                "Entradas sem painéis: ${unused.joinToString { "MPPT ${it.index}" }}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TemperatureCard(live: InverterLive, modifier: Modifier) {
    val temp = live.temperatureC ?: return
    val (label, color) = when {
        temp >= 80 -> "Alta" to SolarPalette.Alert
        temp >= 70 -> "Quente" to SolarPalette.Sun
        else -> "Normal" to SolarPalette.Leaf
    }
    InsetCard(modifier) {
        CardLabel("Temperatura", Icons.Rounded.Thermostat, color)
        Box(Modifier.padding(top = 10.dp), contentAlignment = Alignment.BottomCenter) {
            SemiGauge((temp / 90.0).toFloat(), Modifier.widthIn(max = 200.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedNumber(temp, { "${Formatters.number(it, 0)}°" }, MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

@Composable
private fun FrequencyCard(live: InverterLive, modifier: Modifier) {
    val freq = live.gridFrequency ?: return
    InsetCard(modifier) {
        CardLabel("Frequência", Icons.Rounded.ElectricalServices, SolarPalette.Sky)
        AnimatedNumber(freq, { "${Formatters.number(it, 2)} Hz" }, MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (kotlin.math.abs(freq - 60) <= 0.5) "Dentro do padrão (60 Hz)" else "Fora do padrão",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tensão da rede com a faixa adequada da ANEEL para 220 V (202–231 V). */
@Composable
private fun GridCard(live: InverterLive, modifier: Modifier) {
    val voltage = live.gridVoltage ?: return
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Rede elétrica", Icons.Rounded.ElectricalServices, MaterialTheme.colorScheme.primary)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(voltage, { "${Formatters.number(it, 1)} V" }, MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(live.gridCurrent?.let { "${Formatters.number(it, 1)} A" },
                    live.apparentPowerKva?.let { "${Formatters.number(it, 2)} kVA" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VoltageBand(voltage, Modifier.padding(top = 14.dp))
        Text(
            when {
                voltage < 202 -> "Abaixo da faixa adequada. Se for frequente, o inversor pode desligar (alarme de tensão baixa)."
                voltage > 231 -> "Acima da faixa adequada. Tensão alta faz o inversor reduzir ou parar a injeção."
                else -> "Dentro da faixa adequada da ANEEL para 220 V (202–231 V)."
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun VoltageBand(voltage: Double, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val position = remember { Animatable(0f) }
    val min = 190.0
    val max = 245.0
    LaunchedEffect(voltage) { position.animateTo(((voltage - min) / (max - min)).toFloat().coerceIn(0f, 1f), tween(900)) }
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val y = size.height / 2
        fun x(v: Double) = ((v - min) / (max - min)).toFloat() * size.width
        drawLine(colors.surfaceVariant, Offset(0f, y), Offset(size.width, y), 8.dp.toPx(), StrokeCap.Round)
        drawLine(SolarPalette.Leaf.copy(alpha = 0.7f), Offset(x(202.0), y), Offset(x(231.0), y), 8.dp.toPx(), StrokeCap.Round)
        val px = position.value * size.width
        drawCircle(colors.surfaceContainerHigh, 9.dp.toPx(), Offset(px, y))
        drawCircle(colors.primary, 6.dp.toPx(), Offset(px, y))
    }
}

@Composable
private fun EquipmentCard(s: PlantSnapshot, live: InverterLive, modifier: Modifier) {
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Equipamentos", Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant)
        val rows = listOfNotNull(
            "Firmware do inversor" to listOfNotNull(live.firmwareHmi?.let { "HMI $it" }, live.firmwareDsp?.let { "DSP $it" }).joinToString(" · "),
            live.gridStandard?.let { "Padrão de rede" to it },
            s.collector?.firmware?.let { "Firmware do coletor Wi-Fi" to it },
            s.collector?.intervalSeconds?.let { "Envio de dados" to "a cada ${it / 60} min" },
            s.collector?.let { "Comunicação" to if (it.online) "Online" else "Offline" },
            s.runningMinutes?.let { "Operando sem falhas há" to Formatters.days(it) },
            live.busVoltage?.let { "Barramento DC" to "${Formatters.number(it, 1)} V" },
            s.nominalPowerKw?.let { "Potência instalada" to "${Formatters.number(it, 1)} kWp" },
            s.installDate?.take(10)?.let { "Instalado em" to runCatching { Formatters.fullDate(java.time.LocalDate.parse(it)) }.getOrDefault(it) },
        )
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

