package com.lucho314.spotter.data.export

/**
 * Tracks the current page and vertical offset while paginating [com.lucho314.spotter.data.export.WorkoutPdfRenderer]'s
 * content. Kept free of any `android.graphics.pdf` import so it stays pure and testable outside
 * Robolectric/instrumentation.
 */
class PageCursor(private val top: Float, private val bottom: Float) {
    var pageIndex = 0
        private set
    var y = top
        private set

    /**
     * True if [height] doesn't fit in what's left of the current page and we're not already at the
     * top of it - an oversize block at the very top of a page is drawn anyway (never triggers
     * another [newPage] on its own), so pagination can't loop forever on a single block taller than
     * a whole page.
     */
    fun needsNewPage(height: Float): Boolean = y + height > bottom && y > top

    fun newPage() {
        pageIndex++
        y = top
    }

    fun advance(height: Float) {
        y += height
    }
}
