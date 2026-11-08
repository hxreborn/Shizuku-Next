package moe.shizuku.manager.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a switch widget is the card and when it is the mark alone.
 *
 * The launcher reports the width an instance may occupy, and this is the line between a size where
 * the title fits beside the icon and one where it does not. Getting it wrong in one direction clips
 * the name; in the other, a cell-sized widget shows two lines of text in a space that has room for
 * neither.
 */
class LabWidgetProviderTest {

    @Test
    fun `a widget at its default width is the card`() {
        // Two cells, which is what a new one comes out as, and anything wider.
        assertFalse(LabWidgetProvider.isCompact(110))
        assertFalse(LabWidgetProvider.isCompact(140))
        assertFalse(LabWidgetProvider.isCompact(240))
    }

    @Test
    fun `shrunk to one cell it is the icon alone`() {
        assertTrue(LabWidgetProvider.isCompact(70))
        assertTrue(LabWidgetProvider.isCompact(40))
    }

    @Test
    fun `a launcher that has not said yet keeps the card`() {
        // Options that arrive empty read as zero, and a card drawn once too large is better than a
        // widget that is nothing but an icon until the launcher speaks.
        assertFalse(LabWidgetProvider.isCompact(0))
    }
}
