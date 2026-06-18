package cat.subi.itaca.profile.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Mifflin-St Jeor BMR -> TDEE (activity factor) -> base target (goal adjustment). */
class CalorieCalculatorTest {
    @Test
    fun `computes a man's targets (78 kg, 178 cm, 30 y, moderate, maintain)`() {
        val t = CalorieCalculator.targets(BodyMetrics(Sex.MALE, 78.0, 178.0, 30, ActivityLevel.MODERATE, Goal.MAINTAIN))

        // 10*78 + 6.25*178 - 5*30 + 5 = 1747.5
        assertEquals(1748, t.bmr)
        // 1747.5 * 1.55 = 2708.625
        assertEquals(2709, t.tdee)
        // + 0 (maintain)
        assertEquals(2709, t.baseTarget)
    }

    @Test
    fun `computes a woman's targets and applies a deficit (light, lose)`() {
        val t = CalorieCalculator.targets(BodyMetrics(Sex.FEMALE, 60.0, 165.0, 28, ActivityLevel.LIGHT, Goal.LOSE))

        // 10*60 + 6.25*165 - 5*28 - 161 = 1330.25
        assertEquals(1330, t.bmr)
        // 1330.25 * 1.375 = 1829.09
        assertEquals(1829, t.tdee)
        // 1829.09 - 500 = 1329.09
        assertEquals(1329, t.baseTarget)
    }

    @Test
    fun `gain adds a surplus`() {
        val t = CalorieCalculator.targets(BodyMetrics(Sex.MALE, 80.0, 180.0, 25, ActivityLevel.ACTIVE, Goal.GAIN))

        assertEquals(t.tdee + 300, t.baseTarget)
    }
}
