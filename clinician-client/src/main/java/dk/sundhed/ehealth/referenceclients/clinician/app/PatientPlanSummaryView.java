package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

/**
 * One row of the home page's patient table: either a roster row (a patient with at least one care
 * plan updated within the recency window, built by {@link HomeController} from the care plans
 * returned by
 * {@link dk.sundhed.ehealth.referenceclients.clinician.api.CarePlanAPI#findRecentCarePlansByCareTeam})
 * or a search result row (built from {@link dk.sundhed.ehealth.referenceclients.clinician.api.PatientAPI#searchPatients},
 * where a plan count doesn't apply). The row links to {@code /patients/{patientId}}.
 *
 * @param planCount number of plans in the recency window, or {@code null} for a search result row
 */
public record PatientPlanSummaryView(
        String patientId,
        @Nullable String patientName,
        @Nullable String patientCpr,
        @Nullable Integer planCount) {
}
