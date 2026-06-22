package cat.subi.itaca.shared.security

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What the SPA needs to render the login gate / show who is signed in. */
data class MeResponse(
    val authenticated: Boolean,
    val email: String?,
    val name: String?,
)

/**
 * Lets the SPA learn its auth state. A 401 here (the security chain's entry point) means
 * "log in with Google"; a 200 means the API is usable — either because a Google session or
 * machine token is present, or because auth is disabled in local development.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {
    @GetMapping("/me")
    fun me(authentication: Authentication?): MeResponse {
        val authed =
            authentication != null &&
                authentication.isAuthenticated &&
                authentication !is AnonymousAuthenticationToken
        return when (val principal = authentication?.principal) {
            is OidcUser -> MeResponse(true, principal.email, principal.fullName ?: principal.email)
            is OAuth2User -> MeResponse(true, principal.getAttribute("email"), principal.getAttribute("name"))
            else -> MeResponse(authed, null, authentication?.name?.takeIf { authed })
        }
    }
}
