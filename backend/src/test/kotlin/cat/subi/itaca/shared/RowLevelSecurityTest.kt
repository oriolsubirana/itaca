package cat.subi.itaca.shared

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertTrue

/**
 * Supabase exposes every public-schema table through its Data API (PostgREST); a table without
 * row-level security is world-readable and world-writable to anyone with the project URL and anon
 * key. Migration 110 enables RLS (deny-all, no policies) on everything, so this guards two
 * regressions: dropping that changeset, and adding a table-creating changeset AFTER it in the
 * master changelog (the sweep must stay last). JobRunr's tables and Modulith's event_publication
 * are exempt: those are created at runtime after Liquibase, so they only get RLS on the following
 * boot — meanwhile the revoked default privileges in migration 110 keep the Data API out of them.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class RowLevelSecurityTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `every public table has row-level security enabled`() {
        val unprotected =
            jdbc.queryForList(
                """
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                  AND NOT c.relrowsecurity
                  AND c.relname NOT LIKE 'jobrunr%'
                  AND c.relname <> 'event_publication'
                ORDER BY c.relname
                """.trimIndent(),
                String::class.java,
            )
        assertTrue(
            unprotected.isEmpty(),
            "Tables without RLS — Supabase's Data API exposes them publicly: $unprotected " +
                "(is their changeset included after 110-shared-data-api-lockdown.sql?)",
        )
    }
}
