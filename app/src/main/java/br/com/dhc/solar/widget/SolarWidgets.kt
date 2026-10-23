package br.com.dhc.solar.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import br.com.dhc.solar.container

/** Faixas de tamanho dos widgets responsivos (o launcher usa a maior que couber). */
private val RESPONSIVE_SIZES = setOf(
    DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp), DpSize(110.dp, 180.dp), DpSize(110.dp, 250.dp),
    DpSize(180.dp, 110.dp), DpSize(250.dp, 50.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 250.dp),
    // Tamanhos reais de 4 colunas em celulares grandes (ex.: S24 Ultra): mais folga para texto e anéis.
    DpSize(340.dp, 70.dp), DpSize(340.dp, 150.dp), DpSize(340.dp, 230.dp), DpSize(340.dp, 320.dp),
)

/** Base: lê o último snapshot do cache (nunca a rede) e desenha o layout do tamanho atual. */
abstract class ResponsiveSolarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)
    override val previewSizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    @Composable
    abstract fun Content(data: WidgetData)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        val data = container.cache.latest()?.let { WidgetData.from(it, container.repository.currentTariff()) }
        provideContent { if (data == null) EmptyWidget() else Content(data) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(WidgetData.sample) }
    }
}

abstract class SummaryWidget : ResponsiveSolarWidget() {
    @Composable
    override fun Content(data: WidgetData) = SummaryContent(data)
}

/* Mesmo widget responsivo com tamanhos iniciais diferentes: cada um vira uma entrada no seletor. */
class SmallSolarWidget : SummaryWidget()
class StripSolarWidget : SummaryWidget()
class LargeSolarWidget : SummaryWidget()
class PanelSolarWidget : SummaryWidget()

class CurveSolarWidget : ResponsiveSolarWidget() {
    @Composable
    override fun Content(data: WidgetData) = CurveContent(data)
}

class SmallSolarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SmallSolarWidget() }
class StripSolarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StripSolarWidget() }
class LargeSolarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LargeSolarWidget() }
class PanelSolarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PanelSolarWidget() }
class CurveSolarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CurveSolarWidget() }

object SolarWidgets {
    private val receivers = listOf(
        SmallSolarWidgetReceiver::class, StripSolarWidgetReceiver::class, LargeSolarWidgetReceiver::class,
        PanelSolarWidgetReceiver::class, CurveSolarWidgetReceiver::class,
    )

    suspend fun updateAll(context: Context) {
        SmallSolarWidget().updateAll(context)
        StripSolarWidget().updateAll(context)
        LargeSolarWidget().updateAll(context)
        PanelSolarWidget().updateAll(context)
        CurveSolarWidget().updateAll(context)
    }

    /**
     * Publica as prévias geradas (Android 15+) para o seletor de widgets.
     * O sistema limita a frequência; chamamos uma vez por versão do app.
     */
    suspend fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val manager = GlanceAppWidgetManager(context)
        receivers.forEach { receiver -> runCatching { manager.setWidgetPreviews(receiver) } }
    }
}
