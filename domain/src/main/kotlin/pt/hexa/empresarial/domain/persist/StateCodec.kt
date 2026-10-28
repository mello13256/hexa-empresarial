package pt.hexa.empresarial.domain.persist

import kotlinx.serialization.json.Json
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.DockSettings
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.HomeItem
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.HomePage
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.OrbitalSettings

object StateCodec {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        coerceInputValues = true
        prettyPrint = false
    }

    fun encode(state: LauncherState): String = json.encodeToString(LauncherState.serializer(), state)

    /** Throws on malformed JSON; repairs anything structurally inconsistent. */
    fun decode(text: String): LauncherState = sanitize(json.decodeFromString(LauncherState.serializer(), text))

    /**
     * Makes a state safe to display: grid within limits, no overlapping or duplicate items, dock
     * size within limits, group references valid, numbers clamped. Valid data is never dropped
     * unless it cannot be placed at all.
     */
    fun sanitize(state: LauncherState): LauncherState {
        val home = sanitizeHome(state.home, state.groups)
        val dockSlots = state.dock.slots.take(DockSettings.MAX_SLOTS).let { s ->
            if (s.size < DockSettings.MIN_SLOTS) s + List(DockSettings.MIN_SLOTS - s.size) { null } else s
        }
        val seenGroups = HashSet<String>()
        val groups = state.groups.filter { seenGroups.add(it.id) }.map { g ->
            g.copy(
                name = g.name.take(40),
                appKeys = g.appKeys.distinct().take(AppGroup.MAX_APPS),
                animationSpeed = g.animationSpeed.coerceIn(0.5f, 2f),
            )
        }
        val o = state.orbital
        return state.copy(
            home = home,
            groups = groups,
            dock = state.dock.copy(
                slots = dockSlots,
                backgroundOpacity = state.dock.backgroundOpacity.coerceIn(0f, 1f),
                iconSizeDp = state.dock.iconSizeDp.coerceIn(36, 72),
                spacingDp = state.dock.spacingDp.coerceIn(0, 32),
                marginDp = state.dock.marginDp.coerceIn(0, 48),
            ),
            drawer = state.drawer.copy(
                gridColumns = state.drawer.gridColumns.coerceIn(3, 7),
                backgroundOpacity = state.drawer.backgroundOpacity.coerceIn(0.5f, 1f),
            ),
            orbital = o.copy(
                maxOrbits = o.maxOrbits.coerceIn(OrbitalSettings.MIN_ORBITS, OrbitalSettings.MAX_ORBITS),
                orbitSpacingDp = o.orbitSpacingDp.coerceIn(36, 140),
                maxRadiusFraction = o.maxRadiusFraction.coerceIn(0.5f, 1f),
                iconSizeDp = o.iconSizeDp.coerceIn(32, 72),
                centerSizeDp = o.centerSizeDp.coerceIn(36, 140),
                minGapDp = o.minGapDp.coerceIn(2, 40),
                orbitOpacity = o.orbitOpacity.coerceIn(0f, 1f),
                decorativeRings = o.decorativeRings.coerceIn(0, 3),
            ),
            appearance = state.appearance.copy(
                surfaceOpacity = state.appearance.surfaceOpacity.coerceIn(0f, 1f),
                cornerScale = state.appearance.cornerScale.coerceIn(0f, 1.5f),
                scrim = state.appearance.scrim.coerceIn(0f, 0.7f),
            ),
            icons = state.icons.copy(
                sizeDp = state.icons.sizeDp.coerceIn(36, 72),
                labelSizeSp = state.icons.labelSizeSp.coerceIn(9, 16),
                spacingDp = state.icons.spacingDp.coerceIn(0, 20),
                labelLines = state.icons.labelLines.coerceIn(1, 2),
            ),
            animations = state.animations.copy(speed = state.animations.speed.coerceIn(0.5f, 1.5f)),
            apps = state.apps.copy(
                favorites = state.apps.favorites.distinct(),
                priority = state.apps.priority.distinct(),
                hidden = state.apps.hidden.distinct(),
                manualOrder = state.apps.manualOrder.distinct(),
            ),
        )
    }

    private fun sanitizeHome(home: HomeLayout, groups: List<AppGroup>): HomeLayout {
        val cols = home.columns.coerceIn(HomeLayout.MIN_COLUMNS, HomeLayout.MAX_COLUMNS)
        val rows = home.rows.coerceIn(HomeLayout.MIN_ROWS, HomeLayout.MAX_ROWS)
        val groupIds = groups.map { it.id }.toSet()
        val seenIds = HashSet<String>()
        val seenPages = HashSet<String>()
        val pages = home.pages.take(HomeLayout.MAX_PAGES).mapIndexed { i, p ->
            val id = if (seenPages.add(p.id)) p.id else "p_fix_$i".also { seenPages.add(it) }
            val items = p.items.filter { it !is GroupItem || it.groupId in groupIds }.filter { seenIds.add(it.id) }
            HomePage(id, items)
        }.ifEmpty { listOf(HomePage("p1")) }
        val raw = home.copy(pages = pages)
        val inRange = home.columns in HomeLayout.MIN_COLUMNS..HomeLayout.MAX_COLUMNS && home.rows in HomeLayout.MIN_ROWS..HomeLayout.MAX_ROWS
        // Overlapping or out-of-bounds items are re-placed through the grid engine.
        val laidOut = if (inRange && HomeGrid.isConsistent(raw)) raw else rebuild(raw, cols, rows)
        return laidOut.copy(defaultPage = laidOut.defaultPage.coerceIn(0, laidOut.pages.lastIndex))
    }

    private fun rebuild(layout: HomeLayout, cols: Int, rows: Int): HomeLayout {
        var out = layout.copy(columns = cols, rows = rows, pages = layout.pages.map { it.copy(items = emptyList()) })
        layout.pages.forEachIndexed { pi, page ->
            for (item in page.items) {
                val shrunk: HomeItem = item.moveTo(item.cell.copy(w = item.cell.w.coerceIn(1, cols), h = item.cell.h.coerceIn(1, rows)))
                val r = HomeGrid.add(out, pi, shrunk)
                if (r is pt.hexa.empresarial.domain.grid.GridResult.Ok) out = r.layout
            }
        }
        return out
    }
}
