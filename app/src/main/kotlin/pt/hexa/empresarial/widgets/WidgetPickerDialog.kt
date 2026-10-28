package pt.hexa.empresarial.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import pt.hexa.empresarial.R
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.search.foldForSearch
import pt.hexa.empresarial.home.HomeViewModel
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.Hint

/** Lists the widgets installed apps offer (real AppWidgetProviderInfo), with previews and sizes. */
@Composable
fun WidgetPickerDialog(vm: HomeViewModel, page: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val colors = HexaTheme.colors
    val manager = context.container.widgets
    val options by produceState<List<WidgetOption>?>(null) { value = manager.options(context) }
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = vm::closeWidgetPicker, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.widgets_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::closeWidgetPicker) { Text(stringResource(R.string.action_cancel)) }
                }
                Hint(stringResource(R.string.widgets_hint))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.widgets_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = options
                when {
                    list == null -> CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally), color = colors.accent)
                    list.isEmpty() -> Hint(stringResource(R.string.widgets_none))
                    else -> {
                        val q = foldForSearch(query)
                        val filtered = if (q.isEmpty()) list else list.filter { foldForSearch(it.appLabel + " " + it.label).contains(q) }
                        LazyColumn {
                            items(filtered, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { opt ->
                                WidgetRow(opt) { vm.addWidget(opt.info, page, density) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(opt: WidgetOption, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = HexaTheme.colors
    val manager = context.container.widgets
    val preview by produceState<ImageBitmap?>(null, opt.info.provider) {
        value = try {
            manager.preview(context, opt.info)?.toBitmap(240, 160)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val p = preview
        if (p != null) {
            Image(p, null, Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Spacer(Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceHigh))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(opt.label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(opt.appLabel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
        }
    }
}
