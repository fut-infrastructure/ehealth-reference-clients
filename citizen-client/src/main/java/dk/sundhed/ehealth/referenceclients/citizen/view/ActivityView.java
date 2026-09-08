package dk.sundhed.ehealth.referenceclients.citizen.view;

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
 * @param occurrencesRequested    expected number of measurements for this slot; null when the server
 *                                didn't report a target (e.g. {@code Adhoc}/{@code Unresolved} rows)
 * @param totalSubmitted          measurements already submitted for this slot; null when none reported
 */
public record ActivityView(
        String title,
        LocalDateTime scheduledAt,
        LocalDateTime scheduledEnd,
        String timingType,
        String carePlanId,
        @Nullable String serviceRequestVersionId,
        @Nullable String serviceRequestRef,
        @Nullable String episodeRef,
        @Nullable Integer occurrencesRequested,
        @Nullable Integer totalSubmitted) {

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

    /**
     * True once at least as many measurements were submitted as the slot expects.
     */
    public boolean completed() {
        return occurrencesRequested != null && totalSubmitted != null
                && totalSubmitted >= occurrencesRequested;
    }

    /**
     * "Completed", or a "{@code submitted}/{@code requested}" count while still open; {@code null}
     * when the server reported no target count to show progress against.
     */
    public String progressLabel() {
        if (occurrencesRequested == null) {
            return null;
        }
        if (completed()) {
            return "Completed";
        }
        return (totalSubmitted != null ? totalSubmitted : 0) + "/" + occurrencesRequested;
    }
}
