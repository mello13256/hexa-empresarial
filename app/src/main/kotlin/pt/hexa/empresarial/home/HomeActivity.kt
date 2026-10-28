package pt.hexa.empresarial.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import pt.hexa.empresarial.LocaleOverride
import pt.hexa.empresarial.container
import pt.hexa.empresarial.settings.SettingsActivity

/**
 * The HOME activity: singleTask, no recents entry, state not needed (everything is persisted).
 * Pressing Home while it is visible arrives in [onNewIntent].
 */
class HomeActivity : ComponentActivity(), HomeHost {

    private val vm: HomeViewModel by viewModels()

    override val rootView: View get() = window.decorView

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onWidgetBindResult(it.resultCode == RESULT_OK)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        vm.host = this
        setContent { HomeScreen(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed(drawerOpen = drawerVisible)
    }

    /** Updated by the UI so the Home button can close the drawer first. */
    var drawerVisible = false

    override fun onStart() {
        super.onStart()
        container.widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        container.widgets.stopListening()
        vm.onStop()
    }

    override fun onDestroy() {
        if (vm.host === this) vm.host = null
        super.onDestroy()
    }

    override fun startIntent(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun requestWidgetBind(intent: Intent) {
        try {
            bindWidget.launch(intent)
        } catch (e: ActivityNotFoundException) {
            vm.onWidgetBindResult(false)
        }
    }

    override fun requestWidgetConfigure(appWidgetId: Int) {
        try {
            container.widgets.host.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQUEST_CONFIGURE, null)
        } catch (e: Exception) {
            vm.onWidgetConfigureResult(false)
        }
    }

    @Deprecated("AppWidgetHost only offers the request-code based API for configuration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) vm.onWidgetConfigureResult(resultCode == RESULT_OK)
    }

    private companion object {
        const val REQUEST_CONFIGURE = 4201
    }
}
