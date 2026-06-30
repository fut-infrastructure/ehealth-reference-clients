package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

/**
 * Lightweight row for the care-plan list shown on the episode detail page. Carries the bare
 * CarePlan id, its status code, and a display title (falling back to the id when the plan has no
 * title). The episode detail template links each row to
 * {@code /episodes/{episodeOfCareId}/care-plans/{id}}.
 */
public record CarePlanSummaryView(
        String id,
        @Nullable String status,
        String title) {
}
