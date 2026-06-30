package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.EpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Create-episode flow.
 *
 * <p>{@code GET /episodes/new?patient=<patientId>} renders a form with a {@link
 * ConditionCodeOption} dropdown. {@code POST /episodes} builds a transaction {@link Bundle} that
 * creates the diagnosis {@code Condition} and the {@link EpisodeOfCare} together (the IG profile
 * {@code ehealth-episodeofcare} forbids contained Conditions on {@code diagnosis.condition}),
 * persists them via {@link EpisodeOfCareAPI#createEpisode}, and redirects to the detail page for
 * the new resource.
 */
@Controller
@RequestMapping("/episodes")
public class CreateEpisodeController {

    private final EpisodeOfCareAPI episodeOfCareAPI;
    private final EpisodeOfCareMapper mapper;
    private final IdFactory idFactory;

    public CreateEpisodeController(
            EpisodeOfCareAPI episodeOfCareAPI, EpisodeOfCareMapper mapper, IdFactory idFactory) {
        this.episodeOfCareAPI = episodeOfCareAPI;
        this.mapper = mapper;
        this.idFactory = idFactory;
    }

    @GetMapping("/new")
    public String form(@RequestParam("patient") String patientId, Model model) {
        model.addAttribute("patientId", patientId);
        model.addAttribute("conditionOptions", ConditionCodeOption.OPTIONS);
        return "create-episode";
    }

    @PostMapping
    public String create(
            @RequestParam("patient") String patientId,
            @RequestParam("conditionCode") String conditionCode,
            EHealthContext context,
            Model model) {
        ConditionCodeOption option = ConditionCodeOption.byCode(conditionCode);
        if (option == null) {
            model.addAttribute("patientId", patientId);
            model.addAttribute("conditionOptions", ConditionCodeOption.OPTIONS);
            model.addAttribute("error", "Please pick a diagnosis from the list.");
            return "create-episode";
        }

        String qualifiedPatientId = idFactory.createId(FhirServer.PATIENT, Patient.class, patientId);
        // $create-episode-of-care requires the token to carry patient context; without it the
        // server rejects with "Security token context missing for user type: Patient".
        EHealthContext patientContext = context.withPatient(qualifiedPatientId);
        Bundle transactionBundle =
                mapper.toCreateEpisodeTransaction(qualifiedPatientId, option, patientContext);
        EpisodeOfCare created = episodeOfCareAPI.createEpisode(transactionBundle, patientContext);
        return "redirect:/episodes/" + created.getIdElement().getIdPart();
    }
}
