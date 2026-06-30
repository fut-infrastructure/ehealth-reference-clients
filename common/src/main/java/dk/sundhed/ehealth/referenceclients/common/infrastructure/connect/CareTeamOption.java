package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * One care team the user is a member of, as returned by the {@code ehealth-connect/contexts}
 * endpoint. {@code id} is a bare logical FHIR id; {@link #affiliation()} carries the owning
 * organization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CareTeamOption(
        @Nullable String id,
        @Nullable String name,
        @Nullable OrganizationOption affiliation,
        @Nullable List<String> roles,
        @Nullable List<String> programs)
        implements Serializable {}
