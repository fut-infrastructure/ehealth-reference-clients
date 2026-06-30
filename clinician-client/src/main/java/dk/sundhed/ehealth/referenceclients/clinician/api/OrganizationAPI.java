package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirClientFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.SearchUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bulk lookup against the Organization FHIR server for {@link Organization} and {@link CareTeam}.
 *
 * <p>Used as a follow-up after an {@code EpisodeOfCare} search whose CapabilityStatement only
 * permits {@code _include=EpisodeOfCare:condition}; managing-organization, care-manager, and team
 * references are resolved here instead of via {@code _include}.
 */
@Component
public class OrganizationAPI {

    private static final TokenClientParam RES_ID = new TokenClientParam("_id");

    private final FhirClientFactory fhirClientFactory;

    public OrganizationAPI(FhirClientFactory fhirClientFactory) {
        this.fhirClientFactory = fhirClientFactory;
    }

    public List<Organization> findOrganizationsById(Set<String> ids, EHealthContext context) {
        if (ids.isEmpty()) {
            return List.of();
        }
        IGenericClient client = fhirClientFactory.createClient(FhirServer.ORGANIZATION, context);
        Bundle first = client.search()
                .forResource(Organization.class)
                .where(RES_ID.exactly().codes(new ArrayList<>(ids)))
                .count(200)
                .returnBundle(Bundle.class)
                .execute();
        Bundle all = SearchUtil.loadAllPages(client, first);
        return BundleUtil.extract(all, Organization.class);
    }

    public List<CareTeam> findCareTeamsById(Set<String> ids, EHealthContext context) {
        if (ids.isEmpty()) {
            return List.of();
        }
        IGenericClient client = fhirClientFactory.createClient(FhirServer.ORGANIZATION, context);
        Bundle first = client.search()
                .forResource(CareTeam.class)
                .where(RES_ID.exactly().codes(new ArrayList<>(ids)))
                .count(200)
                .returnBundle(Bundle.class)
                .execute();
        Bundle all = SearchUtil.loadAllPages(client, first);
        return BundleUtil.extract(all, CareTeam.class);
    }
}
