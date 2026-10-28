package pt.hexa.empresarial.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.hexa.empresarial.BuildConfig
import pt.hexa.empresarial.LocaleOverride
import pt.hexa.empresarial.R
import pt.hexa.empresarial.domain.backup.BackupCodec
import pt.hexa.empresarial.domain.backup.BackupError
import pt.hexa.empresarial.domain.backup.BackupResult
import pt.hexa.empresarial.domain.backup.BackupWarning
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.Hint
import pt.hexa.empresarial.ui.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

private sealed interface BackupUi {
    data object Idle : BackupUi
    data class Message(val text: Int, val ok: Boolean) : BackupUi
    data class Confirm(val result: BackupResult.Valid) : BackupUi
    data class Error(val error: BackupError?) : BackupUi
}

/** Export and import of the configuration via the Storage Access Framework. */
@Composable
fun BackupSettingsScreen(env: SettingsEnv) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<BackupUi>(BackupUi.Idle) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = BackupCodec.export(env.container.state.value, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }.isSuccess
            }
            ui = BackupUi.Message(if (ok) R.string.backup_export_done else R.string.backup_export_failed, ok)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readNBytesCompat(MAX_BACKUP_BYTES + 1)
                        if (bytes.size > MAX_BACKUP_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            ui = if (text == null) {
                BackupUi.Error(null)
            } else {
                val installed = env.container.catalog.snapshot.value.let { if (it.loaded) it.byKey.keys else null }
                when (val r = BackupCodec.validate(text, installed)) {
                    is BackupResult.Valid -> BackupUi.Confirm(r)
                    is BackupResult.Invalid -> BackupUi.Error(r.error)
                }
            }
        }
    }

    SectionHeader(stringResource(R.string.backup_export_section))
    Hint(stringResource(R.string.backup_export_text))
    Button(
        onClick = { export.launch(fileName()) },
        colors = ButtonDefaults.buttonColors(containerColor = HexaTheme.colors.accent),
        modifier = Modifier.padding(vertical = 8.dp),
    ) { Text(stringResource(R.string.backup_export)) }

    SectionHeader(stringResource(R.string.backup_import_section))
    Hint(stringResource(R.string.backup_import_text))
    OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.backup_import))
    }

    SectionHeader(stringResource(R.string.backup_limits_section))
    Hint(stringResource(R.string.backup_limit_widgets))
    Hint(stringResource(R.string.backup_limit_images))
    Hint(stringResource(R.string.backup_limit_apps))
    Hint(stringResource(R.string.backup_limit_privacy))

    when (val s = ui) {
        BackupUi.Idle -> Unit
        is BackupUi.Message -> AlertDialog(
            onDismissRequest = { ui = BackupUi.Idle },
            title = { Text(stringResource(if (s.ok) R.string.backup_title_ok else R.string.backup_title_error)) },
            text = { Text(stringResource(s.text)) },
            confirmButton = { TextButton(onClick = { ui = BackupUi.Idle }) { Text(stringResource(R.string.action_ok)) } },
        )
        is BackupUi.Error -> AlertDialog(
            onDismissRequest = { ui = BackupUi.Idle },
            title = { Text(stringResource(R.string.backup_title_invalid)) },
            text = {
                Text(
                    stringResource(
                        when (s.error) {
                            null -> R.string.backup_error_read
                            BackupError.NOT_JSON -> R.string.backup_error_not_json
                            BackupError.WRONG_FORMAT -> R.string.backup_error_format
                            BackupError.UNSUPPORTED_VERSION -> R.string.backup_error_version
                            BackupError.INVALID_CONTENT -> R.string.backup_error_content
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { ui = BackupUi.Idle }) { Text(stringResource(R.string.action_ok)) } },
        )
        is BackupUi.Confirm -> ConfirmImport(
            s.result,
            onCancel = { ui = BackupUi.Idle },
            onApply = {
                // Widgets of the current layout are released; the backup's widgets come back as placeholders.
                env.container.state.value.home.pages.flatMap { it.items }.filterIsInstance<WidgetItem>()
                    .filter { !it.needsRestore }.forEach { env.container.widgets.delete(it.appWidgetId) }
                env.container.state.set(s.result.state.copy(initialized = true))
                scope.launch { env.container.state.flush() }
                ui = BackupUi.Message(R.string.backup_import_done, true)
            },
        )
    }
}

@Composable
private fun ConfirmImport(r: BackupResult.Valid, onCancel: () -> Unit, onApply: () -> Unit) {
    val colors = HexaTheme.colors
    val sum = r.summary
    val date = if (sum.createdAt > 0) {
        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", LocaleOverride.PT_PT).format(Instant.ofEpochMilli(sum.createdAt).atZone(ZoneId.systemDefault()))
    } else {
        stringResource(R.string.backup_date_unknown)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_confirm_created, date), style = MaterialTheme.typography.bodyMedium)
                Text(pluralStringResource(R.plurals.backup_pages, sum.pages, sum.pages), style = MaterialTheme.typography.bodyMedium)
                Text(pluralStringResource(R.plurals.app_count, sum.apps, sum.apps), style = MaterialTheme.typography.bodyMedium)
                Text(pluralStringResource(R.plurals.backup_groups, sum.groups, sum.groups), style = MaterialTheme.typography.bodyMedium)
                Text(pluralStringResource(R.plurals.backup_widgets, sum.widgets, sum.widgets), style = MaterialTheme.typography.bodyMedium)
                for (w in r.warnings.sortedBy { it.ordinal }) {
                    val text = when (w) {
                        BackupWarning.WIDGETS_NEED_RESTORE -> stringResource(R.string.backup_warn_widgets)
                        BackupWarning.IMAGES_NOT_INCLUDED -> stringResource(R.string.backup_warn_images)
                        BackupWarning.MISSING_APPS -> pluralStringResource(R.plurals.backup_warn_missing, r.missingApps, r.missingApps)
                        BackupWarning.REPAIRED -> stringResource(R.string.backup_warn_repaired)
                    }
                    Text("• $text", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 6.dp))
                }
                Text(
                    stringResource(R.string.backup_confirm_overwrite),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.backup_apply), color = colors.danger) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun fileName(): String =
    "hexa-empresarial-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").format(java.time.LocalDateTime.now()) + ".json"

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
