package pt.hexa.empresarial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.hexa.empresarial.domain.groups.GroupGeometry
import pt.hexa.empresarial.domain.model.GroupShape
import kotlin.math.hypot

class GroupGeometryTest {

    @Test
    fun everyShapeIsCollisionFree() {
        val icon = 120f
        val gap = 20f
        val center = 160f
        for (shape in GroupShape.entries) for (n in 1..24) {
            val a = GroupGeometry.arrange(shape, n, icon, gap, center)
            assertEquals("$shape n=$n", n, a.spots.size)
            for (i in a.spots.indices) {
                for (j in i + 1 until a.spots.size) {
                    val d = hypot(a.spots[i].dx - a.spots[j].dx, a.spots[i].dy - a.spots[j].dy)
                    assertTrue("$shape n=$n overlap $i/$j d=$d", d >= icon + gap - 1f)
                }
                if (shape != GroupShape.GRID) {
                    val r = hypot(a.spots[i].dx, a.spots[i].dy)
                    assertTrue("$shape n=$n hits centre", r >= center / 2f + icon / 2f - 1f)
                }
            }
        }
    }

    @Test
    fun emptyGroup() {
        assertTrue(GroupGeometry.arrange(GroupShape.CIRCLE, 0, 10f, 1f, 10f).spots.isEmpty())
    }
}
