package dk.sundhed.ehealth.referenceclients.clinician.controller;

import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dk.sundhed.ehealth.referenceclients.clinician.fhir.CarePlanAPI;
import dk.sundhed.ehealth.referenceclients.clinician.fhir.MeasurementAPI;
import dk.sundhed.ehealth.referenceclients.clinician.fhir.TaskAPI;
import dk.sundhed.ehealth.referenceclients.clinician.view.MeasurementView;
import dk.sundhed.ehealth.referenceclients.clinician.view.TaskGroupView;
import dk.sundhed.ehealth.referenceclients.clinician.view.TaskView;
import dk.sundhed.ehealth.referenceclients.common.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final CarePlanAPI carePlanAPI;
    private final BaseUrlResolver baseUrlResolver;

    public TasksController(
            TaskAPI taskAPI, MeasurementAPI measurementAPI, CarePlanAPI carePlanAPI, BaseUrlResolver baseUrlResolver) {
        this.taskAPI = taskAPI;
        this.measurementAPI = measurementAPI;
        this.carePlanAPI = carePlanAPI;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/{id}/tasks")
    public String tasks(
            @PathVariable("id") String episodeId,
            @RequestParam(value = "patient", required = false) String patientId,
            @RequestParam(value = "hideCompleted", required = false) Boolean hideCompleted,
            EHealthContext context,
            Model model) {
        String qualifiedEpisodeOfCare = baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        String qualifiedPatient = qualifiedPatient(patientId);
        EHealthContext taskContext = context.withPatient(qualifiedPatient).withEpisodeOfCare(qualifiedEpisodeOfCare);

        List<Task> tasks = taskAPI.findTasksByEpisode(qualifiedEpisodeOfCare, taskContext);
        Map<String, MeasurementView> byObservation =
                measurementsByObservation(qualifiedEpisodeOfCare, qualifiedPatient, context);
        List<TaskView> taskViews = TaskView.from(tasks, baseUrlResolver, byObservation);
        if (Boolean.TRUE.equals(hideCompleted)) {
            taskViews = taskViews.stream().filter(task -> !"completed".equals(task.status())).toList();
        }
        Map<String, String> carePlanTitleById = carePlanTitleById(qualifiedEpisodeOfCare, context);

        model.addAttribute("episodeId", episodeId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("hideCompleted", Boolean.TRUE.equals(hideCompleted));
        model.addAttribute("taskGroups", TaskGroupView.groupByCarePlan(taskViews, carePlanTitleById));
        return "tasks";
    }

    @PostMapping("/{id}/tasks/{taskId}/status")
    public String changeStatus(
            @PathVariable("id") String episodeId,
            @PathVariable String taskId,
            @RequestParam("target") String target,
            @RequestParam(value = "patient", required = false) String patientId,
            @RequestParam(value = "hideCompleted", required = false) Boolean hideCompleted,
            EHealthContext context) {
        String qualifiedEpisodeOfCare = baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        String qualifiedTask = baseUrlResolver.createId(FhirServer.TASK, Task.class, taskId);
        Task.TaskStatus status = Task.TaskStatus.fromCode(target);
        if (status == null) {
            throw new IllegalArgumentException("Unsupported Task status: " + target);
        }
        EHealthContext taskContext =
                context.withPatient(qualifiedPatient(patientId)).withEpisodeOfCare(qualifiedEpisodeOfCare);
        taskAPI.changeTaskStatus(qualifiedTask, status, taskContext);

        return "redirect:/episodes/" + episodeId + "/tasks" + queryString(patientId, hideCompleted);
    }

    /**
     * Loads the episode's measurements indexed by Observation id, for joining tasks to their focus.
     * Returns an empty map when the caller's role cannot read measurements, so tasks still render
     * (just without the inline reading).
     */
    private Map<String, MeasurementView> measurementsByObservation(
            String qualifiedEpisodeOfCare, String qualifiedPatient, EHealthContext context) {
        Date start = Date.from(
                LocalDate.now().minusDays(MEASUREMENT_LOOKBACK_DAYS).atStartOfDay(ZONE).toInstant());
        try {
            Bundle outer = measurementAPI.searchMeasurements(
                    qualifiedEpisodeOfCare, start, context.withPatient(qualifiedPatient));
            return MeasurementView.byObservationId(MeasurementView.from(outer));
        } catch (ForbiddenOperationException forbiddenOperationException) {
            return Map.of();
        }
    }

    /**
     * Care plan title by bare id, for the task groups' headers, falling back to the bare id for a
     * plan with no title (see {@link TaskGroupView#carePlanTitle()}).
     */
    private Map<String, String> carePlanTitleById(String qualifiedEpisodeOfCare, EHealthContext context) {
        List<CarePlan> carePlans = carePlanAPI.findCarePlansByEpisode(qualifiedEpisodeOfCare, context);
        return carePlans.stream().collect(Collectors.toMap(
                carePlan -> carePlan.getIdElement().getIdPart(),
                carePlan -> carePlan.hasTitle() ? carePlan.getTitle() : carePlan.getIdElement().getIdPart(),
                (existing, replacement) -> existing));
    }

    /**
     * Builds the query string that carries the task list's view state (which patient, whether
     * completed tasks are hidden) through a redirect back to it, so a status change doesn't reset
     * either.
     */
    private static String queryString(String patientId, Boolean hideCompleted) {
        StringBuilder query = new StringBuilder();
        if (patientId != null && !patientId.isBlank()) {
            query.append(query.isEmpty() ? '?' : '&').append("patient=").append(patientId);
        }
        if (Boolean.TRUE.equals(hideCompleted)) {
            query.append(query.isEmpty() ? '?' : '&').append("hideCompleted=true");
        }
        return query.toString();
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
