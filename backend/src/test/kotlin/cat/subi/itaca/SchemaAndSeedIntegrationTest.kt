package cat.subi.itaca

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Arranca el contexto completo contra un Postgres real (Testcontainers),
 * aplica las migraciones de Liquibase y comprueba los seeds.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class SchemaAndSeedIntegrationTest {

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    @Test
    fun `los seeds de training estan cargados`() {
        assertEquals(10, count("exercises"))
        assertEquals(3, count("routines"))
        assertEquals(10, count("routine_exercises"))
        assertEquals(3, count("workouts"))
        assertEquals(30, count("sets"))
    }

    @Test
    fun `la ultima sesion completada es Push`() {
        val ultimaRutina = jdbc.queryForObject(
            """
            SELECT r.nombre FROM workouts w
            JOIN routines r ON r.id = w.routine_id
            WHERE w.completado ORDER BY w.fecha DESC LIMIT 1
            """.trimIndent(),
            String::class.java,
        )
        assertEquals("Push", ultimaRutina)
    }

    @Test
    fun `el diccionario de analitos cubre los marcadores clave de EII`() {
        assertEquals(32, count("analytes"))
        val codigos = jdbc.queryForList("SELECT codigo FROM analytes", String::class.java)
        assertTrue(codigos.containsAll(listOf("calprotectina_fecal", "pcr", "vsg", "ferritina", "vitamina_d")))
    }

    @Test
    fun `las cuentas estan creadas con su divisa`() {
        assertEquals(4, count("accounts"))
        val monedaNeon = jdbc.queryForObject(
            "SELECT moneda FROM accounts WHERE nombre = 'Neon'",
            String::class.java,
        )
        assertEquals("CHF", monedaNeon)
    }

    @Test
    fun `el registry de eventos de Modulith tiene su tabla`() {
        assertEquals(0, count("event_publication"))
    }
}
