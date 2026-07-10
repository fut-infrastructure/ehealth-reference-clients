package dk.sundhed.ehealth.referenceclients.citizen.app;

import jakarta.annotation.Nullable;

/**
 * View record for the measurement submission form.
 *
 * <p>Carries the ServiceRequest-derived context (code, patient, episode) as hidden form fields so
 * the POST handler can build the Observation without a second server round-trip. The {@code value}
 * and {@code unit} fields are null on GET and populated from the submitted form on POST.
 *
 * @param serviceRequestRef        fully-qualified ServiceRequest URL, set as {@code basedOn}
 * @param serviceRequestVersionRef fully-qualified versioned URL ({@code /_history/{v}}), used in
 *                                 the {@code ehealth-resolved-timing} extension
 * @param patientRef               fully-qualified Patient URL; subject and performer
 * @param episodeRef               fully-qualified EpisodeOfCare URL; workflow extension and token
 * @param codeSystem               observation code system from {@code ActivityDefinition.code}
 * @param codeCode                 observation code code from {@code ActivityDefinition.code}
 * @param codeDisplay              observation code display (may be null)
 * @param timingType               resolved-timing-type code: {@code Resolved}, {@code Adhoc}, etc.
 * @param serviceRequestVersionId  version id from {@code $get-patient-procedures}; overrides the SR read version
 * @param slotStart                ISO local datetime of the scheduled slot start; null for Adhoc
 * @param slotEnd                  ISO local datetime of the scheduled slot end; null for Adhoc
 * @param value                    numeric value entered by the citizen (null on GET)
 * @param unit                     unit string entered by the citizen (null on GET)
 */
public record SubmitMeasurementFormView(
        String serviceRequestRef,
        String serviceRequestVersionRef,
        String patientRef,
        String episodeRef,
        String codeSystem,
        String codeCode,
        @Nullable String codeDisplay,
        @Nullable String timingType,
        @Nullable String serviceRequestVersionId,
        @Nullable String slotStart,
        @Nullable String slotEnd,
        @Nullable String value,
        @Nullable String unit) {
}
