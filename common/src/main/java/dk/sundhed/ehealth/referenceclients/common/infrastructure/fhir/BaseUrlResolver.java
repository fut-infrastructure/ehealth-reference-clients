package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the base URL of a {@link FhirServer} from {@code fhir.server.*} configuration
 * properties.
 *
 * <p>Keys in the property map are the lower-kebab-case names of the {@link FhirServer} enum
 * constants (e.g. {@code fhir.server.patient}, {@code fhir.server.care-plan}).
 */
@Component
@ConfigurationProperties(prefix = "fhir")
@Validated
public class BaseUrlResolver {

    @NotNull
    private Map<String, String> server = new HashMap<>();

    /**
     * Resolves the base URL for the given FHIR server.
     *
     * @param server the server to look up
     * @return the configured base URL
     * @throws IllegalStateException if no URL is configured for the server
     */
    public String resolve(FhirServer server) {
        String name = server.name().toLowerCase().replace('_', '-');
        String url = this.server.get(name);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("No URL configured for FHIR server " + name);
        }
        return url;
    }

    /**
     * Constructs a fully-qualified FHIR resource URL of the form {@code
     * <baseUrl>/<ResourceType>/<id>}.
     *
     * <p>The FUT FHIR infrastructure uses absolute URLs as resource IDs; this pairs the configured
     * base URL from {@link #resolve(FhirServer)} with HAPI's {@link IdType} for the actual URL
     * assembly.
     *
     * @param server       the FHIR server that owns the resource
     * @param resourceType the FHIR resource class (e.g. {@code Patient.class})
     * @param resourceId   the bare logical ID of the resource
     * @return a fully-qualified URL of the form {@code <baseUrl>/<ResourceType>/<id>}
     */
    public String createId(
            FhirServer server, Class<? extends IBaseResource> resourceType, String resourceId) {
        return new IdType(resolve(server), resourceType.getSimpleName(), resourceId, null).getValue();
    }

    /**
     * Returns the URL map (keyed by lower-kebab-case enum name).
     */
    public Map<String, String> getServer() {
        return server;
    }

    /**
     * Sets the URL map. Called by Spring Boot's {@code ConfigurationProperties} binding.
     */
    public void setServer(Map<String, String> server) {
        this.server = server;
    }
}
