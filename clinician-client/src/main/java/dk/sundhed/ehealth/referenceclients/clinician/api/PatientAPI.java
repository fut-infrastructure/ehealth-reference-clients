package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.SearchUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wraps the {@code $createPatient} custom operation on {@code fut-patient}.
 *
 * <p>One call covers CPR look-up, patient creation (or refresh), and NSP enrichment.
 * The server returns a {@link Parameters} bundle whose first parameter resource is the
 * resulting {@link Patient}.
 */
@Component
public class PatientAPI {

    /**
     * CPR system OID, as required by the {@code $createPatient} operation.
     */
    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

    private static final TokenClientParam RES_ID = new TokenClientParam("_id");

    private final FhirClientFactory fhirClientFactory;

    public PatientAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Invokes {@code POST /fhir/Patient/$createPatient} with the supplied CPR number.
     *
     * <p>The returned {@link Patient} is the server-enriched representation; it may already have
     * existed locally (update path) or be brand-new (create path).
     *
     * @param cpr     ten-digit CPR string, digits only
     * @param context security context carrying the clinician's access token
     * @return the created or updated {@link Patient}
     * @throws ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException when the CPR is unknown
     *                                                                      at NSP
     */
    public Patient createPatientFromCpr(String cpr, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PATIENT, context);

        Identifier cprIdentifier = new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setSystem(CPR_SYSTEM)
                .setValue(cpr);

        Parameters parameters = new Parameters();
        parameters.addParameter().setName("crn").setValue(cprIdentifier);

        Parameters response = client
                .operation()
                .onType(Patient.class)
                .named("$createPatient")
                .withParameters(parameters)
                .execute();

        return response.getParameter().stream()
                .map(Parameters.ParametersParameterComponent::getResource)
                .filter(resource -> resource instanceof Patient)
                .map(resource -> (Patient) resource)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "$createPatient returned no Patient resource in response Parameters"));
    }

    /**
     * Bulk lookup of {@link Patient} resources by bare logical id. Returns an empty list when
     * {@code ids} is empty.
     */
    public List<Patient> findPatientsById(Set<String> ids, EHealthContext context) {
        if (ids.isEmpty()) {
            return List.of();
        }
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PATIENT, context);
        Bundle first = client.search()
                .forResource(Patient.class)
                .where(RES_ID.exactly().codes(new ArrayList<>(ids)))
                .count(200)
                .returnBundle(Bundle.class)
                .execute();
        Bundle all = SearchUtil.loadAllPages(client, first);
        return BundleUtil.extract(all, Patient.class);
    }
}
