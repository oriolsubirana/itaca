package cat.subi.itaca.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Autenticación mínima de la fase actual: un bearer token estático para toda
 * la API. Cuando llegue la fase de auth se sustituirá por Spring Security 7 + JWT.
 *
 * Si el token no está configurado (desarrollo local), el filtro se desactiva
 * y lo deja registrado en el log.
 */
class BearerTokenFilter(private val configuredToken: String) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(BearerTokenFilter::class.java)
    private val enabled = configuredToken.isNotBlank()

    init {
        if (!enabled) {
            log.warn("ITACA_API_TOKEN no configurado: la API queda SIN autenticación (solo desarrollo)")
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
        response.writer.write("""{"error":"No autorizado"}""")
    }

    private fun isAuthorized(request: HttpServletRequest): Boolean {
        val header = request.getHeader("Authorization") ?: return false
        val token = header.removePrefix("Bearer ").trim()
        return MessageDigest.isEqual(token.toByteArray(), configuredToken.toByteArray())
    }
}
