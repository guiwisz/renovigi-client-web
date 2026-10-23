package br.com.dhc.solar.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.dhc.solar.domain.InverterStatus
import br.com.dhc.solar.domain.PlantSnapshot
import br.com.dhc.solar.domain.SolarClock
import br.com.dhc.solar.ui.components.AnimatedNumber
import br.com.dhc.solar.ui.components.BlockProgress
import br.com.dhc.solar.ui.components.CardLabel
import br.com.dhc.solar.ui.components.CardRow
import br.com.dhc.solar.ui.components.InsetCard
import br.com.dhc.solar.ui.components.LineChart
import br.com.dhc.solar.ui.components.LineSeries
import br.com.dhc.solar.ui.components.MetricCard
import br.com.dhc.solar.ui.components.OverlapLayout
import br.com.dhc.solar.ui.components.pressable
import br.com.dhc.solar.ui.components.SquareIconButton
import br.com.dhc.solar.ui.components.TabScaffold
import br.com.dhc.solar.ui.components.appear
import br.com.dhc.solar.ui.format.Formatters
import br.com.dhc.solar.ui.scene.SolarScene
import br.com.dhc.solar.ui.shell.MainUiState
import br.com.dhc.solar.ui.theme.SolarPalette
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable
fun NowScreen(state: MainUiState, onRefresh: () -> Unit, onOpenSettings: () -> Unit, onOpenAlerts: () -> Unit) {
    val snapshot = state.snapshot
    val title = snapshot?.plantName?.takeIf { it.isNotBlank() && it != "PLANT" } ?: "Minha usina"
    TabScaffold(
        title = title,
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        actions = { SquareIconButton(Icons.Rounded.Settings, "Ajustes", onOpenSettings) },
        header = snapshot?.let { s ->
            {
                OverlapLayout(
                    hero = { SceneHero(s, title, onOpenSettings) },
                    card = { PowerHero(s, Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 20.dp).appear(1)) },
                    overlap = 26.dp,
                )
            }
        },
    ) {
        if (snapshot == null) {
            EmptyState(state.error)
            return@TabScaffold
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        CardRow(Modifier.appear(2)) {
            MetricCard("Economia hoje", Icons.Rounded.Payments, SolarPalette.Leaf, snapshot.savings(state.tariff), Formatters::money,
                "${Formatters.money(state.tariff)}/kWh", Modifier.weight(1f).fillMaxHeight())
            MetricCard("CO₂ evitado hoje", Icons.Rounded.Eco, SolarPalette.Leaf, snapshot.todayKwh * (snapshot.co2KgPerKwh ?: 0.0),
                { Formatters.weight(it) },
                snapshot.co2KgPerKwh?.let { "${Formatters.number(it, 2)} kg por kWh" }, Modifier.weight(1f).fillMaxHeight())
        }
        CurveCard(snapshot, Modifier.appear(3))
        GoalCard(snapshot, Modifier.appear(4))
        PaybackCard(snapshot, state.tariff, state.investment, Modifier.appear(5))
        snapshot.recentAlarms.firstOrNull { alarm ->
            alarm.start()?.let { ChronoUnit.DAYS.between(it.toLocalDate(), LocalDate.now()) <= 7 } == true
        }?.let { alarm ->
            InsetCard(Modifier.fillMaxWidth().appear(6), onClick = onOpenAlerts) {
                CardLabel(if (alarm.active) "Alerta ativo" else "Último alerta", Icons.Rounded.WarningAmber,
                    if (alarm.active) SolarPalette.Alert else SolarPalette.Sun)
                Text(alarm.info.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                Text(
                    alarm.start()?.let { "${Formatters.dayMonth(it)} às ${"%02d:%02d".format(it.hour, it.minute)}" }.orEmpty() +
                        (alarm.duration?.let { " · durou ${Formatters.duration(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CardRow(Modifier.appear(7)) {
            MetricCard("Este mês", Icons.Rounded.CalendarMonth, SolarPalette.Sky, snapshot.monthKwh, Formatters::kwh,
                Formatters.money(snapshot.monthKwh * state.tariff), Modifier.weight(1f).fillMaxHeight())
            MetricCard("Este ano", Icons.Rounded.Today, SolarPalette.Sky, snapshot.yearKwh, Formatters::kwh,
                Formatters.money(snapshot.yearKwh * state.tariff), Modifier.weight(1f).fillMaxHeight())
        }
        ImpactCard(snapshot, Modifier.appear(8))
    }
}

private fun statusLabel(status: InverterStatus): Pair<String, Color> = when (status) {
    InverterStatus.ONLINE -> "Online" to SolarPalette.Leaf
    InverterStatus.OFFLINE -> "Offline" to Color(0xFF8E8E93)
    InverterStatus.WARNING -> "Em alarme" to SolarPalette.Alert
    InverterStatus.ATTENTION -> "Atenção" to SolarPalette.Sun
    InverterStatus.UNKNOWN -> "—" to Color(0xFF8E8E93)
}

/** Cena de ponta a ponta com título, status e ajustes sobre o céu (o céu muda com a hora). */
@Composable
private fun SceneHero(s: PlantSnapshot, title: String, onOpenSettings: () -> Unit) {
    // Recalcula a posição do dia a cada minuto.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val local = LocalDateTime.ofEpochSecond(now / 1000, 0, ZoneOffset.ofTotalSeconds(s.utcOffsetSeconds))
    val minute = local.hour * 60 + local.minute + local.second / 60.0
    val sun = SolarClock.sunTimes(local.toLocalDate(), s.latitude, s.longitude, s.utcOffsetSeconds)
    val nominal = s.nominalPowerKw?.takeIf { it > 0 } ?: 1.0
    val expected = SolarClock.clearSkyPowerKw(minute, sun, nominal)
    val cloudiness = if (expected > nominal * 0.15) (1 - s.currentPowerKw / expected).coerceIn(0.0, 1.0) else 0.0
    val progress = SolarClock.dayProgress(minute, sun)
    // Texto escuro sobre céu claro; claro sobre céu de amanhecer/entardecer/noite.
    val brightSky = progress in 0.29f..0.71f
    val ink = if (brightSky) Color(0xFF16161A) else Color.White
    val inkSoft = ink.copy(alpha = 0.78f)

    Box(Modifier.fillMaxWidth()) {
        SolarScene(
            dayProgress = progress,
            powerFraction = (s.currentPowerKw / nominal).toFloat(),
            cloudiness = cloudiness.toFloat(),
            fadeTo = MaterialTheme.colorScheme.background,
        )
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = ink)
                val (label, dot) = statusLabel(s.status)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                    Text(
                        (if (s.isDemo) " Modo demonstração" else " $label") +
                            (Formatters.readingTime(s.lastReadingAt)?.let { " · leitura das $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge, color = inkSoft,
                    )
                }
                Text(
                    "Nascer ${hm(sun.sunrise)} · Pôr ${hm(sun.sunset)}",
                    style = MaterialTheme.typography.labelMedium, color = inkSoft, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
                    .pressable(onOpenSettings)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Settings, "Ajustes", tint = Color(0xFF16161A), modifier = Modifier.size(24.dp)) }
        }
    }
}

private fun hm(minutes: Double): String = "%02d:%02d".format((minutes / 60).toInt(), (minutes % 60).toInt())

/** Card "Gerando agora" que sobe sobre a base da cena. */
@Composable
private fun PowerHero(s: PlantSnapshot, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.align(Alignment.Top).size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Bolt, null, tint = SolarPalette.Sun, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Gerando agora", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedNumber(s.currentPowerKw, Formatters::kw, MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface)
                Text(" kW", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            s.nominalPowerKw?.takeIf { it > 0 }?.let {
                Text("${Formatters.percent(s.currentPowerKw / it)} de ${Formatters.number(it, 1)} kWp",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        Column(horizontalAlignment = Alignment.End) {
            Text("Hoje", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedNumber(s.todayKwh, Formatters::kwh, MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CurveCard(s: PlantSnapshot, modifier: Modifier) {
    InsetCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Curva do dia", Icons.AutoMirrored.Rounded.ShowChart, SolarPalette.Sun, Modifier.weight(1f))
            s.todayCurve.maxOfOrNull { it.kw }?.let {
                Text("pico ${Formatters.kw(it)} kW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LineChart(
            series = listOf(LineSeries(s.todayCurve.map { it.minuteOfDay to it.kw }, MaterialTheme.colorScheme.primary, "Potência")),
            format = { "${Formatters.kw(it)} kW" },
            modifier = Modifier.padding(top = 8.dp),
            liveEnd = true,
        )
    }
}

@Composable
private fun GoalCard(s: PlantSnapshot, modifier: Modifier) {
    val typical = s.typicalDayKwh ?: s.nominalPowerKw?.let { it * 4.5 } ?: return
    val fraction = (s.todayKwh / typical).toFloat()
    InsetCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Comparado a um dia típico", Icons.Rounded.WbSunny, SolarPalette.Sun, Modifier.weight(1f))
            Text(Formatters.percent(fraction.toDouble()), style = MaterialTheme.typography.titleMedium)
        }
        BlockProgress(fraction, Modifier.padding(vertical = 14.dp))
        Text(
            "${Formatters.kwh(s.todayKwh)} de ${Formatters.kwh(typical)} — média dos últimos 28 dias",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaybackCard(s: PlantSnapshot, tariff: Double, investment: Double, modifier: Modifier) {
    val earned = s.proceedsTotal ?: (s.totalKwh * tariff)
    if (investment <= 0) return
    val fraction = (earned / investment).coerceIn(0.0, 1.0)
    val remaining = investment - earned
    // Ritmo recente: média diária do mês corrente.
    val dailySavings = (s.typicalDayKwh ?: (s.monthKwh / LocalDate.now().dayOfMonth.coerceAtLeast(1))) * tariff
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Retorno do investimento", Icons.Rounded.Savings, SolarPalette.Leaf)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(fraction * 100, { "${Formatters.number(it, 0)}%" }, MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                "  ${Formatters.money(earned)} de ${Formatters.money(investment)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = SolarPalette.Leaf,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            if (remaining <= 0) "O sistema já se pagou! Tudo daqui pra frente é economia."
            else {
                val days = if (dailySavings > 0) (remaining / dailySavings).toLong() else null
                "Faltam ${Formatters.money(remaining)}" + (days?.let { " · previsão: ${Formatters.monthYear(LocalDate.now().plusDays(it))}" } ?: "")
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ImpactCard(s: PlantSnapshot, modifier: Modifier) {
    val co2 = s.co2TotalKg ?: return
    InsetCard(modifier.fillMaxWidth()) {
        CardLabel("Impacto desde a instalação", Icons.Rounded.Eco, SolarPalette.Leaf)
        Row(Modifier.padding(top = 12.dp)) {
            Impact(Formatters.kwh(s.totalKwh), "gerados", Modifier.weight(1f))
            Impact(Formatters.weight(co2), "de CO₂ evitados", Modifier.weight(1f))
            // Uma árvore absorve ~22 kg de CO₂ por ano.
            Impact(Formatters.number(co2 / 22.0, 0), "árvores por um ano", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Impact(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(error: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.WbSunny, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            error ?: "Buscando dados da sua usina…",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
    }
}
