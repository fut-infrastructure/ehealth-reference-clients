package dk.sundhed.ehealth.referenceclients.citizen.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.SearchUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Citizen-side list of the logged-in user's active {@link EpisodeOfCare} resources on {@code
 * fut-careplan}. Walks all bundle pages.
 *
 * <p>Direct {@code CarePlan} search is not permitted for citizen tokens by the FUT careplan
 * service (returns 403 "Search parameters not matching security token context: Patient"). The
 * intended citizen-side entry point is {@code EpisodeOfCare?patient=&status=active}; per-episode
 * activities are then fetched via the {@code $get-patient-procedures} server operation when
 * needed.
 */
@Component
public class CitizenEpisodeOfCareAPI {

    private final FhirClientFactory fhirClientFactory;

    public CitizenEpisodeOfCareAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    public List<EpisodeOfCare> listMyActiveEpisodes(EHealthContext context) {
        IGenericClient client = fhirClientFactory.createClient(FhirServer.CARE_PLAN, context);
        Bundle firstPage = client.search()
                .forResource(EpisodeOfCare.class)
                .where(EpisodeOfCare.STATUS.exactly().code(
                        EpisodeOfCare.EpisodeOfCareStatus.ACTIVE.toCode()))
                .where(new ReferenceClientParam("patient").hasId(context.patientId()))
                .returnBundle(Bundle.class)
                .execute();
        Bundle allPages = SearchUtil.loadAllPages(client, firstPage);
        return BundleUtil.extract(allPages, EpisodeOfCare.class);
    }
}
