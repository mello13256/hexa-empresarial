package pt.hexa.empresarial.domain.model

import kotlinx.serialization.Serializable

const val STATE_VERSION = 1

/**
 * Everything the launcher persists: home layout, dock, groups and all preferences. Stored as a
 * single JSON document so a save is atomic and a backup is a copy of this object.
 */
@Serializable
data class LauncherState(
    val version: Int = STATE_VERSION,
    val home: HomeLayout = HomeLayout(),
    val dock: DockSettings = DockSettings(),
    val groups: List<AppGroup> = emptyList(),
    val drawer: DrawerSettings = DrawerSettings(),
    val orbital: OrbitalSettings = OrbitalSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val icons: IconSettings = IconSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val animations: AnimationSettings = AnimationSettings(),
    val clock: ClockSettings = ClockSettings(),
    val apps: AppPreferences = AppPreferences(),
    /** False until the first-run setup has filled the home screen. */
    val initialized: Boolean = false,
) {
    fun group(id: String): AppGroup? = groups.firstOrNull { it.id == id }
}

// ---- Home screen --------------------------------------------------------------------------------

@Serializable
data class HomeLayout(
    val columns: Int = 4,
    val rows: Int = 6,
    val pages: List<HomePage> = listOf(HomePage("p1")),
    val defaultPage: Int = 0,
) {
    companion object {
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 7
        const val MIN_ROWS = 4
        const val MAX_ROWS = 9
        const val MAX_PAGES = 12
    }
}

@Serializable
data class HomePage(val id: String, val items: List<HomeItem> = emptyList())

/** A rectangle of grid cells. */
@Serializable
data class Cell(val x: Int, val y: Int, val w: Int = 1, val h: Int = 1) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun overlaps(o: Cell): Boolean = x < o.right && o.x < right && y < o.bottom && o.y < bottom

    fun fitsIn(columns: Int, rows: Int): Boolean = x >= 0 && y >= 0 && w >= 1 && h >= 1 && right <= columns && bottom <= rows
}

@Serializable
sealed interface HomeItem {
    val id: String
    val cell: Cell

    fun moveTo(cell: Cell): HomeItem
}

@Serializable
@kotlinx.serialization.SerialName("app")
data class AppItem(override val id: String, override val cell: Cell, val appKey: String, val label: String = "") : HomeItem {
    override fun moveTo(cell: Cell) = copy(cell = cell)
}

@Serializable
@kotlinx.serialization.SerialName("group")
data class GroupItem(override val id: String, override val cell: Cell, val groupId: String) : HomeItem {
    override fun moveTo(cell: Cell) = copy(cell = cell)
}

/**
 * A real Android widget. [appWidgetId] is allocated by the system; -1 means the widget must be
 * added again (after restoring a backup, ids cannot be transferred).
 */
@Serializable
@kotlinx.serialization.SerialName("widget")
data class WidgetItem(
    override val id: String,
    override val cell: Cell,
    val appWidgetId: Int,
    /** Flattened ComponentName of the AppWidgetProviderInfo. */
    val provider: String,
    val label: String = "",
    val userSerial: Long = 0,
) : HomeItem {
    val needsRestore: Boolean get() = appWidgetId < 0

    override fun moveTo(cell: Cell) = copy(cell = cell)
}

@Serializable
@kotlinx.serialization.SerialName("clock")
data class ClockItem(override val id: String, override val cell: Cell) : HomeItem {
    override fun moveTo(cell: Cell) = copy(cell = cell)
}

@Serializable
@kotlinx.serialization.SerialName("search")
data class SearchBarItem(override val id: String, override val cell: Cell) : HomeItem {
    override fun moveTo(cell: Cell) = copy(cell = cell)
}

// ---- Dock ---------------------------------------------------------------------------------------

@Serializable
enum class DockStyle { PLAIN, GOLD_MEDALLION, SILVER_MEDALLION }

@Serializable
data class DockSettings(
    val enabled: Boolean = true,
    /** One app key (or null for an empty position) per slot. Its size is the number of positions. */
    val slots: List<String?> = List(4) { null },
    val showBackground: Boolean = true,
    val backgroundOpacity: Float = 0.85f,
    val iconSizeDp: Int = 56,
    val spacingDp: Int = 8,
    val marginDp: Int = 16,
    val showLabels: Boolean = false,
    val style: DockStyle = DockStyle.GOLD_MEDALLION,
    /** Adds the square "all applications" button to the dock. */
    val showAllAppsButton: Boolean = false,
) {
    companion object {
        const val MIN_SLOTS = 3
        const val MAX_SLOTS = 7
    }
}

// ---- Groups -------------------------------------------------------------------------------------

@Serializable
enum class GroupShape { CIRCLE, RADIAL, HEXAGON, GRID, SPIRAL }

@Serializable
enum class GroupSize { SMALL, MEDIUM, LARGE }

@Serializable
enum class GroupOpenMode { EXPAND_IN_PLACE, FULL_SCREEN }

@Serializable
enum class CenterKind { SYMBOL, APP_ICON, IMAGE, NONE }

/** Geometric symbols drawn by the launcher itself (no bitmap assets). */
@Serializable
enum class CenterSymbol { NETWORK, HEXAGON, DIAMOND, ORBIT, MONOGRAM }

@Serializable
data class CenterElement(
    val kind: CenterKind = CenterKind.SYMBOL,
    val symbol: CenterSymbol = CenterSymbol.NETWORK,
    val appKey: String? = null,
    /** File name of an image copied into the launcher's private storage. */
    val imagePath: String? = null,
)

@Serializable
data class AppGroup(
    val id: String,
    val name: String,
    val appKeys: List<String> = emptyList(),
    val shape: GroupShape = GroupShape.CIRCLE,
    val center: CenterElement = CenterElement(kind = CenterKind.SYMBOL, symbol = CenterSymbol.HEXAGON),
    val size: GroupSize = GroupSize.MEDIUM,
    val openMode: GroupOpenMode = GroupOpenMode.EXPAND_IN_PLACE,
    val animationSpeed: Float = 1f,
) {
    companion object {
        const val MAX_APPS = 24
    }
}

// ---- Drawer -------------------------------------------------------------------------------------

@Serializable
enum class DrawerView { ORBITAL, GRID }

@Serializable
enum class AppSort { ALPHABETICAL, MOST_USED, MANUAL }

@Serializable
data class DrawerSettings(
    val view: DrawerView = DrawerView.ORBITAL,
    val gridColumns: Int = 4,
    val gridShowLabels: Boolean = true,
    val sort: AppSort = AppSort.ALPHABETICAL,
    val autoKeyboard: Boolean = false,
    val backgroundOpacity: Float = 0.96f,
)

@Serializable
enum class OrbitalBackground { DEEP_BLACK, GRAPHITE, GRADIENT, WALLPAPER_DIMMED }

@Serializable
enum class IconBackdrop { NONE, DARK_DISC, LIGHT_DISC, GOLD_DISC }

@Serializable
enum class OrbitalDistribution { AUTOMATIC, MANUAL }

@Serializable
enum class OrbitalOpening { RADIAL, PERSPECTIVE, FADE }

@Serializable
data class OrbitalSettings(
    val maxOrbits: Int = 4,
    /** Distance between consecutive orbits, in dp (raised automatically if icons would touch). */
    val orbitSpacingDp: Int = 52,
    /** Largest orbit radius as a fraction of half the shortest screen side. */
    val maxRadiusFraction: Float = 1f,
    val iconSizeDp: Int = 44,
    val centerSizeDp: Int = 64,
    val minGapDp: Int = 8,
    /** Thin decorative rings drawn between the icon orbits, like the reference composition. */
    val decorativeRings: Int = 1,
    val lineColor: Long = 0xFFB8BCC2,
    val orbitOpacity: Float = 0.22f,
    val showLines: Boolean = true,
    val background: OrbitalBackground = OrbitalBackground.GRAPHITE,
    val iconBackdrop: IconBackdrop = IconBackdrop.DARK_DISC,
    val shadows: Boolean = true,
    val center: CenterElement = CenterElement(),
    val distribution: OrbitalDistribution = OrbitalDistribution.AUTOMATIC,
    val opening: OrbitalOpening = OrbitalOpening.RADIAL,
    /** Allow turning the orbits with a circular drag. */
    val rotation: Boolean = true,
) {
    companion object {
        const val MIN_ORBITS = 1
        const val MAX_ORBITS = 7
    }
}

/** Per-app organisation shared by both drawer views. */
@Serializable
data class AppPreferences(
    val favorites: List<String> = emptyList(),
    val priority: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val manualOrder: List<String> = emptyList(),
)

// ---- Appearance ---------------------------------------------------------------------------------

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class ContrastLevel { STANDARD, MEDIUM, HIGH }

@Serializable
enum class WallpaperMode { SYSTEM, EXECUTIVE_GRADIENT, SOLID, IMAGE }

@Serializable
enum class LogoPlacement { HIDDEN, HOME_TOP, DRAWER_HEADER }

@Serializable
data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Accent colour (metallic gold by default). */
    val accent: Long = 0xFFC9A227,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
    /** Opacity of panels (search bar, clock card, dock), 0..1. */
    val surfaceOpacity: Float = 0.78f,
    /** Multiplier for corner radii, 0..1.5. */
    val cornerScale: Float = 1f,
    val logo: LogoPlacement = LogoPlacement.DRAWER_HEADER,
    val wallpaper: WallpaperMode = WallpaperMode.SYSTEM,
    val wallpaperColor: Long = 0xFF14161F,
    val wallpaperImage: String? = null,
    /** Darkening over the wallpaper to keep labels readable, 0..0.7. */
    val scrim: Float = 0.12f,
)

@Serializable
enum class IconShape { SYSTEM, CIRCLE, ROUNDED_SQUARE, SQUIRCLE }

@Serializable
enum class IconStyle { ORIGINAL, MONOCHROME, GOLD }

@Serializable
enum class LabelAlignment { CENTER, START }

@Serializable
data class IconSettings(
    val sizeDp: Int = 52,
    val labelSizeSp: Int = 12,
    val showLabels: Boolean = true,
    val shape: IconShape = IconShape.SYSTEM,
    val style: IconStyle = IconStyle.ORIGINAL,
    /** Package name of an installed icon pack, or null. */
    val iconPack: String? = null,
    val alignment: LabelAlignment = LabelAlignment.CENTER,
    /** Extra padding inside each grid cell, in dp. */
    val spacingDp: Int = 6,
    val labelLines: Int = 1,
)

// ---- Gestures -----------------------------------------------------------------------------------

@Serializable
enum class GestureAction {
    NONE,
    OPEN_DRAWER,
    OPEN_ORBITAL,
    OPEN_GRID,
    OPEN_SEARCH,
    OPEN_SETTINGS,
    EDIT_HOME,
    OPEN_APP,
    DEFAULT_PAGE,

    /** The following need the optional accessibility service (the system offers no other API). */
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN,
    RECENT_APPS,
    ;

    val needsAccessibility: Boolean
        get() = this == NOTIFICATIONS || this == QUICK_SETTINGS || this == LOCK_SCREEN || this == RECENT_APPS
}

@Serializable
data class GestureBinding(val action: GestureAction = GestureAction.NONE, val appKey: String? = null)

@Serializable
data class GestureSettings(
    val swipeUp: GestureBinding = GestureBinding(GestureAction.OPEN_DRAWER),
    val swipeDown: GestureBinding = GestureBinding(GestureAction.OPEN_SEARCH),
    val doubleTap: GestureBinding = GestureBinding(GestureAction.NONE),
    val homeButton: GestureBinding = GestureBinding(GestureAction.DEFAULT_PAGE),
)

@Serializable
data class AnimationSettings(
    val enabled: Boolean = true,
    /** Duration multiplier, 0.5 (faster) .. 1.5 (slower). */
    val speed: Float = 1f,
)

@Serializable
data class ClockSettings(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showWeekday: Boolean = true,
    /** null = follow the system 12/24 h setting. */
    val use24h: Boolean? = null,
    val showCard: Boolean = true,
)
