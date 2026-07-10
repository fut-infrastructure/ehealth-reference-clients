package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Raw FHIR wrappers around the {@code PlanDefinition} resource on the {@link FhirServer#PLAN}
 * server.
 *
 * <p>No business logic or derivation lives here. Derivation goes in {@code clinician/app/} mappers.
 */
@Component
public class PlanAPI {

    public record SearchResult(
            List<PlanDefinition> planDefinitions,
            Map<String, ActivityDefinition> activityDefinitions) {
    }

    /**
     * Hard cap on PlanDefinitions loaded for the picker. The shared plan server holds tens of
     * thousands of active PlanDefinitions (load-test data exceeds 44&nbsp;000), so walking every
     * {@code next} page would fire hundreds of sequential requests (minutes of latency) and the
     * server-side {@code _getpages} cursor expires mid-walk, surfacing as an HTTP 500 (the same
     * failure mode {@code EpisodeOfCareAPI} guards against). We deliberately fetch a single bounded
     * page; a production client would constrain the search further (by topic, publisher, etc.).
     */
    private static final int MAX_PLAN_DEFINITIONS = 100;

    private final FhirClientFactory fhirClientFactory;

    public PlanAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Searches the plan server for PlanDefinitions in {@code status=active}, which is the FUT IG
     * convention for "published".
     *
     * <p>Returns a single bounded page capped at {@link #MAX_PLAN_DEFINITIONS}; see that field for
     * why the full set is never walked.
     *
     * @param context        security context
     * @param titleSearch    optional title substring; passed as {@code title:contains} when non-blank
     * @param withActivities when true, adds {@code definition:missing=false} so only PlanDefinitions
     *                       that carry at least one action with a definition are returned
     * @return up to {@link #MAX_PLAN_DEFINITIONS} published PlanDefinitions
     */
    public SearchResult findPublishedPlanDefinitions(
            EHealthContext context, String titleSearch, boolean withActivities) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PLAN, context);

        var query = client.search()
                .forResource(PlanDefinition.class)
                .where(PlanDefinition.STATUS.exactly().code("active"));

        if (titleSearch != null && !titleSearch.isBlank()) {
            query = query.and(PlanDefinition.TITLE.contains().value(titleSearch));
        }
        if (withActivities) {
            query = query.and(new ca.uhn.fhir.rest.gclient.ReferenceClientParam("definition")
                    .isMissing(false));
        }

        Bundle page = query
                .include(PlanDefinition.INCLUDE_DEFINITION)
                .sort().descending("_lastUpdated")
                .count(MAX_PLAN_DEFINITIONS)
                .returnBundle(Bundle.class)
                .execute();

        List<PlanDefinition> plans = BundleUtil.extract(page, PlanDefinition.class);
        Map<String, ActivityDefinition> activities = BundleUtil.extract(page, ActivityDefinition.class)
                .stream()
                .collect(Collectors.toMap(
                        activityDefinition -> activityDefinition.getIdElement().toVersionless().getValue(),
                        activityDefinition -> activityDefinition));
        return new SearchResult(plans, activities);
    }
}
