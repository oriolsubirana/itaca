package cat.subi.itaca.nutrition.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class NutritionServiceIntegrationTest {
    @Autowired
    lateinit var nutrition: NutritionService

    private fun log(
        type: String,
        desc: String,
        onPlan: Boolean?,
        calories: Int? = null,
        date: String? = null,
    ): MealResult = nutrition.logMeal(type, desc, onPlan = onPlan, calories = calories, date = date, notes = null)

    @Test
    fun `logs meals with calories and reports anti-inflammatory paleo adherence`() {
        log("dinner", "Salmón al horno con brócoli", onPlan = true, calories = 620)
        log("snack", "Galletas", onPlan = false, calories = 300)

        val summary = nutrition.queryMeals(7)

        assertEquals(2, summary.total)
        assertEquals(1, summary.onPlan)
        assertEquals(620, summary.meals.single { it.description.contains("Salmón") }.calories)
    }

    @Test
    fun `accepts a Spanish meal type and stores the canonical wire form`() {
        val result = log("cena", "Pollo con boniato", onPlan = true, date = "2026-06-10")

        assertTrue(result.saved)
        assertEquals("dinner", result.meal?.mealType)
        assertEquals("2026-06-10", result.meal?.date)
    }

    @Test
    fun `rejects an unknown meal type without saving`() {
        val result = log("brunch", "algo", onPlan = null)

        assertFalse(result.saved)
        assertEquals(0, nutrition.queryMeals(30).total)
    }

    @Test
    fun `deletes a meal`() {
        val result = log("lunch", "Ensalada con aguacate", onPlan = true)

        nutrition.delete(result.meal!!.id)

        assertEquals(0, nutrition.recent(30).total)
    }
}
