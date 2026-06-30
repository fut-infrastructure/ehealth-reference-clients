package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import dk.sundhed.ehealth.referenceclients.clinician.app.util.CarePlanActivationUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw FHIR wrappers around the {@code CarePlan} resource on the {@link FhirServer#CARE_PLAN}
 * server, plus the {@code $apply} instance-level operation on the {@code PlanDefinition} hosted
 * by the {@link FhirServer#PLAN} server for instantiating a plan from a {@code PlanDefinition}.
 *
 * <p>No business logic lives here. All derivation (status badges, activity grouping, ...) belongs
 * in mappers under {@code clinician/app/}.
 */
@Component
public class CarePlanAPI {

    private static final ReferenceClientParam EPISODE_OF_CARE =
            new ReferenceClientParam("episodeOfCare");
    private static final ReferenceClientParam CARE_TEAM = new ReferenceClientParam("care-team");

    /**
     * Page size requested when walking the recency window. The careplan server caps the effective
     * page at ~200 regardless, so this asks for the largest page it will serve to keep the number of
     * round-trips (and thus the chance of hitting {@link #MAX_RECENT_PAGES}) as low as possible.
     */
    private static final int RECENT_PAGE_SIZE = 200;

    /**
     * Safety cap on how many pages of the recency window are walked when building the
     * patients-with-plans roster. See {@link #findRecentCarePlansByCareTeam} for why the window is
     * walked at all and why this bound is safe. At {@link #RECENT_PAGE_SIZE} per page this covers a
     * few thousand plans (comfortably above a realistic 30-day window) before giving up.
     */
    private static final int MAX_RECENT_PAGES = 30;

    private final FhirClientFactory fhirClientFactory;

    public CarePlanAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    /**
     * Invokes the instance-level {@code $apply} operation on a {@link PlanDefinition} to
     * instantiate a draft {@link CarePlan} for the given {@code EpisodeOfCare}.
     *
     * <p>The FUT IG declares {@code PlanDefinition-i-apply} with {@code instance=true} (see
     * {@code OperationDefinition-PlanDefinition-i-apply.json} and the {@code PlanDefinition}
     * block of {@code CapabilityStatement-plan.json}). The operation is therefore invoked as
     * {@code POST /PlanDefinition/{id}/$apply} on the plan server with a {@link Parameters} body
     * carrying the {@code episodeOfCare} {@link Reference}. The server materialises the CarePlan
     * and its activities (Task/Appointment/ServiceRequest), persists them and returns the
     * resulting {@link CarePlan} (or a {@link Bundle} when {@code Prefer: return=representation}
     * is honoured).
     *
     * @param planDefinitionId fully-qualified PlanDefinition URL
     * @param episodeOfCareId  fully-qualified EpisodeOfCare URL
     * @param context          security context
     * @return the newly-created draft {@link CarePlan}
     */
    public CarePlan applyPlanDefinition(
            String planDefinitionId, String episodeOfCareId, EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.PLAN, context);

        Parameters parameters = new Parameters();
        parameters.addParameter().setName("episodeOfCare").setValue(new Reference(episodeOfCareId));

        MethodOutcome outcome = client
                .operation()
                .onInstance(new IdType(planDefinitionId))
                .named("apply")
                .withParameters(parameters)
                .preferResponseType(Bundle.class)
                .returnMethodOutcome()
                .withAdditionalHeader("Prefer", "return=representation")
                .execute();

        return extractCarePlan(outcome);
    }

    /**
     * Lists every {@link CarePlan} attached to the given episode for the current care team.
     *
     * <p>The careplan server's security layer requires a search to carry every dimension present in
     * the access token's context. With an episode-scoped token that means both {@code episodeOfCare}
     * and {@code care-team} must appear as search parameters, otherwise the request is rejected with
     * HTTP 403 ("Search parameters not matching security token context: ..."). The token is scoped
     * to the episode here so the read is authorised.
     *
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL
     * @param context         security context (must carry the care team)
     * @return the care plans owned by the episode
     */
    public List<CarePlan> findCarePlansByEpisode(String episodeOfCareId, EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);

        Bundle bundle = client.search()
                .forResource(CarePlan.class)
                .where(EPISODE_OF_CARE.hasId(episodeOfCareId))
                .where(CARE_TEAM.hasId(context.careTeamId()))
                .returnBundle(Bundle.class)
                .execute();

        return BundleUtil.extract(bundle, CarePlan.class);
    }

    /**
     * Returns every {@link CarePlan} for the current care team whose {@code _lastUpdated} is on or
     * after {@code since}, used to build the home roster of patients with recent care plans.
     *
     * <p>Why walk pages here when {@code EpisodeOfCareAPI} and {@code PlanAPI} deliberately fetch a
     * single bounded page: the roster needs the <em>distinct patients</em> behind these plans, and
     * the careplan server ignores {@code _sort} (results always come back ascending by id) and does
     * not return a {@code total}. A patient with a single recent plan can therefore sit anywhere in
     * the result, including the last page, so a single page would silently drop them. The
     * {@code _lastUpdated} filter bounds the set to the recency window (a 30-day window on a busy
     * load-test team is on the order of a few hundred plans across a handful of pages), which makes
     * the walk safe. Walking the full 44k/71k catalogues would be unsafe because the {@code _getpages} cursor
     * expires mid-walk. {@link #MAX_RECENT_PAGES} caps the worst case; because the order is ascending
     * by id, hitting the cap drops the <em>newest</em> plans, so the cap is set generously above any
     * realistic 30-day volume.
     *
     * @param context security context (must carry the care team)
     * @param since   inclusive lower bound on {@code _lastUpdated}
     * @return care plans in the window, ascending by id, up to the page cap
     */
    public List<CarePlan> findRecentCarePlansByCareTeam(EHealthContext context, LocalDate since) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);

        Bundle page = client.search()
                .forResource(CarePlan.class)
                .where(CARE_TEAM.hasId(context.careTeamId()))
                .lastUpdated(new DateRangeParam(
                        new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, since.toString())))
                .count(RECENT_PAGE_SIZE)
                .returnBundle(Bundle.class)
                .execute();

        List<CarePlan> all = new ArrayList<>(BundleUtil.extract(page, CarePlan.class));
        int pages = 1;
        while (page.getLink(Bundle.LINK_NEXT) != null && pages < MAX_RECENT_PAGES) {
            page = client.loadPage().next(page).execute();
            all.addAll(BundleUtil.extract(page, CarePlan.class));
            pages++;
        }
        return all;
    }

    /**
     * Reads a {@link CarePlan} and includes its referenced activities in a single round-trip.
     *
     * <p>HAPI's {@code read()} does not honour {@code _include}, so this performs a search. The
     * careplan server rejects a plain {@code _id} search under an episode-scoped token (HTTP 403
     * "Search parameters not matching security token context: ...") because the search must carry
     * every dimension in the token context. The search is therefore constrained by
     * {@code episodeOfCare}, {@code care-team} and {@code _id}, with
     * {@code _include=CarePlan:activity-reference} pulling the activities. The result is the raw
     * search bundle; callers extract the {@link CarePlan} and its activity resources (Task,
     * Appointment, ServiceRequest) themselves.
     *
     * @param carePlanId      fully-qualified CarePlan URL
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL (scopes the token and the search)
     * @param context         security context (must carry the care team)
     * @return the raw search bundle containing the CarePlan and its activity resources
     */
    public Bundle fetchCarePlanByIdWithActivities(
            String carePlanId, String episodeOfCareId, EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);

        String bareId = new IdType(carePlanId).getIdPart();
        return client.search()
                .forResource(CarePlan.class)
                .where(EPISODE_OF_CARE.hasId(episodeOfCareId))
                .where(CARE_TEAM.hasId(context.careTeamId()))
                .where(IAnyResource.RES_ID.exactly().code(bareId))
                .include(new Include("CarePlan:activity-reference"))
                .returnBundle(Bundle.class)
                .execute();
    }

    /**
     * Executes a FHIR transaction Bundle against the careplan server, scoping the token to the
     * supplied episode (the server denies CarePlan writes without the episode in context). Used by
     * the activate-care-plan flow to flip the CarePlan and its activities to {@code active}
     * atomically. See {@link CarePlanActivationUtil}.
     *
     * @param transactionBundle the prepared transaction bundle
     * @param episodeOfCareId   fully-qualified EpisodeOfCare URL (scopes the token)
     * @param context           security context
     * @return the transaction response bundle
     */
    public Bundle executeTransaction(
            Bundle transactionBundle, String episodeOfCareId, EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);
        return client.transaction().withBundle(transactionBundle).execute();
    }

    /**
     * Activates a draft {@link CarePlan} together with all its activities in one atomic
     * transaction.
     *
     * <p>Fetches the plan plus its {@link ServiceRequest} activities, hands them to
     * {@link CarePlanActivationUtil} to build a transaction Bundle, and executes it.
     *
     * @param carePlanId      fully-qualified CarePlan URL
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL (scopes the token and the search)
     * @param context         security context
     */
    public void activateCarePlan(
            String carePlanId, String episodeOfCareId, EHealthContext context) {
        Bundle searchBundle =
                fetchCarePlanByIdWithActivities(carePlanId, episodeOfCareId, context);
        CarePlan carePlan = BundleUtil.extractFirst(searchBundle, CarePlan.class)
                .orElseThrow(() -> new IllegalStateException(
                        "No CarePlan returned for id " + carePlanId));
        List<ServiceRequest> serviceRequests = BundleUtil.extract(searchBundle, ServiceRequest.class);

        Bundle transactionBundle =
                CarePlanActivationUtil.buildActivationBundle(carePlan, serviceRequests);
        executeTransaction(transactionBundle, episodeOfCareId, context);
    }

    /**
     * Sets the {@link CarePlan} status to the supplied target and PUTs the resource back to the
     * server. Activities are not touched.
     *
     * <p>The token is scoped to the episode: the careplan server denies a plain read or update
     * unless the access token carries the owning episode (HTTP 403 "Security token context missing
     * for user type: EpisodeOfCare"). Supported targets per the spec: {@code active},
     * {@code on-hold}, {@code completed}, {@code revoked}, {@code entered-in-error}.
     *
     * @param carePlanId      fully-qualified CarePlan URL
     * @param episodeOfCareId fully-qualified EpisodeOfCare URL (scopes the token)
     * @param target          desired {@link CarePlan.CarePlanStatus}
     * @param context         security context
     */
    public void changeCarePlanStatus(
            String carePlanId,
            String episodeOfCareId,
            CarePlan.CarePlanStatus target,
            EHealthContext context) {
        EHealthContext episodeContext = context.withEpisodeOfCare(episodeOfCareId);
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, episodeContext);

        CarePlan carePlan = client.read().resource(CarePlan.class).withUrl(carePlanId).execute();
        carePlan.setStatus(target);
        client.update()
                .resource(carePlan)
                .prefer(PreferReturnEnum.MINIMAL)
                .execute();
    }

    private static CarePlan extractCarePlan(MethodOutcome outcome) {
        if (outcome.getResource() instanceof CarePlan carePlan) {
            return carePlan;
        }
        if (outcome.getResource() instanceof Bundle bundle) {
            return BundleUtil.extractFirst(bundle, CarePlan.class)
                    .orElseThrow(() -> new IllegalStateException(
                            "$apply response Bundle contained no CarePlan"));
        }
        throw new IllegalStateException(
                "$apply returned no CarePlan and no Bundle; resource was "
                        + (outcome.getResource() == null
                        ? "null"
                        : outcome.getResource().getClass().getSimpleName()));
    }
}
