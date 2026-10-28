package pt.hexa.empresarial.domain.orbital

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Inputs in pixels. */
data class OrbitalParams(
    val width: Float,
    val height: Float,
    val iconSize: Float,
    val centerSize: Float,
    val minGap: Float,
    val orbitSpacing: Float,
    val maxOrbits: Int,
    /** Largest orbit radius as a fraction of the available radius (0.4..1). */
    val maxRadiusFraction: Float = 1f,
    /** Margin kept free between the outermost icons and the area edge. */
    val edgeMargin: Float = 0f,
    /** Smallest icon size the engine may shrink to before adding pages. */
    val minIconSize: Float = iconSize,
)

data class OrbitSlot(val index: Int, val angle: Float, val x: Float, val y: Float)

data class Orbit(val radius: Float, val slots: List<OrbitSlot>)

data class OrbitalPage(val orbits: List<Orbit>, val count: Int)

data class OrbitalLayout(
    val centerX: Float,
    val centerY: Float,
    val iconSize: Float,
    val centerSize: Float,
    /** Every orbit drawn on screen (including empty ones, so rings stay stable between pages). */
    val ringRadii: List<Float>,
    val pages: List<OrbitalPage>,
    /** Capacity of one page. */
    val pageCapacity: Int,
) {
    val pageCount: Int get() = pages.size
}

/**
 * Places app icons on concentric orbits around a central element.
 *
 * - Orbit radii start just outside the centre element and grow by at least one icon plus the
 *   minimum gap, so icons on different orbits never touch.
 * - The capacity of an orbit comes from the chord between neighbours: chord = 2·r·sin(θ/2) must be
 *   at least icon + gap. Neighbours on the same orbit therefore never touch either.
 * - Orbits never extend past the available radius (shortest half-side minus margins).
 * - If all apps do not fit, icons shrink down to [OrbitalParams.minIconSize]; beyond that the
 *   engine produces several pages (the UI swipes between them).
 * - Items are spread across orbits in proportion to their capacity (inner orbits first for the
 *   first items, so favourites sit closest to the centre), evenly spaced, with each orbit rotated
 *   by a different phase so icons do not line up in spokes.
 */
object OrbitalGeometry {

    private const val GOLDEN_ANGLE = 2.3999632f

    fun layout(count: Int, p: OrbitalParams): OrbitalLayout {
        var icon = p.iconSize
        var radii = radii(p, icon)
        var caps = radii.map { capacity(it, icon, p.minGap) }
        // Shrink icons (down to the minimum) while everything does not fit on one page.
        while (count > caps.sum() && icon > p.minIconSize) {
            icon = max(p.minIconSize, icon * 0.92f)
            radii = radii(p, icon)
            caps = radii.map { capacity(it, icon, p.minGap) }
        }
        val cx = p.width / 2f
        val cy = p.height / 2f
        val pageCapacity = caps.sum()
        val pages = ArrayList<OrbitalPage>()
        if (pageCapacity > 0) {
            var start = 0
            while (start < count || pages.isEmpty()) {
                val n = min(pageCapacity, count - start)
                pages += page(n, start, radii, caps, cx, cy)
                start += n
                if (n == 0) break
            }
        } else {
            pages += OrbitalPage(emptyList(), 0)
        }
        return OrbitalLayout(cx, cy, icon, p.centerSize, radii, pages, pageCapacity)
    }

    /** Radii of the orbits that fit inside the area for this icon size. */
    fun radii(p: OrbitalParams, icon: Float): List<Float> {
        val available = (min(p.width, p.height) / 2f - p.edgeMargin) * p.maxRadiusFraction.coerceIn(0.3f, 1f) - icon / 2f
        val first = p.centerSize / 2f + p.minGap + icon / 2f
        val step = max(p.orbitSpacing, icon + p.minGap)
        val out = ArrayList<Float>()
        var r = first
        while (out.size < p.maxOrbits && r <= available + 0.01f) {
            out += r
            r += step
        }
        // Very small areas: keep one orbit so apps are never left without a place.
        if (out.isEmpty() && p.maxOrbits > 0) out += first
        return out
    }

    /** How many icons fit on an orbit of radius [r] without neighbours touching. */
    fun capacity(r: Float, icon: Float, gap: Float): Int {
        val d = icon + gap
        if (r <= 0f) return 0
        if (d >= 2f * r) return 1
        val theta = 2.0 * asin(d / (2.0 * r))
        return floor(2.0 * PI / theta + 1e-6).toInt().coerceAtLeast(1)
    }

    private fun page(n: Int, startIndex: Int, radii: List<Float>, caps: List<Int>, cx: Float, cy: Float): OrbitalPage {
        val counts = allocate(n, caps)
        var index = startIndex
        val orbits = radii.mapIndexed { k, r ->
            val m = counts[k]
            val phase = (-PI / 2).toFloat() + k * GOLDEN_ANGLE
            val slots = (0 until m).map { i ->
                val a = phase + (2f * PI.toFloat() * i) / m
                OrbitSlot(index++, a, cx + r * cos(a), cy + r * sin(a))
            }
            Orbit(r, slots)
        }
        return OrbitalPage(orbits, n)
    }

    /**
     * Splits [n] items over orbits proportionally to capacity (largest remainder), never exceeding
     * any orbit's capacity. With few items this spreads them over all rings, like the reference
     * composition, instead of crowding the first orbit.
     */
    fun allocate(n: Int, caps: List<Int>): List<Int> {
        val total = caps.sum()
        if (n <= 0 || total == 0) return caps.map { 0 }
        if (n >= total) return caps
        val exact = caps.map { it.toDouble() * n / total }
        val counts = exact.map { floor(it).toInt() }.toMutableList()
        var left = n - counts.sum()
        val order = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
        for (i in order) {
            if (left == 0) break
            if (counts[i] < caps[i]) {
                counts[i]++
                left--
            }
        }
        // Safety: any rounding leftovers go to the first orbit with room.
        var k = 0
        while (left > 0 && k < counts.size) {
            if (counts[k] < caps[k]) {
                counts[k]++
                left--
            } else {
                k++
            }
        }
        return counts
    }

    /** Index of the icon under ([x], [y]) on [page], or null (touch radius = half icon + half gap). */
    fun hitTest(layout: OrbitalLayout, page: Int, x: Float, y: Float, rotation: Float = 0f, touchSlack: Float = 0f): Int? {
        val pg = layout.pages.getOrNull(page) ?: return null
        val reach = layout.iconSize / 2f + touchSlack
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        for (orbit in pg.orbits) for (s in orbit.slots) {
            val a = s.angle + rotation
            val sx = layout.centerX + orbit.radius * cos(a)
            val sy = layout.centerY + orbit.radius * sin(a)
            val d = sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy))
            if (d <= reach && d < bestD) {
                bestD = d
                best = s.index
            }
        }
        return best
    }

    /** Smallest distance between any two icon centres on a page (diagnostics/tests). */
    fun minDistance(page: OrbitalPage): Float {
        val pts = page.orbits.flatMap { it.slots }
        var m = Float.MAX_VALUE
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            val dx = pts[i].x - pts[j].x
            val dy = pts[i].y - pts[j].y
            m = min(m, sqrt(dx * dx + dy * dy))
        }
        return m
    }
}
