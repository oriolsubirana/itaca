package cat.subi.itaca.training.domain

/**
 * Conservative progression for working sets of 3x6-8: increase by one standard
 * step (+2.5 kg) only after exceeding the top of the rep range with margin.
 */
class ProgressionPolicy(
    private val targetReps: Reps = Reps.of(DEFAULT_TARGET_TOP_REPS),
) {
    fun suggestNextWeight(
        lastWeight: Weight,
        lastReps: Reps,
    ): Weight =
        if (lastReps.exceedsWithMargin(targetReps)) {
            lastWeight.increasedByStandardStep()
        } else {
            lastWeight
        }

    companion object {
        private const val DEFAULT_TARGET_TOP_REPS = 8
    }
}
