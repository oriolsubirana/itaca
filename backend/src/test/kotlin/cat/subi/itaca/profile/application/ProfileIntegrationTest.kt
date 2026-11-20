package cat.subi.itaca.profile.application

import cat.subi.itaca.TestcontainersConfiguration
import cat.subi.itaca.profile.domain.ActivityLevel
import cat.subi.itaca.profile.domain.BodyMetrics
import cat.subi.itaca.profile.domain.CalorieCalculator
import cat.subi.itaca.profile.domain.Goal
import cat.subi.itaca.profile.domain.Sex
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class ProfileIntegrationTest {
    @Autowired
    lateinit var profile: ProfileService

    @Test
    fun `saves the profile and computes the calorie targets`() {
        val dto = profile.save(ProfileCommand(78.0, 178, "1994-06-15", "male", "moderate", "maintain"))

        assertEquals("male", dto.sex)
        // Age is derived from birth_date against the clock, so recompute with that age.
        val age = dto.age!!
        val metrics = BodyMetrics(Sex.MALE, 78.0, 178.0, age, ActivityLevel.MODERATE, Goal.MAINTAIN)
        val expected = CalorieCalculator.targets(metrics)
        assertEquals(expected.bmr, dto.bmr)
        assertEquals(expected.tdee, dto.tdee)
        assertEquals(dto.tdee, dto.baseTarget) // maintain -> no adjustment
    }

    @Test
    fun `targets are null while the data is incomplete`() {
        val dto = profile.save(ProfileCommand(weightKg = 80.0))

        assertEquals(80.0, dto.weightKg)
        assertNull(dto.bmr)
        assertNull(dto.baseTarget)
    }

    @Test
    fun `a target weight by a date yields its trajectory and pace`() {
        val date = LocalDate.now().plusWeeks(20)

        val dto = profile.save(ProfileCommand(weightKg = 78.0, targetWeightKg = 75.0, targetDate = date.toString()))

        assertEquals(75.0, dto.targetWeightKg)
        assertEquals(date.toString(), dto.targetDate)
        val plan = assertNotNull(dto.weightPlan)
        assertEquals(-0.15, plan.weeklyChangeKg)
        assertEquals("sustainable", plan.pace)
    }

    @Test
    fun `no trajectory without a target`() {
        assertNull(profile.save(ProfileCommand(weightKg = 78.0)).weightPlan)
    }

    @Test
    fun `rejects an invalid enum value`() {
        assertFailsWith<IllegalArgumentException> { profile.save(ProfileCommand(sex = "other")) }
    }
}
