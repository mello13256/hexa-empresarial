package pt.hexa.empresarial.domain.model

import pt.hexa.empresarial.domain.grid.GridResult
import pt.hexa.empresarial.domain.grid.HomeGrid

/** Roles used to pick the first-run apps; the app layer resolves each to an installed app. */
enum class AppRole { DIALER, MESSAGES, BROWSER, CAMERA, GALLERY, EMAIL, MAPS, MUSIC, CALENDAR, CONTACTS, CLOCK, SETTINGS, STORE, FILES }

object DefaultLayout {

    /** Dock: phone, messages, browser, camera. Home: clock card, search bar, then 8 everyday apps. */
    val DOCK_ROLES = listOf(AppRole.DIALER, AppRole.MESSAGES, AppRole.BROWSER, AppRole.CAMERA)
    val HOME_ROLES = listOf(
        AppRole.GALLERY, AppRole.EMAIL, AppRole.MAPS, AppRole.MUSIC,
        AppRole.CALENDAR, AppRole.CONTACTS, AppRole.CLOCK, AppRole.SETTINGS,
    )

    /**
     * Builds the first-run state. [resolve] maps a role to an app key and label (or null when the
     * device has no such app); [fallback] supplies extra apps when roles are missing.
     */
    fun create(base: LauncherState, resolve: (AppRole) -> Pair<String, String>?, fallback: List<Pair<String, String>>): LauncherState {
        val used = HashSet<String>()
        val dock = DOCK_ROLES.map { role -> resolve(role)?.takeIf { used.add(it.first) }?.first }
        val homeApps = HOME_ROLES.mapNotNull { resolve(it) }.filter { used.add(it.first) }.toMutableList()
        for (f in fallback) {
            if (homeApps.size >= 8) break
            if (used.add(f.first)) homeApps += f
        }
        val cols = base.home.columns
        var layout = HomeLayout(columns = cols, rows = base.home.rows, pages = listOf(HomePage("p1"), HomePage("p2")))
        fun add(item: HomeItem, page: Int = 0) {
            val r = HomeGrid.add(layout, page, item)
            if (r is GridResult.Ok) layout = r.layout
        }
        add(ClockItem(Ids.item(), Cell(0, 0, cols, 2)))
        add(SearchBarItem(Ids.item(), Cell(0, 2, cols, 1)))
        homeApps.forEachIndexed { i, (key, label) ->
            add(AppItem(Ids.item(), Cell(i % cols, 3 + i / cols), key, label))
        }
        return base.copy(home = layout, dock = base.dock.copy(slots = dock), initialized = true)
    }
}
