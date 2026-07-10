package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenCarePlanAPI;
import dk.sundhed.ehealth.referenceclients.citizen.api.CitizenMeasurementAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Renders {@code GET /care-plans/{id}}: the detail page a citizen reaches by clicking an activity
 * in the weekly overview. Shows the owning {@link org.hl7.fhir.r4.model.CarePlan}'s status, period
 * and activities. When the plan is not visible to the citizen the request redirects home rather
 * than surfacing a raw error.
 */
@Controller
public class CitizenCarePlanController {

    /** How far back to list a plan's submitted measurements. */
    private static final int MEASUREMENT_LOOKBACK_DAYS = 365;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final CitizenCarePlanAPI carePlanApi;
    private final CitizenMeasurementAPI measurementApi;

    public CitizenCarePlanController(
            CitizenCarePlanAPI carePlanApi, CitizenMeasurementAPI measurementApi) {
        this.carePlanApi = carePlanApi;
        this.measurementApi = measurementApi;
    }

    @GetMapping("/care-plans/{id}")
    public String carePlan(
            @PathVariable("id") String carePlanId,
            EHealthContext context,
            Model model,
            RedirectAttributes redirectAttributes) {
        Bundle bundle = carePlanApi.fetchCarePlanWithActivities(carePlanId, context);
        CitizenCarePlanDetailView view = CitizenCarePlanDetailView.from(bundle);
        if (view == null) {
            redirectAttributes.addFlashAttribute("error", "Care plan not found.");
            return "redirect:/";
        }
        model.addAttribute("carePlan", view);
        model.addAttribute("measurements", submittedMeasurements(view, context));
        return "care-plan-detail";
    }

    /**
     * Lists the citizen's submitted measurements for the plan's episode. Returns an empty list when
     * the plan carries no episode reference (nothing to scope the measurement token to).
     */
    private List<SubmittedMeasurementView> submittedMeasurements(
            CitizenCarePlanDetailView view, EHealthContext context) {
        if (view.episodeRef() == null || view.serviceRequestIds().isEmpty()) {
            return List.of();
        }
        Date start = Date.from(
                LocalDate.now().minusDays(MEASUREMENT_LOOKBACK_DAYS).atStartOfDay(ZONE).toInstant());
        Bundle outer = measurementApi.searchMeasurements(view.episodeRef(), start, context);
        return SubmittedMeasurementView.from(outer, view.serviceRequestIds());
    }
}
