package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

/**
 * Helpers for reading {@link CodeableConcept}s in a display-oriented way.
 */
public final class CodeableConcepts {

    private CodeableConcepts() {
    }

    /**
     * Returns a human-readable label for {@code concept}: the first coding's {@code display},
     * falling back to the concept {@code text}, or {@code null} when neither is present.
     *
     * @param concept the concept to label; may be {@code null}
     * @return a display label, or {@code null}
     */
    public static String displayOf(CodeableConcept concept) {
        if (concept == null) {
            return null;
        }
        for (Coding coding : concept.getCoding()) {
            if (coding.hasDisplay()) {
                return coding.getDisplay();
            }
        }
        return concept.hasText() ? concept.getText() : null;
    }
}
