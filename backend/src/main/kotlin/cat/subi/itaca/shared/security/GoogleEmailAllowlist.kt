package cat.subi.itaca.shared.security

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser

/**
 * Single-user gate for Google sign-in: only the configured email may complete the login.
 * An empty allowlist allows any Google account that finishes the flow (only safe when the
 * OAuth client itself is private to one user). Loads the standard OIDC user, then checks.
 */
class GoogleEmailAllowlist(
    private val allowedEmail: String,
) : OidcUserService() {
    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val user = super.loadUser(userRequest)
        if (!isAllowed(user.email, allowedEmail)) {
            throw OAuth2AuthenticationException(
                OAuth2Error("access_denied"),
                "Email not allowed",
            )
        }
        return user
    }
}

/** Pure allow decision (extracted so it can be tested without hitting Google). */
internal fun isAllowed(
    email: String?,
    allowedEmail: String,
): Boolean = allowedEmail.isBlank() || allowedEmail.equals(email, ignoreCase = true)
