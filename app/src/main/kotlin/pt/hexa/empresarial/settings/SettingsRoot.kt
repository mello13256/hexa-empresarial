package pt.hexa.empresarial.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pt.hexa.empresarial.R
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.theme.HexaLogo
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphButton
import pt.hexa.empresarial.ui.GlyphIcon

/** The twelve settings categories, daily ones first and advanced options last. */
enum class Category(val title: Int, val summary: Int, val glyph: Glyph) {
    HOME(R.string.cat_home, R.string.cat_home_summary, Glyph.PAGE),
    ICONS(R.string.cat_icons, R.string.cat_icons_summary, Glyph.GRID),
    DOCK(R.string.cat_dock, R.string.cat_dock_summary, Glyph.WIDGET),
    DRAWER(R.string.cat_drawer, R.string.cat_drawer_summary, Glyph.SEARCH),
    ORBITAL(R.string.cat_orbital, R.string.cat_orbital_summary, Glyph.ORBIT),
    GROUPS(R.string.cat_groups, R.string.cat_groups_summary, Glyph.GROUP),
    WIDGETS(R.string.cat_widgets, R.string.cat_widgets_summary, Glyph.WIDGET),
    GESTURES(R.string.cat_gestures, R.string.cat_gestures_summary, Glyph.EDIT),
    APPEARANCE(R.string.cat_appearance, R.string.cat_appearance_summary, Glyph.SETTINGS),
    ANIMATIONS(R.string.cat_animations, R.string.cat_animations_summary, Glyph.ORBIT),
    BACKUPS(R.string.cat_backups, R.string.cat_backups_summary, Glyph.CHECK),
    ADVANCED(R.string.cat_advanced, R.string.cat_advanced_summary, Glyph.SETTINGS),
}

/** Settings shared state: the launcher state and a way to change it. */
class SettingsEnv(val activity: SettingsActivity, val state: LauncherState) {
    val container = activity.container
    fun update(transform: (LauncherState) -> LauncherState) = container.state.update(transform)
}

@Composable
fun SettingsRoot(activity: SettingsActivity, onClose: () -> Unit) {
    val state by activity.container.state.flow.collectAsState()
    var current by rememberSaveable { mutableStateOf<Category?>(null) }
    BackHandler(enabled = current != null) { current = null }
    HexaTheme(state.appearance, state.animations) {
        Surface(Modifier.fillMaxSize(), color = HexaTheme.colors.background) {
            val env = SettingsEnv(activity, state)
            val cat = current
            if (cat == null) {
                CategoryList(env, onOpen = { current = it }, onClose = onClose)
            } else {
                SettingsPage(stringResource(cat.title), onBack = { current = null }) {
                    when (cat) {
                        Category.HOME -> HomeSettings(env)
                        Category.ICONS -> IconSettingsScreen(env)
                        Category.DOCK -> DockSettingsScreen(env)
                        Category.DRAWER -> DrawerSettingsScreen(env)
                        Category.ORBITAL -> OrbitalSettingsScreen(env)
                        Category.GROUPS -> GroupSettingsScreen(env)
                        Category.WIDGETS -> WidgetSettingsScreen(env)
                        Category.GESTURES -> GestureSettingsScreen(env)
                        Category.APPEARANCE -> AppearanceSettingsScreen(env)
                        Category.ANIMATIONS -> AnimationSettingsScreen(env)
                        Category.BACKUPS -> BackupSettingsScreen(env)
                        Category.ADVANCED -> AdvancedSettingsScreen(env)
                    }
                }
            }
        }
    }
}

/** Common page frame: title with a back button, scrollable content. */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = HexaTheme.colors
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.CLOSE, stringResource(R.string.action_back), onBack)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun CategoryList(env: SettingsEnv, onOpen: (Category) -> Unit, onClose: () -> Unit) {
    val colors = HexaTheme.colors
    var isDefault by remember { mutableStateOf(env.activity.isDefaultHome()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = env.activity.isDefaultHome() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = env.activity.isDefaultHome() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.CLOSE, stringResource(R.string.action_close), onClose)
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { HexaLogo() }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        if (!isDefault) {
            val shape = RoundedCornerShape(HexaTheme.tokens.radiusMedium)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(colors.accent.copy(alpha = 0.12f)).border(1.dp, colors.accent.copy(alpha = 0.5f), shape).padding(16.dp),
            ) {
                Text(stringResource(R.string.default_home_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(R.string.default_home_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { request.launch(env.activity.defaultHomeIntent()) }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                    Text(stringResource(R.string.default_home_action))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Category.entries.forEach { cat ->
            if (cat == Category.ADVANCED) Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium))
                    .clickable { onOpen(cat) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceHigh), contentAlignment = Alignment.Center) {
                    GlyphIcon(cat.glyph, colors.accent, size = 20.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(cat.title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(cat.summary), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_footer, pt.hexa.empresarial.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
    }
}
