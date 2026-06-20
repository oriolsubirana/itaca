package cat.subi.itaca.shared.security

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Two auth paths that coexist:
 *  - Machines (Garmin Action, iOS Shortcut, Strava sync) send the static bearer token,
 *    authenticated by [BearerTokenFilter].
 *  - The human (browser/PWA) signs in with Google (OAuth2 login → session), gated to a
 *    single email by [GoogleEmailAllowlist].
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
        clientRegistrations: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        val authEnabled = token.isNotBlank()
        val googleConfigured = clientRegistrations.ifAvailable != null
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
            if (googleConfigured) {
                oauth2Login {
                    userInfoEndpoint {
                        oidcUserService = GoogleEmailAllowlist(allowedEmail)
                    }
                }
            }
            exceptionHandling {
                defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiMatcher)
            }
        }
        return http.build()
    }
}
