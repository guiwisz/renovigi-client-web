package br.com.dhc.solar.debug

import android.app.Activity
import android.os.Bundle
import br.com.dhc.solar.container
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Liga/desliga o modo demonstração sem mexer na conta salva (só debug, para prints):
 *   adb shell am start -n br.com.dhc.solar/.debug.DemoToggleActivity --ez on true
 */
class DemoToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val on = intent.getBooleanExtra("on", true)
        MainScope().launch {
            val cache = container.cache
            cache.setDemo(on)
            runCatching { container.repository.refresh() }
            finish()
        }
    }
}
