package dk.sundhed.ehealth.referenceclients.clinician.app;

import java.util.List;

/**
 * One patient and the episodes of care that belong to them, rendered on {@code /episodes}.
 *
 * <p>Built by {@link EpisodeOfCareMapper#toPatientEpisodesViews} from the raw FHIR bundle returned
 * by {@link dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI}.
 */
public record PatientEpisodesView(
        String patientId,
        String patientName,
        String patientCpr,
        List<EpisodeSummaryView> episodes) {

    /**
     * Compact projection of an {@code EpisodeOfCare} for list rendering: id, status, primary
     * diagnosis label, and start date.
     */
    public record EpisodeSummaryView(
            String episodeId,
            String status,
            String diagnosisLabel,
            String startDate) {
    }
}
