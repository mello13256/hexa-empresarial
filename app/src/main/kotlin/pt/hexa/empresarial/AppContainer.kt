package pt.hexa.empresarial

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.hexa.empresarial.apps.AppCatalog
import pt.hexa.empresarial.apps.AppLauncher
import pt.hexa.empresarial.apps.DefaultApps
import pt.hexa.empresarial.apps.IconPacks
import pt.hexa.empresarial.apps.IconProvider
import pt.hexa.empresarial.data.ImageStore
import pt.hexa.empresarial.data.StateRepository
import pt.hexa.empresarial.data.UsageRepository
import pt.hexa.empresarial.widgets.WidgetHostManager
import java.io.File

/** Process-wide objects, created once (a launcher process lives as long as the device is on). */
class AppContainer(val app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val io = Dispatchers.IO.limitedParallelism(4)

    val state = StateRepository(File(app.filesDir, "estado.json"), scope, io)
    val usage = UsageRepository(File(app.filesDir, "utilizacao.json"), scope, io)
    val catalog = AppCatalog(app, scope, io)
    val iconPacks = IconPacks(app, io)
    val icons = IconProvider(app, catalog, iconPacks, io)
    val launcher = AppLauncher(app, catalog)
    val widgets = WidgetHostManager(app, io)
    val images = ImageStore(app, io)

    fun start() {
        catalog.start()
        scope.launch {
            state.load()
            usage.load()
            if (!state.value.initialized) {
                val snapshot = catalog.snapshot.first { it.loaded }
                state.set(DefaultApps.firstRunState(app, state.value, snapshot))
                state.flush()
            }
        }
    }

    fun flush() {
        scope.launch {
            state.flush()
            usage.flush()
        }
    }
}
