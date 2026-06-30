package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.CarePlanAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.OrganizationAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.PatientAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final EpisodeOfCareAPI episodeOfCareAPI;
    private final CarePlanAPI carePlanAPI;
    private final PatientAPI patientAPI;
    private final OrganizationAPI organizationAPI;
    private final EpisodeOfCareMapper mapper;
    private final IdFactory idFactory;

    public EpisodesController(
            EpisodeOfCareAPI episodeOfCareAPI,
            CarePlanAPI carePlanAPI,
            PatientAPI patientAPI,
            OrganizationAPI organizationAPI,
            EpisodeOfCareMapper mapper,
            IdFactory idFactory) {
        this.episodeOfCareAPI = episodeOfCareAPI;
        this.carePlanAPI = carePlanAPI;
        this.patientAPI = patientAPI;
        this.organizationAPI = organizationAPI;
        this.mapper = mapper;
        this.idFactory = idFactory;
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
    public String detail(@PathVariable("id") String id, EHealthContext context, Model model) {
        EpisodeOfCareAPI.SearchResult result = episodeOfCareAPI.fetchEpisodeOfCareById(id, context);
        Resolved resolved = resolveReferences(result.episodes(), context);
        EpisodeOfCareDetailView view = mapper.toDetailView(
                result, resolved.patients(), resolved.organizations(), resolved.careTeams());
        model.addAttribute("episode", view);

        String qualifiedEoc = idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, id);
        List<CarePlan> carePlans = carePlanAPI.findCarePlansByEpisode(qualifiedEoc, context);
        model.addAttribute("carePlans", CarePlanMapper.toSummaries(carePlans));
        return "episode-detail";
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
                .filter(r -> r.getIdElement().getIdPart() != null)
                .collect(Collectors.toMap(r -> r.getIdElement().getIdPart(), Function.identity(), (a, b) -> a));
    }

    private record Resolved(
            Map<String, Patient> patients,
            Map<String, Organization> organizations,
            Map<String, CareTeam> careTeams) {
    }
}
