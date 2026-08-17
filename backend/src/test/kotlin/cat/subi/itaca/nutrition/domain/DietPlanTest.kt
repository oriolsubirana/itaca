package cat.subi.itaca.nutrition.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DietPlanTest {
    @Test
    fun `phase 1 plan is complete`() {
        assertTrue(DietPlan.allowed.size >= 10, "food categories transcribed")
        assertTrue(DietPlan.avoid.size >= 8, "avoid list transcribed")
        assertEquals(4, DietPlan.supplementSchedule.size, "fasting / pre-lunch / with meal / bedtime")
        assertTrue(DietPlan.supplementSchedule.all { it.items.isNotEmpty() && it.moment.isNotBlank() })
        assertTrue(DietPlan.allowed.all { it.category.isNotBlank() && it.items.isNotBlank() })
        assertTrue(DietPlan.mealHabits.isNotEmpty() && DietPlan.keyHabits.isNotEmpty())
    }
}
