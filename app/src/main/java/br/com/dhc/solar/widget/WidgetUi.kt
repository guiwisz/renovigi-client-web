package br.com.dhc.solar.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import br.com.dhc.solar.MainActivity
import br.com.dhc.solar.R
import kotlin.math.roundToInt

object WidgetColors {
    val text = Color.White
    val soft = Color(0xC7FFFFFF)
    val faint = Color(0x26FFFFFF)
    val ringStart = Color(0xFFFFB020)
    val ringEnd = Color(0xFFFF6A1A)
    val accent = Color(0xFFFF8A3D)
    val online = Color(0xFF34C77B)
    val offline = Color(0xFFFF5A4F)
}

private fun color(c: Color) = ColorProvider(c)

/** Cartão escuro em degradê; tocar abre o app. */
@Composable
fun WidgetFrame(padding: Dp = 16.dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(26.dp)
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
    ) { content() }
}

/** Anel com raio e porcentagem no meio. */
@Composable
fun Ring(fraction: Float?, size: Dp, showBolt: Boolean = true) {
    val density = LocalContext.current.resources.displayMetrics.density
    val px = (size.value * density).roundToInt()
    val stroke = (size.value * 0.085f).coerceIn(4f, 9f) * density
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(WidgetGraphics.ring(px, fraction, stroke)), contentDescription = null, modifier = GlanceModifier.size(size))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showBolt) {
                Image(
                    ImageProvider(R.drawable.ic_widget_bolt), contentDescription = null,
                    colorFilter = ColorFilter.tint(color(WidgetColors.text)),
                    modifier = GlanceModifier.size((size.value * 0.22f).dp),
                )
            }
            Text(
                fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                style = TextStyle(color = color(WidgetColors.text), fontSize = (size.value * 0.19f).coerceIn(10f, 20f).sp,
                    fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
fun Caption(text: String, size: TextUnit = 12.sp) {
    Text(text, maxLines = 1, style = TextStyle(color = color(WidgetColors.soft), fontSize = size, fontWeight = FontWeight.Medium))
}

@Composable
fun Value(text: String, size: TextUnit = 22.sp, accent: Boolean = false) {
    Text(text, maxLines = 1, style = TextStyle(color = color(if (accent) WidgetColors.accent else WidgetColors.text),
        fontSize = size, fontWeight = FontWeight.Medium))
}

/** Rótulo em cima, valor embaixo. */
@Composable
fun LabeledValue(label: String, value: String, modifier: GlanceModifier = GlanceModifier, valueSize: TextUnit = 22.sp, accent: Boolean = false) {
    Column(modifier) {
        Caption(label, if (valueSize.value < 17) 10.sp else if (valueSize.value < 20) 11.sp else 12.sp)
        Value(value, valueSize, accent)
    }
}

/**
 * Rodapé: casa + nome da usina e horário, "Ver detalhes ›" (compacto: só a seta).
 * O nome é escolhido pela pessoa e pode ser longo: se não couber inteiro na
 * largura da faixa, mostramos só o horário (nunca reticências).
 */
@Composable
fun Footer(data: WidgetData, compact: Boolean = false) {
    val width = LocalSize.current.width.value
    // Espaço fixo: margens do cartão (~32), ícone + ponto + espaços (~33), seta (18) e "Ver detalhes" (~80).
    val available = width - 32 - 33 - 18 - (if (compact) 0 else 80)
    val full = listOfNotNull(data.plantName, data.updatedAt).joinToString(" · ")
    // ~0,6 em por caractere a 12 sp (margem de segurança sobre a média do Roboto).
    val label = if (full.length * 12 * 0.6 <= available) full else data.updatedAt?.let { "Atualizado $it" } ?: ""
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_widget_home), null, colorFilter = ColorFilter.tint(color(WidgetColors.soft)),
            modifier = GlanceModifier.size(16.dp))
        Spacer(GlanceModifier.width(6.dp))
        Box(GlanceModifier.size(6.dp).cornerRadius(3.dp).background(if (data.online) WidgetColors.online else WidgetColors.offline)) {}
        Spacer(GlanceModifier.width(5.dp))
        Text(
            label,
            maxLines = 1,
            style = TextStyle(color = color(WidgetColors.soft), fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!compact) {
            Text("Ver detalhes", maxLines = 1, style = TextStyle(color = color(WidgetColors.text), fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        Image(ImageProvider(R.drawable.ic_widget_chevron), null, colorFilter = ColorFilter.tint(color(WidgetColors.text)),
            modifier = GlanceModifier.size(18.dp))
    }
}

@Composable
fun VerticalDivider() {
    Box(GlanceModifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp)) {
        Box(GlanceModifier.fillMaxSize().background(WidgetColors.faint)) {}
    }
}

@Composable
fun EmptyWidget() = WidgetFrame {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(ImageProvider(R.drawable.ic_widget_bolt), null, colorFilter = ColorFilter.tint(color(WidgetColors.accent)),
            modifier = GlanceModifier.size(24.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text("Abra o app para conectar", maxLines = 2, style = TextStyle(color = color(WidgetColors.soft), fontSize = 12.sp))
    }
}
