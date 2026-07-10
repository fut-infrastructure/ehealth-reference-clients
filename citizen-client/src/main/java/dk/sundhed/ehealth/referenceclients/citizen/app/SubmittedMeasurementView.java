package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.CodeableConcepts;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.Observations;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * One submitted measurement, as shown to the citizen on a care-plan page. Derived from an
 * {@link Observation} extracted from the inner Bundles returned by
 * {@code $search-measurements-bundle-limit}.
 *
 * @param when     formatted effective time; null when the Observation carried none
 * @param activity measured activity display (Observation.code); null when absent
 * @param value    formatted value with unit (e.g. {@code "72 /min"}); null when absent
 * @param status   Observation status code (e.g. {@code final})
 */
public record SubmittedMeasurementView(
        @Nullable String when,
        @Nullable String activity,
        @Nullable String value,
        @Nullable String status) {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    /**
     * Flattens the outer search bundle into one row per Observation, newest first, keeping only
     * measurements based on one of {@code serviceRequestIds}. The search operation returns every
     * measurement in the episode, but several care plans can share one episode, so the list is
     * narrowed to this plan's ServiceRequests. An empty id set yields no rows.
     */
    public static List<SubmittedMeasurementView> from(Bundle outer, Collection<String> serviceRequestIds) {
        Set<String> planServiceRequestIds = Set.copyOf(serviceRequestIds);
        List<Observation> observations = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : outer.getEntry()) {
            if (entry.getResource() instanceof Bundle inner) {
                for (Observation observation : BundleUtil.extract(inner, Observation.class)) {
                    if (isBasedOnAnyOf(observation, planServiceRequestIds)) {
                        observations.add(observation);
                    }
                }
            }
        }
        observations.sort(Observations.BY_EFFECTIVE_DESC);
        return observations.stream().map(SubmittedMeasurementView::toView).toList();
    }

    private static SubmittedMeasurementView toView(Observation observation) {
        return new SubmittedMeasurementView(
                formatWhen(Observations.effectiveStart(observation)),
                CodeableConcepts.displayOf(observation.getCode()),
                Observations.formatValue(observation),
                observation.hasStatus() ? observation.getStatus().toCode() : null);
    }

    private static String formatWhen(Date instant) {
        return instant == null
                ? null
                : DATE_FORMAT.format(LocalDateTime.ofInstant(instant.toInstant(), ZONE));
    }

    private static boolean isBasedOnAnyOf(Observation observation, Set<String> serviceRequestIds) {
        for (Reference basedOn : observation.getBasedOn()) {
            if (basedOn.hasReference()
                    && serviceRequestIds.contains(new IdType(basedOn.getReference()).getIdPart())) {
                return true;
            }
        }
        return false;
    }
}
