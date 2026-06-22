package cat.subi.itaca.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository

/**
 * Builds the Google OAuth2 client only when GOOGLE_CLIENT_ID is configured, so local/test runs
 * (no env var) have no ClientRegistrationRepository and SecurityConfig skips Google login. One
 * consent covers identity plus the Gmail/Calendar/Drive read scopes used by later phases; the
 * built-in `google` provider supplies the endpoints and the standard redirect URI
 * (/login/oauth2/code/google).
 *
 * The authorized client (access + refresh token) is persisted in Postgres
 * (JdbcOAuth2AuthorizedClientService) so it survives restarts and can be used off the request
 * thread (chat tools, background jobs). The principal name is the email (userNameAttributeName),
 * giving a stable single-user handle to look the tokens up by.
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
                .userNameAttributeName("email")
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

    @Bean
    fun authorizedClientService(
        jdbc: JdbcTemplate,
        repo: ClientRegistrationRepository,
    ): OAuth2AuthorizedClientService = JdbcOAuth2AuthorizedClientService(jdbc, repo)

    @Bean
    fun authorizedClientRepository(service: OAuth2AuthorizedClientService): OAuth2AuthorizedClientRepository =
        AuthenticatedPrincipalOAuth2AuthorizedClientRepository(service)

    @Bean
    fun authorizedClientManager(
        repo: ClientRegistrationRepository,
        service: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager {
        val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, service)
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build())
        return manager
    }
}
