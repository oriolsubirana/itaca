package cat.subi.itaca

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boots the full context against a real Postgres (Testcontainers),
 * applies the Liquibase migrations and verifies the seeds.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class SchemaAndSeedIntegrationTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun count(table: String): Int = jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    @Test
    fun `training seeds are loaded`() {
        assertEquals(10, count("exercises"))
        assertEquals(3, count("routines"))
        assertEquals(10, count("routine_exercises"))
        assertEquals(3, count("workouts"))
        assertEquals(30, count("sets"))
    }

    @Test
    fun `the last completed workout is Push`() {
        val lastRoutine =
            jdbc.queryForObject(
                """
                SELECT r.name FROM workouts w
                JOIN routines r ON r.id = w.routine_id
                WHERE w.completed ORDER BY w.date DESC LIMIT 1
                """.trimIndent(),
                String::class.java,
            )
        assertEquals("Push", lastRoutine)
    }

    @Test
    fun `the analyte dictionary covers the key IBD markers`() {
        assertEquals(59, count("analytes"))
        val codes = jdbc.queryForList("SELECT code FROM analytes", String::class.java)
        val expected = listOf("fecal_calprotectin", "mch", "lipase", "hba1c", "semen_concentration", "prothrombin_time")
        assertTrue(codes.containsAll(expected))
    }

    @Test
    fun `the analyte dictionary includes German and Catalan synonyms for Swiss and Catalan labs`() {
        val viaGerman =
            jdbc.queryForObject(
                "SELECT count(*) FROM analytes WHERE 'Hämatokrit' = ANY(synonyms)",
                Int::class.java,
            )
        assertEquals(1, viaGerman)
        val viaCatalan =
            jdbc.queryForObject(
                "SELECT count(*) FROM analytes WHERE 'leucòcits' = ANY(synonyms)",
                Int::class.java,
            )
        assertEquals(1, viaCatalan)
    }

    @Test
    fun `accounts are created with their currency`() {
        assertEquals(5, count("accounts"))
        val neonCurrency =
            jdbc.queryForObject(
                "SELECT currency FROM accounts WHERE name = 'Neon'",
                String::class.java,
            )
        assertEquals("CHF", neonCurrency)
    }

    @Test
    fun `every analyte is assigned a panel category`() {
        val uncategorized =
            jdbc.queryForObject("SELECT count(*) FROM analytes WHERE category IS NULL", Int::class.java)
        assertEquals(0, uncategorized)
    }

    @Test
    fun `clinical document tables exist and start empty`() {
        assertEquals(0, count("medical_documents"))
        assertEquals(0, count("medical_diagnoses"))
        assertEquals(0, count("medical_medications"))
    }

    @Test
    fun `the Modulith event registry has its table`() {
        assertEquals(0, count("event_publication"))
    }
}
