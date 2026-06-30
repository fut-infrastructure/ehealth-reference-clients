package dk.sundhed.ehealth.referenceclients.citizen.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * OAuth2 authorization-code login against the citizen NemLogin client.
 *
 * <p>The citizen flow has a single context (the logged-in patient), so no LoginSuccessHandler /
 * picker is required. {@link CitizenEHealthContextArgumentResolver} derives the patient id from
 * the OIDC {@code user_id} claim on every request.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, LogoutSuccessHandler logoutSuccessHandler) throws Exception {
        http.authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/",
                                                "/error",
                                                "/css/**",
                                                "/fonts/**",
                                                "/webjars/**",
                                                "/actuator/**",
                                                "/login/**",
                                                "/oauth2/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true))
                .logout(l -> l.logoutSuccessHandler(logoutSuccessHandler));
        return http.build();
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
