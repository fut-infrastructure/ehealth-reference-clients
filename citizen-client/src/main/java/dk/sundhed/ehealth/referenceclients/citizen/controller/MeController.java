package dk.sundhed.ehealth.referenceclients.citizen.controller;

import dk.sundhed.ehealth.referenceclients.citizen.fhir.CitizenCarePlanAPI;
import dk.sundhed.ehealth.referenceclients.citizen.fhir.CitizenEpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.citizen.fhir.CitizenPatientAPI;
import dk.sundhed.ehealth.referenceclients.citizen.view.CitizenCarePlanSummaryView;
import dk.sundhed.ehealth.referenceclients.common.fhir.PatientDemographics;
import dk.sundhed.ehealth.referenceclients.common.security.EHealthContext;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Renders {@code GET /me}: the citizen's own Patient resource plus their active episodes of care.
 *
 * <p>Patient is projected through {@link CitizenView} so the Thymeleaf template stays free of
 * FHIR identifier lookup logic.
 */
@Controller
public class MeController {

    private final CitizenPatientAPI patientApi;
    private final CitizenEpisodeOfCareAPI episodeApi;
    private final CitizenCarePlanAPI carePlanApi;

    public MeController(
            CitizenPatientAPI patientApi,
            CitizenEpisodeOfCareAPI episodeApi,
            CitizenCarePlanAPI carePlanApi) {
        this.patientApi = patientApi;
        this.episodeApi = episodeApi;
        this.carePlanApi = carePlanApi;
    }

    @GetMapping("/me")
    public String me(EHealthContext context, Model model) {
        Patient patient = patientApi.readSelf(context);
        List<EpisodeOfCare> episodes = episodeApi.listMyActiveEpisodes(context);
        model.addAttribute("patient", CitizenView.from(patient));
        model.addAttribute("episodes", episodes);
        model.addAttribute(
                "carePlans", CitizenCarePlanSummaryView.from(carePlanApi.findMyCarePlans(context)));
        return "me";
    }

    /**
     * View projection of the citizen's own {@link Patient}, with CPR pre-extracted.
     */
    public record CitizenView(
            String displayName,
            String cpr,
            String birthDate,
            String gender,
            String address) {

        static CitizenView from(Patient patient) {
            return new CitizenView(
                    PatientDemographics.displayName(patient),
                    PatientDemographics.cpr(patient),
                    patient.getBirthDateElement() != null
                            ? patient.getBirthDateElement().getValueAsString()
                            : null,
                    patient.getGender() != null ? patient.getGender().getDisplay() : null,
                    !patient.getAddress().isEmpty() ? patient.getAddress().getFirst().getText() : null);
        }
    }
}
