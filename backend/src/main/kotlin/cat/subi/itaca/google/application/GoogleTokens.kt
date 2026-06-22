package cat.subi.itaca.google.application

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.stereotype.Component

/**
 * Resolves a current Google access token for the single user, refreshing it from the stored
 * refresh token when expired. Works off the request thread (chat tools, background jobs) because
 * it looks the authorized client up by the configured email (the OAuth principal name) instead of
 * the current SecurityContext.
 *
 * Returns null when Google isn't wired (no client configured locally) or not connected yet (no
 * stored token, or the refresh token lapsed — in Testing mode Google expires it after 7 days).
 */
@Component
class GoogleTokens(
    private val manager: ObjectProvider<OAuth2AuthorizedClientManager>,
    @Value("\${itaca.security.allowed-email:}") private val principalName: String,
) {
    fun accessToken(): String? {
        val mgr = manager.ifAvailable ?: return null
        if (principalName.isBlank()) return null
        val principal = UsernamePasswordAuthenticationToken(principalName, null, emptyList())
        val request = OAuth2AuthorizeRequest.withClientRegistrationId("google").principal(principal).build()
        return runCatching { mgr.authorize(request)?.accessToken?.tokenValue }.getOrNull()
    }
}
