package dk.sundhed.ehealth.referenceclients.citizen.app;

import jakarta.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Flattened view of one {@code $get-patient-procedures} row for the citizen's weekly overview.
 *
 * @param title                   activity description as rendered by the server
 * @param scheduledAt             slot start in local time; null when {@link #timingType} is
 *                                {@code Unresolved} or {@code Adhoc}
 * @param scheduledEnd            slot end in local time; null when no slot
 * @param timingType              {@code Resolved} | {@code Unresolved} | {@code Adhoc} | {@code Extra}
 * @param progress                short submitted-vs-expected string like {@code "1/3"}; null when not applicable
 * @param carePlanId              bare id of the owning CarePlan, used to link to its detail page; null when the
 *                                row carried no CarePlan reference
 * @param serviceRequestVersionId version id the server used when computing the slot (from
 *                                {@code $get-patient-procedures}); used in {@code ehealth-resolved-timing}
 * @param serviceRequestRef       fully-qualified ServiceRequest URL used as the {@code serviceRequest}
 *                                query param on the submit-measurement page; null when the row carried no
 *                                ServiceRequest reference
 * @param episodeRef              fully-qualified EpisodeOfCare URL extracted from the owning CarePlan;
 *                                passed to the submit-measurement page so the measurement token can be
 *                                scoped to the episode
 */
public record ActivityView(
        String title,
        LocalDateTime scheduledAt,
        LocalDateTime scheduledEnd,
        String timingType,
        String progress,
        String carePlanId,
        @Nullable String serviceRequestVersionId,
        @Nullable String serviceRequestRef,
        @Nullable String episodeRef) {

    /**
     * ISO local date-time expected by {@code SubmitMeasurementController} on the submit link.
     */
    private static final DateTimeFormatter SLOT_PARAM_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Slot start as a submit-link query value, or {@code null} when the activity has no slot.
     */
    public String slotStartParam() {
        return scheduledAt == null ? null : scheduledAt.format(SLOT_PARAM_FORMAT);
    }

    /**
     * Slot end as a submit-link query value, or {@code null} when the activity has no slot end.
     */
    public String slotEndParam() {
        return scheduledEnd == null ? null : scheduledEnd.format(SLOT_PARAM_FORMAT);
    }
}
