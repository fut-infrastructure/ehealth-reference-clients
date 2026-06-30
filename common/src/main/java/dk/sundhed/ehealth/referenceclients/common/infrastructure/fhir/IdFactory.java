package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

/**
 * Builds fully-qualified FHIR resource IDs of the form {@code <baseUrl>/<resourceType>/<id>}.
 *
 * <p>The FUT FHIR infrastructure uses absolute URLs as resource IDs. Constructing them by hand from
 * the bare ID and the resource type goes through this factory so the base URL stays consistent with
 * {@link BaseUrlResolver}.
 */
@Component
public class IdFactory {

    private final BaseUrlResolver baseUrlResolver;

    public IdFactory(BaseUrlResolver baseUrlResolver) {
        this.baseUrlResolver = baseUrlResolver;
    }

    /**
     * Constructs a fully-qualified FHIR resource URL.
     *
     * @param server       the FHIR server that owns the resource
     * @param resourceType the FHIR resource class (e.g. {@code Patient.class})
     * @param id           the bare logical ID of the resource
     * @return a fully-qualified URL of the form {@code <baseUrl>/<ResourceType>/<id>}
     */
    public String createId(
            FhirServer server, Class<? extends IBaseResource> resourceType, String id) {
        return baseUrlResolver.resolve(server) + "/" + resourceType.getSimpleName() + "/" + id;
    }
}
