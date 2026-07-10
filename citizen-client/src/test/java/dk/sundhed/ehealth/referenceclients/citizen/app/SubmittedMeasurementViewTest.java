package dk.sundhed.ehealth.referenceclients.citizen.app;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmittedMeasurementViewTest {

    @Test
    void keepsOnlyMeasurementsBasedOnTheGivenServiceRequests() {
        Bundle outer = outer(
                measurement("657450", "2026-04-16T10:00:00Z", 72),
                measurement("999999", "2026-04-16T11:00:00Z", 80));

        List<SubmittedMeasurementView> views = SubmittedMeasurementView.from(outer, List.of("657450"));

        assertThat(views).hasSize(1);
        SubmittedMeasurementView view = views.getFirst();
        assertThat(view.activity()).isEqualTo("Puls");
        assertThat(view.value()).isEqualTo("72 /min");
        assertThat(view.status()).isEqualTo("final");
    }

    @Test
    void emptyServiceRequestSetYieldsNoRows() {
        Bundle outer = outer(measurement("657450", "2026-04-16T10:00:00Z", 72));

        assertThat(SubmittedMeasurementView.from(outer, List.of())).isEmpty();
    }

    @Test
    void ordersNewestFirst() {
        Bundle outer = outer(
                measurement("657450", "2026-04-14T10:00:00Z", 60),
                measurement("657450", "2026-04-16T10:00:00Z", 72));

        List<SubmittedMeasurementView> views = SubmittedMeasurementView.from(outer, List.of("657450"));

        assertThat(views).extracting(SubmittedMeasurementView::value)
                .containsExactly("72 /min", "60 /min");
    }

    /**
     * Wraps each Observation in its own inner Bundle, mirroring the search-operation response.
     */
    private static Bundle outer(Observation... observations) {
        Bundle outer = new Bundle();
        for (Observation observation : observations) {
            Bundle inner = new Bundle();
            inner.addEntry().setResource(observation);
            outer.addEntry().setResource(inner);
        }
        return outer;
    }

    private static Observation measurement(String serviceRequestId, String effective, double value) {
        Observation observation = new Observation();
        observation.addBasedOn(new Reference("ServiceRequest/" + serviceRequestId));
        observation.setEffective(new Period().setStart(Date.from(Instant.parse(effective))));
        observation.setValue(new Quantity().setValue(value).setUnit("/min"));
        observation.setCode(new CodeableConcept().addCoding(new Coding().setDisplay("Puls")));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        return observation;
    }
}
