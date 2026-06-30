package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * Response payload of {@code GET {issuer-uri}/resource/ehealth-connect/contexts}.
 *
 * <p>The server-side type is {@code AvailableContextModel}. We only deserialize the fields the
 * reference client needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailableContexts(
        @JsonProperty("care_teams") @Nullable List<CareTeamOption> careTeams,
        @Nullable List<OrganizationOption> organizations,
        @JsonProperty("warning_details") @Nullable List<String> warnings)
        implements Serializable {

    public List<CareTeamOption> careTeamsOrEmpty() {
        return careTeams == null ? List.of() : careTeams;
    }
}
