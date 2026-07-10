package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenEpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenTaskAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.CodeableConcepts;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Renders and manages the citizen task list.
 *
 * <p>The GET handler collects tasks across all of the citizen's active episodes (the Task server
 * requires episodeOfCare in both the search params and the token context; plain patient search
 * returns 403). Each incomplete task row carries an inline form that POSTs to complete it.
 */
@Controller
@RequestMapping("/tasks")
public class CitizenTasksController {

    private final CitizenTaskAPI taskAPI;
    private final CitizenEpisodeOfCareAPI episodeAPI;
    private final IdFactory idFactory;

    public CitizenTasksController(
            CitizenTaskAPI taskAPI, CitizenEpisodeOfCareAPI episodeAPI, IdFactory idFactory) {
        this.taskAPI = taskAPI;
        this.episodeAPI = episodeAPI;
        this.idFactory = idFactory;
    }

    @GetMapping
    public String tasks(EHealthContext context, Model model) {
        List<EpisodeOfCare> episodes = episodeAPI.listMyActiveEpisodes(context);
        List<CitizenTaskView> taskViews = episodes.stream()
                .flatMap(eoc -> {
                    String eocUrl = idFactory.createId(
                            FhirServer.CARE_PLAN, EpisodeOfCare.class, eoc.getIdElement().getIdPart());
                    return taskAPI.findTasksByEpisode(eocUrl, context.withEpisodeOfCare(eocUrl)).stream()
                            .map(task -> toView(task, eocUrl));
                })
                .toList();
        model.addAttribute("tasks", taskViews);
        return "tasks";
    }

    @PostMapping("/{taskId}/complete")
    public String complete(
            @PathVariable String taskId,
            @RequestParam(name = "episodeRef", required = false) String episodeRef,
            EHealthContext context) {
        String qualifiedTask = idFactory.createId(FhirServer.TASK, Task.class, taskId);
        EHealthContext episodeContext = episodeRef != null && !episodeRef.isBlank()
                ? context.withEpisodeOfCare(episodeRef)
                : context;
        taskAPI.completeTask(qualifiedTask, episodeContext);
        return "redirect:/tasks";
    }

    private CitizenTaskView toView(Task task, String episodeRef) {
        String bareId = task.getIdElement().getIdPart();
        String qualifiedId = idFactory.createId(FhirServer.TASK, Task.class, bareId);
        String status = task.getStatus() != null ? task.getStatus().toCode() : null;
        String priority = task.getPriority() != null ? task.getPriority().toCode() : null;
        String category = CodeableConcepts.displayOf(task.getCode());
        String description = task.hasDescription() ? task.getDescription() : null;
        return new CitizenTaskView(bareId, qualifiedId, status, category, description, priority, episodeRef);
    }
}
