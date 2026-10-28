package pt.hexa.empresarial.home

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Rect
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import pt.hexa.empresarial.R
import pt.hexa.empresarial.apps.AppShortcut
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.apps.LaunchResult
import pt.hexa.empresarial.container
import pt.hexa.empresarial.data.UsageCounts
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.grid.GridResult
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.Cell
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.GestureAction
import pt.hexa.empresarial.domain.model.GestureBinding
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.HomeItem
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.Ids
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.OrbitalDistribution
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.system.SystemActionsService

/** Things only the activity can do. */
interface HomeHost {
    val rootView: View
    fun startIntent(intent: Intent): Boolean
    fun openSettings()
    fun requestWidgetBind(intent: Intent)
    fun requestWidgetConfigure(appWidgetId: Int)
}

/** A message shown briefly at the bottom of the screen (string resource + optional argument). */
data class UiMessage(@StringRes val text: Int, val arg: String? = null)

/** Where a context menu was opened from. */
enum class MenuSource { HOME, DOCK, DRAWER, ORBITAL, GROUP }

sealed interface MenuTarget {
    val anchor: Rect

    data class App(val appKey: String, val source: MenuSource, override val anchor: Rect, val itemId: String? = null, val dockSlot: Int? = null, val shortcuts: List<AppShortcut> = emptyList()) : MenuTarget
    data class Item(val itemId: String, override val anchor: Rect) : MenuTarget
}

data class OpenGroup(val groupId: String, val origin: Rect?)

/** A widget being added: kept while the bind / configuration screens are shown. */
data class PendingWidget(val appWidgetId: Int, val info: AppWidgetProviderInfo, val page: Int, val span: Pair<Int, Int>, val replaceItemId: String?)

/** Request to show the drawer (from a gesture, the search bar, the dock button...). */
data class DrawerRequest(val view: DrawerView?, val focusSearch: Boolean, val serial: Long = System.nanoTime())

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val c = app.container
    val state: StateFlow<LauncherState> = c.state.flow
    val ready: StateFlow<Boolean> = c.state.ready
    val catalog: StateFlow<CatalogSnapshot> = c.catalog.snapshot
    val usage: StateFlow<UsageCounts> = c.usage.flow

    var host: HomeHost? = null

    var editMode by mutableStateOf(false)
        private set
    var menu by mutableStateOf<MenuTarget?>(null)
        private set
    var openGroup by mutableStateOf<OpenGroup?>(null)
        private set
    var groupEditor by mutableStateOf<String?>(null)
        private set
    var widgetPickerPage by mutableStateOf<Int?>(null)
        private set
    var selectedWidget by mutableStateOf<String?>(null)
        private set
    var drawerRequest by mutableStateOf<DrawerRequest?>(null)
        private set
    var drawerCloseRequest by mutableStateOf(0L)
        private set
    var pageRequest by mutableStateOf<Pair<Int, Long>?>(null)
        private set
    /** Search text of the drawer. */
    var query by mutableStateOf("")
    var accessibilityPrompt by mutableStateOf(false)
        private set

    /** Current pager page, reported by the UI. */
    var currentPage: Int = 0

    /** App order currently shown by the orbital drawer (for "closer to / further from the centre"). */
    var orbitalOrder: List<String> = emptyList()

    /** Cell size in dp, reported by the UI (used to size new widgets). */
    var cellSizeDp: Pair<Float, Float> = 80f to 100f

    var pendingWidget: PendingWidget? = null
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Set after an app was started: overlays reset when the launcher goes to background. */
    private var resetOnStop = false

    private fun message(@StringRes res: Int, arg: String? = null) {
        _messages.tryEmit(UiMessage(res, arg))
    }

    private fun update(transform: (LauncherState) -> LauncherState) = c.state.update(transform)

    private fun updateHome(transform: (HomeLayout) -> GridResult): Boolean {
        val r = transform(state.value.home)
        return when (r) {
            is GridResult.Ok -> {
                update { it.copy(home = r.layout) }
                true
            }
            is GridResult.Rejected -> {
                message(
                    when (r.reason) {
                        GridResult.Reason.TOO_MANY_PAGES -> R.string.msg_too_many_pages
                        else -> R.string.msg_no_space
                    },
                )
                false
            }
        }
    }

    // ---- launching ----------------------------------------------------------------------------

    fun launch(appKey: String, bounds: Rect?) {
        when (c.launcher.launch(appKey, bounds, host?.rootView)) {
            LaunchResult.STARTED -> {
                c.usage.record(appKey)
                resetOnStop = true
            }
            LaunchResult.NOT_INSTALLED -> message(R.string.msg_app_not_installed)
            LaunchResult.UNAVAILABLE -> message(R.string.msg_app_unavailable)
        }
    }

    fun launchShortcut(s: AppShortcut, bounds: Rect?) {
        closeMenu()
        if (c.launcher.startShortcut(s, bounds)) resetOnStop = true else message(R.string.msg_shortcut_failed)
    }

    // ---- gestures -----------------------------------------------------------------------------

    fun perform(binding: GestureBinding) {
        when (binding.action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> openDrawer(null, false)
            GestureAction.OPEN_ORBITAL -> openDrawer(DrawerView.ORBITAL, false)
            GestureAction.OPEN_GRID -> openDrawer(DrawerView.GRID, false)
            GestureAction.OPEN_SEARCH -> openDrawer(null, true)
            GestureAction.OPEN_SETTINGS -> host?.openSettings()
            GestureAction.EDIT_HOME -> enterEdit()
            GestureAction.DEFAULT_PAGE -> pageRequest = state.value.home.defaultPage to System.nanoTime()
            GestureAction.OPEN_APP -> binding.appKey?.let { launch(it, null) } ?: message(R.string.msg_gesture_no_app)
            GestureAction.NOTIFICATIONS, GestureAction.QUICK_SETTINGS, GestureAction.LOCK_SCREEN, GestureAction.RECENT_APPS -> {
                if (!SystemActionsService.perform(binding.action)) accessibilityPrompt = true
            }
        }
    }

    fun dismissAccessibilityPrompt() {
        accessibilityPrompt = false
    }

    fun openAccessibilitySettings() {
        accessibilityPrompt = false
        host?.startIntent(SystemActionsService.settingsIntent())
    }

    fun onHomePressed(drawerOpen: Boolean) {
        val hadOverlay = drawerOpen || menu != null || openGroup != null || editMode || groupEditor != null || widgetPickerPage != null
        closeOverlays()
        if (!hadOverlay) perform(state.value.gestures.homeButton)
    }

    fun closeOverlays() {
        menu = null
        openGroup = null
        groupEditor = null
        widgetPickerPage = null
        selectedWidget = null
        editMode = false
        query = ""
        drawerCloseRequest = System.nanoTime()
    }

    fun onStop() {
        if (resetOnStop) {
            resetOnStop = false
            menu = null
            openGroup = null
            query = ""
            drawerCloseRequest = System.nanoTime()
        }
        c.flush()
    }

    // ---- drawer -------------------------------------------------------------------------------

    fun openDrawer(view: DrawerView?, focusSearch: Boolean) {
        menu = null
        if (view != null) setDrawerView(view)
        drawerRequest = DrawerRequest(view, focusSearch)
    }

    fun setDrawerView(view: DrawerView) = update { it.copy(drawer = it.drawer.copy(view = view)) }

    fun clearQuery() {
        query = ""
    }

    // ---- context menus ------------------------------------------------------------------------

    fun showAppMenu(appKey: String, source: MenuSource, anchor: Rect, itemId: String? = null, dockSlot: Int? = null) {
        menu = MenuTarget.App(appKey, source, anchor, itemId, dockSlot, c.launcher.shortcuts(appKey))
    }

    fun showItemMenu(itemId: String, anchor: Rect) {
        menu = MenuTarget.Item(itemId, anchor)
    }

    fun closeMenu() {
        menu = null
    }

    fun appInfo(key: String) {
        closeMenu()
        if (!c.launcher.openAppInfo(key)) message(R.string.msg_app_not_installed)
    }

    fun uninstall(key: String) {
        closeMenu()
        val intent = c.launcher.uninstallIntent(key) ?: return message(R.string.msg_app_not_installed)
        if (host?.startIntent(intent) != true) message(R.string.msg_action_failed)
    }

    fun toggleFavorite(key: String) {
        closeMenu()
        update { it.copy(apps = it.apps.copy(favorites = AppOrdering.toggle(it.apps.favorites, key))) }
    }

    fun hideApp(key: String) {
        closeMenu()
        update { it.copy(apps = it.apps.copy(hidden = (it.apps.hidden + key).distinct())) }
        message(R.string.msg_app_hidden)
    }

    /** Orbital manual distribution: moves the app one position closer to / further from the centre. */
    fun nudgeOrbital(key: String, delta: Int, currentOrder: List<String>) {
        closeMenu()
        update {
            it.copy(
                apps = AppOrdering.moveInManualOrder(it.apps, currentOrder, key, delta),
                orbital = it.orbital.copy(distribution = OrbitalDistribution.MANUAL),
            )
        }
    }

    // ---- home editing -------------------------------------------------------------------------

    fun enterEdit() {
        menu = null
        drawerCloseRequest = System.nanoTime()
        editMode = true
    }

    fun exitEdit() {
        editMode = false
        selectedWidget = null
        update { it.copy(home = HomeGrid.removeEmptyPages(it.home).let { h -> if (h.pages.isEmpty()) it.home else h }) }
        c.flush()
    }

    fun addAppToHome(key: String, label: String) {
        closeMenu()
        val page = currentPage.coerceIn(0, state.value.home.pages.lastIndex)
        if (updateHome { HomeGrid.add(it, page, AppItem(Ids.item(), Cell(0, 0), key, label)) }) message(R.string.msg_added_home)
    }

    fun moveItem(itemId: String, page: Int, x: Int, y: Int) {
        updateHome { HomeGrid.move(it, itemId, page, x, y) }
    }

    fun removeItem(itemId: String) {
        closeMenu()
        val found = HomeGrid.find(state.value.home, itemId)?.second
        if (found is WidgetItem && !found.needsRestore) c.widgets.delete(found.appWidgetId)
        update { it.copy(home = HomeGrid.remove(it.home, itemId)) }
        if (selectedWidget == itemId) selectedWidget = null
    }

    fun addPage() {
        if (updateHome { HomeGrid.addPage(it) }) pageRequest = state.value.home.pages.lastIndex to System.nanoTime()
    }

    fun removePage(index: Int) {
        val page = state.value.home.pages.getOrNull(index) ?: return
        page.items.filterIsInstance<WidgetItem>().filter { !it.needsRestore }.forEach { c.widgets.delete(it.appWidgetId) }
        update { it.copy(home = HomeGrid.removePage(it.home, index)) }
    }

    fun movePage(from: Int, to: Int) {
        update { it.copy(home = HomeGrid.movePage(it.home, from, to)) }
        pageRequest = to.coerceIn(0, state.value.home.pages.lastIndex) to System.nanoTime()
    }

    fun setDefaultPage(index: Int) = update { it.copy(home = it.home.copy(defaultPage = index.coerceIn(0, it.home.pages.lastIndex))) }

    fun selectWidget(itemId: String?) {
        selectedWidget = itemId
    }

    fun resizeWidget(itemId: String, w: Int, h: Int) {
        val r = HomeGrid.resize(state.value.home, itemId, w, h)
        if (r is GridResult.Ok) update { it.copy(home = r.layout) }
    }

    // ---- dock ---------------------------------------------------------------------------------

    fun setDockSlot(slot: Int, key: String?) = update { s ->
        val slots = s.dock.slots.toMutableList()
        if (slot !in slots.indices) return@update s
        // An app appears once in the dock: clear its previous slot.
        if (key != null) slots.indices.forEach { if (slots[it] == key) slots[it] = null }
        slots[slot] = key
        s.copy(dock = s.dock.copy(slots = slots))
    }

    fun addToDock(key: String) {
        closeMenu()
        val slots = state.value.dock.slots
        val free = slots.indexOfFirst { it == null }
        if (free < 0) return message(R.string.msg_dock_full)
        setDockSlot(free, key)
    }

    /** Drop of a dragged home item or dock app on dock position [slot]. */
    fun dropOnDock(slot: Int, appKey: String, fromItemId: String?, fromDockSlot: Int?) {
        val current = state.value.dock.slots.getOrNull(slot)
        if (fromDockSlot != null) {
            // Swap two dock positions.
            update { s ->
                val slots = s.dock.slots.toMutableList()
                if (slot !in slots.indices || fromDockSlot !in slots.indices) return@update s
                slots[fromDockSlot] = current
                slots[slot] = appKey
                s.copy(dock = s.dock.copy(slots = slots))
            }
            return
        }
        setDockSlot(slot, appKey)
        if (fromItemId != null) {
            update { it.copy(home = HomeGrid.remove(it.home, fromItemId)) }
            // The app that was in the dock goes to the home screen instead of disappearing.
            if (current != null && current != appKey) {
                val label = catalog.value.label(current).orEmpty()
                updateHome { HomeGrid.add(it, currentPage, AppItem(Ids.item(), Cell(0, 0), current, label)) }
            }
        }
    }

    /** Drop of a dock app on the home grid. */
    fun dropDockOnHome(slot: Int, page: Int, x: Int, y: Int) {
        val key = state.value.dock.slots.getOrNull(slot) ?: return
        val label = catalog.value.label(key).orEmpty()
        if (updateHome { HomeGrid.add(it, page, AppItem(Ids.item(), Cell(x, y), key, label)) }) setDockSlot(slot, null)
    }

    // ---- groups -------------------------------------------------------------------------------

    fun openGroup(groupId: String, origin: Rect?) {
        menu = null
        openGroup = OpenGroup(groupId, origin)
    }

    fun closeGroup() {
        openGroup = null
    }

    fun editGroup(groupId: String?) {
        menu = null
        openGroup = null
        groupEditor = groupId ?: NEW_GROUP
    }

    fun closeGroupEditor() {
        groupEditor = null
    }

    fun saveGroup(group: AppGroup, isNew: Boolean) {
        update { s ->
            val groups = if (s.groups.any { it.id == group.id }) s.groups.map { if (it.id == group.id) group else it } else s.groups + group
            s.copy(groups = groups)
        }
        if (isNew) {
            val page = currentPage.coerceIn(0, state.value.home.pages.lastIndex)
            updateHome { HomeGrid.add(it, page, GroupItem(Ids.item(), Cell(0, 0), group.id)) }
        }
        groupEditor = null
    }

    fun deleteGroup(groupId: String) {
        update { s ->
            s.copy(
                groups = s.groups.filter { it.id != groupId },
                home = s.home.copy(pages = s.home.pages.map { p -> p.copy(items = p.items.filter { it !is GroupItem || it.groupId != groupId }) }),
            )
        }
        groupEditor = null
        openGroup = null
    }

    fun addAppToGroup(groupId: String, key: String) {
        closeMenu()
        update { s -> s.copy(groups = s.groups.map { if (it.id == groupId && key !in it.appKeys && it.appKeys.size < AppGroup.MAX_APPS) it.copy(appKeys = it.appKeys + key) else it }) }
        message(R.string.msg_added_group)
    }

    // ---- widgets ------------------------------------------------------------------------------

    fun openWidgetPicker() {
        widgetPickerPage = currentPage
    }

    fun closeWidgetPicker() {
        widgetPickerPage = null
    }

    /** Starts adding [info] (or re-adding it for a backed-up placeholder [replaceItemId]). */
    fun addWidget(info: AppWidgetProviderInfo, page: Int, density: Float, replaceItemId: String? = null) {
        widgetPickerPage = null
        val home = state.value.home
        val (cw, ch) = cellSizeDp
        val span = if (replaceItemId != null) {
            HomeGrid.find(home, replaceItemId)?.second?.cell?.let { it.w to it.h } ?: (2 to 2)
        } else {
            c.widgets.defaultSpan(info, cw, ch, density, home.columns, home.rows)
        }
        val id = c.widgets.allocateId()
        pendingWidget = PendingWidget(id, info, page, span, replaceItemId)
        if (c.widgets.bindIfAllowed(id, info)) {
            afterBind()
        } else {
            val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            host?.requestWidgetBind(intent) ?: cancelPendingWidget()
        }
    }

    fun onWidgetBindResult(ok: Boolean) {
        if (ok) afterBind() else cancelPendingWidget(R.string.msg_widget_not_allowed)
    }

    fun onWidgetConfigureResult(ok: Boolean) {
        if (ok) placePendingWidget() else cancelPendingWidget(R.string.msg_widget_cancelled)
    }

    private fun afterBind() {
        val p = pendingWidget ?: return
        if (c.widgets.needsConfiguration(p.info)) host?.requestWidgetConfigure(p.appWidgetId) ?: cancelPendingWidget() else placePendingWidget()
    }

    private fun placePendingWidget() {
        val p = pendingWidget ?: return
        pendingWidget = null
        val pm = getApplication<Application>().packageManager
        val label = try {
            p.info.loadLabel(pm)
        } catch (e: Exception) {
            ""
        } ?: ""
        val serial = c.catalog.serialOf(p.info.profile)
        if (p.replaceItemId != null) {
            update { s ->
                s.copy(
                    home = s.home.copy(
                        pages = s.home.pages.map { page ->
                            page.copy(items = page.items.map { if (it.id == p.replaceItemId && it is WidgetItem) it.copy(appWidgetId = p.appWidgetId, label = label) else it })
                        },
                    ),
                )
            }
            return
        }
        val item = WidgetItem(Ids.item(), Cell(0, 0, p.span.first, p.span.second), p.appWidgetId, p.info.provider.flattenToString(), label, serial)
        if (!updateHome { HomeGrid.add(it, p.page, item) }) c.widgets.delete(p.appWidgetId)
    }

    private fun cancelPendingWidget(@StringRes reason: Int? = null) {
        pendingWidget?.let { c.widgets.delete(it.appWidgetId) }
        pendingWidget = null
        if (reason != null) message(reason)
    }

    fun restoreWidget(item: WidgetItem, density: Float) {
        val info = c.widgets.findProvider(item.provider)
        if (info == null) {
            message(R.string.msg_widget_provider_missing)
            return
        }
        val page = HomeGrid.find(state.value.home, item.id)?.first ?: currentPage
        addWidget(info, page, density, replaceItemId = item.id)
    }

    // ---- misc ---------------------------------------------------------------------------------

    fun itemById(id: String): HomeItem? = HomeGrid.find(state.value.home, id)?.second

    fun reportRecovered() {
        if (c.state.recovered) message(R.string.msg_state_recovered)
    }

    init {
        c.widgets.onWidgetLongPress = { id ->
            val item = state.value.home.pages.flatMap { it.items }.filterIsInstance<WidgetItem>().firstOrNull { it.appWidgetId == id }
            if (item != null) {
                enterEdit()
                selectedWidget = item.id
            }
        }
        viewModelScope.launch {
            c.state.ready.collect { if (it) reportRecovered() }
        }
    }

    companion object {
        const val NEW_GROUP = "__novo__"
    }
}
