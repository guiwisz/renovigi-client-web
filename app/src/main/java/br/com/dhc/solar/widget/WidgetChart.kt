package br.com.dhc.solar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import br.com.dhc.solar.domain.PowerSample

/**
 * Desenha a curva de geração do dia num Bitmap: o Glance não tem Canvas,
 * mas aceita imagens. Eixo X fixo das 05h às 19h para a curva "andar" ao
 * longo do dia em vez de esticar.
 */
object WidgetChart {
    /**
     * Média móvel de ~30 min: passagens de nuvem derrubam a potência por poucos
     * minutos e, com leituras a cada 5 min, a curva vira "pentes" que parecem
     * barras. No widget interessa o formato do dia; o app mostra os dados brutos.
     * O último ponto mantém o valor real (é o "agora").
     */
    private fun smooth(points: List<PowerSample>, window: Int = 6): List<PowerSample> {
        if (points.size < 3) return points
        return points.mapIndexed { i, p ->
            if (i == points.lastIndex) return@mapIndexed p
            val from = (i - window / 2).coerceAtLeast(0)
            val to = (i + window / 2).coerceAtMost(points.lastIndex)
            p.copy(kw = points.subList(from, to + 1).map { it.kw }.average())
        }
    }

    private const val START_MINUTE = 5 * 60
    private const val END_MINUTE = 19 * 60

    fun render(samples: List<PowerSample>, widthPx: Int, heightPx: Int, peakKw: Double?, color: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val points = smooth(samples.filter { it.minuteOfDay in START_MINUTE..END_MINUTE }.sortedBy { it.minuteOfDay })
        if (points.size < 2) return bitmap

        val canvas = Canvas(bitmap)
        val maxKw = maxOf(points.maxOf { it.kw }, peakKw ?: 0.0, 0.1)
        val stroke = heightPx * 0.035f
        val top = stroke
        val usableHeight = heightPx - stroke * 2

        fun x(minute: Int) = (minute - START_MINUTE).toFloat() / (END_MINUTE - START_MINUTE) * widthPx
        fun y(kw: Double) = top + usableHeight * (1f - (kw / maxKw).toFloat())

        val line = Path().apply {
            moveTo(x(points.first().minuteOfDay), y(points.first().kw))
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (x(prev.minuteOfDay) + x(cur.minuteOfDay)) / 2
                cubicTo(midX, y(prev.kw), midX, y(cur.kw), x(cur.minuteOfDay), y(cur.kw))
            }
        }
        val area = Path(line).apply {
            lineTo(x(points.last().minuteOfDay), heightPx.toFloat())
            lineTo(x(points.first().minuteOfDay), heightPx.toFloat())
            close()
        }

        val argb = color.toArgb()
        // Área numa camada própria para esmaecer a borda direita (o "agora"), em vez de um corte reto.
        val layer = canvas.saveLayer(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), null)
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightPx.toFloat(),
                color.copy(alpha = 0.45f).toArgb(), color.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP,
            )
        })
        val endX = x(points.last().minuteOfDay)
        val fade = widthPx * 0.12f
        canvas.drawRect(endX - fade, 0f, endX + 1f, heightPx.toFloat(), Paint().apply {
            shader = LinearGradient(endX - fade, 0f, endX, 0f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        })
        canvas.restoreToCount(layer)
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = argb
        })
        // Ponto do "agora" na ponta da curva.
        val endY = y(points.last().kw)
        canvas.drawCircle(endX, endY, stroke * 2.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x55FFFFFF })
        canvas.drawCircle(endX, endY, stroke * 1.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb })
        return bitmap
    }
}
