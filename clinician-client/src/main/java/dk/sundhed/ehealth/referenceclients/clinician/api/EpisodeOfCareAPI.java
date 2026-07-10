package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.*;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw FHIR wrapper around {@code EpisodeOfCare} operations on {@code fut-careplan}.
 *
 * <p>Search calls only request {@code _include=EpisodeOfCare:condition} because the fut-careplan
 * CapabilityStatement rejects every other include on EpisodeOfCare. Patient, Organization, and
 * CareTeam references are resolved by the controller via {@link PatientAPI} / {@link
 * OrganizationAPI}.
 */
@Component
public class EpisodeOfCareAPI {

    private static final ReferenceClientParam TEAM = new ReferenceClientParam("team");
    private static final ReferenceClientParam PATIENT = new ReferenceClientParam("patient");

    /**
     * Hard cap on episodes loaded in one go. A care team can hold tens of thousands of episodes
     * (load-test teams exceed 70&nbsp;000), so walking every {@code next} page would fire thousands
     * of sequential requests (minutes of latency) and the server-side {@code _getpages} search
     * cursor expires mid-walk, surfacing as an HTTP 500. We deliberately fetch a single bounded
     * page; the home roster relies on the in-page filter (and, later, server-side search) rather
     * than ever loading the full set.
     */
    private static final int MAX_EPISODES_PER_QUERY = 100;

    private final FhirClientFactory fhirClientFactory;
    private final BaseUrlResolver baseUrlResolver;

    public EpisodeOfCareAPI(FhirClientFactory fhirClientFactory, BaseUrlResolver baseUrlResolver) {
        this.fhirClientFactory = fhirClientFactory;
        this.baseUrlResolver = baseUrlResolver;
    }

    /**
     * Fetches a single bounded page of {@code EpisodeOfCare} resources owned by the current care
     * team whose status is {@code planned} or {@code active}, with referenced diagnosis
     * {@code Condition}s pulled in via {@code _include=EpisodeOfCare:condition}. Capped at
     * {@link #MAX_EPISODES_PER_QUERY}; see that field for why the full set is never walked.
     */
    public SearchResult findPlannedAndActiveEpisodesByTeam(EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        Bundle page = client.search()
                .forResource(EpisodeOfCare.class)
                .where(TEAM.hasId(context.careTeamId()))
                .where(EpisodeOfCare.STATUS.exactly().codes("planned", "active"))
                .include(EpisodeOfCare.INCLUDE_CONDITION)
                .count(MAX_EPISODES_PER_QUERY)
                .returnBundle(Bundle.class)
                .execute();

        return new SearchResult(
                BundleUtil.extract(page, EpisodeOfCare.class),
                BundleUtil.extract(page, Condition.class));
    }

    /**
     * One page of a patient's {@code EpisodeOfCare} search, plus the opaque server cursor URLs for
     * the adjacent pages (null when there is no such page). The cursor URLs are HAPI
     * {@code _getpages} links; pass one back to {@link #findEpisodesByPatientPage} to fetch it.
     */
    public record EpisodePage(
            List<EpisodeOfCare> episodes,
            List<Condition> conditions,
            String nextPageUrl,
            String previousPageUrl) {
    }

    /**
     * Fetches one page of the given patient's {@code EpisodeOfCare}s on the current care team,
     * following the FHIR server's own pagination cursor rather than loading the whole set. The
     * patient view uses this so a citizen with more episodes than fit on a page can be paged through
     * with the server's {@code next}/{@code previous} links. This is the bounded, on-demand
     * counterpart to the roster's deliberate single-page fetch.
     *
     * @param patientId fully-qualified Patient URL (ignored when {@code pageUrl} is supplied)
     * @param context   security context
     * @param pageUrl   an opaque cursor URL from a previous page, or {@code null} for the first page
     * @param pageSize  page size for the first page (the server echoes it onto the cursor links)
     */
    public EpisodePage findEpisodesByPatientPage(
            String patientId, EHealthContext context, String pageUrl, int pageSize) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        Bundle page;
        if (pageUrl == null || pageUrl.isBlank()) {
            // Same constraints as findEpisodesByPatient: the careplan server requires the team filter
            // alongside the patient, and only EpisodeOfCare:condition may be included.
            page = client.search()
                    .forResource(EpisodeOfCare.class)
                    .where(PATIENT.hasId(patientId))
                    .where(TEAM.hasId(context.careTeamId()))
                    .include(EpisodeOfCare.INCLUDE_CONDITION)
                    .count(pageSize)
                    .returnBundle(Bundle.class)
                    .execute();
        } else {
            page = client.loadPage().byUrl(pageUrl).andReturnBundle(Bundle.class).execute();
        }

        return new EpisodePage(
                BundleUtil.extract(page, EpisodeOfCare.class),
                BundleUtil.extract(page, Condition.class),
                linkUrl(page, Bundle.LINK_NEXT),
                linkUrl(page, Bundle.LINK_PREV));
    }

    private static String linkUrl(Bundle bundle, String relation) {
        Bundle.BundleLinkComponent link = bundle.getLink(relation);
        return link == null ? null : link.getUrl();
    }

    /**
     * Reads a single {@code EpisodeOfCare} by its bare logical id, with its diagnosis {@code
     * Condition}s included.
     */
    public SearchResult fetchEpisodeOfCareById(String episodeId, EHealthContext context) {
        String bareId = new IdType(episodeId).getIdPart();
        String qualifiedEpisode =
                baseUrlResolver.resolve(FhirServer.CARE_PLAN) + "/EpisodeOfCare/" + bareId;

        // careplan does not register _id as a search parameter (so EpisodeOfCare?_id=... is a 400),
        // and a plain read is denied unless the access token carries the episode in its context
        // (HTTP 403 "Security token context missing for user type: EpisodeOfCare"). So we scope the
        // token to this episode via episode_of_care_id and read it directly. Diagnosis Conditions
        // can't ride along on an _include (that needs a search), and Condition exposes no search
        // parameters at all, so each referenced Condition is read by id under the same token.
        EHealthContext episodeContext = context.withEpisodeOfCare(qualifiedEpisode);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);

        EpisodeOfCare episode =
                client.read().resource(EpisodeOfCare.class).withId(bareId).execute();

        List<Condition> conditions = new ArrayList<>();
        for (EpisodeOfCare.DiagnosisComponent diagnosis : episode.getDiagnosis()) {
            Reference conditionRef = diagnosis.getCondition();
            if (conditionRef == null || conditionRef.isEmpty()) {
                continue;
            }
            String conditionId = conditionRef.getReferenceElement().getIdPart();
            if (conditionId == null || conditionId.isBlank()) {
                continue;
            }
            conditions.add(client.read().resource(Condition.class).withId(conditionId).execute());
        }

        return new SearchResult(List.of(episode), conditions);
    }

    /**
     * Creates an {@code EpisodeOfCare} (plus its diagnosis {@code Condition}) atomically via a
     * FHIR transaction. The bundle is built by
     * {@link dk.sundhed.ehealth.referenceclients.clinician.app.EpisodeOfCareMapper#toCreateEpisodeTransaction}.
     */
    public EpisodeOfCare createEpisode(Bundle episodeOfCareAndProvenances, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        // EpisodeOfCare does not allow a direct POST (the careplan CapabilityStatement lists only
        // search/read/vread/patch). Creation goes through the system operation
        // $create-episode-of-care, which takes the EpisodeOfCare + Condition + privacy Provenance
        // as a single transaction Bundle wrapped in an episodeOfCareAndProvenances parameter.
        Parameters parameters = new Parameters();
        parameters.addParameter()
                .setName("episodeOfCareAndProvenances")
                .setResource(episodeOfCareAndProvenances);

        MethodOutcome outcome = client.operation()
                .onServer()
                .named("create-episode-of-care")
                .withParameters(parameters)
                .withAdditionalHeader("Prefer", "return=representation")
                .returnMethodOutcome()
                .execute();

        if (!(outcome.getResource() instanceof Bundle response)) {
            throw new IllegalStateException("$create-episode-of-care did not return a Bundle");
        }
        return BundleUtil.extractFirst(response, EpisodeOfCare.class)
                .orElseThrow(() -> new IllegalStateException(
                        "$create-episode-of-care response contained no EpisodeOfCare"));
    }

    /**
     * Issues a JSON-Patch {@code replace /status} against the given {@code EpisodeOfCare}.
     */
    public void changeEpisodeStatus(
            String episodeId, EpisodeOfCare.EpisodeOfCareStatus target, EHealthContext context) {
        // Like a direct read, the careplan server denies a patch on an EpisodeOfCare unless the
        // access token carries that episode in its context (HTTP 403 "Security token context
        // missing for user type: EpisodeOfCare"). The id is already the fully-qualified URL.
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);

        String patch = JsonPatch.builder()
                .replace("/status", target.toCode())
                .build();

        client.patch().withBody(patch).withId(new IdType(episodeId)).execute();
    }

    /**
     * Raw projection of an {@code EpisodeOfCare} search: the episodes plus their included
     * diagnosis Conditions. Patient / Organization / CareTeam references are resolved by the
     * controller layer via {@link PatientAPI} / {@link OrganizationAPI}.
     */
    public record SearchResult(List<EpisodeOfCare> episodes, List<Condition> conditions) {
    }
}
