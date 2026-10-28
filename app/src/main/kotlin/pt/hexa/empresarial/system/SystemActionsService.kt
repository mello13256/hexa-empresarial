package pt.hexa.empresarial.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import pt.hexa.empresarial.domain.model.GestureAction
import java.lang.ref.WeakReference

/**
 * Optional accessibility service used only to perform system actions that have no other public
 * API for launchers (notification panel, quick settings, recent apps, lock screen). It does not
 * read window content and ignores every accessibility event.
 */
class SystemActionsService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private var instance: WeakReference<SystemActionsService>? = null

        val isActive: Boolean get() = instance?.get() != null

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = ComponentName(context, SystemActionsService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Returns false when the service is not enabled or the action is not supported. */
        fun perform(action: GestureAction): Boolean {
            val service = instance?.get() ?: return false
            val global = when (action) {
                GestureAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                GestureAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                GestureAction.RECENT_APPS -> GLOBAL_ACTION_RECENTS
                GestureAction.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
                else -> return false
            }
            return service.performGlobalAction(global)
        }
    }
}
