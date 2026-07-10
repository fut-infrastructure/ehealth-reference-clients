package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenCarePlanAPI;
import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenEpisodeOfCareAPI;
import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenPatientAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
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

    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

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
                    displayName(patient),
                    cpr(patient),
                    patient.getBirthDateElement() != null
                            ? patient.getBirthDateElement().getValueAsString()
                            : null,
                    patient.getGender() != null ? patient.getGender().getDisplay() : null,
                    !patient.getAddress().isEmpty() ? patient.getAddress().get(0).getText() : null);
        }

        private static String displayName(Patient patient) {
            return patient.getName().stream()
                    .findFirst()
                    .map(name -> {
                        if (name.getText() != null && !name.getText().isBlank()) {
                            return name.getText();
                        }
                        String given = name.getGivenAsSingleString();
                        String family = name.getFamily();
                        if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
                            return given + " " + family;
                        }
                        if (family != null && !family.isBlank()) {
                            return family;
                        }
                        return given != null ? given : "";
                    })
                    .orElse("");
        }

        private static String cpr(Patient patient) {
            return patient.getIdentifier().stream()
                    .filter(identifier -> CPR_SYSTEM.equals(identifier.getSystem()))
                    .map(identifier -> identifier.getValue() != null ? identifier.getValue() : "")
                    .findFirst()
                    .orElse("");
        }
    }
}
