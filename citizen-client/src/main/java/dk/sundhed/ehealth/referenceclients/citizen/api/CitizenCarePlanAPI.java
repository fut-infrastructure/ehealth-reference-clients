package dk.sundhed.ehealth.referenceclients.citizen.api;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirExtensions;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Citizen-side wrapper around the {@code $get-patient-procedures} system operation on
 * {@code fut-careplan} (IG: {@code OperationDefinition--s-get-patient-procedures.json}).
 *
 * <p>The server returns a {@code Bundle} whose first entry is a {@code Parameters} resource. Each
 * top-level parameter is named {@code item_1}, {@code item_2}, … and carries one row per active
 * {@code ServiceRequest} timing slot that overlaps the requested {@code [start, end]} window. The
 * row's columns appear as {@code parameter.part} entries. The remaining Bundle entries are the
 * referenced {@code CarePlan} and {@code ServiceRequest} resources (not consumed here yet, as
 * the row already contains the rendered activity description).
 */
@Component
public class CitizenCarePlanAPI {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final FhirClientFactory fhirClientFactory;

    public CitizenCarePlanAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Reads a single {@link CarePlan} and its activity resources for the citizen's weekly-view
     * drill-down. Performed as a search rather than a {@code read()} because HAPI's {@code read()}
     * does not honour {@code _include}; the search is constrained by {@code patient} (the citizen
     * token's only context dimension) plus {@code _id}, and pulls the activities via
     * {@code _include=CarePlan:activity-reference}. The IG careplan CapabilityStatement lists all
     * three of {@code patient}, {@code _id} and that include as supported.
     *
     * @param carePlanId bare or qualified CarePlan id
     * @param context    citizen security context (carries the patient)
     * @return the raw search bundle containing the CarePlan and its Task/Appointment/ServiceRequest
     * activities; empty when the plan is not visible to this citizen
     */
    public Bundle fetchCarePlanWithActivities(String carePlanId, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);
        String bareId = new IdType(carePlanId).getIdPart();
        return client.search()
                .forResource(CarePlan.class)
                .where(new ReferenceClientParam("patient").hasId(context.patientId()))
                .where(IAnyResource.RES_ID.exactly().code(bareId))
                .include(new Include("CarePlan:activity-reference"))
                .returnBundle(Bundle.class)
                .execute();
    }

    /**
     * Lists the citizen's own {@link CarePlan}s ({@code CarePlan?patient=<self>}), one bounded page,
     * newest-relevant first as the server returns them. Used to surface clickable plans on the
     * {@code /me} page. The search carries only the {@code patient} dimension, which is all a
     * citizen token holds.
     *
     * @param context citizen security context (carries the patient)
     * @return the CarePlans visible to this citizen (may be empty)
     */
    public List<CarePlan> findMyCarePlans(EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);
        Bundle bundle = client.search()
                .forResource(CarePlan.class)
                .where(new ReferenceClientParam("patient").hasId(context.patientId()))
                .count(50)
                .returnBundle(Bundle.class)
                .execute();
        return BundleUtil.extract(bundle, CarePlan.class);
    }

    /**
     * Invokes {@code POST /fhir/$get-patient-procedures} on the care-plan server for the citizen's
     * patient over the inclusive date range {@code [startDate, endDate]}.
     *
     * <p>Returns a {@link ProcedureBundle} containing the timing rows plus a bare CarePlan-id →
     * EpisodeOfCare-reference map derived from the CarePlan resources the server includes in the
     * same response bundle. The episode map is used downstream to scope the measurement-submission
     * token, which the measurement server requires.
     */
    public ProcedureBundle getPatientProcedures(
            LocalDate startDate, LocalDate endDate, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        Parameters input = new Parameters();
        input.addParameter().setName("patient").setValue(new Reference(context.patientId()));
        input.addParameter()
                .setName("start")
                .setValue(new DateTimeType(
                        Date.from(startDate.atStartOfDay(ZONE).toInstant())));
        input.addParameter()
                .setName("end")
                .setValue(new DateTimeType(
                        Date.from(endDate.plusDays(1).atStartOfDay(ZONE).minusSeconds(1).toInstant())));

        Bundle response = client.operation()
                .onServer()
                .named("get-patient-procedures")
                .withParameters(input)
                .returnResourceType(Bundle.class)
                .execute();

        return new ProcedureBundle(extractRows(response), extractEpisodes(response));
    }

    private static Map<String, String> extractEpisodes(Bundle response) {
        Map<String, String> episodeByCarePlanId = new HashMap<>();
        for (Bundle.BundleEntryComponent entry : response.getEntry()) {
            if (!(entry.getResource() instanceof CarePlan carePlan)) {
                continue;
            }
            String bareId = carePlan.getIdElement().getIdPart();
            String episodeRef = FhirExtensions.referenceValue(carePlan, FhirExtensions.EPISODE_OF_CARE);
            if (bareId != null && episodeRef != null) {
                episodeByCarePlanId.put(bareId, episodeRef);
            }
        }
        return episodeByCarePlanId;
    }

    private static List<ProcedureRow> extractRows(Bundle response) {
        List<ProcedureRow> rows = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : response.getEntry()) {
            if (!(entry.getResource() instanceof Parameters parameters)) {
                continue;
            }
            for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
                if (!param.getName().startsWith("item_")) {
                    continue;
                }
                rows.add(toRow(param));
            }
        }
        return rows;
    }

    private static ProcedureRow toRow(Parameters.ParametersParameterComponent row) {
        String carePlan = null;
        String serviceRequest = null;
        String serviceRequestVersionId = null;
        String activity = null;
        Date resolvedStart = null;
        Date resolvedEnd = null;
        String timingType = null;
        Integer occurrencesRequested = null;
        Integer totalSubmitted = null;

        for (Parameters.ParametersParameterComponent part : row.getPart()) {
            String name = part.getName();
            switch (name) {
                case "CarePlan" -> carePlan = referenceOf(part);
                case "ServiceRequest" -> serviceRequest = referenceOf(part);
                case "ServiceRequestVersionId" -> serviceRequestVersionId = stringOf(part);
                case "Activity" -> activity = stringOf(part);
                case "ResolvedTimingStart" -> resolvedStart = dateOf(part);
                case "ResolvedTimingEnd" -> resolvedEnd = dateOf(part);
                case "TimingType" -> timingType = codeableConceptCodeOf(part);
                case "OccurrencesRequested" -> occurrencesRequested = integerOf(part);
                case "TotalSubmitted" -> totalSubmitted = integerOf(part);
                default -> { /* ignore */ }
            }
        }
        return new ProcedureRow(
                carePlan,
                serviceRequest,
                serviceRequestVersionId,
                activity,
                resolvedStart,
                resolvedEnd,
                timingType,
                occurrencesRequested,
                totalSubmitted);
    }

    private static String referenceOf(Parameters.ParametersParameterComponent part) {
        return part.getValue() instanceof Reference ref && ref.hasReference() ? ref.getReference() : null;
    }

    private static String codeableConceptCodeOf(Parameters.ParametersParameterComponent part) {
        if (part.getValue() instanceof CodeableConcept codeableConcept && !codeableConcept.getCoding().isEmpty()) {
            return codeableConcept.getCoding().get(0).getCode();
        }
        return stringOf(part);
    }

    private static String stringOf(Parameters.ParametersParameterComponent part) {
        if (part.getValue() instanceof StringType stringType) {
            return stringType.getValue();
        }
        if (part.getValue() instanceof PrimitiveType<?> primitiveType) {
            return primitiveType.getValueAsString();
        }
        return null;
    }

    private static Date dateOf(Parameters.ParametersParameterComponent part) {
        return part.getValue() instanceof DateTimeType dateTime ? dateTime.getValue() : null;
    }

    private static Integer integerOf(Parameters.ParametersParameterComponent part) {
        if (part.getValue() instanceof IntegerType integerType) {
            return integerType.getValue();
        }
        if (part.getValue() instanceof PrimitiveType<?> primitiveType && primitiveType.getValueAsString() != null) {
            try {
                return Integer.parseInt(primitiveType.getValueAsString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
