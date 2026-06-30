package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.time.LocalDateTime;

/**
 * Flattened view of one {@code $get-patient-procedures} row for the citizen's weekly overview.
 *
 * @param title       activity description as rendered by the server
 * @param scheduledAt slot start in local time; null when {@link #timingType} is
 *                    {@code Unresolved} or {@code Adhoc}
 * @param timingType  {@code Resolved} | {@code Unresolved} | {@code Adhoc} | {@code Extra}
 * @param progress    short submitted-vs-expected string like {@code "1/3"}; null when not applicable
 * @param carePlanId  bare id of the owning CarePlan, used to link to its detail page; null when the
 *                    row carried no CarePlan reference
 */
public record ActivityView(
        String title,
        LocalDateTime scheduledAt,
        String timingType,
        String progress,
        String carePlanId) {
}
