package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * One care team the user is a member of, as returned by the {@code ehealth-connect/contexts}
 * endpoint. {@code careTeamId} is a bare logical FHIR id; {@link #affiliation()} carries the
 * owning organization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CareTeamOption(
        @JsonProperty("id") @Nullable String careTeamId,
        @Nullable String name,
        @Nullable OrganizationOption affiliation,
        @Nullable List<String> roles,
        @Nullable List<String> programs)
        implements Serializable {

    /**
     * Projects this care team into the {@link EHealthContext} token scope it implies: the care team
     * id plus, when the team has an affiliation, its organization. Both are passed through as-is,
     * so an absent id or affiliation simply stays unset in the tuple.
     */
    public EHealthContext toEHealthContext() {
        String organizationId = affiliation == null ? null : affiliation.organizationId();
        return EHealthContext.empty()
                .withCareTeam(careTeamId)
                .withOrganization(organizationId);
    }
}
