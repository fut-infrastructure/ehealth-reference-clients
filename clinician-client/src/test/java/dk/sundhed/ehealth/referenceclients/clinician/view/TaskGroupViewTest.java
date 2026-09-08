package dk.sundhed.ehealth.referenceclients.clinician.view;

import dk.sundhed.ehealth.referenceclients.common.fhir.BaseUrlResolver;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskGroupViewTest {

    private static final String EXT_REFERENCE_CAREPLAN =
            "http://ehealth.sundhed.dk/fhir/StructureDefinition/ehealth-reference-careplan";

    private BaseUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BaseUrlResolver();
        resolver.setServer(Map.of("task", "https://task.example/fhir"));
    }

    @Test
    void tasksForTheSameCarePlanLandInOneGroup() {
        Task a = task("1", "635491");
        Task b = task("2", "635491");
        Task c = task("3", "700000");

        List<TaskView> views = TaskView.from(List.of(a, b, c), resolver);
        List<TaskGroupView> groups = TaskGroupView.groupByCarePlan(
                views, Map.of("635491", "Blood pressure monitoring"));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).carePlanId()).isEqualTo("635491");
        assertThat(groups.get(0).carePlanTitle()).isEqualTo("Blood pressure monitoring");
        assertThat(groups.get(0).tasks()).extracting(TaskView::taskId).containsExactly("1", "2");
        assertThat(groups.get(1).carePlanId()).isEqualTo("700000");
        // No entry in the title map for this one: falls back to the bare id.
        assertThat(groups.get(1).carePlanTitle()).isEqualTo("700000");
        assertThat(groups.get(1).tasks()).extracting(TaskView::taskId).containsExactly("3");
    }

    @Test
    void tasksWithNoCarePlanExtensionShareTheNullGroup() {
        Task noExtension = new Task();
        noExtension.setId("Task/9");
        noExtension.setStatus(Task.TaskStatus.REQUESTED);

        List<TaskView> views = TaskView.from(List.of(noExtension), resolver);
        List<TaskGroupView> groups = TaskGroupView.groupByCarePlan(views, Map.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().carePlanId()).isNull();
        assertThat(groups.getFirst().carePlanTitle()).isNull();
        assertThat(groups.getFirst().tasks()).extracting(TaskView::taskId).containsExactly("9");
    }

    private static Task task(String taskId, String carePlanId) {
        Task task = new Task();
        task.setId("Task/" + taskId);
        task.setStatus(Task.TaskStatus.REQUESTED);
        task.addExtension(EXT_REFERENCE_CAREPLAN, new Reference("https://careplan.example/fhir/CarePlan/" + carePlanId));
        return task;
    }
}
