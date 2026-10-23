package br.com.dhc.solar.debug

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import br.com.dhc.solar.widget.CurveSolarWidget
import br.com.dhc.solar.widget.PanelSolarWidget
import br.com.dhc.solar.widget.WidgetData
import br.com.dhc.solar.widget.SummaryContent
import br.com.dhc.solar.widget.CurveContent
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Galeria dos widgets em vários tamanhos, com os dados reais do cache (só debug):
 *   adb shell am start -n br.com.dhc.solar/.debug.WidgetGalleryActivity
 */
class WidgetGalleryActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(40), dp(14), dp(40))
        }
        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF7FA7C9.toInt(), 0xFF35506E.toInt()))
            addView(column)
        })
        val summary = PanelSolarWidget()
        val curve = CurveSolarWidget()
        // Cada faixa responsiva no tamanho EXATO (pior caso): se não cortar aqui, não corta em tamanho real.
        val which = intent.getStringExtra("widget") ?: "summary"
        val stress = intent.getBooleanExtra("stress", false)
        val widget: GlanceAppWidget = when {
            stress && which == "curve" -> StressWidget(curve = true)
            stress -> StressWidget(curve = false)
            which == "curve" -> curve
            else -> summary
        }
        val sizes = listOf(
            110 to 50, 110 to 110, 110 to 180, 110 to 250, 180 to 110, 250 to 50, 250 to 110,
            250 to 180, 250 to 250, 340 to 70, 340 to 150, 340 to 230, 340 to 320,
        )
        val rows: List<List<Triple<GlanceAppWidget, Int, Int>>> = buildList {
            val narrow = sizes.filter { it.first <= 180 }
            narrow.chunked(3).forEach { chunk -> add(chunk.map { Triple(widget, it.first, it.second) }) }
            sizes.filter { it.first > 180 }.forEach { add(listOf(Triple(widget, it.first, it.second))) }
        }
        scope.launch {
            rows.forEach { row ->
                val line = LinearLayout(this@WidgetGalleryActivity).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEach { (widget, w, h) ->
                    val views = widget.compose(this@WidgetGalleryActivity, size = DpSize(w.dp, h.dp))
                    val cell = LinearLayout(this@WidgetGalleryActivity).apply { orientation = LinearLayout.VERTICAL }
                    cell.addView(TextView(this@WidgetGalleryActivity).apply {
                        text = "${widget::class.simpleName?.removeSuffix("SolarWidget")} ${w}x$h"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    })
                    val frame = FrameLayout(this@WidgetGalleryActivity)
                    frame.addView(views.apply(this@WidgetGalleryActivity, frame), FrameLayout.LayoutParams(dp(w), dp(h)))
                    cell.addView(frame, LinearLayout.LayoutParams(dp(w), dp(h)))
                    line.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 0, dp(10), dp(14))
                    })
                }
                column.addView(line)
            }
        }
    }

    override fun onDestroy() {
        scope.launch { }.cancel()
        super.onDestroy()
    }

    /** Valores mais longos possíveis (pior caso de largura de texto). */
    class StressWidget(private val curve: Boolean) : GlanceAppWidget() {
        override val sizeMode = androidx.glance.appwidget.SizeMode.Exact
        override suspend fun provideGlance(context: android.content.Context, id: androidx.glance.GlanceId) {
            val data = WidgetData.sample.copy(
                plantName = "Usina da casa de praia em Porto Seguro", updatedAt = "23:59", nowKw = 6.71, todayKwh = 45.1, typicalDayKwh = 38.6,
                weekKwh = 312.4, typicalWeekKwh = 288.0, monthKwh = 1022.0, typicalMonthKwh = 905.0, savingsToday = 41.94,
            )
            provideContent { if (curve) CurveContent(data) else SummaryContent(data) }
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
