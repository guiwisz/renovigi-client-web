package br.com.dhc.solar.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.com.dhc.solar.ui.scene.SolarScene

/**
 * Pré-visualização da cena (só debug):
 *   adb shell am start -n br.com.dhc.solar/.debug.ScenePreviewActivity --ef p 0.4 --ef power 0.6 --ef clouds 0.2
 */
class ScenePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = intent.getFloatExtra("p", 0.4f)
        val power = intent.getFloatExtra("power", 0.6f)
        val clouds = intent.getFloatExtra("clouds", 0.2f)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                SolarScene(p, power, clouds, timelapseIntro = false)
            }
        }
    }
}
