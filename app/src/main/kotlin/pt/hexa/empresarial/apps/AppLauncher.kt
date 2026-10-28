package pt.hexa.empresarial.apps

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View

enum class LaunchResult { STARTED, NOT_INSTALLED, UNAVAILABLE }

data class AppShortcut(val packageName: String, val id: String, val label: String, val serial: Long)

/** Starts apps, app shortcuts and system screens for a given app. */
class AppLauncher(context: Context, private val catalog: AppCatalog) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)!!

    fun launch(key: String, bounds: Rect?, view: View?): LaunchResult {
        val app = catalog.snapshot.value.resolve(key) ?: return LaunchResult.NOT_INSTALLED
        if (!app.enabled) return LaunchResult.UNAVAILABLE
        return try {
            launcherApps.startMainActivity(app.component, app.user, bounds, options(view, bounds))
            LaunchResult.STARTED
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NOT_INSTALLED
        } catch (e: SecurityException) {
            LaunchResult.UNAVAILABLE
        } catch (e: IllegalStateException) {
            LaunchResult.UNAVAILABLE
        }
    }

    fun openAppInfo(key: String): Boolean {
        val app = catalog.snapshot.value.resolve(key) ?: return false
        return try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Intent for the system uninstall confirmation (the system asks the user). */
    fun uninstallIntent(key: String): Intent? {
        val app = catalog.snapshot.value.resolve(key) ?: return null
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val canUseShortcuts: Boolean
        get() = try {
            launcherApps.hasShortcutHostPermission()
        } catch (e: Exception) {
            false
        }

    /** App shortcuts ("Nova mensagem"...). Only available while this is the default home app. */
    fun shortcuts(key: String): List<AppShortcut> {
        if (!canUseShortcuts) return emptyList()
        val app = catalog.snapshot.value.resolve(key) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val list: List<ShortcutInfo> = try {
            launcherApps.getShortcuts(query, app.user).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        return list.filter { it.isEnabled }.sortedBy { it.rank }.take(5).map {
            AppShortcut(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString(), catalog.serialOf(app.user))
        }
    }

    fun startShortcut(s: AppShortcut, bounds: Rect?): Boolean {
        val user = catalog.userFor(s.serial) ?: return false
        return try {
            launcherApps.startShortcut(s.packageName, s.id, bounds, null, user)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun options(view: View?, bounds: Rect?): Bundle? {
        if (view == null || bounds == null) return null
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return ActivityOptions.makeClipRevealAnimation(view, bounds.left - loc[0], bounds.top - loc[1], bounds.width(), bounds.height()).toBundle()
    }
}
