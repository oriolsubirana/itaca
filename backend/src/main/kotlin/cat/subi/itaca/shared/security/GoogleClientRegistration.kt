package cat.subi.itaca.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

/**
 * Builds the Google OAuth2 client only when GOOGLE_CLIENT_ID is configured, so local/test runs
 * (no env var) have no ClientRegistrationRepository and SecurityConfig skips Google login. One
 * consent covers identity plus the Gmail/Calendar/Drive read scopes used by later phases; the
 * built-in `google` provider supplies the endpoints and the standard redirect URI
 * (/login/oauth2/code/google).
 */
@Configuration
@ConditionalOnProperty(name = ["GOOGLE_CLIENT_ID"])
class GoogleClientRegistration {
    @Bean
    fun clientRegistrationRepository(
        @Value("\${GOOGLE_CLIENT_ID}") clientId: String,
        @Value("\${GOOGLE_CLIENT_SECRET:}") clientSecret: String,
    ): ClientRegistrationRepository {
        val google =
            CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope(
                    "openid",
                    "email",
                    "profile",
                    "https://www.googleapis.com/auth/gmail.readonly",
                    "https://www.googleapis.com/auth/calendar.readonly",
                    "https://www.googleapis.com/auth/drive.readonly",
                ).build()
        return InMemoryClientRegistrationRepository(google)
    }
}
