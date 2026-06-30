package dk.sundhed.ehealth.referenceclients.clinician.app;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import dk.sundhed.ehealth.referenceclients.clinician.api.PatientAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Create-citizen flow.
 *
 * <p>{@code GET /citizens/new} renders a single-field CPR entry form.
 * {@code POST /citizens} calls {@code $createPatient} and redirects to the patient view on
 * success, or re-renders the form with a friendly error when the CPR is unknown at NSP.
 */
@Controller
@RequestMapping("/citizens")
public class CitizensController {

    private final PatientAPI patientAPI;

    public CitizensController(PatientAPI patientAPI) {
        this.patientAPI = patientAPI;
    }

    /** Renders the CPR entry form. */
    @GetMapping("/new")
    public String newCitizenForm() {
        return "citizens/new";
    }

    /**
     * Submits a CPR to {@code $createPatient} and forwards to the result page, or re-renders the
     * form with an error when the CPR is not found at NSP.
     *
     * @param cpr     ten-digit CPR, validated client-side via {@code pattern="\d{10}"}
     * @param context clinician context injected by {@link
     *                dk.sundhed.ehealth.referenceclients.clinician.infrastructure.EHealthContextArgumentResolver}
     * @param model   Spring MVC model
     * @return view name
     */
    @PostMapping
    public String createCitizen(
            @RequestParam("cpr") String cpr,
            EHealthContext context,
            Model model) {
        try {
            Patient patient = patientAPI.createPatientFromCpr(cpr, context);
            return "redirect:/patients/" + patient.getIdElement().getIdPart();
        } catch (ResourceNotFoundException e) {
            model.addAttribute("error", "Citizen not found in the CPR registry");
            return "citizens/new";
        }
    }
}
