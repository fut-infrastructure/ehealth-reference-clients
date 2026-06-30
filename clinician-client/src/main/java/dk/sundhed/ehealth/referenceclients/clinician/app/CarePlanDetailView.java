package dk.sundhed.ehealth.referenceclients.clinician.app;

import jakarta.annotation.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * View record for the care-plan detail page.
 *
 * <p>Derivation-only fields ({@code canActivate}, {@code allowedTransitions}) are computed by
 * {@link CarePlanMapper}; the raw FHIR API stays free of UI affordances.
 *
 * <p>Activities are grouped by FHIR resource type (Task / Appointment / ServiceRequest); the
 * template renders one section per non-empty group. The {@code episodeOfCareId} carries the bare
 * id of the parent EpisodeOfCare so the page can offer a back-link without a second lookup.
 */
public record CarePlanDetailView(
        String id,
        String status,
        boolean canActivate,
        List<String> allowedTransitions,
        @Nullable String title,
        @Nullable String description,
        @Nullable LocalDate start,
        @Nullable LocalDate end,
        @Nullable String episodeOfCareId,
        List<ActivityView> tasks,
        List<ActivityView> appointments,
        List<ActivityView> serviceRequests) {

    /**
     * One activity row in the detail page. {@code type} is the FHIR resource type
     * (Task/Appointment/ServiceRequest), {@code label} is a short display string derived from the
     * resource's code element when present.
     */
    public record ActivityView(
            String id,
            String type,
            @Nullable String status,
            @Nullable String label) {
    }
}
