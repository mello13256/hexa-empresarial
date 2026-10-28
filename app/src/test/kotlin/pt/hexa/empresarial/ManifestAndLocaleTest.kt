package pt.hexa.empresarial

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.hexa.empresarial.home.HomeActivity
import pt.hexa.empresarial.system.SystemActionsService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class ManifestAndLocaleTest {

    private val app get() = ApplicationProvider.getApplicationContext<HexaEmpresarialApp>()

    @Test
    fun homeActivityAnswersTheHomeIntent() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.packageName)
        val matches = app.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(matches.any { it.activityInfo.name == HomeActivity::class.java.name })
    }

    @Test
    fun homeActivityIsSingleTaskAndHiddenFromRecents() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, HomeActivity::class.java), 0)
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        assertTrue(info.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
    }

    @Test
    fun accessibilityServiceIsNotExported() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, SystemActionsService::class.java), 0)
        assertFalse(info.exported)
        assertEquals(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun interfaceIsPortugueseEvenWithEnglishSystem() {
        val ctx = LocaleOverride.wrap(app)
        assertEquals("pt", ctx.resources.configuration.locales[0].language)
        assertEquals("PT", ctx.resources.configuration.locales[0].country)
        assertEquals("Cancelar", ctx.getString(R.string.action_cancel))
        assertEquals("Ecrã inicial", ctx.getString(R.string.cat_home))
        // The application context itself is already wrapped.
        assertEquals("Definições", app.getString(R.string.settings_title))
    }

    @Test
    fun pluralsFollowPortugueseRules() {
        val res = LocaleOverride.wrap(app).resources
        assertEquals("1 aplicação", res.getQuantityString(R.plurals.app_count, 1, 1))
        assertEquals("0 aplicações", res.getQuantityString(R.plurals.app_count, 0, 0))
        assertEquals("5 aplicações", res.getQuantityString(R.plurals.app_count, 5, 5))
        assertEquals("2 grupos", res.getQuantityString(R.plurals.backup_groups, 2, 2))
    }

    @Test
    fun datesUsePortugueseNames() {
        val date = LocalDate.of(2026, 10, 24)
        val text = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", LocaleOverride.PT_PT).format(date)
        assertEquals("sábado, 24 de outubro", text)
    }
}
