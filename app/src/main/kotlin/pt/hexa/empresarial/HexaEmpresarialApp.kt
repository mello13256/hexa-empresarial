package pt.hexa.empresarial

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context

class HexaEmpresarialApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleOverride.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.icons.trim()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as HexaEmpresarialApp).container
