package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenCarePlanAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Renders {@code GET /care-plans/{id}}: the detail page a citizen reaches by clicking an activity
 * in the weekly overview. Shows the owning {@link org.hl7.fhir.r4.model.CarePlan}'s status, period
 * and activities. When the plan is not visible to the citizen the request redirects home rather
 * than surfacing a raw error.
 */
@Controller
public class CitizenCarePlanController {

    private final CitizenCarePlanAPI carePlanApi;

    public CitizenCarePlanController(CitizenCarePlanAPI carePlanApi) {
        this.carePlanApi = carePlanApi;
    }

    @GetMapping("/care-plans/{id}")
    public String carePlan(
            @PathVariable("id") String id,
            EHealthContext context,
            Model model,
            RedirectAttributes redirectAttributes) {
        Bundle bundle = carePlanApi.fetchCarePlanWithActivities(id, context);
        CitizenCarePlanDetailView view = CitizenCarePlanDetailView.from(bundle);
        if (view == null) {
            redirectAttributes.addFlashAttribute("error", "Care plan not found.");
            return "redirect:/";
        }
        model.addAttribute("carePlan", view);
        return "care-plan-detail";
    }
}
