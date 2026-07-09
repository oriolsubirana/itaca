package cat.subi.itaca.shared

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression guard for the login lifetime: Spring Session must actually be ENGAGED (the repository
 * bean only exists when the Boot starter module is present — bare spring-session-jdbc is inert and
 * sessions silently fall back to in-memory Tomcat with a 30-minute timeout), bind the 7-day
 * timeout, and persist sessions to SPRING_SESSION (Liquibase migration 101) so logins survive
 * deploys.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class SessionPersistenceTest {
    @Autowired
    lateinit var repository: SessionRepository<*>

    @Test
    fun `sessions live in Postgres with a one-week timeout`() {
        // The concrete session type (JdbcSession) is package-private, hence the erased view.
        @Suppress("UNCHECKED_CAST")
        val sessions = repository as SessionRepository<Session>
        val session = sessions.createSession()
        assertEquals(Duration.ofDays(7), session.maxInactiveInterval, "spring.session.timeout must bind")

        sessions.save(session)
        val reloaded = assertNotNull(sessions.findById(session.id), "session must round-trip via SPRING_SESSION")
        assertEquals(Duration.ofDays(7), reloaded.maxInactiveInterval)

        sessions.deleteById(session.id)
    }
}
