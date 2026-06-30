package dk.sundhed.ehealth.referenceclients.citizen.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

/**
 * Citizen-side read of the logged-in user's own {@link Patient} resource on {@code fut-patient}.
 *
 * <p>The patient URL is supplied by {@link EHealthContext#patientId()}, which the citizen
 * argument resolver derives from the OIDC {@code user_id} claim.
 */
@Component
public class CitizenPatientAPI {

    private final FhirClientFactory fhirClientFactory;

    public CitizenPatientAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    public Patient readSelf(EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PATIENT, context);
        // patientId() is a fully-qualified URL. IdType parses it; the HAPI client rebuilds the
        // request URL from its own base + the logical id. Passing the full URL to withUrl() while
        // also calling resource(Patient.class) double-prefixes and yields HAPI-0300.
        return client.read().resource(Patient.class).withId(new IdType(context.patientId())).execute();
    }
}
