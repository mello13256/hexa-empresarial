package pt.hexa.empresarial.home

import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import pt.hexa.empresarial.LocaleOverride
import pt.hexa.empresarial.R
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.ClockSettings
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.domain.model.LabelAlignment
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.groups.CenterView
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.theme.HexaType
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphIcon
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** Converts compose window bounds to an android Rect (used for launch animations and menus). */
fun LayoutCoordinates.windowRect(): android.graphics.Rect = boundsInWindow().let {
    android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
}

/**
 * Classic launcher touch handling for one item: tap opens, long press shows the menu, moving after
 * the long press drags the item. Positions passed to the drag callbacks are in root coordinates.
 */
fun Modifier.itemGestures(
    key: Any,
    onTap: (android.graphics.Rect) -> Unit,
    onLongPress: (android.graphics.Rect) -> Unit,
    onDragStart: (root: Offset, grab: Offset) -> Unit,
    onDrag: (root: Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    this
        .onGloballyPositioned { coords = it }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                var finished = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    val u = waitForUpOrCancellation()
                    finished = true
                    u
                }
                val c = coords ?: return@awaitEachGesture
                if (finished) {
                    if (up != null) {
                        up.consume()
                        onTap(c.windowRect())
                    }
                    return@awaitEachGesture
                }
                // Long press.
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress(c.windowRect())
                val origin = c.positionInRoot()
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val root = origin + change.position
                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        onDragStart(root, down.position)
                    }
                    if (dragging) {
                        change.consume()
                        onDrag(root)
                    }
                }
                if (dragging) onDragEnd()
            }
        }
}

private val labelShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 4f)

/** App icon with optional label, as on any Android home screen. [badge] sits on the icon's corner. */
@Composable
fun AppCell(
    appKey: String,
    label: String,
    icons: IconSettings,
    missing: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = icons.showLabels,
    labelColor: Color = Color.White,
    badge: (@Composable () -> Unit)? = null,
) {
    val description = if (missing) stringResource(R.string.a11y_app_missing, label) else label
    Column(
        modifier
            .fillMaxSize()
            .padding(icons.spacingDp.dp / 2)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button },
        horizontalAlignment = if (icons.alignment == LabelAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(icons.sizeDp.dp), contentAlignment = Alignment.Center) {
            AppIcon(appKey, icons.sizeDp.dp, icons)
            if (missing) {
                Box(Modifier.size(icons.sizeDp.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text("?", color = Color.White, style = HexaType.label.copy(fontSize = 20.sp))
                }
            }
            if (badge != null) Box(Modifier.align(Alignment.TopStart).offset((-12).dp, (-12).dp)) { badge() }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = labelColor,
                style = HexaType.label.copy(fontSize = icons.labelSizeSp.sp, shadow = labelShadow),
                maxLines = icons.labelLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (icons.alignment == LabelAlignment.CENTER) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** Optional clock: time, weekday and date in Portuguese, on an optional dark card. */
@Composable
fun ClockCell(settings: ClockSettings, surfaceOpacity: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = HexaTheme.colors
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake up right after the next minute starts.
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50)
        }
    }
    val use24 = settings.use24h ?: DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm", LocaleOverride.PT_PT))
    val ampm = if (use24) "" else now.format(DateTimeFormatter.ofPattern("a", LocaleOverride.PT_PT))
    val datePattern = when {
        settings.showWeekday && settings.showDate -> "EEEE, d 'de' MMMM"
        settings.showWeekday -> "EEEE"
        settings.showDate -> "d 'de' MMMM 'de' yyyy"
        else -> null
    }
    val date = datePattern?.let { now.format(DateTimeFormatter.ofPattern(it, LocaleOverride.PT_PT)).replaceFirstChar { c -> c.titlecase(LocaleOverride.PT_PT) } }
    val shape = RoundedCornerShape(HexaTheme.tokens.radiusLarge)
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(if (settings.showCard) Modifier.clip(shape).background(Color.Black.copy(alpha = surfaceOpacity)) else Modifier)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (settings.showTime) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, style = HexaType.clock.copy(color = Color.White, shadow = if (settings.showCard) null else labelShadow))
                if (ampm.isNotEmpty()) Text(" $ampm", style = HexaType.date.copy(color = Color.White), modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        if (date != null) {
            Text(
                date,
                style = HexaType.date.copy(brush = colors.silver(), shadow = if (settings.showCard) null else labelShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Search entry on the home screen: dark pill with a thin metallic outline. Touches are handled by
 * the item gestures passed in [modifier] (tap opens, long press shows the menu and drags), so this
 * only adds the accessibility action.
 */
@Composable
fun SearchBarCell(surfaceOpacity: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HexaTheme.colors
    val shape = RoundedCornerShape(HexaTheme.tokens.radiusPill)
    val hint = stringResource(R.string.search_apps_hint)
    Row(
        modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = surfaceOpacity))
            .border(1.dp, colors.metallic(), shape)
            .semantics {
                contentDescription = hint
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.SEARCH, Color.White.copy(alpha = 0.9f), size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(hint, color = Color.White.copy(alpha = 0.7f), style = TextStyle(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Compact group: the centre element with up to six app icons around it, and the group name. */
@Composable
fun GroupCell(group: AppGroup, icons: IconSettings, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null) {
    val colors = HexaTheme.colors
    val size = icons.sizeDp.dp
    Column(modifier.fillMaxSize().padding(icons.spacingDp.dp / 2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(size)) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, colors.metallic(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CenterView(group.center, size * 0.42f)
                val shown = group.appKeys.take(6)
                shown.forEachIndexed { i, key ->
                    val a = -Math.PI / 2 + i * 2 * Math.PI / shown.size.coerceAtLeast(1)
                    val r = size.value * 0.34f
                    AppIcon(key, size * 0.26f, icons, Modifier.offset((r * cos(a)).dp, (r * sin(a)).dp))
                }
            }
            if (badge != null) Box(Modifier.align(Alignment.TopStart).offset((-12).dp, (-12).dp)) { badge() }
        }
        if (icons.showLabels) {
            Spacer(Modifier.height(4.dp))
            Text(group.name, color = Color.White, style = HexaType.label.copy(fontSize = icons.labelSizeSp.sp, shadow = labelShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A real widget (AppWidgetHostView). The view is created once per widget id and moved between
 * parents when pages are recomposed. Backed-up widgets that still need to be re-added show a
 * placeholder that starts the system's add-widget flow.
 */
@Composable
fun WidgetCell(item: WidgetItem, widthDp: Float, heightDp: Float, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = HexaTheme.colors
    if (item.needsRestore) {
        val text = stringResource(R.string.widget_restore_placeholder, item.label.ifEmpty { stringResource(R.string.widget_generic) })
        Box(
            modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(HexaTheme.tokens.radiusMedium))
                .clickable(onClick = onRestore)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = Color.White, textAlign = TextAlign.Center, style = TextStyle(fontSize = 13.sp)) }
        return
    }
    val manager = context.container.widgets
    LaunchedEffect(item.appWidgetId, widthDp, heightDp) { manager.reportSize(item.appWidgetId, widthDp, heightDp) }
    AndroidView(
        factory = { ctx ->
            val view = manager.view(ctx, item.appWidgetId) ?: FrameLayout(ctx)
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier.fillMaxSize().padding(4.dp),
    )
}

/** Rectangle with handles shown around a widget selected in edit mode. */
@Composable
fun ResizeFrame(onResize: (dw: Int, dh: Int) -> Unit, cellWidthPx: Float, cellHeightPx: Float, canW: Boolean, canH: Boolean, modifier: Modifier = Modifier) {
    val colors = HexaTheme.colors
    val shape = RoundedCornerShape(HexaTheme.tokens.radiusSmall)
    Box(modifier.fillMaxSize().border(2.dp, colors.accent, shape)) {
        if (canW) {
            Handle(Modifier.align(Alignment.CenterEnd)) { dx, _ -> (dx / cellWidthPx).let { if (kotlin.math.abs(it) >= 0.5f) onResize(kotlin.math.round(it).toInt(), 0) } }
        }
        if (canH) {
            Handle(Modifier.align(Alignment.BottomCenter)) { _, dy -> (dy / cellHeightPx).let { if (kotlin.math.abs(it) >= 0.5f) onResize(0, kotlin.math.round(it).toInt()) } }
        }
    }
}

@Composable
private fun Handle(modifier: Modifier, onRelease: (Float, Float) -> Unit) {
    val colors = HexaTheme.colors
    val description = stringResource(R.string.a11y_resize_handle)
    Box(
        modifier
            .size(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var total = Offset.Zero
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        total += ch.position - ch.previousPosition
                        ch.consume()
                    }
                    onRelease(total.x, total.y)
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawCircle(colors.accent)
            drawCircle(Color.Black, radius = size.minDimension / 4f)
        }
    }
}

/** Remove target shown at the top of the screen while dragging. */
@Composable
fun RemoveZone(active: Boolean, modifier: Modifier = Modifier) {
    val colors = HexaTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(HexaTheme.tokens.radiusPill))
            .background(if (active) colors.danger.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.TRASH, Color.White, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_remove), color = Color.White)
    }
}

/** Accessibility actions for items, mirroring tap and long press. */
fun Modifier.itemSemantics(label: String, openLabel: String, menuLabel: String, onOpen: () -> Unit, onMenu: () -> Unit): Modifier = semantics {
    contentDescription = label
    onClick(openLabel) { onOpen(); true }
    onLongClick(menuLabel) { onMenu(); true }
}

