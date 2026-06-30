package dk.sundhed.ehealth.referenceclients.citizen.api;

import java.util.Date;

/**
 * One row from a {@code $get-patient-procedures} response. Each row corresponds to one resolved
 * timing slot for an active ServiceRequest, or to an Unresolved / Adhoc activity with no concrete
 * time. See {@code OperationDefinition--s-get-patient-procedures.json} in the FUT IG.
 *
 * @param carePlanRef          reference to the owning CarePlan
 * @param serviceRequestRef    reference to the underlying ServiceRequest
 * @param activity             human-readable activity description as rendered by the server
 * @param resolvedStart        slot start (null for {@code Unresolved} / {@code Adhoc} rows)
 * @param resolvedEnd          slot end (may equal start when the regime has no duration)
 * @param timingType           {@code Resolved} | {@code Unresolved} | {@code Adhoc} | {@code Extra}
 * @param occurrencesRequested expected number of measurements for the slot (may be null)
 * @param totalSubmitted       measurements already submitted for the slot (may be null)
 */
public record ProcedureRow(
        String carePlanRef,
        String serviceRequestRef,
        String activity,
        Date resolvedStart,
        Date resolvedEnd,
        String timingType,
        Integer occurrencesRequested,
        Integer totalSubmitted) {
}
