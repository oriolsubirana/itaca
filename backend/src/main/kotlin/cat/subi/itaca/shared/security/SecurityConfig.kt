package cat.subi.itaca.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecurityConfig {

    @Bean
    fun bearerTokenFilter(
        @Value("\${itaca.security.token:}") token: String,
    ): FilterRegistrationBean<BearerTokenFilter> =
        FilterRegistrationBean(BearerTokenFilter(token)).apply {
            addUrlPatterns("/api/*")
            order = Int.MIN_VALUE
        }
}
