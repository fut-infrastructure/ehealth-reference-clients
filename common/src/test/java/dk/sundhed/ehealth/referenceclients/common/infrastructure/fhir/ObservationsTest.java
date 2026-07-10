package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationsTest {

    @Test
    void effectiveStart_prefersEffectiveDateTime() {
        Date when = Date.from(Instant.parse("2026-04-16T10:00:00Z"));
        Observation observation = new Observation().setEffective(new DateTimeType(when));

        assertThat(Observations.effectiveStart(observation)).isEqualTo(when);
    }

    @Test
    void effectiveStart_fallsBackToPeriodStart() {
        Date start = Date.from(Instant.parse("2026-04-16T10:00:00Z"));
        Observation observation = new Observation().setEffective(new Period().setStart(start));

        assertThat(Observations.effectiveStart(observation)).isEqualTo(start);
    }

    @Test
    void effectiveStart_isNullWhenNoEffectiveTime() {
        assertThat(Observations.effectiveStart(new Observation())).isNull();
    }

    @Test
    void formatValue_rendersQuantityWithUnitAndStripsTrailingZeros() {
        Observation observation = new Observation()
                .setValue(new Quantity().setValue(72.0).setUnit("/min"));

        assertThat(Observations.formatValue(observation)).isEqualTo("72 /min");
    }

    @Test
    void formatValue_fallsBackToQuantityCodeWhenUnitAbsent() {
        Observation observation = new Observation()
                .setValue(new Quantity().setValue(80.5).setCode("mm[Hg]"));

        assertThat(Observations.formatValue(observation)).isEqualTo("80.5 mm[Hg]");
    }

    @Test
    void formatValue_rendersStringValue() {
        Observation observation = new Observation().setValue(new StringType("positive"));

        assertThat(Observations.formatValue(observation)).isEqualTo("positive");
    }

    @Test
    void formatValue_joinsComponentQuantities() {
        Observation observation = new Observation();
        observation.addComponent().setValue(new Quantity().setValue(120).setUnit("mmHg"));
        observation.addComponent().setValue(new Quantity().setValue(80).setUnit("mmHg"));

        assertThat(Observations.formatValue(observation)).isEqualTo("120 mmHg / 80 mmHg");
    }

    @Test
    void formatValue_isNullWhenNoValuePresent() {
        assertThat(Observations.formatValue(new Observation())).isNull();
    }

    @Test
    void byEffectiveDesc_ordersNewestFirstWithUndatedLast() {
        Observation older = observationAt("2026-01-01T00:00:00Z");
        Observation newer = observationAt("2026-06-01T00:00:00Z");
        Observation undated = new Observation();

        List<Observation> observations = new ArrayList<>(List.of(older, undated, newer));
        observations.sort(Observations.BY_EFFECTIVE_DESC);

        assertThat(observations).containsExactly(newer, older, undated);
    }

    private static Observation observationAt(String instant) {
        return new Observation()
                .setEffective(new DateTimeType(Date.from(Instant.parse(instant))));
    }
}
