package pt.hexa.empresarial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.hexa.empresarial.domain.orbital.OrbitalGeometry
import pt.hexa.empresarial.domain.orbital.OrbitalParams
import kotlin.math.hypot

class OrbitalGeometryTest {

    private val screens = listOf(
        1080f to 1700f, // phone, area under the search field
        720f to 1100f,
        2200f to 900f, // landscape
        1600f to 2200f, // tablet
        600f to 600f,
    )

    private fun params(w: Float, h: Float, density: Float = 2.75f, orbits: Int = 4) = OrbitalParams(
        width = w, height = h,
        iconSize = 44 * density, centerSize = 64 * density, minGap = 8 * density,
        orbitSpacing = 52 * density, maxOrbits = orbits, maxRadiusFraction = 1f,
        edgeMargin = 4 * density, minIconSize = 36 * density,
    )

    @Test
    fun everyAppIsPlacedExactlyOnce() {
        for ((w, h) in screens) for (n in listOf(0, 1, 2, 7, 8, 23, 60, 150, 320)) {
            val l = OrbitalGeometry.layout(n, params(w, h))
            val indices = l.pages.flatMap { p -> p.orbits.flatMap { o -> o.slots.map { it.index } } }
            assertEquals("n=$n ${w}x$h", (0 until n).toList(), indices.sorted())
        }
    }

    @Test
    fun noOverlapsNoCenterCollisionAndInsideBounds() {
        for ((w, h) in screens) for (n in listOf(1, 3, 8, 20, 45, 100, 300)) {
            val p = params(w, h)
            val l = OrbitalGeometry.layout(n, p)
            for (page in l.pages) {
                val pts = page.orbits.flatMap { it.slots }
                if (pts.size > 1) {
                    val min = OrbitalGeometry.minDistance(page)
                    assertTrue("overlap n=$n ${w}x$h min=$min", min >= l.iconSize + p.minGap - 0.5f)
                }
                for (s in pts) {
                    val d = hypot(s.x - l.centerX, s.y - l.centerY)
                    assertTrue("hits centre", d - l.iconSize / 2f >= l.centerSize / 2f + p.minGap - 0.5f)
                    assertTrue("left", s.x - l.iconSize / 2 >= -0.5f)
                    assertTrue("right", s.x + l.iconSize / 2 <= w + 0.5f)
                    assertTrue("top", s.y - l.iconSize / 2 >= -0.5f)
                    assertTrue("bottom", s.y + l.iconSize / 2 <= h + 0.5f)
                }
            }
        }
    }

    @Test
    fun fullOrbitsSpillIntoNextOrbitsAndPages() {
        val p = params(1080f, 1700f)
        val caps = OrbitalGeometry.radii(p, p.iconSize).map { OrbitalGeometry.capacity(it, p.iconSize, p.minGap) }
        assertTrue("outer orbits hold more", caps.zipWithNext().all { (a, b) -> b > a })
        val big = OrbitalGeometry.layout(caps.sum() * 3, p)
        assertTrue(big.pages.size >= 2)
        assertTrue(big.iconSize < p.iconSize) // icons shrink before paging
        assertTrue(big.iconSize >= p.minIconSize - 0.01f)
    }

    @Test
    fun fewAppsAreSpreadOverSeveralOrbits() {
        val l = OrbitalGeometry.layout(8, params(1080f, 1700f))
        val used = l.pages.single().orbits.count { it.slots.isNotEmpty() }
        assertTrue("a phone fits at least 3 orbits, got ${l.ringRadii.size}", l.ringRadii.size >= 3)
        assertEquals("every orbit gets icons", l.ringRadii.size, used)
    }

    @Test
    fun allocationRespectsCapacityAndTotals() {
        val caps = listOf(6, 11, 17, 22)
        for (n in 0..56) {
            val a = OrbitalGeometry.allocate(n, caps)
            assertEquals(minOf(n, caps.sum()), a.sum())
            a.zip(caps).forEach { (c, cap) -> assertTrue(c <= cap) }
        }
    }

    @Test
    fun capacityMatchesChordRule() {
        assertEquals(0, OrbitalGeometry.capacity(0f, 10f, 2f))
        assertEquals(1, OrbitalGeometry.capacity(5f, 20f, 2f))
        assertEquals(6, OrbitalGeometry.capacity(100f, 100f, 0f)) // hexagon: chord = radius
    }

    @Test
    fun maxOrbitsAndRadiusAreRespected() {
        val p = params(1080f, 1700f, orbits = 2)
        assertEquals(2, OrbitalGeometry.radii(p, p.iconSize).size)
        val tight = p.copy(maxOrbits = 7, maxRadiusFraction = 0.7f)
        val radii = OrbitalGeometry.radii(tight, tight.iconSize)
        assertTrue(radii.isNotEmpty())
        assertTrue(radii.last() + tight.iconSize / 2 <= (540f - tight.edgeMargin) * 0.7f + 0.5f)
        // An area too small for any orbit still gets one, so no app disappears.
        assertEquals(1, OrbitalGeometry.radii(p.copy(width = 100f, height = 100f), p.iconSize).size)
    }

    @Test
    fun hitTestFindsIcons() {
        val l = OrbitalGeometry.layout(12, params(1080f, 1700f))
        val slot = l.pages[0].orbits.first { it.slots.isNotEmpty() }.slots.first()
        assertEquals(slot.index, OrbitalGeometry.hitTest(l, 0, slot.x + 3f, slot.y - 3f))
        assertNull(OrbitalGeometry.hitTest(l, 0, l.centerX, l.centerY))
    }
}
