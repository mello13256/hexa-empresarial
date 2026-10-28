package pt.hexa.empresarial.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class WidgetOption(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val packageName: String,
)

/**
 * Hosts real third-party widgets through [AppWidgetHost]. Views are created once per widget id
 * and reused across recompositions; the host listens for updates only while the home screen is
 * started.
 */
class WidgetHostManager(context: Context, private val io: CoroutineDispatcher) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = object : AppWidgetHost(appContext, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            LongPressWidgetView(context)
    }

    private val views = HashMap<Int, AppWidgetHostView>()

    /** Called when the user long-presses a widget (to move, resize or remove it). */
    var onWidgetLongPress: ((Int) -> Unit)? = null

    fun startListening() = try {
        host.startListening()
    } catch (e: Exception) {
        // Some OEM builds throw while a profile is locked; widgets refresh at the next start.
    }

    fun stopListening() = try {
        host.stopListening()
    } catch (e: Exception) {
    }

    suspend fun options(context: Context): List<WidgetOption> = withContext(io) {
        val pm = context.packageManager
        val providers = try {
            manager.installedProviders
        } catch (e: Exception) {
            emptyList()
        }
        providers.map { info ->
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            } catch (e: Exception) {
                info.provider.packageName
            }
            WidgetOption(info, info.loadLabel(pm) ?: appLabel, appLabel, info.provider.packageName)
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun preview(context: Context, info: AppWidgetProviderInfo): Drawable? = try {
        info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi) ?: info.loadIcon(context, context.resources.displayMetrics.densityDpi)
    } catch (e: Exception) {
        null
    }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun findProvider(flattened: String): AppWidgetProviderInfo? {
        val cn = ComponentName.unflattenFromString(flattened) ?: return null
        return try {
            manager.installedProviders.firstOrNull { it.provider == cn }
        } catch (e: Exception) {
            null
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    /** True if the system let us bind without asking (otherwise the bind dialog must be shown). */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
    } catch (e: Exception) {
        false
    }

    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= 28 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        ) {
            return false
        }
        return true
    }

    fun delete(id: Int) {
        views.remove(id)
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun view(context: Context, id: Int): AppWidgetHostView? {
        views[id]?.let { return it }
        val info = providerInfo(id) ?: return null
        val v = host.createView(context.applicationContext, id, info)
        (v as? LongPressWidgetView)?.onLongPress = { onWidgetLongPress?.invoke(id) }
        views[id] = v
        return v
    }

    /** Tells the widget its size in dp so it can choose a layout. */
    fun reportSize(id: Int, widthDp: Float, heightDp: Float) {
        val v = views[id] ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                v.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        } catch (e: Exception) {
        }
    }

    /** Cells needed by a provider for a given cell size in dp. */
    fun defaultSpan(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, density: Float, columns: Int, rows: Int): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
        }
        val w = (info.minWidth / density / cellWidthDp).let { kotlin.math.ceil(it).toInt() }
        val h = (info.minHeight / density / cellHeightDp).let { kotlin.math.ceil(it).toInt() }
        return w.coerceIn(1, columns) to h.coerceIn(1, rows)
    }

    /** Smallest span the widget accepts when resized. */
    fun minSpan(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, density: Float): Pair<Int, Int> {
        val mw = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
        val mh = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        return kotlin.math.ceil(mw / density / cellWidthDp).toInt().coerceAtLeast(1) to kotlin.math.ceil(mh / density / cellHeightDp).toInt().coerceAtLeast(1)
    }

    fun canResize(info: AppWidgetProviderInfo, horizontal: Boolean): Boolean =
        info.resizeMode and (if (horizontal) AppWidgetProviderInfo.RESIZE_HORIZONTAL else AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

    companion object {
        const val HOST_ID = 0x48455841 // "HEXA"
    }
}

/** Host view that reports long presses while letting the widget receive normal touches. */
class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val trigger = Runnable {
        fired = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                postDelayed(trigger, timeout)
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(trigger)
        }
        // Once the long press fired, take the gesture away from the widget.
        return fired
    }
}
