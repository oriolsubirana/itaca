package cat.subi.itaca.shared.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BearerTokenFilterTest {
    private val filter = BearerTokenFilter(configuredToken = "secret")

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun run(
        authorization: String?,
        f: BearerTokenFilter = filter,
    ): MockFilterChain {
        val request =
            MockHttpServletRequest("GET", "/api/workouts").apply {
                authorization?.let { addHeader("Authorization", it) }
            }
        val chain = MockFilterChain()
        f.doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }

    @Test
    fun `authenticates a valid token as the machine role`() {
        val chain = run("Bearer secret")

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertTrue(auth.authorities.any { it.authority == "ROLE_MACHINE" })
        assertNotNull(chain.request) // the filter never blocks; it only sets the authentication
    }

    @Test
    fun `leaves the context unauthenticated for a wrong token`() {
        run("Bearer wrong")

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `leaves the context unauthenticated without an Authorization header`() {
        run(null)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `is a no-op when no token is configured`() {
        run(null, BearerTokenFilter(""))

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
