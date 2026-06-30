package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * OAuth2 refresh-token response client that adds the {@link EHealthContext} fields as extra form
 * parameters on the {@code refresh_token} grant sent to the FUT Keycloak token endpoint.
 *
 * <p>Delegates all HTTP work to {@link RestClientRefreshTokenTokenResponseClient} (Spring Security
 * 6.4+). Before delegating, it registers an additional parameters converter that reads the {@link
 * EHealthContext} off the {@link EHealthRefreshTokenGrantRequest} and appends the four scope fields
 * ({@code organization_id}, {@code care_team_id}, {@code patient_id}, {@code episode_of_care_id}),
 * adding only non-null values, to the outgoing form body.
 */
public class EHealthRefreshTokenResponseClient
        implements OAuth2AccessTokenResponseClient<EHealthRefreshTokenGrantRequest> {

    private static final Logger log =
            LoggerFactory.getLogger(EHealthRefreshTokenResponseClient.class);

    private final RestClientRefreshTokenTokenResponseClient delegate;

    /**
     * Creates an instance backed by a default {@link RestClientRefreshTokenTokenResponseClient}.
     */
    public EHealthRefreshTokenResponseClient() {
        this.delegate = new RestClientRefreshTokenTokenResponseClient();
        this.delegate.addParametersConverter(this::contextParameters);
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(EHealthRefreshTokenGrantRequest request) {
        return delegate.getTokenResponse(request);
    }

    private MultiValueMap<String, String> contextParameters(OAuth2RefreshTokenGrantRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (!(request instanceof EHealthRefreshTokenGrantRequest ehealthRequest)) {
            return params;
        }
        EHealthContext ctx = ehealthRequest.context();
        if (ctx.organizationId() != null) {
            params.add("organization_id", ctx.organizationId());
            log.debug("Refresh grant: adding organization_id={}", ctx.organizationId());
        }
        if (ctx.careTeamId() != null) {
            params.add("care_team_id", ctx.careTeamId());
            log.debug("Refresh grant: adding care_team_id={}", ctx.careTeamId());
        }
        if (ctx.patientId() != null) {
            params.add("patient_id", ctx.patientId());
            log.debug("Refresh grant: adding patient_id={}", ctx.patientId());
        }
        if (ctx.episodeOfCareId() != null) {
            params.add("episode_of_care_id", ctx.episodeOfCareId());
            log.debug("Refresh grant: adding episode_of_care_id={}", ctx.episodeOfCareId());
        }
        return params;
    }
}
