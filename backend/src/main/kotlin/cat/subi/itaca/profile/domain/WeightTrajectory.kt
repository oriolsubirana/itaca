package cat.subi.itaca.profile.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How hard the weekly change toward the target weight is, as % of body weight per week. */
enum class Pace { REACHED, SUSTAINABLE, DEMANDING, AGGRESSIVE }

data class WeightGoalPlan(
    val targetKg: Double,
    val targetDate: LocalDate,
    val weeks: Double,
    val weeklyChangeKg: Double,
    val weeklyPercent: Double,
    val pace: Pace,
    /** Where a sustainable pace (≤0.5 %/week) actually gets by the target date. */
    val reachableKg: Double,
)

/**
 * The trajectory from the current weight to a target weight by a date. For an endurance athlete
 * with colitis the sustainable pace is gentle (≤0.5 %/week): performance first, never an aggressive
 * restriction. Orientation only — unintended weight loss is a medical question, not this.
 */
object WeightTrajectory {
    private const val SUSTAINABLE_PCT = 0.5
    private const val DEMANDING_PCT = 1.0
    private const val REACHED_KG = 0.3
    private const val DAYS_PER_WEEK = 7.0
    private const val PERCENT = 100.0

    fun plan(
        currentKg: Double,
        targetKg: Double,
        today: LocalDate,
        targetDate: LocalDate,
    ): WeightGoalPlan? {
        val days = ChronoUnit.DAYS.between(today, targetDate)
        if (days <= 0) return null
        val weeks = days / DAYS_PER_WEEK
        val diff = targetKg - currentKg
        val weekly = diff / weeks
        val weeklyPct = abs(weekly) / currentKg * PERCENT
        val pace =
            when {
                abs(diff) <= REACHED_KG -> Pace.REACHED
                weeklyPct <= SUSTAINABLE_PCT -> Pace.SUSTAINABLE
                weeklyPct <= DEMANDING_PCT -> Pace.DEMANDING
                else -> Pace.AGGRESSIVE
            }
        val maxChange = currentKg * SUSTAINABLE_PCT / PERCENT * weeks
        val reachable = if (diff < 0) max(targetKg, currentKg - maxChange) else min(targetKg, currentKg + maxChange)
        return WeightGoalPlan(targetKg, targetDate, weeks, weekly, weeklyPct, pace, reachable)
    }
}
