package cat.subi.itaca.google.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.stereotype.Component

/**
 * Resolves a current Google access token for the single user, refreshing it from the stored
 * refresh token when expired. Works off the request thread (chat tools, background jobs) because
 * it looks the authorized client up by the principal name actually stored at login (read from the
 * token table) instead of guessing it from config — so it's robust to email/sub mismatches.
 *
 * Returns null when Google isn't wired (no client configured locally) or not connected yet (no
 * stored token, or the refresh token lapsed — in Testing mode Google expires it after 7 days).
 */
@Component
class GoogleTokens(
    private val manager: ObjectProvider<OAuth2AuthorizedClientManager>,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(GoogleTokens::class.java)

    fun accessToken(): String? {
        val mgr = manager.ifAvailable ?: return null
        val principalName = storedPrincipal() ?: return null
        log.info("Google token: resolving for stored principal=[{}]", principalName)
        val principal = UsernamePasswordAuthenticationToken(principalName, null, emptyList())
        val request = OAuth2AuthorizeRequest.withClientRegistrationId("google").principal(principal).build()
        return runCatching {
            val client = mgr.authorize(request) ?: return@runCatching null
            // The scopes Google actually GRANTED (not what we requested). A Drive 404 with a valid
            // files.list request means the token can't see the folder — usually because drive.readonly
            // was never granted (consent predates it / not on the OAuth consent screen).
            log.info("Google token: granted scopes={}", client.accessToken.scopes)
            client.accessToken.tokenValue
        }.getOrNull()
    }

    /** The single stored Google principal (most recently authorized), or null if none yet. */
    private fun storedPrincipal(): String? =
        runCatching {
            jdbc
                .query(
                    """
                    SELECT principal_name FROM oauth2_authorized_client
                    WHERE client_registration_id = 'google'
                    ORDER BY access_token_issued_at DESC
                    LIMIT 1
                    """.trimIndent(),
                ) { rs, _ -> rs.getString(1) }
                .firstOrNull()
        }.getOrNull()
}
