package pt.hexa.empresarial.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pt.hexa.empresarial.domain.apps.AppKey
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.domain.model.IconShape
import pt.hexa.empresarial.domain.model.IconStyle
import kotlin.math.min

/** How an icon must be rendered; part of the cache key. */
data class IconSpec(
    val sizePx: Int,
    val shape: IconShape,
    val style: IconStyle,
    val pack: String?,
    /** ARGB used by the monochrome style. */
    val tint: Int,
    /** ARGB used by the gold style. */
    val accent: Int,
) {
    companion object {
        fun from(s: IconSettings, sizePx: Int, tint: Int, accent: Int) = IconSpec(sizePx, s.shape, s.style, s.iconPack, tint, accent)
    }
}

/**
 * Renders app icons into bitmaps (icon pack -> shape mask -> style) and keeps them in a byte
 * bounded LRU cache. Rendering runs on a background dispatcher.
 */
class IconProvider(
    context: Context,
    private val catalog: AppCatalog,
    private val packs: IconPacks,
    private val io: CoroutineDispatcher,
) {
    private val densityDpi = context.resources.displayMetrics.densityDpi

    private val cache = object : LruCache<String, Bitmap>(min(Runtime.getRuntime().maxMemory() / 8, 40L * 1024 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    init {
        catalog.onPackageChanged { pkg -> cache.snapshot().keys.filter { AppKey.packageName(it) == pkg }.forEach { cache.remove(it) } }
    }

    fun peek(key: String, spec: IconSpec): Bitmap? = cache.get(cacheKey(key, spec))

    suspend fun load(key: String, spec: IconSpec): Bitmap? {
        val ck = cacheKey(key, spec)
        cache.get(ck)?.let { return it }
        return withContext(io) {
            cache.get(ck)?.let { return@withContext it }
            val app = catalog.snapshot.value.resolve(key) ?: return@withContext null
            val info = catalog.activityInfo(app.key) ?: return@withContext null
            val themed = spec.pack?.let { packs.drawable(it, app.component, densityDpi) }
            val drawable = themed ?: try {
                info.getBadgedIcon(densityDpi)
            } catch (e: Exception) {
                null
            } ?: return@withContext null
            val bmp = render(drawable, spec, fromPack = themed != null)
            cache.put(ck, bmp)
            bmp
        }
    }

    fun clear() = cache.evictAll()

    fun trim() = cache.trimToSize(cache.maxSize() / 2)

    private fun cacheKey(key: String, s: IconSpec) = "$key|${s.sizePx}|${s.shape}|${s.style}|${s.pack}|${s.tint}|${s.accent}"

    private fun render(d: Drawable, spec: IconSpec, fromPack: Boolean): Bitmap {
        val size = spec.sizePx.coerceIn(16, 512)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val monochrome = if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null

        when {
            spec.style != IconStyle.ORIGINAL && monochrome != null -> {
                // Android 13+ themed icon layer: a single-colour glyph, the cleanest "line" style.
                val color = if (spec.style == IconStyle.GOLD) spec.accent else spec.tint
                val inset = -size / 4
                monochrome.setBounds(inset, inset, size - inset, size - inset)
                monochrome.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                monochrome.draw(canvas)
                return bmp
            }
            !fromPack && spec.shape != IconShape.SYSTEM && d is AdaptiveIconDrawable -> {
                // Layers are 108 units with a 72-unit visible area: draw them 1.5x and clip.
                val path = shapePath(spec.shape, size.toFloat())
                canvas.save()
                canvas.clipPath(path)
                val inset = -size / 4
                d.background?.apply { setBounds(inset, inset, size - inset, size - inset); draw(canvas) }
                d.foreground?.apply { setBounds(inset, inset, size - inset, size - inset); draw(canvas) }
                canvas.restore()
            }
            else -> {
                d.setBounds(0, 0, size, size)
                d.draw(canvas)
            }
        }
        if (spec.style != IconStyle.ORIGINAL) return recolor(bmp, spec)
        return bmp
    }

    /** Fallback for icons without a monochrome layer: desaturate, then tint for the gold style. */
    private fun recolor(src: Bitmap, spec: IconSpec): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = ColorMatrix().apply { setSaturation(0f) }
        if (spec.style == IconStyle.GOLD) {
            val r = ((spec.accent shr 16) and 0xFF) / 255f
            val g = ((spec.accent shr 8) and 0xFF) / 255f
            val b = (spec.accent and 0xFF) / 255f
            m.postConcat(ColorMatrix(floatArrayOf(r * 1.3f, 0f, 0f, 0f, 0f, 0f, g * 1.3f, 0f, 0f, 0f, 0f, 0f, b * 1.3f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        }
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    companion object {
        fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
            when (shape) {
                IconShape.CIRCLE, IconShape.SYSTEM -> addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
                IconShape.ROUNDED_SQUARE -> addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
                IconShape.SQUIRCLE -> {
                    // Superellipse |x|^4 + |y|^4 = 1, sampled.
                    val r = size / 2f
                    for (i in 0..72) {
                        val t = Math.toRadians(i * 5.0)
                        val c = Math.cos(t)
                        val s = Math.sin(t)
                        val x = r + r * Math.signum(c) * Math.pow(Math.abs(c), 0.5)
                        val y = r + r * Math.signum(s) * Math.pow(Math.abs(s), 0.5)
                        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                    }
                    close()
                }
            }
        }
    }
}
