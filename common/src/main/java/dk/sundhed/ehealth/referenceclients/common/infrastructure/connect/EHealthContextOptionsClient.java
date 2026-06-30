package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

/**
 * Interface-based HTTP client for the Keycloak ehealth-connect resource provider.
 *
 * <p>The bearer token is passed explicitly per call because the contexts endpoint is invoked from
 * an {@code AuthenticationSuccessHandler} where the freshly issued access token is already in hand.
 */
public interface EHealthContextOptionsClient {

    /**
     * Lists the contexts (care teams and organizations) the supplied bearer token has access to.
     *
     * @param authorization a full {@code Authorization} header value, e.g. {@code "Bearer xxx"}
     * @return the available contexts; never {@code null}
     */
    @GetExchange(url = "/resource/ehealth-connect/contexts", accept = "application/json")
    AvailableContexts contexts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
