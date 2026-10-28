package pt.hexa.empresarial.drawer

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.home.AppCell
import pt.hexa.empresarial.home.HomeViewModel
import pt.hexa.empresarial.home.MenuSource
import pt.hexa.empresarial.home.windowRect

/**
 * Conventional, predictable grid of all apps (alphabetical by default). Pulling down at the top of
 * the list closes the drawer, like on Android.
 */
@Composable
fun GridDrawer(
    vm: HomeViewModel,
    state: LauncherState,
    apps: List<AppEntry>,
    catalog: CatalogSnapshot,
    onPull: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pull by rememberUpdatedState(onPull)
    val release by rememberUpdatedState(onRelease)
    val connection = remember {
        object : NestedScrollConnection {
            var pulled = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled = true
                    pull(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulled && available.y < 0f) {
                    pull(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled) {
                    pulled = false
                    release()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.drawer_empty), color = LocalDrawerFg.current.copy(alpha = 0.7f)) }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.drawer.gridColumns),
        modifier = modifier.fillMaxSize().nestedScroll(connection),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val info = catalog.byKey[app.key]
            Box(
                Modifier
                    .height((state.icons.sizeDp + if (state.drawer.gridShowLabels) 36 else 16).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .onGloballyPositioned { coords = it }
                    .combinedClickable(
                        onClick = { vm.launch(app.key, coords?.windowRect()) },
                        onLongClick = { coords?.let { vm.showAppMenu(app.key, MenuSource.DRAWER, it.windowRect()) } },
                    ),
            ) {
                AppCell(app.key, app.label, state.icons, missing = info?.enabled == false, showLabel = state.drawer.gridShowLabels, labelColor = LocalDrawerFg.current)
            }
        }
    }
}
