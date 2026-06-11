package cat.subi.itaca.health.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Normalization against the seeded dictionary of 32 IBD analytes with
 * Spanish/Swiss lab synonyms.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class AnalyteMatcherIntegrationTest {
    @Autowired
    lateinit var matcher: AnalyteMatcher

    @Test
    fun `matches canonical Spanish names case-insensitively`() {
        assertEquals("fecal_calprotectin", matcher.match("calprotectina FECAL")?.code)
        assertEquals("hemoglobin", matcher.match("Hemoglobina")?.code)
    }

    @Test
    fun `matches Spanish and Swiss lab synonyms`() {
        assertEquals("crp", matcher.match("PCR")?.code)
        assertEquals("esr", matcher.match("VSG")?.code)
        assertEquals("crp", matcher.match("CRP ultrasensible")?.code)
        assertEquals("esr", matcher.match("vitesse de sédimentation")?.code)
        assertEquals("vitamin_d", matcher.match("25-OH-D")?.code)
    }

    @Test
    fun `unknown names return null instead of guessing`() {
        assertNull(matcher.match("flux capacitance"))
        assertNull(matcher.match(""))
    }
}
