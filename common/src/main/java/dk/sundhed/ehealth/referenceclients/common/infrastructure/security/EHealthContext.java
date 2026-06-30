package dk.sundhed.ehealth.referenceclients.common.infrastructure.security;

import jakarta.annotation.Nullable;

import java.io.Serializable;

/**
 * Immutable scope tuple carried alongside the OAuth2 access token.
 *
 * <p>All four fields are fully-qualified FHIR URLs (e.g. {@code
 * https://patient.devenvcgi.ehealth.sundhed.dk/fhir/Patient/123}). The FUT Keycloak accepts these
 * as form parameters on a {@code refresh_token} grant and re-issues an access token scoped to the
 * tuple.
 */
public record EHealthContext(
        @Nullable String organizationId,
        @Nullable String careTeamId,
        @Nullable String patientId,
        @Nullable String episodeOfCareId)
        implements Serializable {

    public static EHealthContext empty() {
        return new EHealthContext(null, null, null, null);
    }

    public EHealthContext withOrganization(@Nullable String organizationId) {
        return new EHealthContext(organizationId, careTeamId, patientId, episodeOfCareId);
    }

    public EHealthContext withCareTeam(@Nullable String careTeamId) {
        return new EHealthContext(organizationId, careTeamId, patientId, episodeOfCareId);
    }

    public EHealthContext withPatient(@Nullable String patientId) {
        return new EHealthContext(organizationId, careTeamId, patientId, episodeOfCareId);
    }

    public EHealthContext withEpisodeOfCare(@Nullable String episodeOfCareId) {
        return new EHealthContext(organizationId, careTeamId, patientId, episodeOfCareId);
    }
}
