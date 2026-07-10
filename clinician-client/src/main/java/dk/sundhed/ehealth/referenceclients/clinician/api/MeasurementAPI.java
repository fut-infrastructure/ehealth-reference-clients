package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Raw FHIR wrapper around the {@code $search-measurements-bundle-limit} system operation on
 * {@link FhirServer#MEASUREMENT}.
 *
 * <p>The operation returns an outer {@link Bundle} whose entries are inner Bundles. Each inner
 * Bundle holds the complete set of resources from one {@code $submit-measurement} invocation:
 * {@code Observation}(s), {@code Media}, {@code QuestionnaireResponse}, and {@code Provenance}.
 */
@Component
public class MeasurementAPI {

    private static final int MAX_COUNT = 100;

    private final FhirClientFactory fhirClientFactory;

    public MeasurementAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Invokes {@code POST /$search-measurements-bundle-limit} on the measurement server, scoped to
     * the given episode of care. Returns up to 100 inner Bundles whose effective time is on or after
     * {@code start}, sorted ascending.
     *
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL on the careplan server
     * @param start           inclusive lower bound on the effective measurement time
     * @param context         security context (episode is added internally)
     * @return the outer Bundle; entries are inner measurement Bundles
     */
    public Bundle searchMeasurements(String episodeOfCareId, Date start, EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.MEASUREMENT, episodeContext);

        Parameters parameters = new Parameters();
        parameters.addParameter().setName("episodeOfCare").setValue(new Reference(episodeOfCareId));
        parameters.addParameter().setName("start").setValue(new DateTimeType(start));
        parameters.addParameter().setName("count").setValue(new IntegerType(MAX_COUNT));

        return client.operation()
                .onServer()
                .named("search-measurements-bundle-limit")
                .withParameters(parameters)
                .returnResourceType(Bundle.class)
                .execute();
    }
}
