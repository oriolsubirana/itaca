package cat.subi.itaca.health.application

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnitCompatibilityTest {
    @Test
    fun `equivalent concentration spellings stay compatible`() {
        assertTrue(unitsCompatible("10^3/µL", "10^9/L"), "same magnitude, different spelling")
        assertTrue(unitsCompatible("10^3/u", "10^9/L"))
        assertTrue(unitsCompatible("G/l", "10^9/L"))
    }

    @Test
    fun `a blank or unknown unit never blocks a match`() {
        assertTrue(unitsCompatible(null, "10^9/L"))
        assertTrue(unitsCompatible("", "10^9/L"))
        assertTrue(unitsCompatible("mg/L", "mg/L"))
    }

    @Test
    fun `a per-visual-field microscopy count is not a blood concentration`() {
        assertFalse(unitsCompatible("/Gesichtsfeld", "10^9/L"))
        assertFalse(unitsCompatible("/Gesic", "10^9/L"))
        assertFalse(unitsCompatible("/HPF", "10^9/L"))
    }

    @Test
    fun `a percentage is not the same measurement as an absolute concentration`() {
        assertFalse(unitsCompatible("%", "10^9/L"), "relative count vs absolute count")
        assertFalse(unitsCompatible("10^9/L", "%"))
        assertTrue(unitsCompatible("%", "%"), "percentage analytes (RDW) match")
    }
}
