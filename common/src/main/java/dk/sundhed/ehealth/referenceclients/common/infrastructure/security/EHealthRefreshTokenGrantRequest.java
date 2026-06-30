package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

/**
 * Subclass of {@link OAuth2RefreshTokenGrantRequest} that carries an {@link EHealthContext}.
 *
 * <p>{@code EHealthRefreshTokenResponseClient} reads the context off this request and adds the four
 * scope fields as form parameters on the {@code refresh_token} grant.
 */
public class EHealthRefreshTokenGrantRequest extends OAuth2RefreshTokenGrantRequest {

    private final EHealthContext context;

    public EHealthRefreshTokenGrantRequest(
            ClientRegistration clientRegistration,
            OAuth2AccessToken accessToken,
            OAuth2RefreshToken refreshToken,
            EHealthContext context) {
        super(clientRegistration, accessToken, refreshToken);
        this.context = context;
    }

    public EHealthContext context() {
        return context;
    }
}
