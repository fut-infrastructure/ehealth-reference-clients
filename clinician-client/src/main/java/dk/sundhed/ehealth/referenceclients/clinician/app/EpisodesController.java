package dk.sundhed.ehealth.referenceclients.clinician.app;

import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dk.sundhed.ehealth.referenceclients.clinician.api.*;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renders episodes of care belonging to the selected care team.
 *
 * <p>The fut-careplan CapabilityStatement only allows {@code _include=EpisodeOfCare:condition}
 * for EpisodeOfCare searches. Patient, managing-organization, care-manager, and team references
 * are resolved here via bulk follow-up calls against the patient and organization servers.
 */
@Controller
@RequestMapping("/episodes")
public class EpisodesController {

    /**
     * Recent-measurement preview window on the episode-detail page.
     */
    private static final int MEASUREMENT_PREVIEW_DAYS = 90;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final EpisodeOfCareAPI episodeOfCareAPI;
    private final CarePlanAPI carePlanAPI;
    private final PatientAPI patientAPI;
    private final OrganizationAPI organizationAPI;
    private final MeasurementAPI measurementAPI;
    private final TaskAPI taskAPI;
    private final EpisodeOfCareMapper mapper;
    private final BaseUrlResolver baseUrlResolver;

    public EpisodesController(
            EpisodeOfCareAPI episodeOfCareAPI,
            CarePlanAPI carePlanAPI,
            PatientAPI patientAPI,
            OrganizationAPI organizationAPI,
            MeasurementAPI measurementAPI,
            TaskAPI taskAPI,
            EpisodeOfCareMapper mapper,
            BaseUrlResolver baseUrlResolver) {
        this.episodeOfCareAPI = episodeOfCareAPI;
        this.carePlanAPI = carePlanAPI;
        this.patientAPI = patientAPI;
        this.organizationAPI = organizationAPI;
        this.measurementAPI = measurementAPI;
        this.taskAPI = taskAPI;
        this.mapper = mapper;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping
    public String list(EHealthContext context, Model model) {
        EpisodeOfCareAPI.SearchResult result =
                episodeOfCareAPI.findPlannedAndActiveEpisodesByTeam(context);
        Resolved resolved = resolveReferences(result.episodes(), context);
        List<PatientEpisodesView> views =
                mapper.toPatientEpisodesViews(result, resolved.patients());
        model.addAttribute("patients", views);
        return "episodes";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String episodeId, EHealthContext context, Model model) {
        EpisodeOfCareAPI.SearchResult result = episodeOfCareAPI.fetchEpisodeOfCareById(episodeId, context);
        Resolved resolved = resolveReferences(result.episodes(), context);
        EpisodeOfCareDetailView view = mapper.toDetailView(
                result, resolved.patients(), resolved.organizations(), resolved.careTeams());
        model.addAttribute("episode", view);

        String qualifiedEoc = baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeId);
        List<CarePlan> carePlans = carePlanAPI.findCarePlansByEpisode(qualifiedEoc, context);
        model.addAttribute("carePlans", CarePlanMapper.toSummaries(carePlans));

        String qualifiedPatient = view.patientId() != null
                ? baseUrlResolver.createId(FhirServer.PATIENT, Patient.class, view.patientId())
                : null;
        addMeasurementPreview(model, qualifiedEoc, qualifiedPatient, context);
        addTaskPreview(model, qualifiedEoc, qualifiedPatient, context);
        return "episode-detail";
    }

    /**
     * Adds a recent-measurement preview to the model. When the caller's role cannot read
     * measurements the server answers 403; the page then shows an access notice instead of the
     * table, so the {@code measurementsForbidden} flag is set rather than failing the whole page.
     */
    private void addMeasurementPreview(
            Model model, String qualifiedEoc, String qualifiedPatient, EHealthContext context) {
        EHealthContext measurementContext = context.withPatient(qualifiedPatient);
        Date start = Date.from(
                LocalDate.now().minusDays(MEASUREMENT_PREVIEW_DAYS).atStartOfDay(ZONE).toInstant());
        try {
            Bundle outer = measurementAPI.searchMeasurements(qualifiedEoc, start, measurementContext);
            model.addAttribute("measurements", MeasurementView.from(outer));
        } catch (ForbiddenOperationException forbiddenOperationException) {
            model.addAttribute("measurementsForbidden", true);
        }
    }

    /**
     * Adds a task preview to the model, degrading to a {@code tasksForbidden} flag on 403.
     */
    private void addTaskPreview(
            Model model, String qualifiedEoc, String qualifiedPatient, EHealthContext context) {
        EHealthContext taskContext = context.withPatient(qualifiedPatient).withEpisodeOfCare(qualifiedEoc);
        try {
            List<Task> tasks = taskAPI.findTasksByEpisode(qualifiedEoc, taskContext);
            model.addAttribute("tasks", TaskView.from(tasks, baseUrlResolver));
        } catch (ForbiddenOperationException forbiddenOperationException) {
            model.addAttribute("tasksForbidden", true);
        }
    }

    private Resolved resolveReferences(List<EpisodeOfCare> episodes, EHealthContext context) {
        Set<String> patientIds = new LinkedHashSet<>();
        Set<String> organizationIds = new LinkedHashSet<>();
        Set<String> careTeamIds = new LinkedHashSet<>();

        for (EpisodeOfCare episode : episodes) {
            addId(patientIds, episode.getPatient());
            addId(organizationIds, episode.getManagingOrganization());
            for (Reference team : episode.getTeam()) {
                addId(careTeamIds, team);
            }
        }

        Map<String, Patient> patients = indexById(
                patientAPI.findPatientsById(patientIds, context));
        Map<String, Organization> organizations = indexById(
                organizationAPI.findOrganizationsById(organizationIds, context));
        Map<String, CareTeam> careTeams = indexById(
                organizationAPI.findCareTeamsById(careTeamIds, context));

        return new Resolved(patients, organizations, careTeams);
    }

    private static void addId(Set<String> sink, Reference ref) {
        if (ref == null || ref.isEmpty()) {
            return;
        }
        String idPart = ref.getReferenceElement().getIdPart();
        if (idPart != null && !idPart.isBlank()) {
            sink.add(idPart);
        }
    }

    private static <R extends Resource> Map<String, R> indexById(List<R> resources) {
        return resources.stream()
                .filter(resource -> resource.getIdElement().getIdPart() != null)
                .collect(Collectors.toMap(
                        resource -> resource.getIdElement().getIdPart(),
                        Function.identity(),
                        (existing, replacement) -> existing));
    }

    private record Resolved(
            Map<String, Patient> patients,
            Map<String, Organization> organizations,
            Map<String, CareTeam> careTeams) {
    }
}
