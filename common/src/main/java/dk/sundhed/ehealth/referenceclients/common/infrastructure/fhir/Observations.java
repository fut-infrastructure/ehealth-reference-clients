package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;

import java.util.Comparator;
import java.util.Date;

/**
 * Helpers for reading FUT measurement {@link Observation}s (as returned by
 * {@code $search-measurements-bundle-limit}).
 */
public final class Observations {

    /**
     * Newest effective time first; observations without an effective time sort last.
     */
    public static final Comparator<Observation> BY_EFFECTIVE_DESC =
            Comparator.comparing(Observations::effectiveStart,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private Observations() {
    }

    /**
     * Returns the instant a measurement was taken: {@code effectiveDateTime} when present,
     * otherwise {@code effectivePeriod.start}, or {@code null} when the observation carries neither.
     *
     * @param observation the observation to read
     * @return the effective instant, or {@code null}
     */
    public static Date effectiveStart(Observation observation) {
        if (observation.hasEffectiveDateTimeType()
                && observation.getEffectiveDateTimeType().getValue() != null) {
            return observation.getEffectiveDateTimeType().getValue();
        }
        if (observation.hasEffectivePeriod() && observation.getEffectivePeriod().hasStart()) {
            return observation.getEffectivePeriod().getStart();
        }
        return null;
    }

    /**
     * Formats an observation's value for display: a {@code Quantity} as {@code "<value> <unit>"}, a
     * string value verbatim, or the component quantities joined with {@code " / "} (e.g. the
     * systolic/diastolic pair of a blood pressure). Returns {@code null} when no value is present.
     *
     * @param observation the observation to read
     * @return the formatted value, or {@code null}
     */
    public static String formatValue(Observation observation) {
        if (observation.hasValueQuantity() && observation.getValueQuantity().hasValue()) {
            return formatQuantity(observation.getValueQuantity());
        }
        if (observation.hasValueStringType()) {
            return observation.getValueStringType().getValue();
        }
        StringBuilder joined = new StringBuilder();
        for (Observation.ObservationComponentComponent component : observation.getComponent()) {
            if (component.hasValueQuantity() && component.getValueQuantity().hasValue()) {
                if (!joined.isEmpty()) {
                    joined.append(" / ");
                }
                joined.append(formatQuantity(component.getValueQuantity()));
            }
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    private static String formatQuantity(Quantity quantity) {
        if (!quantity.hasValue()) {
            return null;
        }
        String number = quantity.getValue().stripTrailingZeros().toPlainString();
        String unit = quantity.hasUnit()
                ? " " + quantity.getUnit()
                : (quantity.hasCode() ? " " + quantity.getCode() : "");
        return number + unit;
    }
}
