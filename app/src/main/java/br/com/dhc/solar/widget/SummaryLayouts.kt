package br.com.dhc.solar.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import br.com.dhc.solar.R
import br.com.dhc.solar.ui.format.Formatters

/**
 * Widget de resumo responsivo: o layout muda conforme o tamanho que a pessoa
 * dá ao widget na tela inicial (o launcher escolhe a faixa mais próxima).
 *
 * Cada layout foi dimensionado para a MENOR largura/altura da sua faixa, com o
 * texto mais longo possível (valores de no máximo 8 caracteres, ver [kwh]),
 * para que nada seja cortado em nenhum tamanho real.
 */
@Composable
fun SummaryContent(data: WidgetData) {
    val size = LocalSize.current
    when {
        size.height < 100.dp -> if (size.width >= 200.dp) Strip(data, showRing = size.width >= 300.dp) else Compact(data)
        size.height < 170.dp -> when {
            size.width >= 300.dp -> Medium(data)
            size.width >= 200.dp -> MediumCompact(data)
            size.width >= 170.dp -> SquareWide(data)
            else -> Square(data)
        }
        size.height < 240.dp -> if (size.width >= 200.dp) TwoRows(data) else SquareTall(data)
        else -> if (size.width >= 200.dp) Full(data) else Narrow(data)
    }
}

/** kWh com no máximo 8 caracteres ("31,0 kWh", "776 kWh", "1,02 MWh"). */
internal fun kwh(value: Double?): String = when {
    value == null -> "—"
    value >= 1000 -> "${Formatters.number(value / 1000, if (value >= 10_000) 1 else 2)} MWh"
    else -> Formatters.kwh(value)
}

/** 110×50 (2x1): raio + "Hoje" e o valor. Sem anel: não cabe junto do valor. */
@Composable
private fun Compact(d: WidgetData) = WidgetFrame(padding = 8.dp) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_widget_bolt), null, colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.accent)),
                modifier = GlanceModifier.size(12.dp))
            Caption(" Hoje", 10.sp)
        }
        Value(kwh(d.todayKwh), 15.sp)
    }
}

/** 4x1: agora · hoje · economia (anel só a partir de ~300 dp de largura). */
@Composable
private fun Strip(d: WidgetData, showRing: Boolean) = WidgetFrame(padding = 8.dp) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (showRing) {
            Ring(d.dayFraction, 40.dp, showBolt = false)
            Spacer(GlanceModifier.width(10.dp))
        } else {
            Spacer(GlanceModifier.width(4.dp))
        }
        LabeledValue("Agora", "${Formatters.kw(d.nowKw)} kW", GlanceModifier.defaultWeight(), valueSize = 15.sp, accent = true)
        VerticalDivider()
        Spacer(GlanceModifier.width(8.dp))
        LabeledValue("Hoje", kwh(d.todayKwh), GlanceModifier.defaultWeight(), valueSize = 15.sp)
        VerticalDivider()
        Spacer(GlanceModifier.width(8.dp))
        LabeledValue("Economia", Formatters.money(d.savingsToday), GlanceModifier.defaultWeight(), valueSize = 15.sp)
    }
}

/** 110×110 (2x2): anel menor + kWh + potência agora. */
@Composable
private fun Square(d: WidgetData) = WidgetFrame(padding = 10.dp) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Ring(d.dayFraction, 50.dp)
        Spacer(GlanceModifier.height(4.dp))
        Value(kwh(d.todayKwh), 15.sp)
        Caption("agora ${Formatters.kw(d.nowKw)} kW", 10.sp)
    }
}

/** 180×110: anel ao lado do texto (largo mas baixo). */
@Composable
private fun SquareWide(d: WidgetData) = WidgetFrame(padding = 12.dp) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(d.dayFraction, 62.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Caption("Hoje", 11.sp)
            Value(kwh(d.todayKwh), 17.sp)
            Caption("agora ${Formatters.kw(d.nowKw)} kW", 10.sp)
        }
    }
}

/** 110×180 (2x3): anel grande + kWh + agora + média. */
@Composable
private fun SquareTall(d: WidgetData) = WidgetFrame(padding = 10.dp) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Ring(d.dayFraction, 78.dp)
        Spacer(GlanceModifier.height(6.dp))
        Value(kwh(d.todayKwh), 17.sp)
        Caption("agora ${Formatters.kw(d.nowKw)} kW", 10.sp)
        Caption("média ${kwh(d.typicalDayKwh)}", 10.sp)
    }
}

/** 250×110 (4x2 em telas menores): anel + hoje e média empilhados + rodapé. */
@Composable
private fun MediumCompact(d: WidgetData) = WidgetFrame(padding = 14.dp) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Ring(d.dayFraction, 56.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Caption("Hoje", 11.sp)
                Value(kwh(d.todayKwh), 19.sp)
                Caption("média diária ${kwh(d.typicalDayKwh)}", 11.sp)
            }
        }
        Footer(d, compact = true)
    }
}

/** ≥300×110 (4x2): anel do dia, hoje × média diária, potência agora e rodapé. */
@Composable
private fun Medium(d: WidgetData) = WidgetFrame {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Ring(d.dayFraction, 70.dp)
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                LabeledValue("Hoje", kwh(d.todayKwh), valueSize = 21.sp)
                Caption("agora ${Formatters.kw(d.nowKw)} kW", 11.sp)
            }
            LabeledValue("Média diária", kwh(d.typicalDayKwh), GlanceModifier.defaultWeight(), valueSize = 21.sp)
        }
        Footer(d)
    }
}

/** 4x3: dia e mês. */
@Composable
private fun TwoRows(d: WidgetData) = WidgetFrame {
    Column(GlanceModifier.fillMaxSize()) {
        PeriodRow(d.dayFraction, "Hoje", d.todayKwh, "Média diária", d.typicalDayKwh, GlanceModifier.defaultWeight())
        PeriodRow(d.monthFraction, "Este mês", d.monthKwh, "Média mensal", d.typicalMonthKwh, GlanceModifier.defaultWeight())
        Footer(d, compact = LocalSize.current.width < 300.dp)
    }
}

/** 4x4: dia, semana e mês (estilo do widget de referência). */
@Composable
private fun Full(d: WidgetData) = WidgetFrame(padding = 16.dp) {
    Column(GlanceModifier.fillMaxSize()) {
        PeriodRow(d.dayFraction, "Hoje", d.todayKwh, "Média diária", d.typicalDayKwh, GlanceModifier.defaultWeight())
        PeriodRow(d.weekFraction, "Esta semana", d.weekKwh, "Média semanal", d.typicalWeekKwh, GlanceModifier.defaultWeight())
        PeriodRow(d.monthFraction, "Este mês", d.monthKwh, "Média mensal", d.typicalMonthKwh, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.height(4.dp))
        Footer(d, compact = LocalSize.current.width < 300.dp)
    }
}

/** 2x4 (estreito e alto): três blocos empilhados, anel em cima e valor embaixo. */
@Composable
private fun Narrow(d: WidgetData) = WidgetFrame(padding = 10.dp) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf(
            Triple(d.dayFraction, "Hoje", d.todayKwh),
            Triple(d.weekFraction, "Semana", d.weekKwh),
            Triple(d.monthFraction, "Mês", d.monthKwh),
        ).forEach { (fraction, label, value) ->
            Column(
                GlanceModifier.fillMaxWidth().defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ring(fraction, 40.dp, showBolt = false)
                Caption(label, 10.sp)
                Value(kwh(value), 13.sp)
            }
        }
    }
}

@Composable
private fun PeriodRow(
    fraction: Float?,
    label: String,
    value: Double?,
    averageLabel: String,
    average: Double?,
    modifier: GlanceModifier,
) {
    val roomy = LocalSize.current.width >= 300.dp
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ring(fraction, if (roomy) 66.dp else 54.dp)
        Spacer(GlanceModifier.width(if (roomy) 16.dp else 10.dp))
        val size = if (roomy) 22.sp else 17.sp
        LabeledValue(label, kwh(value), GlanceModifier.defaultWeight(), valueSize = size)
        LabeledValue(averageLabel, kwh(average), GlanceModifier.defaultWeight(), valueSize = size)
    }
}
