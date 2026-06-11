package cat.subi.itaca.shared.security

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BearerTokenFilterTest {
    private val filter = BearerTokenFilter(configuredToken = "secret")

    private fun request(authorization: String? = null) =
        MockHttpServletRequest("GET", "/api/workouts").apply {
            authorization?.let { addHeader("Authorization", it) }
        }

    @Test
    fun `rejects requests without Authorization header`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request(), response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `rejects wrong tokens`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request("Bearer wrong"), response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `lets requests with the right token through`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request("Bearer secret"), response, chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `disables itself when no token is configured`() {
        val openFilter = BearerTokenFilter(configuredToken = "")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        openFilter.doFilter(request(), response, chain)

        assertNotNull(chain.request)
    }
}
