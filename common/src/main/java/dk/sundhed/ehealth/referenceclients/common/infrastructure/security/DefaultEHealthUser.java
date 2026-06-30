package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped implementation of {@link EHealthUser}.
 *
 * <p>On each call to {@link #obtainAccessToken(EHealthContext)}, loads the current {@link
 * OAuth2AuthorizedClient} from the repository, triggers a context-scoped refresh via {@link
 * EHealthRefreshTokenResponseClient}, and persists the new client back to the repository. If the
 * authorized client carries no refresh token the existing access token is returned as-is (best
 * effort).
 */
@Component
@RequestScope
public class DefaultEHealthUser implements EHealthUser {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final EHealthRefreshTokenResponseClient responseClient;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final String clientRegistrationId;

    /**
     * Creates an instance wired with all required Spring Security collaborators.
     */
    public DefaultEHealthUser(
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            ClientRegistrationRepository clientRegistrationRepository,
            EHealthRefreshTokenResponseClient responseClient,
            HttpServletRequest request,
            HttpServletResponse response,
            @Value("${ehealth.client-registration-id:clinician}") String clientRegistrationId) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.responseClient = responseClient;
        this.request = request;
        this.response = response;
        this.clientRegistrationId = clientRegistrationId;
    }

    /**
     * Returns an access token scoped to the supplied {@link EHealthContext}.
     *
     * <p>Loads the current {@link OAuth2AuthorizedClient} for the configured registration, performs a
     * context-scoped refresh-token grant via {@link EHealthRefreshTokenResponseClient}, saves the
     * updated client back to the repository, and returns the new access token value. If the client
     * has no refresh token the existing access token is returned unchanged.
     *
     * @param context the eHealth context (organization / care team / patient / episode of care) to
     *                embed in the refreshed token
     * @return the raw access token value to use as a {@code Bearer} token
     * @throws StaleAuthenticationException if no authorized client is found for the configured
     *                                      registration, which typically indicates the session was invalidated by an app restart
     */
    @Override
    public String obtainAccessToken(EHealthContext context) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        OAuth2AuthorizedClient client =
                authorizedClientRepository.loadAuthorizedClient(
                        clientRegistrationId, authentication, request);
        if (client == null) {
            throw new StaleAuthenticationException(
                    "No authorized client for registration " + clientRegistrationId);
        }

        if (client.getRefreshToken() == null) {
            return client.getAccessToken().getTokenValue();
        }

        EHealthRefreshTokenGrantRequest grantRequest =
                new EHealthRefreshTokenGrantRequest(
                        client.getClientRegistration(),
                        client.getAccessToken(),
                        client.getRefreshToken(),
                        context);

        OAuth2AccessTokenResponse tokenResponse = responseClient.getTokenResponse(grantRequest);

        OAuth2AuthorizedClient updatedClient =
                new OAuth2AuthorizedClient(
                        client.getClientRegistration(),
                        client.getPrincipalName(),
                        tokenResponse.getAccessToken(),
                        tokenResponse.getRefreshToken() != null
                                ? tokenResponse.getRefreshToken()
                                : client.getRefreshToken());

        authorizedClientRepository.saveAuthorizedClient(
                updatedClient, authentication, request, response);

        return tokenResponse.getAccessToken().getTokenValue();
    }
}
