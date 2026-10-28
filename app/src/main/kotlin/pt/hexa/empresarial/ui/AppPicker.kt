package pt.hexa.empresarial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.hexa.empresarial.R
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.theme.HexaTheme

/**
 * Searchable list of installed apps. With [multiple] it returns a selection (groups); otherwise
 * the first tap returns (dock slot, gesture app...).
 */
@Composable
fun AppPickerDialog(
    title: String,
    icons: IconSettings,
    onDismiss: () -> Unit,
    onPick: (List<AppEntry>) -> Unit,
    multiple: Boolean = false,
    initiallySelected: List<String> = emptyList(),
    maxSelection: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val catalog by context.container.catalog.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initiallySelected) }
    val colors = HexaTheme.colors
    val list = remember(catalog, query) {
        if (query.isBlank()) AppOrdering.alphabetical(catalog.entries) else catalog.search.search(query, 100)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    if (multiple) {
                        TextButton(onClick = { onPick(selected.mapNotNull { k -> catalog.byKey[k]?.entry }) }) { Text(stringResource(R.string.action_save)) }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (multiple) {
                    Hint(context.resources.getQuantityString(R.plurals.selected_apps, selected.size, selected.size))
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(list, key = { it.key }) { app ->
                        val isSelected = app.key in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(HexaTheme.tokens.radiusSmall))
                                .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.background)
                                .clickable {
                                    if (!multiple) {
                                        onPick(listOf(app))
                                    } else {
                                        selected = if (isSelected) selected - app.key else if (selected.size < maxSelection) selected + app.key else selected
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp, icons)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (app.isWorkProfile) stringResource(R.string.app_work_profile, app.label) else app.label,
                                color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (multiple) {
                                Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}
