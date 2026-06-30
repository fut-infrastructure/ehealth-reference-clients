package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * One organization the user belongs to, as returned by the {@code ehealth-connect/contexts}
 * endpoint. {@code id} is a bare logical FHIR id (qualified into a full URL by {@code IdFactory}
 * when handed to {@code EHealthContext}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationOption(
        @Nullable String id,
        @Nullable String name,
        @Nullable List<String> roles)
        implements Serializable {}
