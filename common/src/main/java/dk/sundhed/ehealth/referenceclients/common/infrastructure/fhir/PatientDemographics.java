package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

/**
 * Helpers for reading display-oriented demographics off a {@link Patient} resource.
 */
public final class PatientDemographics {

    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

    private PatientDemographics() {
    }

    /**
     * Returns the patient's first name as {@code HumanName.text} when present, otherwise "given
     * family", falling back to just the family name or just the given name when the other half is
     * missing, or {@code null} when the patient has no usable name at all.
     */
    public static String displayName(Patient patient) {
        return patient.getName().stream().findFirst().map(name -> {
            if (name.getText() != null && !name.getText().isBlank()) {
                return name.getText();
            }
            String given = name.getGivenAsSingleString();
            String family = name.getFamily();
            if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
                return given + " " + family;
            }
            if (family != null && !family.isBlank()) {
                return family;
            }
            return given != null && !given.isBlank() ? given : null;
        }).orElse(null);
    }

    /**
     * Returns the patient's CPR number from its DK-Core identifier, or {@code null} when the
     * patient carries no CPR identifier.
     */
    public static String cpr(Patient patient) {
        return patient.getIdentifier().stream()
                .filter(identifier -> CPR_SYSTEM.equals(identifier.getSystem()))
                .map(Identifier::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
