package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth2 infrastructure beans for the eHealth reference client stack.
 *
 * <p>The standard {@code OAuth2AuthorizedClientProvider} chain handles authorization-code login at
 * session start. {@link DefaultEHealthUser} then runs context-scoped refreshes on demand via {@link
 * EHealthRefreshTokenResponseClient}, bypassing the manager. Spring Boot's auto-configuration of
 * {@code OAuth2AuthorizedClientManager} covers everything else.
 */
@Configuration
public class EHealthOAuth2Configuration {

    /**
     * Produces the {@link EHealthRefreshTokenResponseClient} bean used by {@link DefaultEHealthUser}
     * to perform context-scoped refresh-token grants against the FUT Keycloak token endpoint.
     *
     * @return a new {@link EHealthRefreshTokenResponseClient}
     */
    @Bean
    public EHealthRefreshTokenResponseClient ehealthRefreshTokenResponseClient() {
        return new EHealthRefreshTokenResponseClient();
    }
}
