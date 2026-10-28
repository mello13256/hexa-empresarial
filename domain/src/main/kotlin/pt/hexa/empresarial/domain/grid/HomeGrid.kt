package pt.hexa.empresarial.domain.grid

import pt.hexa.empresarial.domain.model.Cell
import pt.hexa.empresarial.domain.model.HomeItem
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.HomePage
import kotlin.math.abs

/** Outcome of an edit on the home grid. Rejections carry a reason the UI can translate. */
sealed interface GridResult {
    data class Ok(val layout: HomeLayout) : GridResult
    data class Rejected(val reason: Reason) : GridResult

    enum class Reason { NO_SPACE, OUT_OF_BOUNDS, NOT_FOUND, TOO_MANY_PAGES }
}

/**
 * Pure operations on the home screen grid. Items never overlap and always lie inside the grid;
 * every function returns a new layout and leaves the input untouched.
 */
object HomeGrid {

    fun isFree(page: HomePage, cell: Cell, columns: Int, rows: Int, ignoreId: String? = null): Boolean =
        cell.fitsIn(columns, rows) && page.items.none { it.id != ignoreId && it.cell.overlaps(cell) }

    /**
     * Nearest free position for a [w]x[h] item, searching outwards from ([preferX], [preferY]) in
     * reading order of distance. Returns null when the page has no room.
     */
    fun findFree(page: HomePage, w: Int, h: Int, columns: Int, rows: Int, preferX: Int = 0, preferY: Int = 0, ignoreId: String? = null): Cell? {
        if (w > columns || h > rows) return null
        var best: Cell? = null
        var bestScore = Int.MAX_VALUE
        for (y in 0..rows - h) for (x in 0..columns - w) {
            val c = Cell(x, y, w, h)
            if (!isFree(page, c, columns, rows, ignoreId)) continue
            val score = abs(y - preferY) * (columns + 1) * 2 + abs(x - preferX) * 2 + (if (y < preferY) 1 else 0)
            if (score < bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    /** Adds [item] at its cell, or at the nearest free cell of that page, or on a later/new page. */
    fun add(layout: HomeLayout, pageIndex: Int, item: HomeItem): GridResult {
        val pages = layout.pages.toMutableList()
        var index = pageIndex.coerceIn(0, pages.size)
        while (true) {
            if (index == pages.size) {
                if (pages.size >= HomeLayout.MAX_PAGES) return GridResult.Rejected(GridResult.Reason.TOO_MANY_PAGES)
                pages += HomePage(newPageId(pages))
            }
            val page = pages[index]
            val target = if (isFree(page, item.cell, layout.columns, layout.rows)) {
                item.cell
            } else {
                findFree(page, item.cell.w, item.cell.h, layout.columns, layout.rows, item.cell.x, item.cell.y)
            }
            if (target != null) {
                pages[index] = page.copy(items = page.items + item.moveTo(target))
                return GridResult.Ok(layout.copy(pages = pages))
            }
            if (item.cell.w > layout.columns || item.cell.h > layout.rows) return GridResult.Rejected(GridResult.Reason.NO_SPACE)
            index++
        }
    }

    /**
     * Moves item [itemId] to [toPage] at ([x], [y]). If the target is taken by a single item of the
     * same size, the two swap places; otherwise the item goes to the nearest free spot on that page.
     */
    fun move(layout: HomeLayout, itemId: String, toPage: Int, x: Int, y: Int): GridResult {
        val (fromPage, item) = find(layout, itemId) ?: return GridResult.Rejected(GridResult.Reason.NOT_FOUND)
        if (toPage !in layout.pages.indices) return GridResult.Rejected(GridResult.Reason.OUT_OF_BOUNDS)
        val cols = layout.columns
        val rows = layout.rows
        val wanted = Cell(
            x.coerceIn(0, (cols - item.cell.w).coerceAtLeast(0)),
            y.coerceIn(0, (rows - item.cell.h).coerceAtLeast(0)),
            item.cell.w,
            item.cell.h,
        )
        val pages = layout.pages.toMutableList()
        pages[fromPage] = pages[fromPage].copy(items = pages[fromPage].items.filter { it.id != itemId })
        val target = pages[toPage]

        if (isFree(target, wanted, cols, rows)) {
            pages[toPage] = target.copy(items = target.items + item.moveTo(wanted))
            return GridResult.Ok(layout.copy(pages = pages))
        }
        val blockers = target.items.filter { it.cell.overlaps(wanted) }
        val blocker = blockers.singleOrNull()
        if (blocker != null && blocker.cell.w == item.cell.w && blocker.cell.h == item.cell.h && blocker.cell == wanted) {
            // Swap: the occupant takes the dragged item's old place (or the nearest free one).
            val origin = pages[fromPage]
            val back = if (fromPage == toPage) {
                item.cell
            } else if (isFree(origin, item.cell, cols, rows)) {
                item.cell
            } else {
                findFree(origin, blocker.cell.w, blocker.cell.h, cols, rows, item.cell.x, item.cell.y)
            }
            if (back != null) {
                if (fromPage == toPage) {
                    pages[toPage] = target.copy(items = target.items.map { if (it.id == blocker.id) it.moveTo(back) else it } + item.moveTo(wanted))
                } else {
                    pages[toPage] = target.copy(items = target.items.filter { it.id != blocker.id } + item.moveTo(wanted))
                    pages[fromPage] = origin.copy(items = origin.items + blocker.moveTo(back))
                }
                return GridResult.Ok(layout.copy(pages = pages))
            }
        }
        val free = findFree(target, item.cell.w, item.cell.h, cols, rows, wanted.x, wanted.y) ?: return GridResult.Rejected(GridResult.Reason.NO_SPACE)
        pages[toPage] = target.copy(items = target.items + item.moveTo(free))
        return GridResult.Ok(layout.copy(pages = pages))
    }

    fun resize(layout: HomeLayout, itemId: String, w: Int, h: Int): GridResult {
        val (pageIndex, item) = find(layout, itemId) ?: return GridResult.Rejected(GridResult.Reason.NOT_FOUND)
        val cell = item.cell.copy(w = w, h = h)
        val page = layout.pages[pageIndex]
        if (!cell.fitsIn(layout.columns, layout.rows)) return GridResult.Rejected(GridResult.Reason.OUT_OF_BOUNDS)
        if (!isFree(page, cell, layout.columns, layout.rows, ignoreId = itemId)) return GridResult.Rejected(GridResult.Reason.NO_SPACE)
        return GridResult.Ok(replace(layout, pageIndex, item.moveTo(cell)))
    }

    fun remove(layout: HomeLayout, itemId: String): HomeLayout =
        layout.copy(pages = layout.pages.map { p -> p.copy(items = p.items.filter { it.id != itemId }) })

    fun addPage(layout: HomeLayout, at: Int = layout.pages.size): GridResult {
        if (layout.pages.size >= HomeLayout.MAX_PAGES) return GridResult.Rejected(GridResult.Reason.TOO_MANY_PAGES)
        val pages = layout.pages.toMutableList()
        pages.add(at.coerceIn(0, pages.size), HomePage(newPageId(pages)))
        val default = if (at <= layout.defaultPage) layout.defaultPage + 1 else layout.defaultPage
        return GridResult.Ok(layout.copy(pages = pages, defaultPage = default.coerceIn(0, pages.lastIndex)))
    }

    /** Removes empty pages, always keeping at least one; the default page follows its content. */
    fun removeEmptyPages(layout: HomeLayout): HomeLayout {
        if (layout.pages.size <= 1) return layout
        val keep = layout.pages.withIndex().filter { it.value.items.isNotEmpty() }
        if (keep.isEmpty()) return layout.copy(pages = listOf(layout.pages.first()), defaultPage = 0)
        val newDefault = keep.indexOfFirst { it.index >= layout.defaultPage }.let { if (it < 0) keep.lastIndex else it }
        return layout.copy(pages = keep.map { it.value }, defaultPage = newDefault)
    }

    fun removePage(layout: HomeLayout, index: Int): HomeLayout {
        if (layout.pages.size <= 1 || index !in layout.pages.indices) return layout
        val pages = layout.pages.filterIndexed { i, _ -> i != index }
        val default = when {
            layout.defaultPage > index -> layout.defaultPage - 1
            layout.defaultPage == index -> 0
            else -> layout.defaultPage
        }
        return layout.copy(pages = pages, defaultPage = default.coerceIn(0, pages.lastIndex))
    }

    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        if (from !in layout.pages.indices || to !in layout.pages.indices || from == to) return layout
        val pages = layout.pages.toMutableList()
        val p = pages.removeAt(from)
        pages.add(to, p)
        val defaultId = layout.pages[layout.defaultPage].id
        return layout.copy(pages = pages, defaultPage = pages.indexOfFirst { it.id == defaultId }.coerceAtLeast(0))
    }

    /**
     * Applies a new grid size. Items that no longer fit keep their size if possible and move to the
     * nearest free cell on their page, then to following pages; items larger than the new grid are
     * shrunk to fit. Nothing is ever dropped.
     */
    fun resizeGrid(layout: HomeLayout, columns: Int, rows: Int): HomeLayout {
        val cols = columns.coerceIn(HomeLayout.MIN_COLUMNS, HomeLayout.MAX_COLUMNS)
        val rws = rows.coerceIn(HomeLayout.MIN_ROWS, HomeLayout.MAX_ROWS)
        var result = layout.copy(columns = cols, rows = rws, pages = layout.pages.map { it.copy(items = emptyList()) })
        val overflow = ArrayList<Pair<Int, HomeItem>>()
        layout.pages.forEachIndexed { pi, page ->
            for (item in page.items.sortedWith(compareBy({ it.cell.y }, { it.cell.x }))) {
                val clamped = item.cell.copy(w = item.cell.w.coerceAtMost(cols), h = item.cell.h.coerceAtMost(rws))
                val fitted = item.moveTo(clamped)
                val target = result.pages[pi]
                val cell = if (isFree(target, clamped, cols, rws)) clamped else findFree(target, clamped.w, clamped.h, cols, rws, clamped.x, clamped.y)
                if (cell != null) {
                    result = replacePage(result, pi, target.copy(items = target.items + fitted.moveTo(cell)))
                } else {
                    overflow += pi to fitted
                }
            }
        }
        for ((pi, item) in overflow) {
            when (val r = add(result, pi + 1, item)) {
                is GridResult.Ok -> result = r.layout
                is GridResult.Rejected -> Unit // only possible beyond MAX_PAGES; extremely unlikely
            }
        }
        return result.copy(defaultPage = result.defaultPage.coerceIn(0, result.pages.lastIndex))
    }

    fun find(layout: HomeLayout, itemId: String): Pair<Int, HomeItem>? {
        layout.pages.forEachIndexed { i, p -> p.items.firstOrNull { it.id == itemId }?.let { return i to it } }
        return null
    }

    /** True when no two items overlap and all are inside the grid (used by validation and tests). */
    fun isConsistent(layout: HomeLayout): Boolean = layout.pages.all { page ->
        page.items.all { it.cell.fitsIn(layout.columns, layout.rows) } &&
            page.items.indices.all { i -> (i + 1 until page.items.size).none { j -> page.items[i].cell.overlaps(page.items[j].cell) } }
    }

    private fun replace(layout: HomeLayout, pageIndex: Int, item: HomeItem): HomeLayout =
        replacePage(layout, pageIndex, layout.pages[pageIndex].let { p -> p.copy(items = p.items.map { if (it.id == item.id) item else it }) })

    private fun replacePage(layout: HomeLayout, index: Int, page: HomePage): HomeLayout =
        layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == index) page else p })

    private fun newPageId(pages: List<HomePage>): String {
        var n = pages.size + 1
        while (pages.any { it.id == "p$n" }) n++
        return "p$n"
    }
}
