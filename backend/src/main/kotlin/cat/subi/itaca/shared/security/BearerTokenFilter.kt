package cat.subi.itaca.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Minimal auth for the current phase: a single static bearer token for the
 * whole API. Will be replaced by Spring Security 7 + JWT in the auth phase.
 *
 * If the token is not configured (local development), the filter disables
 * itself and logs a warning.
 */
class BearerTokenFilter(private val configuredToken: String) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(BearerTokenFilter::class.java)
    private val enabled = configuredToken.isNotBlank()

    init {
        if (!enabled) {
            log.warn("ITACA_API_TOKEN not configured: the API runs WITHOUT authentication (development only)")
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!enabled || isAuthorized(request)) {
            filterChain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error":"Unauthorized"}""")
    }

    private fun isAuthorized(request: HttpServletRequest): Boolean {
        val header = request.getHeader("Authorization") ?: return false
        val token = header.removePrefix("Bearer ").trim()
        return MessageDigest.isEqual(token.toByteArray(), configuredToken.toByteArray())
    }
}
