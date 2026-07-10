package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskViewTest {

    private BaseUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BaseUrlResolver();
        resolver.setServer(Map.of("task", "https://task.example/fhir"));
    }

    @Test
    void joinsTaskToTheMeasurementItFocusesOn() {
        MeasurementView reading = new MeasurementView("900", "2026-04-16", "Puls", "72 /min", "final");
        Task task = assessmentTask("74364", "Observation/900");

        TaskView view = TaskView.from(List.of(task), resolver, Map.of("900", reading)).getFirst();

        assertThat(view.taskId()).isEqualTo("74364");
        assertThat(view.qualifiedId()).isEqualTo("https://task.example/fhir/Task/74364");
        assertThat(view.status()).isEqualTo("requested");
        assertThat(view.category()).isEqualTo("Observation assessment");
        assertThat(view.focusObservationId()).isEqualTo("900");
        assertThat(view.measurement()).isSameAs(reading);
    }

    @Test
    void leavesMeasurementNullWhenFocusIsNotAnObservation() {
        Task task = assessmentTask("74364", "ServiceRequest/1");

        TaskView view = TaskView.from(List.of(task), resolver, Map.of()).getFirst();

        assertThat(view.focusObservationId()).isNull();
        assertThat(view.measurement()).isNull();
    }

    @Test
    void leavesMeasurementNullWhenFocusIsUnknownToTheIndex() {
        Task task = assessmentTask("74364", "Observation/900");

        TaskView view = TaskView.from(List.of(task), resolver, Map.of()).getFirst();

        assertThat(view.focusObservationId()).isEqualTo("900");
        assertThat(view.measurement()).isNull();
    }

    @Test
    void twoArgOverloadResolvesNoMeasurements() {
        Task task = assessmentTask("74364", "Observation/900");

        TaskView view = TaskView.from(List.of(task), resolver).getFirst();

        assertThat(view.measurement()).isNull();
    }

    private static Task assessmentTask(String taskId, String focusReference) {
        Task task = new Task();
        task.setId("Task/" + taskId);
        task.setStatus(Task.TaskStatus.REQUESTED);
        task.setFocus(new Reference(focusReference));
        task.setCode(new CodeableConcept().addCoding(new Coding().setDisplay("Observation assessment")));
        return task;
    }
}
