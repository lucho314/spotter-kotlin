package com.lucho314.spotter.data.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageCursorTest {

    @Test
    fun `a block that fits doesn't need a new page`() {
        val cursor = PageCursor(top = 0f, bottom = 800f)

        assertThat(cursor.needsNewPage(100f)).isFalse()
        cursor.advance(100f)
        assertThat(cursor.y).isEqualTo(100f)
    }

    @Test
    fun `a block that overflows needs a new page, which resets y and increments pageIndex`() {
        val cursor = PageCursor(top = 36f, bottom = 800f)
        cursor.advance(750f) // y = 786

        assertThat(cursor.needsNewPage(50f)).isTrue()
        cursor.newPage()

        assertThat(cursor.pageIndex).isEqualTo(1)
        assertThat(cursor.y).isEqualTo(36f)
    }

    @Test
    fun `an oversize block at the very top of a page is drawn anyway, no infinite loop`() {
        val cursor = PageCursor(top = 36f, bottom = 800f)

        assertThat(cursor.needsNewPage(2000f)).isFalse()
    }
}
