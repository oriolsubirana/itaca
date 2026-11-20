package cat.subi.itaca.profile.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeightTrajectoryTest {
    private val today = LocalDate.parse("2026-10-12")

    @Test
    fun `a gentle loss is sustainable`() {
        // 78 -> 75 kg in 20 weeks = -0.15 kg/week (~0.19 %/week).
        val plan = WeightTrajectory.plan(78.0, 75.0, today, today.plusWeeks(20))!!

        assertEquals(-0.15, plan.weeklyChangeKg, 0.001)
        assertEquals(Pace.SUSTAINABLE, plan.pace)
        assertEquals(75.0, plan.reachableKg, 0.001)
    }

    @Test
    fun `beyond half a percent a week is demanding, beyond one percent aggressive`() {
        assertEquals(Pace.DEMANDING, WeightTrajectory.plan(80.0, 76.0, today, today.plusWeeks(6))!!.pace)
        assertEquals(Pace.AGGRESSIVE, WeightTrajectory.plan(80.0, 74.0, today, today.plusWeeks(5))!!.pace)
    }

    @Test
    fun `caps what is reachable at the sustainable pace`() {
        // 10 weeks at -0.5 %/week from 80 kg.
        val plan = WeightTrajectory.plan(80.0, 70.0, today, today.plusWeeks(10))!!

        assertEquals(76.0, plan.reachableKg, 0.001)
    }

    @Test
    fun `within 300 g the target is reached`() {
        assertEquals(Pace.REACHED, WeightTrajectory.plan(75.2, 75.0, today, today.plusWeeks(4))!!.pace)
    }

    @Test
    fun `no plan without a future date`() {
        assertNull(WeightTrajectory.plan(78.0, 75.0, today, today))
    }
}
