package dk.sundhed.ehealth.referenceclients.clinician.app;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementViewTest {

    @Test
    void fromFlattensInnerBundlesNewestFirst() {
        Bundle outer = outer(
                measurement("100", "2026-04-14T10:00:00Z", 60),
                measurement("101", "2026-04-16T10:00:00Z", 72));

        List<MeasurementView> views = MeasurementView.from(outer);

        assertThat(views).extracting(MeasurementView::observationId).containsExactly("101", "100");
        assertThat(views.get(0).date()).isEqualTo("2026-04-16");
        assertThat(views.get(0).value()).isEqualTo("72 /min");
        assertThat(views.get(0).status()).isEqualTo("final");
    }

    @Test
    void byObservationIdIndexesEachView() {
        List<MeasurementView> views = MeasurementView.from(
                outer(measurement("100", "2026-04-14T10:00:00Z", 60)));

        Map<String, MeasurementView> byId = MeasurementView.byObservationId(views);

        assertThat(byId).containsOnlyKeys("100");
        assertThat(byId.get("100").value()).isEqualTo("60 /min");
    }

    @Test
    void matchesIsCaseInsensitiveAcrossDateNameAndValue() {
        MeasurementView view = new MeasurementView("100", "2026-04-16", "Puls", "72 /min", "final");

        assertThat(view.matches("puls")).isTrue();
        assertThat(view.matches("2026-04")).isTrue();
        assertThat(view.matches("/min")).isTrue();
        assertThat(view.matches("temperature")).isFalse();
    }

    @Test
    void matchesReturnsTrueForBlankQuery() {
        MeasurementView view = new MeasurementView("100", "2026-04-16", "Puls", "72 /min", "final");

        assertThat(view.matches(null)).isTrue();
        assertThat(view.matches("  ")).isTrue();
    }

    private static Bundle outer(Observation... observations) {
        Bundle outer = new Bundle();
        for (Observation observation : observations) {
            Bundle inner = new Bundle();
            inner.addEntry().setResource(observation);
            outer.addEntry().setResource(inner);
        }
        return outer;
    }

    private static Observation measurement(String observationId, String effective, double value) {
        Observation observation = new Observation();
        observation.setId("Observation/" + observationId);
        observation.setEffective(new Period().setStart(Date.from(Instant.parse(effective))));
        observation.setValue(new Quantity().setValue(value).setUnit("/min"));
        observation.setCode(new CodeableConcept().addCoding(new Coding().setDisplay("Puls")));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        return observation;
    }
}
