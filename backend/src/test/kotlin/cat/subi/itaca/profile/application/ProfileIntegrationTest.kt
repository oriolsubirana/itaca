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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `rejects an invalid enum value`() {
        assertFailsWith<IllegalArgumentException> { profile.save(ProfileCommand(sex = "other")) }
    }
}
