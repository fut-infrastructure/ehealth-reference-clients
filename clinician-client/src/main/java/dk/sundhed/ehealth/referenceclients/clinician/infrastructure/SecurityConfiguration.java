package dk.sundhed.ehealth.referenceclients.clinician.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * OAuth2 authorization-code login against the clinician Keycloak client.
 *
 * <p>Anonymous routes: {@code /}, static assets, {@code /actuator/**}, the OAuth2 redirect
 * endpoints, {@code /error}. Everything else requires an authenticated session.
 *
 * <p>{@link LoginSuccessHandler} runs after a successful OIDC login and fetches the user's
 * available care teams from the {@code ehealth-connect} resource provider. OIDC client-initiated
 * logout is wired so {@code POST /logout} ends the Keycloak session too.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler loginSuccessHandler,
            LogoutSuccessHandler logoutSuccessHandler)
            throws Exception {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize.requestMatchers(
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
                .oauth2Login(login -> login.successHandler(loginSuccessHandler))
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler));
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
