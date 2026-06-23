package cat.subi.itaca.shared.security

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Two auth paths that coexist:
 *  - Machines (Garmin Action, iOS Shortcut, Strava sync) send the static bearer token,
 *    authenticated by [BearerTokenFilter].
 *  - The human (browser/PWA) signs in with Google (OAuth2 login → session), gated to a
 *    single email by [GoogleEmailAllowlist], then lands back on the SPA (itaca.app-url).
 *
 * When `itaca.security.token` is blank (local/test), the API stays fully open — the previous
 * development behaviour — so the existing suite runs unchanged. Google login only activates
 * when a client is configured (GOOGLE_CLIENT_ID set), otherwise there is no
 * ClientRegistrationRepository and we skip it. API requests get a 401 (not a redirect to
 * Google) so the SPA's fetch can react.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Value("\${itaca.security.token:}") token: String,
        @Value("\${itaca.security.allowed-email:}") allowedEmail: String,
        @Value("\${itaca.app-url:http://localhost:5173}") appUrl: String,
        clientRegistrations: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        val clientRepo = clientRegistrations.ifAvailable
        // Protect /api when EITHER a machine token OR Google login is configured, so a prod deploy
        // that uses only Google (no static token) is never left open. Blank both = open (local/dev).
        val authEnabled = token.isNotBlank() || clientRepo != null
        val apiMatcher = RequestMatcher { it.requestURI.startsWith("/api/") }

        http {
            csrf { disable() }
            authorizeHttpRequests {
                if (authEnabled) {
                    authorize("/api/**", authenticated)
                }
                authorize(anyRequest, permitAll)
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(BearerTokenFilter(token))
            logout {
                // The SPA logs out via a full-page GET to /logout; clear the session and bounce back.
                logoutRequestMatcher = RequestMatcher { it.requestURI == "/logout" }
                logoutSuccessHandler = SimpleUrlLogoutSuccessHandler().apply { setDefaultTargetUrl(appUrl) }
                invalidateHttpSession = true
                deleteCookies("JSESSIONID")
            }
            if (clientRepo != null) {
                oauth2Login {
                    authorizationEndpoint {
                        authorizationRequestResolver = offlineAccessResolver(clientRepo)
                    }
                    userInfoEndpoint {
                        oidcUserService = GoogleEmailAllowlist(allowedEmail)
                    }
                    authenticationSuccessHandler = SimpleUrlAuthenticationSuccessHandler(appUrl)
                    authenticationFailureHandler = SimpleUrlAuthenticationFailureHandler("$appUrl/?login=denied")
                }
            }
            exceptionHandling {
                defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiMatcher)
            }
        }
        return http.build()
    }

    /**
     * Asks Google for offline access so it returns a refresh token (prompt=consent forces it on
     * every login, not just the first). Without this we'd only get a 1h access token and could
     * never read Calendar/Gmail/Drive off the request thread.
     */
    private fun offlineAccessResolver(repo: ClientRegistrationRepository): OAuth2AuthorizationRequestResolver =
        DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization").apply {
            setAuthorizationRequestCustomizer { builder ->
                builder.additionalParameters { params ->
                    params["access_type"] = "offline"
                    params["prompt"] = "consent"
                }
            }
        }
}
