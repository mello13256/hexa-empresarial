package pt.hexa.empresarial.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Images chosen by the user (wallpaper, group and orbital centres). They are copied, reduced and
 * stored privately, so no storage permission or persistent URI grant is needed.
 */
class ImageStore(context: Context, private val io: CoroutineDispatcher) {

    private val dir = File(context.filesDir, "imagens").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val cache = LruCache<String, Bitmap>(8)

    suspend fun import(uri: Uri, maxSide: Int): String? = withContext(io) {
        try {
            val bmp = decode(uri, maxSide) ?: return@withContext null
            val name = "img_" + UUID.randomUUID().toString().take(12) + ".webp"
            File(dir, name).outputStream().use {
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                bmp.compress(format, 90, it)
            }
            cache.put(name, bmp)
            name
        } catch (e: Exception) {
            null
        }
    }

    suspend fun load(name: String): Bitmap? {
        cache.get(name)?.let { return it }
        return withContext(io) {
            val f = File(dir, name)
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.path)?.also { cache.put(name, it) }
        }
    }

    suspend fun delete(name: String) = withContext(io) {
        cache.remove(name)
        File(dir, name).delete()
    }

    private fun decode(uri: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { d, info, _ ->
                val s = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
                if (s < 1f) d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
}
