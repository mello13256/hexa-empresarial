package pt.hexa.empresarial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.hexa.empresarial.domain.grid.GridResult
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.Cell
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.HomePage
import pt.hexa.empresarial.domain.model.WidgetItem
import kotlin.random.Random

class HomeGridTest {

    private fun app(id: String, x: Int, y: Int) = AppItem(id, Cell(x, y), "pkg.$id/.Main", id)
    private fun ok(r: GridResult) = (r as GridResult.Ok).layout
    private fun layout(vararg items: AppItem, cols: Int = 4, rows: Int = 5) = HomeLayout(cols, rows, listOf(HomePage("p1", items.toList())))

    @Test
    fun findFreeSkipsOccupiedCellsAndPrefersNearest() {
        val l = layout(app("a", 0, 0), app("b", 1, 0))
        val page = l.pages[0]
        assertEquals(Cell(2, 0), HomeGrid.findFree(page, 1, 1, 4, 5))
        assertEquals(Cell(2, 0), HomeGrid.findFree(page, 1, 1, 4, 5, preferX = 1, preferY = 0)) // same row first
        assertEquals(Cell(0, 1), HomeGrid.findFree(page, 1, 1, 4, 5, preferX = 0, preferY = 1))
        assertNull(HomeGrid.findFree(page, 5, 1, 4, 5))
    }

    @Test
    fun moveToFreeCell() {
        val l = ok(HomeGrid.move(layout(app("a", 0, 0)), "a", 0, 3, 4))
        assertEquals(Cell(3, 4), l.pages[0].items.single().cell)
    }

    @Test
    fun moveOntoSameSizeItemSwaps() {
        val l = ok(HomeGrid.move(layout(app("a", 0, 0), app("b", 2, 2)), "a", 0, 2, 2))
        val byId = l.pages[0].items.associateBy { it.id }
        assertEquals(Cell(2, 2), byId.getValue("a").cell)
        assertEquals(Cell(0, 0), byId.getValue("b").cell)
        assertTrue(HomeGrid.isConsistent(l))
    }

    @Test
    fun moveBetweenPages() {
        val base = HomeLayout(4, 5, listOf(HomePage("p1", listOf(app("a", 0, 0))), HomePage("p2", listOf(app("b", 1, 1)))))
        val l = ok(HomeGrid.move(base, "a", 1, 1, 1)) // onto b: swap across pages
        assertEquals("a", l.pages[1].items.single { it.cell == Cell(1, 1) }.id)
        assertEquals("b", l.pages[0].items.single().id)
        assertTrue(HomeGrid.isConsistent(l))
    }

    @Test
    fun widgetResizeRespectsNeighbours() {
        val w = WidgetItem("w", Cell(0, 0, 2, 2), 7, "pkg/.Provider")
        val l = HomeLayout(4, 5, listOf(HomePage("p1", listOf(w, app("a", 3, 0)))))
        assertTrue(HomeGrid.resize(l, "w", 3, 2) is GridResult.Ok)
        assertEquals(GridResult.Reason.NO_SPACE, (HomeGrid.resize(l, "w", 4, 2) as GridResult.Rejected).reason)
        assertEquals(GridResult.Reason.OUT_OF_BOUNDS, (HomeGrid.resize(l, "w", 2, 6) as GridResult.Rejected).reason)
    }

    @Test
    fun addGoesToNextPageWhenFull() {
        var l = HomeLayout(3, 4, listOf(HomePage("p1")))
        repeat(12) { i -> l = ok(HomeGrid.add(l, 0, app("a$i", 0, 0))) }
        assertEquals(1, l.pages.size)
        l = ok(HomeGrid.add(l, 0, app("extra", 0, 0)))
        assertEquals(2, l.pages.size)
        assertEquals("extra", l.pages[1].items.single().id)
    }

    @Test
    fun pagesAddRemoveAndReorder() {
        var l = layout(app("a", 0, 0))
        l = ok(HomeGrid.addPage(l))
        l = ok(HomeGrid.addPage(l))
        assertEquals(3, l.pages.size)
        assertEquals(1, HomeGrid.removeEmptyPages(l).pages.size)
        val moved = HomeGrid.movePage(l, 0, 2)
        assertEquals("p1", moved.pages[2].id)
        assertEquals(2, moved.defaultPage) // the default page follows its content
        assertEquals(2, HomeGrid.removePage(l, 1).pages.size)
        assertEquals(1, HomeGrid.removePage(layout(), 0).pages.size) // never below one page
    }

    @Test
    fun shrinkingGridKeepsEveryItem() {
        val items = (0 until 30).map { app("a$it", it % 6, it / 6) }
        val l = HomeLayout(6, 6, listOf(HomePage("p1", items)))
        val small = HomeGrid.resizeGrid(l, 4, 5)
        assertEquals(30, small.pages.sumOf { it.items.size })
        assertTrue(HomeGrid.isConsistent(small))
        assertTrue(small.pages.size >= 2)
    }

    @Test
    fun randomEditsNeverBreakConsistency() {
        val rnd = Random(11)
        var l = HomeLayout(5, 6, listOf(HomePage("p1"), HomePage("p2")))
        repeat(40) { i -> HomeGrid.add(l, rnd.nextInt(2), app("a$i", rnd.nextInt(5), rnd.nextInt(6))).let { if (it is GridResult.Ok) l = it.layout } }
        repeat(400) {
            val all = l.pages.flatMap { it.items }
            val id = all[rnd.nextInt(all.size)].id
            val r = HomeGrid.move(l, id, rnd.nextInt(l.pages.size), rnd.nextInt(5), rnd.nextInt(6))
            if (r is GridResult.Ok) l = r.layout
            assertTrue(HomeGrid.isConsistent(l))
        }
        assertEquals(40, l.pages.sumOf { it.items.size })
        assertNotNull(HomeGrid.find(l, "a0"))
    }
}
