package dk.sundhed.ehealth.referenceclients.citizen.api;

import java.util.List;
import java.util.Map;

/**
 * Result of {@code $get-patient-procedures}: the timing rows plus a map from bare CarePlan id to
 * the fully-qualified EpisodeOfCare reference extracted from each CarePlan in the bundle.
 *
 * <p>The episode map is used downstream to populate episode context on the measurement-submit flow,
 * since the measurement server requires an episode-scoped token.
 */
public record ProcedureBundle(
        List<ProcedureRow> rows,
        Map<String, String> episodesByCarePlanId) {
}
