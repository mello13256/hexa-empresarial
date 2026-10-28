package pt.hexa.empresarial.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.model.AppRole
import pt.hexa.empresarial.domain.model.DefaultLayout
import pt.hexa.empresarial.domain.model.LauncherState

/** Resolves the device's default apps for each [AppRole] to build the first home screen. */
object DefaultApps {

    private fun intentFor(role: AppRole): Intent = when (role) {
        AppRole.DIALER -> Intent(Intent.ACTION_DIAL)
        AppRole.MESSAGES -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        AppRole.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.exemplo.pt"))
        AppRole.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        AppRole.GALLERY -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        AppRole.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        AppRole.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Lisboa"))
        AppRole.MUSIC -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        AppRole.CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        AppRole.CONTACTS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
        AppRole.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        AppRole.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
        AppRole.STORE -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MARKET)
        AppRole.FILES -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, "android.intent.category.APP_FILES")
    }

    fun firstRunState(context: Context, base: LauncherState, catalog: CatalogSnapshot): LauncherState {
        val pm = context.packageManager
        val own = context.packageName
        val primary = catalog.apps.filter { !it.isWorkProfile }
        fun resolve(role: AppRole): Pair<String, String>? {
            val pkg = try {
                pm.resolveActivity(intentFor(role), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            } ?: return null
            if (pkg == "android" || pkg == own) return null // the system chooser, not an app
            return primary.firstOrNull { it.packageName == pkg }?.let { it.key to it.label }
        }
        val fallback = AppOrdering.alphabetical(primary.map { it.entry }).filter { it.packageName != own }.map { it.key to it.label }
        return DefaultLayout.create(base, ::resolve, fallback)
    }
}
