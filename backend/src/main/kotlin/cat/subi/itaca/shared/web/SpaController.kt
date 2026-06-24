package cat.subi.itaca.shared.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Serves the React SPA from the backend (same origin), so the Google session cookie and the
 * `/api` calls just work without CORS. Static assets (with a dot, e.g. /assets/x.js) and `/`
 * are served by Spring Boot's default static handling; this only forwards the SPA's client
 * routes (single path segment, no dot — /chat, /salud, /perfil…) to index.html so a refresh or
 * a deep link doesn't 404. Multi-segment API paths under /api never match (a path variable does
 * not span '/'), and the OAuth login/logout paths are handled by Spring Security before MVC.
 */
@Controller
class SpaController {
    @GetMapping("/{path:[^.]*}")
    @Suppress("FunctionOnlyReturningConstant") // a forwarding controller returns the view name
    fun forward(): String = "forward:/index.html"
}
