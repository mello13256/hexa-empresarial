package pt.hexa.empresarial.domain.groups

import pt.hexa.empresarial.domain.model.GroupShape
import pt.hexa.empresarial.domain.orbital.OrbitalGeometry
import pt.hexa.empresarial.domain.orbital.OrbitalParams
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Icon centre relative to the group centre, in px. */
data class GroupSpot(val dx: Float, val dy: Float)

data class GroupArrangement(val spots: List<GroupSpot>, val radius: Float) {
    /** Size of the square that contains every icon. */
    fun extent(icon: Float): Float = 2f * radius + icon
}

/**
 * Positions of a group's apps around its centre for each shape. All shapes keep icons at least
 * `icon + gap` apart and outside the centre element; the caller scales the result if it does
 * not fit the screen.
 */
object GroupGeometry {

    private const val GOLDEN = 2.3999632f

    fun arrange(shape: GroupShape, n: Int, icon: Float, gap: Float, center: Float): GroupArrangement {
        if (n <= 0) return GroupArrangement(emptyList(), 0f)
        val step = icon + gap
        val spots = when (shape) {
            GroupShape.CIRCLE -> circle(n, step, center, icon, gap)
            GroupShape.RADIAL -> radial(n, icon, gap, center)
            GroupShape.HEXAGON -> hexagon(n, step)
            GroupShape.GRID -> grid(n, step)
            GroupShape.SPIRAL -> spiral(n, step, center, icon, gap)
        }
        val radius = spots.maxOf { sqrt(it.dx * it.dx + it.dy * it.dy) }
        return GroupArrangement(spots, radius)
    }

    /** One ring, as small as the chord rule and the centre allow. */
    private fun circle(n: Int, step: Float, center: Float, icon: Float, gap: Float): List<GroupSpot> {
        val minForCenter = center / 2f + gap + icon / 2f
        val r = if (n == 1) minForCenter else max(minForCenter, (step / (2 * sin(PI / n))).toFloat())
        return (0 until n).map { i ->
            val a = -PI / 2 + 2 * PI * i / n
            GroupSpot((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
        }
    }

    /** Several concentric rings, using the orbital engine with an unbounded area. */
    private fun radial(n: Int, icon: Float, gap: Float, center: Float): List<GroupSpot> {
        val big = 100_000f
        val l = OrbitalGeometry.layout(n, OrbitalParams(big, big, icon, center, gap, icon + gap, maxOrbits = 12))
        return l.pages.first().orbits.flatMap { o -> o.slots }.sortedBy { it.index }.map { GroupSpot(it.x - l.centerX, it.y - l.centerY) }
    }

    /** Honeycomb rings (6, 12, 18...) around the centre, which takes the middle cell. */
    private fun hexagon(n: Int, step: Float): List<GroupSpot> {
        val out = ArrayList<GroupSpot>(n)
        var ring = 1
        // Axial directions of a pointy-top hex grid.
        val dirs = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
        while (out.size < n) {
            var q = -ring
            var r = ring
            for (d in 0 until 6) {
                for (k in 0 until ring) {
                    if (out.size < n) {
                        val x = step * (q + r / 2f)
                        val y = step * (sqrt(3f) / 2f) * r
                        out += GroupSpot(x, y)
                    }
                    q += dirs[d].first
                    r += dirs[d].second
                }
            }
            ring++
        }
        return out
    }

    /** Rows and columns around the centre (the centre shows above the grid). */
    private fun grid(n: Int, step: Float): List<GroupSpot> {
        val cols = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(n / cols.toDouble()).toInt()
        return (0 until n).map { i ->
            val c = i % cols
            val r = i / cols
            val inRow = if (r == rows - 1) n - r * cols else cols
            val x = (c - (inRow - 1) / 2f) * step
            val y = (r - (rows - 1) / 2f) * step + step * 0.6f
            GroupSpot(x, y)
        }
    }

    /**
     * Phyllotaxis spiral (sunflower pattern). The scale keeps every pair at least one step apart:
     * consecutive-distance in the Vogel spiral is about c·√π, so c = step / √π · 1.1 is safe.
     */
    private fun spiral(n: Int, step: Float, center: Float, icon: Float, gap: Float): List<GroupSpot> {
        val c = step / sqrt(PI.toFloat()) * 1.1f
        val offset = ((center / 2f + gap + icon / 2f) / c).let { it * it }
        return (0 until n).map { i ->
            val k = i + offset
            val r = c * sqrt(k)
            val a = k * GOLDEN
            GroupSpot(r * cos(a), r * sin(a))
        }
    }
}
