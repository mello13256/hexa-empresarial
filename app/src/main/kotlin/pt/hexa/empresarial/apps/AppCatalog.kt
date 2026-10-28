package pt.hexa.empresarial.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.apps.AppKey
import pt.hexa.empresarial.domain.search.AppSearch
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    /** False when the app is suspended or its profile is paused. */
    val enabled: Boolean,
) {
    val packageName: String get() = component.packageName
    val entry: AppEntry get() = AppEntry(key, label, packageName, isWorkProfile)
}

class CatalogSnapshot(val apps: List<AppInfo>, val loaded: Boolean) {
    val byKey: Map<String, AppInfo> = apps.associateBy { it.key }
    val entries: List<AppEntry> = apps.map { it.entry }
    val search: AppSearch by lazy { AppSearch(entries) }

    fun label(key: String): String? = byKey[key]?.label

    /** The stored key, or another launcher activity of the same package if the app renamed it. */
    fun resolve(key: String): AppInfo? = byKey[key]
        ?: apps.firstOrNull { it.packageName == AppKey.packageName(key) && AppKey.userSerial(it.key) == AppKey.userSerial(key) }

    companion object {
        val EMPTY = CatalogSnapshot(emptyList(), loaded = false)
    }
}

/**
 * Launchable activities of every user profile, via [LauncherApps] (the API intended for home
 * apps; it respects package visibility through the MAIN/LAUNCHER `<queries>` entry). The system is
 * queried once, then only the package named by each [LauncherApps.Callback] is re-read.
 */
class AppCatalog(context: Context, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)!!
    private val userManager = appContext.getSystemService(UserManager::class.java)!!

    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val mutex = Mutex()
    private var publishJob: Job? = null
    private val listeners = mutableListOf<(String) -> Unit>()

    private val _snapshot = MutableStateFlow(CatalogSnapshot.EMPTY)
    val snapshot: StateFlow<CatalogSnapshot> = _snapshot.asStateFlow()

    fun onPackageChanged(listener: (String) -> Unit) {
        listeners += listener
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName, user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = packageNames.forEach { refresh(it, user) }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = packageNames.forEach { refresh(it, user) }
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = packageNames.forEach { refresh(it, user) }
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = packageNames.forEach { refresh(it, user) }
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reloadAll()
    }

    fun reloadAll() {
        scope.launch {
            mutex.withLock {
                withContext(io) {
                    infos.clear()
                    for (user in userManager.userProfiles) {
                        val list = try {
                            launcherApps.getActivityList(null, user)
                        } catch (e: SecurityException) {
                            emptyList()
                        }
                        for (info in list) infos[keyOf(info)] = info
                    }
                }
                publish()
            }
        }
    }

    fun activityInfo(key: String): LauncherActivityInfo? = infos[key]

    fun userFor(serial: Long): UserHandle? = if (serial == 0L) Process.myUserHandle() else userManager.getUserForSerialNumber(serial)

    fun serialOf(user: UserHandle): Long = if (user == Process.myUserHandle()) 0L else userManager.getSerialNumberForUser(user)

    private fun refresh(packageName: String, user: UserHandle) {
        listeners.forEach { it(packageName) }
        scope.launch {
            mutex.withLock {
                withContext(io) {
                    val serial = serialOf(user)
                    infos.keys.removeAll { AppKey.packageName(it) == packageName && AppKey.userSerial(it) == serial }
                    val list = try {
                        launcherApps.getActivityList(packageName, user)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    for (info in list) infos[keyOf(info)] = info
                }
            }
            publishJob?.cancel()
            publishJob = scope.launch {
                delay(150) // updates usually arrive in bursts
                mutex.withLock { publish() }
            }
        }
    }

    private suspend fun publish() {
        val apps = withContext(io) {
            val me = Process.myUserHandle()
            infos.map { (key, info) ->
                val enabled = try {
                    launcherApps.isActivityEnabled(info.componentName, info.user) &&
                        (android.os.Build.VERSION.SDK_INT < 24 || !userManager.isQuietModeEnabled(info.user))
                } catch (e: Exception) {
                    true
                }
                AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    component = info.componentName,
                    user = info.user,
                    isWorkProfile = info.user != me,
                    enabled = enabled,
                )
            }
        }
        _snapshot.value = CatalogSnapshot(apps, loaded = true)
    }

    private fun keyOf(info: LauncherActivityInfo) =
        AppKey.of(info.componentName.packageName, info.componentName.className, serialOf(info.user))
}
