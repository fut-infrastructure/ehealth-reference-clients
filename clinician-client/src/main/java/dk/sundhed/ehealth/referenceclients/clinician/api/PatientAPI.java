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
import java.util.regex.Pattern;

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

    /**
     * FUT-specific search parameter (see {@code SearchParameter/Patient/cprIdentifier} in the
     * implementation guide) dedicated to the DK-Core CPR identifier, distinct from the generic
     * {@code identifier} token search.
     */
    private static final TokenClientParam CPR_IDENTIFIER = new TokenClientParam("patientCPRIdentifier");

    private static final Pattern CPR_SHAPE = Pattern.compile("\\d{10}");

    /**
     * Hard cap on citizen search results. Same rationale as {@code EpisodeOfCareAPI}'s and
     * {@code PlanAPI}'s bounded single page: a name or CPR search can match broadly, and a single
     * bounded page (no {@code next} walk) keeps the round-trip cheap and avoids a {@code _getpages}
     * cursor expiring mid-walk. Callers surface {@link PatientSearchResult#truncated()} so the user
     * knows to narrow the search rather than assuming the result set is exhaustive.
     */
    private static final int SEARCH_LIMIT = 50;

    private final FhirClientFactory fhirClientFactory;

    public PatientAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * A bounded page of citizen search results.
     *
     * @param patients  up to {@link #SEARCH_LIMIT} matches
     * @param truncated true when the result was capped at {@link #SEARCH_LIMIT}, i.e. there may be
     *                  more matches than shown
     */
    public record PatientSearchResult(List<Patient> patients, boolean truncated) {
    }

    /**
     * Searches fut-patient for citizens matching {@code term}, server-side.
     *
     * <p>A 10-digit term is matched exactly as a CPR number. Anything else is matched as a
     * prefix match on {@code Patient.name} (e.g. "Lars" matches "Larsen" but not "Nilars")
     * not using {@code :contains}, which times out on the amount of patients.
     *
     * <p>Results are capped at {@link #SEARCH_LIMIT}; see that field for why a page walk is not
     * used here.
     *
     * @param term    search text as typed by the clinician
     * @param context security context carrying the clinician's access token
     * @return a bounded page of matching patients
     */
    public PatientSearchResult searchPatients(String term, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PATIENT, context);
        String trimmed = term.trim();

        var query = client.search().forResource(Patient.class);
        query = CPR_SHAPE.matcher(trimmed).matches()
                ? query.where(CPR_IDENTIFIER.exactly().code(trimmed))
                : query.where(Patient.NAME.matches().value(trimmed));

        Bundle page = query.count(SEARCH_LIMIT).returnBundle(Bundle.class).execute();
        List<Patient> patients = BundleUtil.extract(page, Patient.class);
        return new PatientSearchResult(patients, patients.size() == SEARCH_LIMIT);
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
