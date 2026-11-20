package cat.subi.itaca.training.domain

import java.time.LocalDate

/** Heart-rate anchors for the load model; threshold (LTHR) estimated as 90 % of max. */
data class HeartRateProfile(
    val restingHr: Int,
    val maxHr: Int,
) {
    init {
        require(restingHr in 30 until maxHr) { "Resting HR must be below max HR" }
    }

    val thresholdHr: Double get() = maxHr * THRESHOLD_FRACTION

    private companion object {
        const val THRESHOLD_FRACTION = 0.9
    }
}

data class LoadPoint(
    val date: LocalDate,
    val load: Double,
    /** Fitness: 42-day exponentially weighted load. */
    val ctl: Double,
    /** Fatigue: 7-day exponentially weighted load. */
    val atl: Double,
    /** Form: yesterday's fitness minus yesterday's fatigue. */
    val tsb: Double,
)

enum class FormStatus { FRESH, NEUTRAL, PRODUCTIVE, OVERREACHING }

/**
 * Banister-style fitness/fatigue model on a TSS-like scale (1 h at threshold = 100). With heart
 * rate: hrTSS from heart-rate reserve; without it (or swimming, where wrist HR is unreliable): an
 * intensity factor estimated per sport. Orientation for planning, not a physiological measurement.
 */
object TrainingLoad {
    private const val FITNESS_DAYS = 42.0
    private const val FATIGUE_DAYS = 7.0
    private const val TSS_PER_HOUR = 100.0
    private const val MINUTES_PER_HOUR = 60.0
    private const val MIN_IF = 0.3
    private const val MAX_IF = 1.2
    private const val FRESH_TSB = 5.0
    private const val NEUTRAL_TSB = -10.0
    private const val PRODUCTIVE_TSB = -30.0

    // Typical intensity factors per sport for sessions without usable heart rate (domain data).
    @Suppress("MagicNumber")
    private val estimatedIntensity =
        mapOf(
            Sport.SWIM to 0.75,
            Sport.RUN to 0.78,
            Sport.BIKE to 0.70,
            Sport.GYM to 0.60,
            Sport.RACE to 0.90,
        )
    private const val OTHER_INTENSITY = 0.55

    /** [sport] null = an activity outside the plan's sports (hike, yoga...). */
    fun sessionLoad(
        sport: Sport?,
        minutes: Int,
        avgHr: Double?,
        hr: HeartRateProfile,
    ): Double {
        val intensity =
            if (avgHr != null && sport != Sport.SWIM) {
                ((avgHr - hr.restingHr) / (hr.thresholdHr - hr.restingHr)).coerceIn(MIN_IF, MAX_IF)
            } else {
                estimatedIntensity[sport] ?: OTHER_INTENSITY
            }
        return minutes / MINUTES_PER_HOUR * intensity * intensity * TSS_PER_HOUR
    }

    /** Daily series from [from] to [to]; days before [from] in [dailyLoads] warm the averages up. */
    fun series(
        dailyLoads: Map<LocalDate, Double>,
        from: LocalDate,
        to: LocalDate,
    ): List<LoadPoint> {
        val start = listOfNotNull(dailyLoads.keys.minOrNull(), from).min()
        var ctl = 0.0
        var atl = 0.0
        val points = mutableListOf<LoadPoint>()
        var day = start
        while (!day.isAfter(to)) {
            val load = dailyLoads[day] ?: 0.0
            val tsb = ctl - atl
            ctl += (load - ctl) / FITNESS_DAYS
            atl += (load - atl) / FATIGUE_DAYS
            if (!day.isBefore(from)) points += LoadPoint(day, load, ctl, atl, tsb)
            day = day.plusDays(1)
        }
        return points
    }

    fun formStatus(tsb: Double): FormStatus =
        when {
            tsb >= FRESH_TSB -> FormStatus.FRESH
            tsb >= NEUTRAL_TSB -> FormStatus.NEUTRAL
            tsb >= PRODUCTIVE_TSB -> FormStatus.PRODUCTIVE
            else -> FormStatus.OVERREACHING
        }
}
