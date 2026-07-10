package dk.sundhed.ehealth.referenceclients.citizen.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Citizen-side FHIR wrappers for the measurement submission flow.
 *
 * <p>{@link #readServiceRequest} reads the {@code ServiceRequest} that describes what should be
 * measured (code, subject, linked episode). {@link #submitMeasurement} submits the resulting
 * {@code Observation} Bundle via the {@code $submit-measurement} system operation on
 * {@link FhirServer#MEASUREMENT}.
 */
@Component
public class CitizenMeasurementAPI {

    private final FhirClientFactory fhirClientFactory;

    public CitizenMeasurementAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Reads a single {@link ServiceRequest} by its fully-qualified URL from the careplan server.
     *
     * @param serviceRequestUrl fully-qualified ServiceRequest URL
     * @param context           citizen security context (must carry patientId)
     * @return the ServiceRequest resource
     */
    public ServiceRequest readServiceRequest(String serviceRequestUrl, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);
        return client.read().resource(ServiceRequest.class).withUrl(serviceRequestUrl).execute();
    }

    /**
     * Reads a single {@link ActivityDefinition} by its fully-qualified URL from the plan server.
     *
     * @param activityDefinitionUrl fully-qualified ActivityDefinition URL
     * @param context               citizen security context
     * @return the ActivityDefinition resource
     */
    public ActivityDefinition readActivityDefinition(String activityDefinitionUrl, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PLAN, context);
        return client.read().resource(ActivityDefinition.class).withUrl(activityDefinitionUrl).execute();
    }

    /**
     * Invokes {@code POST /$submit-measurement} on the measurement server with the supplied bundle
     * of observations.
     *
     * <p>The context must carry both {@code patientId} and {@code episodeOfCareId}; the measurement
     * server requires the episode in the token for authorisation.
     *
     * @param measurement Bundle containing the ehealth-observation resource(s)
     * @param context     citizen security context scoped to the episode
     */
    public void submitMeasurement(Bundle measurement, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.MEASUREMENT, context);

        Parameters parameters = new Parameters();
        parameters.addParameter().setName("measurement").setResource(measurement);

        client.operation()
                .onServer()
                .named("submit-measurement")
                .withParameters(parameters)
                .returnResourceType(Bundle.class)
                .execute();
    }

    /**
     * Upper bound on measurement bundles returned by {@link #searchMeasurements}.
     */
    private static final int MAX_MEASUREMENTS = 100;

    /**
     * Invokes {@code POST /$search-measurements-bundle-limit} on the measurement server for the
     * citizen's own submissions within an episode. The measurement server requires the episode in
     * the token, so it is added to the context here (mirroring the submission flow).
     *
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL
     * @param start           inclusive lower bound on effective measurement time
     * @param context         citizen security context (patient); episode is added internally
     * @return the outer {@link Bundle}; each entry is an inner per-submission Bundle
     */
    public Bundle searchMeasurements(String episodeOfCareId, Date start, EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.MEASUREMENT, episodeContext);

        Parameters parameters = new Parameters();
        parameters.addParameter().setName("episodeOfCare").setValue(new Reference(episodeOfCareId));
        parameters.addParameter().setName("start").setValue(new DateTimeType(start));
        parameters.addParameter().setName("count").setValue(new IntegerType(MAX_MEASUREMENTS));

        return client.operation()
                .onServer()
                .named("search-measurements-bundle-limit")
                .withParameters(parameters)
                .returnResourceType(Bundle.class)
                .execute();
    }
}
