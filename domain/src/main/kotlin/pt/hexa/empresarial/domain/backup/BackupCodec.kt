package pt.hexa.empresarial.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.WallpaperMode
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.domain.persist.StateCodec

const val BACKUP_FORMAT = "hexa-empresarial-edition/backup"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appVersion: String = "",
    /** Epoch milliseconds. */
    val createdAt: Long = 0,
    val state: LauncherState,
)

data class BackupSummary(
    val pages: Int,
    val apps: Int,
    val dockApps: Int,
    val groups: Int,
    val widgets: Int,
    val createdAt: Long,
)

enum class BackupWarning {
    /** Widget ids belong to the device that created them; widgets must be added again. */
    WIDGETS_NEED_RESTORE,

    /** Personal images (wallpaper, group/centre images) are not part of the file. */
    IMAGES_NOT_INCLUDED,

    /** Some apps of the backup are not installed on this device. */
    MISSING_APPS,

    /** Some entries were inconsistent and were repaired. */
    REPAIRED,
}

enum class BackupError { NOT_JSON, WRONG_FORMAT, UNSUPPORTED_VERSION, INVALID_CONTENT }

sealed interface BackupResult {
    data class Valid(val state: LauncherState, val summary: BackupSummary, val warnings: Set<BackupWarning>, val missingApps: Int) : BackupResult
    data class Invalid(val error: BackupError) : BackupResult
}

/**
 * Export and import of the launcher configuration. Imports are fully validated before anything
 * is applied: format marker, version, structure, and consistency of the layout.
 */
object BackupCodec {

    private val json = Json(StateCodec.json) { prettyPrint = true }

    /** Personal data the file cannot carry is removed (images) or marked for restore (widgets). */
    fun export(state: LauncherState, appVersion: String, now: Long): String {
        val portable = state.copy(
            home = state.home.copy(
                pages = state.home.pages.map { p ->
                    p.copy(items = p.items.map { if (it is WidgetItem) it.copy(appWidgetId = -1) else it })
                },
            ),
            appearance = state.appearance.copy(
                wallpaperImage = null,
                wallpaper = if (state.appearance.wallpaper == WallpaperMode.IMAGE) WallpaperMode.SYSTEM else state.appearance.wallpaper,
            ),
            orbital = state.orbital.copy(center = stripImage(state.orbital.center)),
            groups = state.groups.map { it.copy(center = stripImage(it.center)) },
        )
        return json.encodeToString(BackupFile.serializer(), BackupFile(appVersion = appVersion, createdAt = now, state = portable))
    }

    fun validate(text: String, installedKeys: Set<String>? = null): BackupResult {
        val root: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return BackupResult.Invalid(BackupError.NOT_JSON)
        }
        val format = try {
            root["format"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
        if (format != BACKUP_FORMAT) return BackupResult.Invalid(BackupError.WRONG_FORMAT)
        val version = try {
            root["formatVersion"]?.jsonPrimitive?.intOrNull
        } catch (e: Exception) {
            null
        } ?: return BackupResult.Invalid(BackupError.WRONG_FORMAT)
        if (version > BACKUP_FORMAT_VERSION || version < 1) return BackupResult.Invalid(BackupError.UNSUPPORTED_VERSION)

        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: Exception) {
            return BackupResult.Invalid(BackupError.INVALID_CONTENT)
        }
        val repaired = StateCodec.sanitize(file.state).copy(initialized = true)
        val warnings = LinkedHashSet<BackupWarning>()
        if (repaired != file.state.copy(initialized = true) || !HomeGrid.isConsistent(file.state.home)) warnings += BackupWarning.REPAIRED

        val items = repaired.home.pages.flatMap { it.items }
        val widgets = items.filterIsInstance<WidgetItem>()
        if (widgets.isNotEmpty()) warnings += BackupWarning.WIDGETS_NEED_RESTORE
        warnings += BackupWarning.IMAGES_NOT_INCLUDED

        val referenced = (items.filterIsInstance<AppItem>().map { it.appKey } + repaired.dock.slots.filterNotNull() + repaired.groups.flatMap { it.appKeys }).toSet()
        val missing = if (installedKeys == null) 0 else referenced.count { it !in installedKeys }
        if (missing > 0) warnings += BackupWarning.MISSING_APPS

        val restorable = repaired.copy(
            home = repaired.home.copy(
                pages = repaired.home.pages.map { p -> p.copy(items = p.items.map { if (it is WidgetItem) it.copy(appWidgetId = -1) else it }) },
            ),
        )
        val summary = BackupSummary(
            pages = restorable.home.pages.size,
            apps = items.count { it is AppItem },
            dockApps = restorable.dock.slots.count { it != null },
            groups = items.count { it is GroupItem }.coerceAtLeast(restorable.groups.size),
            widgets = widgets.size,
            createdAt = file.createdAt,
        )
        return BackupResult.Valid(restorable, summary, warnings, missing)
    }

    private fun stripImage(c: pt.hexa.empresarial.domain.model.CenterElement) =
        if (c.kind == CenterKind.IMAGE) c.copy(kind = CenterKind.SYMBOL, imagePath = null) else c.copy(imagePath = null)
}
