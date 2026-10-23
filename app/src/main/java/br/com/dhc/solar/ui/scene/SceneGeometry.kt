package br.com.dhc.solar.ui.scene

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/**
 * Coordenadas da prancheta 400x340 das animações Lottie. Precisa bater com
 * tools/lottie/generate_scenes.py (W, H, DY, sun_arc, PANEL).
 */
object SceneGeometry {
    const val WIDTH = 400f
    const val HEIGHT = 340f

    /** Céu extra acima da paisagem (onde ficam título e barra de status). */
    const val DY = 80f

    /** Centro da face do telhado com as placas. */
    val panelCenter = Offset(248f, 130f + DY)

    /** Posição do sol para um progresso do ciclo; null se abaixo do horizonte. */
    fun sun(progress: Float): Offset? {
        val u = (progress - 0.25f) / 0.5f
        if (u < 0f || u > 1f) return null
        return Offset(30f + 340f * u, DY + 228f - 188f * sin(PI * u).toFloat())
    }
}
