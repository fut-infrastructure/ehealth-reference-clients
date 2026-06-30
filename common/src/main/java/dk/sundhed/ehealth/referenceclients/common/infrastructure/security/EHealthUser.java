package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

/**
 * Request-scoped current-user abstraction.
 *
 * <p>Holds the active {@code OAuth2AuthorizedClient} and runs a context-aware refresh on demand.
 * Used by {@link dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory}
 * to obtain a bearer token scoped to the requested {@link EHealthContext}.
 */
public interface EHealthUser {

    String obtainAccessToken(EHealthContext context);
}
