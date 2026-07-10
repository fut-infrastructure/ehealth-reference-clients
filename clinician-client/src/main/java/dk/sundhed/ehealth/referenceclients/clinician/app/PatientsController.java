package dk.sundhed.ehealth.referenceclients.clinician.app;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.PatientAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Patient view: the per-citizen landing page where a clinician acts on one patient.
 *
 * <p>{@code GET /patients/{id}} renders demographics plus the patient's episodes of care, with a
 * "New episode" entry point. It is the redirect target after {@code $createPatient}, so a
 * freshly-created citizen can immediately get their first forløb (and, from the episode, a plan).
 *
 * <p>EpisodeOfCare references its patient by a fully-qualified URL, so the bare path id is
 * qualified via {@link IdFactory} before the {@code patient=} search.
 *
 * <p>Episodes are paged through the FHIR server's own cursor: {@code GET /patients/{id}} shows the
 * first page, and an optional {@code ?page=<cursor>} request param carries the opaque
 * {@code _getpages} link of an adjacent page. A citizen can therefore hold more episodes than fit on
 * one page without the view loading the whole set.
 */
@Controller
@RequestMapping("/patients")
public class PatientsController {

    /**
     * Episodes shown per page on the patient view before the server's next/previous links kick in.
     */
    private static final int EPISODES_PER_PAGE = 10;

    private final PatientAPI patientAPI;
    private final EpisodeOfCareAPI episodeOfCareAPI;
    private final EpisodeOfCareMapper mapper;
    private final IdFactory idFactory;

    public PatientsController(
            PatientAPI patientAPI,
            EpisodeOfCareAPI episodeOfCareAPI,
            EpisodeOfCareMapper mapper,
            IdFactory idFactory) {
        this.patientAPI = patientAPI;
        this.episodeOfCareAPI = episodeOfCareAPI;
        this.mapper = mapper;
        this.idFactory = idFactory;
    }

    @GetMapping("/{id}")
    public String patient(
            @PathVariable("id") String patientId,
            @RequestParam(value = "page", required = false) String page,
            EHealthContext context,
            Model model) {
        Patient patient = patientAPI.findPatientsById(Set.of(patientId), context).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Patient/" + patientId));

        String qualifiedPatientId = idFactory.createId(FhirServer.PATIENT, Patient.class, patientId);
        EpisodeOfCareAPI.EpisodePage episodePage = episodeOfCareAPI.findEpisodesByPatientPage(
                qualifiedPatientId, context, page, EPISODES_PER_PAGE);

        EpisodeOfCareAPI.SearchResult result =
                new EpisodeOfCareAPI.SearchResult(episodePage.episodes(), episodePage.conditions());
        List<PatientEpisodesView.EpisodeSummaryView> episodes =
                mapper.toPatientEpisodesViews(result, Map.of(patientId, patient)).stream()
                        .filter(view -> patientId.equals(view.patientId()))
                        .findFirst()
                        .map(PatientEpisodesView::episodes)
                        .orElse(List.of());

        model.addAttribute("patient", PatientDetailView.from(patient, episodes));
        model.addAttribute("episodesNextPage", episodePage.nextPageUrl());
        model.addAttribute("episodesPrevPage", episodePage.previousPageUrl());
        return "patients/detail";
    }
}
