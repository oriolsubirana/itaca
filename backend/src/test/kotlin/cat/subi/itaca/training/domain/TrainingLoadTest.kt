package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingLoadTest {
    private val hr = HeartRateProfile(restingHr = 55, maxHr = 185)

    @Test
    fun `an hour at threshold heart rate scores 100`() {
        val threshold = hr.thresholdHr
        assertEquals(100.0, TrainingLoad.sessionLoad(Sport.RUN, 60, threshold, hr), 0.5)
    }

    @Test
    fun `without heart rate the load is estimated from the sport`() {
        val run = TrainingLoad.sessionLoad(Sport.RUN, 60, null, hr)
        val gym = TrainingLoad.sessionLoad(Sport.GYM, 60, null, hr)
        assertTrue(run in 50.0..70.0, "run $run")
        assertTrue(gym < run)
    }

    @Test
    fun `swim ignores wrist heart rate, which is unreliable in the water`() {
        assertEquals(
            TrainingLoad.sessionLoad(Sport.SWIM, 45, null, hr),
            TrainingLoad.sessionLoad(Sport.SWIM, 45, 175.0, hr),
        )
    }

    @Test
    fun `fitness, fatigue and form follow the 42 and 7 day exponential averages`() {
        val start = LocalDate.parse("2026-01-01")
        val loads = (0 until 60).associate { start.plusDays(it.toLong()) to 50.0 }

        val series = TrainingLoad.series(loads, start, start.plusDays(59))

        val last = series.last()
        // A constant load converges: fatigue (7 d) faster than fitness (42 d), so form is negative.
        assertTrue(last.atl > last.ctl, "atl ${last.atl} ctl ${last.ctl}")
        assertTrue(last.atl in 49.0..50.0)
        assertTrue(last.ctl in 30.0..50.0)
        assertTrue(last.tsb < 0)
        assertEquals(60, series.size)
    }

    @Test
    fun `form is yesterday's fitness minus yesterday's fatigue`() {
        val start = LocalDate.parse("2026-01-01")
        val series = TrainingLoad.series(mapOf(start to 70.0), start, start.plusDays(1))

        assertEquals(0.0, series[0].tsb, 1e-9)
        assertEquals(series[0].ctl - series[0].atl, series[1].tsb, 1e-9)
    }

    @Test
    fun `classifies form`() {
        assertEquals(FormStatus.FRESH, TrainingLoad.formStatus(tsb = 8.0))
        assertEquals(FormStatus.NEUTRAL, TrainingLoad.formStatus(tsb = -5.0))
        assertEquals(FormStatus.PRODUCTIVE, TrainingLoad.formStatus(tsb = -20.0))
        assertEquals(FormStatus.OVERREACHING, TrainingLoad.formStatus(tsb = -35.0))
    }
}
