package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;

/**
 * Helpers and canonical URLs for the FHIR extensions the reference clients read and write.
 */
public final class FhirExtensions {

    /**
     * {@code workflow-episodeOfCare}: links a resource to the EpisodeOfCare it belongs to.
     */
    public static final String EPISODE_OF_CARE =
            "http://hl7.org/fhir/StructureDefinition/workflow-episodeOfCare";

    private FhirExtensions() {
    }

    /**
     * Returns the {@code Reference.reference} carried by the first extension on {@code resource}
     * with the given {@code url}, or {@code null} when the extension is absent or does not hold a
     * reference.
     *
     * @param resource the resource whose extensions to scan
     * @param url      the extension URL to look for
     * @return the referenced URL, or {@code null}
     */
    public static String referenceValue(DomainResource resource, String url) {
        for (Extension extension : resource.getExtension()) {
            if (url.equals(extension.getUrl())
                    && extension.getValue() instanceof Reference reference
                    && reference.hasReference()) {
                return reference.getReference();
            }
        }
        return null;
    }
}
