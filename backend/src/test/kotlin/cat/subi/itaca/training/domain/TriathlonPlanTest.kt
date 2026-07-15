package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TriathlonPlanTest {
    @Test
    fun `resolves the phase for a date, inclusive on both ends`() {
        assertEquals("base", TriathlonPlan.phaseOn(LocalDate.parse("2026-07-15"))?.key)
        assertEquals("base", TriathlonPlan.phaseOn(LocalDate.parse("2026-10-31"))?.key)
        assertEquals("winter", TriathlonPlan.phaseOn(LocalDate.parse("2026-11-01"))?.key)
        assertEquals("winter", TriathlonPlan.phaseOn(LocalDate.parse("2027-02-28"))?.key)
        assertEquals("specific", TriathlonPlan.phaseOn(LocalDate.parse("2027-03-01"))?.key)
        assertEquals("peak", TriathlonPlan.phaseOn(LocalDate.parse("2027-06-01"))?.key)
        assertEquals("peak", TriathlonPlan.phaseOn(TriathlonPlan.raceDate)?.key)
    }

    @Test
    fun `no phase before the plan starts or after the race`() {
        assertNull(TriathlonPlan.phaseOn(LocalDate.parse("2026-07-12")))
        assertNull(TriathlonPlan.phaseOn(LocalDate.parse("2027-06-28")))
    }

    @Test
    fun `counts days to race`() {
        assertEquals(347, TriathlonPlan.daysToRace(LocalDate.parse("2026-07-15")))
        assertEquals(0, TriathlonPlan.daysToRace(TriathlonPlan.raceDate))
        assertEquals(-1, TriathlonPlan.daysToRace(LocalDate.parse("2027-06-28")))
    }

    @Test
    fun `tracks the week within the current phase, one-based`() {
        val base = TriathlonPlan.phaseOn(LocalDate.parse("2026-07-15"))!!
        assertEquals(1, base.weekOf(LocalDate.parse("2026-07-15")))
        assertEquals(1, base.weekOf(LocalDate.parse("2026-07-19")))
        assertEquals(2, base.weekOf(LocalDate.parse("2026-07-20")))
        assertEquals(16, base.weekOf(LocalDate.parse("2026-10-31")))
        assertEquals(16, base.totalWeeks)
    }

    @Test
    fun `phases cover the whole runway without gaps`() {
        var day = TriathlonPlan.phases.first().start
        while (!day.isAfter(TriathlonPlan.raceDate)) {
            assertEquals(1, TriathlonPlan.phases.count { day in it.start..it.end }, "uncovered or overlapping: $day")
            day = day.plusDays(1)
        }
    }
}
