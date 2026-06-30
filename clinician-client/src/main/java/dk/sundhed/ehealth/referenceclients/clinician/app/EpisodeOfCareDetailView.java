package dk.sundhed.ehealth.referenceclients.clinician.app;

import java.util.List;

/**
 * Detail projection of one {@code EpisodeOfCare} plus its referenced resources, rendered on
 * {@code /episodes/{id}}.
 */
public record EpisodeOfCareDetailView(
        String id,
        String status,
        String startDate,
        String endDate,
        String patientId,
        String patientName,
        String patientCpr,
        String managingOrganizationName,
        String careTeamName,
        List<DiagnosisView> diagnoses) {

    /**
     * One diagnosis row: SNOMED code, display label, optional FHIR rank.
     */
    public record DiagnosisView(String code, String display, Integer rank) {
    }
}
