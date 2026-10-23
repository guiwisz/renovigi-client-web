package br.com.dhc.solar.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import br.com.dhc.solar.ui.format.Formatters
import kotlin.math.roundToInt

/** Widget da curva do dia, responsivo: minicurva no 2x2, curva + totais no 4x4. */
@Composable
fun CurveContent(data: WidgetData) {
    val size = LocalSize.current
    val wide = size.width >= 200.dp
    val padding = if (size.height < 100.dp) 8.dp else 16.dp
    WidgetFrame(padding = padding) {
        Column(GlanceModifier.fillMaxSize()) {
            when {
                size.height < 100.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(GlanceModifier.width(4.dp))
                    LabeledValue("Agora", "${Formatters.kw(data.nowKw)} kW", valueSize = 15.sp, accent = true)
                    if (wide) {
                        Spacer(GlanceModifier.width(12.dp))
                        CurveImage(data, GlanceModifier.defaultWeight().fillMaxSize(), size.width.value - 110, size.height.value - 16)
                    }
                }
                !wide -> {
                    LabeledValue("Agora", "${Formatters.kw(data.nowKw)} kW", valueSize = 20.sp, accent = true)
                    Caption("hoje ${kwh(data.todayKwh)}", 12.sp)
                    Spacer(GlanceModifier.height(6.dp))
                    CurveImage(data, GlanceModifier.fillMaxWidth().defaultWeight(), size.width.value - 28, size.height.value - 90)
                }
                else -> {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        LabeledValue("Gerando agora", "${Formatters.kw(data.nowKw)} kW", GlanceModifier.defaultWeight(), accent = true)
                        LabeledValue("Hoje", kwh(data.todayKwh))
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    val tall = size.height >= 240.dp
                    val chartHeight = size.height.value - (if (tall) 190 else 100)
                    CurveImage(data, GlanceModifier.fillMaxWidth().defaultWeight(), size.width.value - 32, chartHeight)
                    if (tall) {
                        Spacer(GlanceModifier.height(10.dp))
                        // Três valores lado a lado: fonte menor e respiro entre eles quando a largura é curta.
                        val cell = if (size.width >= 300.dp) 17.sp else 15.sp
                        Row(GlanceModifier.fillMaxWidth()) {
                            LabeledValue("Semana", kwh(data.weekKwh), GlanceModifier.defaultWeight(), valueSize = cell)
                            Spacer(GlanceModifier.width(6.dp))
                            LabeledValue("Mês", kwh(data.monthKwh), GlanceModifier.defaultWeight(), valueSize = cell)
                            Spacer(GlanceModifier.width(6.dp))
                            LabeledValue("Economia", Formatters.money(data.savingsToday), GlanceModifier.defaultWeight(), valueSize = cell)
                        }
                        Spacer(GlanceModifier.height(10.dp))
                        Footer(data, compact = size.width < 300.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurveImage(data: WidgetData, modifier: GlanceModifier, widthDp: Float, heightDp: Float) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bitmap = WidgetChart.render(
        data.curve,
        (widthDp.coerceAtLeast(40f) * density).roundToInt(),
        (heightDp.coerceAtLeast(24f) * density).roundToInt(),
        data.nominalKw,
        WidgetColors.accent,
    )
    Image(ImageProvider(bitmap), contentDescription = "Curva de geração de hoje", contentScale = ContentScale.FillBounds, modifier = modifier)
}
