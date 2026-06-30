package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

/**
 * One row of the home roster: a patient who has at least one care plan updated within the recency
 * window, plus how many of their plans fall in that window. Built by {@link HomeController} from the
 * care plans returned by
 * {@link dk.sundhed.ehealth.referenceclients.clinician.api.CarePlanAPI#findRecentCarePlansByCareTeam}.
 * The row links to {@code /patients/{patientId}}.
 */
public record PatientPlanSummaryView(
        String patientId,
        @Nullable String patientName,
        @Nullable String patientCpr,
        int planCount) {
}
