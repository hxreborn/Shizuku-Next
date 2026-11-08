package moe.shizuku.manager.manage

import moe.shizuku.manager.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every switch outside the app is made of.
 *
 * The three states and the words for them are decided in one place, so the tile and the widget for
 * the same feature cannot say different things. Unknown keeps its own word: a feature whose state
 * could not be read must not be reported as off.
 */
class LabSwitchTest {

    @Test
    fun `each state has one look, and every surface shares it`() {
        assertEquals(LabSwitch.Look.ON, LabSwitch.Look.of(true))
        assertEquals(LabSwitch.Look.OFF, LabSwitch.Look.of(false))
        assertEquals(LabSwitch.Look.UNKNOWN, LabSwitch.Look.of(null))

        assertEquals(R.string.lab_state_on, LabSwitch.Look.ON.label)
        assertEquals(R.string.lab_state_off, LabSwitch.Look.OFF.label)
        assertEquals(R.string.lab_state_unavailable, LabSwitch.Look.UNKNOWN.label)
    }

    @Test
    fun `every switch has a name and an icon of its own`() {
        val labels = LabSwitch.entries.map { it.label }
        val icons = LabSwitch.entries.map { it.image.name }

        assertTrue("a switch without a label", labels.none { it == 0 })
        assertEquals("two switches share a label", labels.size, labels.toSet().size)

        // One mark per switch, named after the Material icon the Labs screen draws: a tile is found
        // by its icon in a grid of them, and six hiding settings that all showed the same eye were
        // six tiles nobody could tell apart without reading them.
        assertEquals("two switches share an icon", icons.size, icons.toSet().size)
    }
}
