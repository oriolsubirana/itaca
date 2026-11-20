package cat.subi.itaca.nutrition.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportsFuelingTest {
    @Test
    fun `covers every training day type with the wire codes the training plan emits`() {
        assertEquals(listOf("rest", "easy", "key", "long", "race"), SportsFueling.dayTargets.map { it.dayType.wire })
    }

    @Test
    fun `carbohydrate rises with the day's demand, protein stays high every day`() {
        val carbs = SportsFueling.dayTargets.map { it.carbsGPerKgMin }
        assertEquals(carbs.sorted(), carbs)
        assertTrue(SportsFueling.dayTargets.all { it.carbsGPerKgMin <= it.carbsGPerKgMax })
        assertTrue(SportsFueling.dayTargets.all { it.proteinGPerKgMin >= 1.4 })
    }

    @Test
    fun `resolves a day type from its wire code`() {
        assertEquals(TrainingDayType.LONG, TrainingDayType.fromWire("long"))
        assertNull(TrainingDayType.fromWire("unknown"))
    }

    @Test
    fun `computes the day's grams from body weight`() {
        val key = SportsFueling.targetFor(TrainingDayType.KEY)
        val grams = key.gramsFor(weightKg = 70.0)
        assertEquals((key.carbsGPerKgMin * 70).toInt(), grams.carbsMin)
        assertEquals((key.proteinGPerKgMax * 70).toInt(), grams.proteinMax)
    }

    @Test
    fun `session fueling scales with duration and the race-week protocol ends on race morning`() {
        assertEquals(3, SportsFueling.sessionFueling.size)
        assertEquals(0, SportsFueling.sessionFueling.first().fromMinutes)
        assertTrue(SportsFueling.raceWeek.size >= 5)
        assertTrue(SportsFueling.raceWeek.any { "carrera" in it.moment.lowercase() })
        assertTrue(SportsFueling.raceWeek.all { it.guidance.isNotEmpty() })
    }
}
