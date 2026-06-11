package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProgressionPolicyTest {
    private val policy = ProgressionPolicy()

    @Test
    fun `suggests one standard step up when target reps are exceeded with margin`() {
        val suggestion = policy.suggestNextWeight(Weight.ofKg(45.0), Reps.of(12))

        assertEquals(Weight.ofKg(47.5), suggestion)
    }

    @Test
    fun `keeps the same weight when reps only meet the target`() {
        val suggestion = policy.suggestNextWeight(Weight.ofKg(45.0), Reps.of(8))

        assertEquals(Weight.ofKg(45.0), suggestion)
    }

    @Test
    fun `keeps the same weight when reps exceed the target without margin`() {
        val suggestion = policy.suggestNextWeight(Weight.ofKg(50.0), Reps.of(9))

        assertEquals(Weight.ofKg(50.0), suggestion)
    }

    @Test
    fun `keeps the same weight when below the target`() {
        val suggestion = policy.suggestNextWeight(Weight.ofKg(14.0), Reps.of(6))

        assertEquals(Weight.ofKg(14.0), suggestion)
    }
}
