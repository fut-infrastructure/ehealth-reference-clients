package dk.sundhed.ehealth.referenceclients.clinician.app;

import org.hl7.fhir.r4.model.Patient;

import java.util.List;

/**
 * One patient plus the episodes of care they hold, rendered on {@code /patients/{id}}.
 *
 * <p>Demographics are projected from the {@link Patient} resource; episodes reuse the compact
 * {@link PatientEpisodesView.EpisodeSummaryView} produced by {@link EpisodeOfCareMapper}. The list
 * is empty for a citizen who has just been created and has no forløb yet.
 */
public record PatientDetailView(
        String patientId,
        String patientName,
        String patientCpr,
        String birthDate,
        String gender,
        String address,
        List<PatientEpisodesView.EpisodeSummaryView> episodes) {

    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

    /**
     * Builds the view from the raw {@link Patient} and the already-mapped episode summaries.
     *
     * @param patient  the Patient resource
     * @param episodes episode summaries for this patient (may be empty)
     */
    public static PatientDetailView from(
            Patient patient, List<PatientEpisodesView.EpisodeSummaryView> episodes) {
        return new PatientDetailView(
                patient.getIdElement().getIdPart(),
                displayName(patient),
                cpr(patient),
                patient.hasBirthDate() ? patient.getBirthDateElement().getValueAsString() : null,
                patient.getGender() != null ? patient.getGender().getDisplay() : null,
                address(patient),
                episodes);
    }

    private static String displayName(Patient patient) {
        return patient.getName().stream()
                .findFirst()
                .map(name -> {
                    String given = name.getGivenAsSingleString();
                    String family = name.getFamily();
                    if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
                        return given + " " + family;
                    }
                    if (family != null && !family.isBlank()) {
                        return family;
                    }
                    return given != null ? given : null;
                })
                .orElse(null);
    }

    private static String cpr(Patient patient) {
        return patient.getIdentifier().stream()
                .filter(id -> CPR_SYSTEM.equals(id.getSystem()))
                .map(id -> id.getValue())
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String address(Patient patient) {
        if (patient.getAddress().isEmpty()) {
            return null;
        }
        String text = patient.getAddressFirstRep().getText();
        return text != null && !text.isBlank() ? text : null;
    }
}
