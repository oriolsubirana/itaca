package cat.subi.itaca.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Machine-to-machine auth: a single static bearer token for callers that can't do the
 * Google login (the Garmin GitHub Action, the iOS Shortcut hitting /api/ingest, the
 * Strava sync). A valid token authenticates the request as the MACHINE role; humans
 * authenticate instead via Google OAuth2 login (a session). The authorization rules in
 * SecurityConfig then gate the API routes.
 *
 * If no token is configured (local/test), the filter is a no-op and SecurityConfig leaves
 * the API open — preserving the previous development behaviour.
 */
class BearerTokenFilter(
    private val configuredToken: String,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(BearerTokenFilter::class.java)
    private val enabled = configuredToken.isNotBlank()

    init {
        if (!enabled) {
            log.warn("ITACA_API_TOKEN not configured: the API accepts machine calls WITHOUT a token (development only)")
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (enabled && SecurityContextHolder.getContext().authentication == null && hasValidToken(request)) {
            val auth =
                UsernamePasswordAuthenticationToken(
                    "machine",
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_MACHINE")),
                )
            SecurityContextHolder.getContext().authentication = auth
        }
        filterChain.doFilter(request, response)
    }

    private fun hasValidToken(request: HttpServletRequest): Boolean {
        val header = request.getHeader("Authorization") ?: return false
        val token = header.removePrefix("Bearer ").trim()
        return MessageDigest.isEqual(token.toByteArray(), configuredToken.toByteArray())
    }
}
