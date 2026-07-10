package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.CodeableConcepts;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.Observations;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * One measurement row for the episode measurements page.
 *
 * <p>Derived from a single {@code Observation} extracted from an inner Bundle returned by
 * {@code $search-measurements-bundle-limit}. Multi-component observations (e.g. blood pressure)
 * are represented as a single row with a slash-delimited value.
 */
public record MeasurementView(
        @Nullable String observationId,
        @Nullable String date,
        @Nullable String codeDisplay,
        @Nullable String value,
        @Nullable String status) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /**
     * Flattens the outer search bundle into one row per Observation, most recent first.
     */
    public static List<MeasurementView> from(Bundle outer) {
        List<Observation> observations = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : outer.getEntry()) {
            if (entry.getResource() instanceof Bundle inner) {
                observations.addAll(BundleUtil.extract(inner, Observation.class));
            }
        }
        observations.sort(Observations.BY_EFFECTIVE_DESC);
        return observations.stream().map(MeasurementView::toView).toList();
    }

    /**
     * Indexes measurements by their bare Observation id, for joining tasks to their focus.
     */
    public static Map<String, MeasurementView> byObservationId(List<MeasurementView> views) {
        Map<String, MeasurementView> byId = new HashMap<>();
        for (MeasurementView view : views) {
            if (view.observationId() != null) {
                byId.putIfAbsent(view.observationId(), view);
            }
        }
        return byId;
    }

    /**
     * Case-insensitive substring match across date, measurement name and value.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase();
        return (date != null && date.toLowerCase().contains(needle))
                || (codeDisplay != null && codeDisplay.toLowerCase().contains(needle))
                || (value != null && value.toLowerCase().contains(needle));
    }

    private static MeasurementView toView(Observation observation) {
        return new MeasurementView(
                observation.getIdElement().getIdPart(),
                formatDate(Observations.effectiveStart(observation)),
                CodeableConcepts.displayOf(observation.getCode()),
                Observations.formatValue(observation),
                observation.hasStatus() ? observation.getStatus().toCode() : null);
    }

    private static String formatDate(Date instant) {
        return instant == null ? null : DATE_FORMAT.format(instant.toInstant().atZone(ZONE).toLocalDate());
    }
}
