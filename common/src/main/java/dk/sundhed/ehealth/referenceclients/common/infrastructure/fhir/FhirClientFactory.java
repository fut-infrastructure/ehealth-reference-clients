package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.DefaultEHealthUser;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Builds an authenticated HAPI {@link IGenericClient} for a given {@link FhirServer}, scoped to an
 * {@link EHealthContext}.
 *
 * <p>This class is a thin infrastructure layer: it resolves the target base URL, obtains a
 * context-scoped bearer token from the current {@link DefaultEHealthUser}, and registers a {@link
 * BearerTokenAuthInterceptor} on the raw HAPI client. No business logic lives here.
 *
 * <p>{@code FhirClientFactory} is a singleton. {@link DefaultEHealthUser} is request-scoped and is
 * injected via an {@link ObjectProvider} so that the dependency is resolved per call rather than at
 * construction time.
 */
@Component
public class FhirClientFactory {

    private final FhirContext fhirContext;
    private final BaseUrlResolver baseUrlResolver;
    private final ObjectProvider<DefaultEHealthUser> ehealthUserProvider;

    /**
     * Creates a new factory.
     *
     * @param fhirContext         the shared FHIR context supplied by the HAPI Spring Boot starter
     * @param baseUrlResolver     resolves configured base URLs for each {@link FhirServer}
     * @param ehealthUserProvider provider for the request-scoped {@link DefaultEHealthUser}
     */
    public FhirClientFactory(
            FhirContext fhirContext,
            BaseUrlResolver baseUrlResolver,
            ObjectProvider<DefaultEHealthUser> ehealthUserProvider) {
        this.fhirContext = fhirContext;
        this.baseUrlResolver = baseUrlResolver;
        this.ehealthUserProvider = ehealthUserProvider;
    }

    /**
     * Creates a HAPI {@link IGenericClient} pre-configured with a bearer token for the given server
     * and context.
     *
     * @param server  the FHIR server to target
     * @param context the security context used to obtain the access token
     * @return a ready-to-use authenticated client
     */
    public IGenericClient createClient(FhirServer server, EHealthContext context) {
        String baseUrl = baseUrlResolver.resolve(server);
        IGenericClient client = fhirContext.newRestfulGenericClient(baseUrl);
        String token = ehealthUserProvider.getObject().obtainAccessToken(context);
        client.registerInterceptor(new BearerTokenAuthInterceptor(token));
        return client;
    }
}
