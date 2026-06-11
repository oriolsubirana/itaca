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

    private val filter = BearerTokenFilter(configuredToken = "secreto")

    private fun request(authorization: String? = null) =
        MockHttpServletRequest("GET", "/api/workouts").apply {
            authorization?.let { addHeader("Authorization", it) }
        }

    @Test
    fun `rechaza peticiones sin cabecera Authorization`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request(), response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `rechaza tokens incorrectos`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request("Bearer incorrecto"), response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `deja pasar peticiones con el token correcto`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request("Bearer secreto"), response, chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `con token sin configurar el filtro queda desactivado`() {
        val openFilter = BearerTokenFilter(configuredToken = "")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        openFilter.doFilter(request(), response, chain)

        assertNotNull(chain.request)
    }
}
