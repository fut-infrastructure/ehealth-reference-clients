package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.CodeableConcepts;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;

import java.util.List;
import java.util.Map;

/**
 * One task row for the episode task list page.
 *
 * <p>Derived from a {@code Task} returned by {@code Task?episodeOfCare=...} on {@code fut-task}.
 * {@code qualifiedId} is the fully-qualified URL used as the PATCH target for status changes.
 * When the task's {@code focus} is a measurement, {@code measurement} carries the resolved reading
 * so the clinician can review it inline before approving.
 */
public record TaskView(
        String taskId,
        String qualifiedId,
        String status,
        @Nullable String category,
        @Nullable String description,
        @Nullable String priority,
        @Nullable String focusObservationId,
        @Nullable MeasurementView measurement) {

    public static List<TaskView> from(List<Task> tasks, BaseUrlResolver baseUrlResolver) {
        return from(tasks, baseUrlResolver, Map.of());
    }

    public static List<TaskView> from(
            List<Task> tasks, BaseUrlResolver baseUrlResolver, Map<String, MeasurementView> measurementsByObservationId) {
        return tasks.stream().map(task -> from(task, baseUrlResolver, measurementsByObservationId)).toList();
    }

    public static TaskView from(
            Task task, BaseUrlResolver baseUrlResolver, Map<String, MeasurementView> measurementsByObservationId) {
        String bareId = task.getIdElement().getIdPart();
        String focusId = focusObservationId(task);
        MeasurementView measurement = focusId == null ? null : measurementsByObservationId.get(focusId);
        return new TaskView(
                bareId,
                baseUrlResolver.createId(FhirServer.TASK, Task.class, bareId),
                task.getStatus() != null ? task.getStatus().toCode() : null,
                CodeableConcepts.displayOf(task.getCode()),
                task.hasDescription() ? task.getDescription() : null,
                task.getPriority() != null ? task.getPriority().toCode() : null,
                focusId,
                measurement);
    }

    /**
     * Bare Observation id from {@code Task.focus}, or null when focus is absent or not an Observation.
     */
    private static String focusObservationId(Task task) {
        if (task.hasFocus() && task.getFocus().hasReference()) {
            IdType focus = new IdType(task.getFocus().getReference());
            if ("Observation".equals(focus.getResourceType())) {
                return focus.getIdPart();
            }
        }
        return null;
    }
}
