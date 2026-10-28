package pt.hexa.empresarial.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Third-party icon packs following the common `appfilter.xml` convention (ADW/Nova/GO intent
 * actions). The mapping `ComponentInfo{package/class}` -> drawable name is read once per pack.
 */
class IconPacks(context: Context, private val io: CoroutineDispatcher) {

    private val pm = context.packageManager
    private val mappings = ConcurrentHashMap<String, Map<String, String>>()

    private val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

    suspend fun installed(): List<IconPackInfo> = withContext(io) {
        actions.flatMap { a ->
            try {
                pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }
        }.map { it.activityInfo.packageName }.distinct().map { pkg ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            IconPackInfo(pkg, label)
        }.sortedBy { it.label.lowercase() }
    }

    /** Drawable for [component] from [pack], or null when the pack does not theme it. Blocking. */
    @SuppressLint("DiscouragedApi")
    fun drawable(pack: String, component: ComponentName, density: Int): Drawable? {
        val map = mappings.getOrPut(pack) { load(pack) }
        val name = map["${component.packageName}/${component.className}"] ?: map[component.packageName] ?: return null
        return try {
            val res = pm.getResourcesForApplication(pack)
            val id = res.getIdentifier(name, "drawable", pack)
            if (id == 0) null else res.getDrawableForDensity(id, density, null)
        } catch (e: Exception) {
            null
        }
    }

    fun forget(pack: String) {
        mappings.remove(pack)
    }

    @SuppressLint("DiscouragedApi")
    private fun load(pack: String): Map<String, String> {
        val res: Resources = try {
            pm.getResourcesForApplication(pack)
        } catch (e: Exception) {
            return emptyMap()
        }
        val out = HashMap<String, String>()
        val parser: XmlPullParser? = try {
            val id = res.getIdentifier("appfilter", "xml", pack)
            if (id != 0) {
                res.getXml(id)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
            }
        } catch (e: Exception) {
            null
        }
        parser ?: return emptyMap()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null && comp.startsWith("ComponentInfo{")) {
                        val flat = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val pkg = flat.substringBefore('/')
                        val cls = flat.substringAfter('/', "").let { if (it.startsWith(".")) pkg + it else it }
                        out["$pkg/$cls"] = drawable
                        out.putIfAbsent(pkg, drawable)
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // A malformed pack themes what it could parse.
        }
        return out
    }
}
