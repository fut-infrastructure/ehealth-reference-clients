package dk.sundhed.ehealth.referenceclients.clinician.app;

import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dk.sundhed.ehealth.referenceclients.clinician.api.MeasurementAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.TaskAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Renders and manages the task list for a single {@code EpisodeOfCare}.
 *
 * <p>The GET handler lists all tasks linked to the episode and joins each task to the measurement
 * it focuses on, so a reviewer sees the reading inline and can approve or reject it. Status changes
 * are applied via JSON Patch against the task server.
 */
@Controller
@RequestMapping("/episodes")
public class TasksController {

    /**
     * Window for resolving task focus measurements, wide enough to cover the whole episode.
     */
    private static final int MEASUREMENT_LOOKBACK_DAYS = 3650;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final TaskAPI taskAPI;
    private final MeasurementAPI measurementAPI;
    private final BaseUrlResolver baseUrlResolver;

    public TasksController(TaskAPI taskAPI, MeasurementAPI measurementAPI, BaseUrlResolver baseUrlResolver) {
        this.taskAPI = taskAPI;
        this.measurementAPI = measurementAPI;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/{id}/tasks")
    public String tasks(
            @PathVariable("id") String episodeId,
            @RequestParam(value = "patient", required = false) String patientId,
            EHealthContext context,
            Model model) {
        String qualifiedEoc = baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        String qualifiedPatient = qualifiedPatient(patientId);
        EHealthContext taskContext = context.withPatient(qualifiedPatient).withEpisodeOfCare(qualifiedEoc);
        List<Task> tasks = taskAPI.findTasksByEpisode(qualifiedEoc, taskContext);
        Map<String, MeasurementView> byObservation =
                measurementsByObservation(qualifiedEoc, qualifiedPatient, context);
        model.addAttribute("episodeId", episodeId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("tasks", TaskView.from(tasks, baseUrlResolver, byObservation));
        return "tasks";
    }

    /**
     * Loads the episode's measurements indexed by Observation id, for joining tasks to their focus.
     * Returns an empty map when the caller's role cannot read measurements, so tasks still render
     * (just without the inline reading).
     */
    private Map<String, MeasurementView> measurementsByObservation(
            String qualifiedEoc, String qualifiedPatient, EHealthContext context) {
        Date start = Date.from(
                LocalDate.now().minusDays(MEASUREMENT_LOOKBACK_DAYS).atStartOfDay(ZONE).toInstant());
        try {
            Bundle outer = measurementAPI.searchMeasurements(
                    qualifiedEoc, start, context.withPatient(qualifiedPatient));
            return MeasurementView.byObservationId(MeasurementView.from(outer));
        } catch (ForbiddenOperationException forbiddenOperationException) {
            return Map.of();
        }
    }

    @PostMapping("/{id}/tasks/{taskId}/status")
    public String changeStatus(
            @PathVariable("id") String episodeId,
            @PathVariable String taskId,
            @RequestParam("target") String target,
            @RequestParam(value = "patient", required = false) String patientId,
            EHealthContext context) {
        String qualifiedEoc = baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        String qualifiedTask = baseUrlResolver.createId(FhirServer.TASK, Task.class, taskId);
        Task.TaskStatus status = Task.TaskStatus.fromCode(target);
        if (status == null) {
            throw new IllegalArgumentException("Unsupported Task status: " + target);
        }
        EHealthContext taskContext =
                context.withPatient(qualifiedPatient(patientId)).withEpisodeOfCare(qualifiedEoc);
        taskAPI.changeTaskStatus(qualifiedTask, status, taskContext);

        String tasksPage = "redirect:/episodes/" + episodeId + "/tasks";
        return (patientId == null || patientId.isBlank()) ? tasksPage : tasksPage + "?patient=" + patientId;
    }

    /**
     * Turns a bare patient id from the request into a fully-qualified reference, or {@code null}
     * when absent so the security context is left unscoped rather than pointing at {@code Patient/}.
     */
    private String qualifiedPatient(String patientId) {
        return (patientId == null || patientId.isBlank())
                ? null
                : baseUrlResolver.createId(FhirServer.PATIENT, Patient.class, patientId);
    }
}
