package pt.hexa.empresarial.domain

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.hexa.empresarial.domain.backup.BackupCodec
import pt.hexa.empresarial.domain.backup.BackupError
import pt.hexa.empresarial.domain.backup.BackupResult
import pt.hexa.empresarial.domain.backup.BackupWarning
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.AppRole
import pt.hexa.empresarial.domain.model.Cell
import pt.hexa.empresarial.domain.model.CenterElement
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.DefaultLayout
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.GestureAction
import pt.hexa.empresarial.domain.model.GestureBinding
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.GroupShape
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.HomePage
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.ThemeMode
import pt.hexa.empresarial.domain.model.WallpaperMode
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.domain.persist.DebouncedState
import pt.hexa.empresarial.domain.persist.StateCodec
import pt.hexa.empresarial.domain.persist.StateStore
import java.io.File

class PersistenceAndBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rich = LauncherState(
        home = HomeLayout(
            5, 6,
            listOf(
                HomePage("p1", listOf(AppItem("a", Cell(0, 0), "com.a/.Main", "A"), WidgetItem("w", Cell(1, 0, 3, 2), 42, "com.w/.Provider", "Tempo"))),
                HomePage("p2", listOf(GroupItem("gi", Cell(2, 2), "g1"))),
            ),
            defaultPage = 1,
        ),
        groups = listOf(AppGroup("g1", "Trabalho", listOf("com.a/.Main", "com.b/.Main"), GroupShape.HEXAGON, CenterElement(CenterKind.IMAGE, imagePath = "img.webp"))),
        drawer = LauncherState().drawer.copy(view = DrawerView.GRID, gridColumns = 5),
        appearance = LauncherState().appearance.copy(theme = ThemeMode.DARK, wallpaper = WallpaperMode.IMAGE, wallpaperImage = "bg.webp"),
        gestures = LauncherState().gestures.copy(doubleTap = GestureBinding(GestureAction.OPEN_APP, "com.a/.Main")),
        dock = LauncherState().dock.copy(slots = listOf("com.a/.Main", null, "com.c/.Main", null, null)),
        initialized = true,
    )

    @Test
    fun stateRoundTrip() {
        assertEquals(rich, StateCodec.decode(StateCodec.encode(rich)))
    }

    @Test
    fun sanitizeFixesOverlapsAndBadReferences() {
        val broken = rich.copy(
            home = rich.home.copy(
                columns = 99,
                pages = listOf(
                    HomePage(
                        "p1",
                        listOf(
                            AppItem("a", Cell(0, 0), "x/.A"),
                            AppItem("b", Cell(0, 0), "y/.B"), // overlaps a
                            AppItem("a", Cell(2, 2), "dup/.D"), // duplicate id
                            GroupItem("gx", Cell(3, 3), "missing-group"),
                        ),
                    ),
                ),
            ),
            dock = rich.dock.copy(slots = listOf("only/.One")),
        )
        val fixed = StateCodec.sanitize(broken)
        assertEquals(HomeLayout.MAX_COLUMNS, fixed.home.columns)
        assertTrue(HomeGrid.isConsistent(fixed.home))
        val ids = fixed.home.pages.flatMap { it.items }.map { it.id }
        assertEquals(listOf("a", "b"), ids.sorted())
        assertEquals(3, fixed.dock.slots.size)
    }

    @Test
    fun storeRecoversFromCorruptedMainFile() {
        val file = File(tmp.root, "estado.json")
        val store = StateStore(file)
        store.write(StateCodec.encode(rich))
        store.write(StateCodec.encode(rich.copy(initialized = false))) // previous version becomes .bak
        file.writeText("{ corrompido")
        val loaded = store.load(StateCodec::decode)
        assertEquals(StateStore.Source.BACKUP, loaded.source)
        assertTrue(loaded.recoveredFromCorruption)
        assertEquals(rich, loaded.value)
    }

    @Test
    fun debouncedStateWritesOnceAndSurvivesRestart() = runTest {
        val file = File(tmp.root, "estado.json")
        val io = StandardTestDispatcher(testScheduler)
        val s = DebouncedState(StateStore(file), LauncherState(), StateCodec::encode, StateCodec::decode, this, io, 500)
        s.load()
        assertFalse(s.hadSavedState)
        repeat(30) { i -> s.update { it.copy(drawer = it.drawer.copy(gridColumns = 3 + i % 4)) } }
        s.set(rich)
        advanceTimeBy(499); runCurrent()
        assertEquals(0, s.writes)
        advanceUntilIdle()
        assertEquals(1, s.writes)
        s.update { it } // no change, no write
        s.flush()
        assertEquals(1, s.writes)

        val restarted = DebouncedState(StateStore(file), LauncherState(), StateCodec::encode, StateCodec::decode, this, io, 500)
        restarted.load()
        assertTrue(restarted.hadSavedState)
        assertEquals(rich, restarted.value)
    }

    @Test
    fun backupExportImportWithWarnings() {
        val text = BackupCodec.export(rich, "1.0.0", 1_700_000_000_000)
        val r = BackupCodec.validate(text, installedKeys = setOf("com.a/.Main")) as BackupResult.Valid
        assertEquals(2, r.summary.pages)
        assertEquals(1, r.summary.widgets)
        assertEquals(1, r.summary.groups)
        assertEquals(2, r.summary.dockApps)
        assertTrue(BackupWarning.WIDGETS_NEED_RESTORE in r.warnings)
        assertTrue(BackupWarning.IMAGES_NOT_INCLUDED in r.warnings)
        assertTrue(BackupWarning.MISSING_APPS in r.warnings)
        assertEquals(2, r.missingApps) // com.b in the group, com.c in the dock
        val w = r.state.home.pages[0].items.filterIsInstance<WidgetItem>().single()
        assertTrue(w.needsRestore)
        assertEquals(WallpaperMode.SYSTEM, r.state.appearance.wallpaper)
        assertEquals(CenterKind.SYMBOL, r.state.groups.single().center.kind)
        assertEquals(DrawerView.GRID, r.state.drawer.view)
        assertEquals(rich.gestures, r.state.gestures)
    }

    @Test
    fun invalidBackupsAreRejected() {
        assertEquals(BackupError.NOT_JSON, (BackupCodec.validate("isto não é json") as BackupResult.Invalid).error)
        assertEquals(BackupError.WRONG_FORMAT, (BackupCodec.validate("""{"format":"outro","formatVersion":1}""") as BackupResult.Invalid).error)
        assertEquals(
            BackupError.UNSUPPORTED_VERSION,
            (BackupCodec.validate("""{"format":"hexa-empresarial-edition/backup","formatVersion":99,"state":{}}""") as BackupResult.Invalid).error,
        )
        assertEquals(
            BackupError.INVALID_CONTENT,
            (BackupCodec.validate("""{"format":"hexa-empresarial-edition/backup","formatVersion":1,"state":"x"}""") as BackupResult.Invalid).error,
        )
    }

    @Test
    fun defaultLayoutUsesRolesAndFallbacks() {
        val roles = mapOf(AppRole.DIALER to ("tel/.T" to "Telefone"), AppRole.CAMERA to ("cam/.C" to "Câmara"), AppRole.GALLERY to ("gal/.G" to "Galeria"))
        val fallback = (1..20).map { "f$it/.F" to "App $it" }
        val s = DefaultLayout.create(LauncherState(), { roles[it] }, fallback)
        assertTrue(s.initialized)
        assertEquals(listOf("tel/.T", null, null, "cam/.C"), s.dock.slots)
        val apps = s.home.pages[0].items.filterIsInstance<AppItem>()
        assertEquals(8, apps.size)
        assertEquals("gal/.G", apps.first().appKey)
        assertTrue(HomeGrid.isConsistent(s.home))
    }
}
