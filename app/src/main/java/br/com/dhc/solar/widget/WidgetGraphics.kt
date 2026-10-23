package br.com.dhc.solar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.toArgb

/** Desenhos em bitmap para os widgets (o Glance não tem Canvas). */
object WidgetGraphics {

    /** Anel de progresso (0..1) com trilho translúcido e arco laranja em degradê, pontas arredondadas. */
    fun ring(sizePx: Int, fraction: Float?, strokePx: Float): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2 + 1
        val oval = RectF(inset, inset, size - inset, size - inset)
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }
        // Miolo levemente mais claro, como no widget de referência.
        canvas.drawOval(RectF(inset + strokePx, inset + strokePx, size - inset - strokePx, size - inset - strokePx),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF })
        canvas.drawArc(oval, 0f, 360f, false, base.apply { color = 0x2EFFFFFF })
        val f = (fraction ?: 0f).coerceIn(0f, 1f)
        if (f > 0f) {
            val sweep = SweepGradient(size / 2f, size / 2f,
                intArrayOf(WidgetColors.ringStart.toArgb(), WidgetColors.ringEnd.toArgb(), WidgetColors.ringStart.toArgb()),
                floatArrayOf(0f, 0.7f, 1f)).apply {
                setLocalMatrix(Matrix().apply { setRotate(-90f, size / 2f, size / 2f) })
            }
            base.color = 0xFFFFFFFF.toInt() // opaco: o alfa da cor também se aplica ao shader
            base.shader = sweep
            canvas.drawArc(oval, -90f, 360f * f, false, base)
        }
        return bitmap
    }
}
