package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.CarePlanAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BundleUtil;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.IdFactory;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Care-plan detail page, activation, and status changes.
 *
 * <p>Routes are nested under the owning episode because the careplan server authorises a read,
 * search or write only when the access token carries that episode in its context. The bare
 * CarePlan id alone is not enough to fetch the plan, so the episode id travels in the URL.
 *
 * <ul>
 *   <li>{@code GET /episodes/{episodeOfCareId}/care-plans/{id}}: detail tree (plan metadata +
 *       activities by type)</li>
 *   <li>{@code POST /episodes/{episodeOfCareId}/care-plans/{id}/activate}: activates the draft
 *       plan and its activities</li>
 *   <li>{@code POST /episodes/{episodeOfCareId}/care-plans/{id}/status}: sets the plan status to
 *       {@code target}</li>
 * </ul>
 */
@Controller
@RequestMapping("/episodes/{episodeOfCareId}/care-plans")
public class CarePlansController {

    private final CarePlanAPI carePlanAPI;
    private final IdFactory idFactory;

    public CarePlansController(CarePlanAPI carePlanAPI, IdFactory idFactory) {
        this.carePlanAPI = carePlanAPI;
        this.idFactory = idFactory;
    }

    @GetMapping("/{id}")
    public String show(
            @PathVariable String episodeOfCareId,
            @PathVariable("id") String carePlanId,
            EHealthContext context,
            Model model) {
        String qualifiedId = idFactory.createId(FhirServer.CARE_PLAN, CarePlan.class, carePlanId);
        String qualifiedEoc =
                idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeOfCareId);

        Bundle bundle =
                carePlanAPI.fetchCarePlanByIdWithActivities(qualifiedId, qualifiedEoc, context);
        CarePlan carePlan = BundleUtil.extractFirst(bundle, CarePlan.class)
                .orElseThrow(() -> new IllegalStateException(
                        "CarePlan " + carePlanId + " not found"));
        List<Task> tasks = BundleUtil.extract(bundle, Task.class);
        List<Appointment> appointments = BundleUtil.extract(bundle, Appointment.class);
        List<ServiceRequest> serviceRequests = BundleUtil.extract(bundle, ServiceRequest.class);

        CarePlanDetailView view =
                CarePlanMapper.toDetailView(carePlan, tasks, appointments, serviceRequests);
        model.addAttribute("carePlan", view);
        model.addAttribute("episodeOfCareId", episodeOfCareId);
        return "careplan-detail";
    }

    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable String episodeOfCareId,
            @PathVariable("id") String carePlanId,
            EHealthContext context) {
        String qualifiedId = idFactory.createId(FhirServer.CARE_PLAN, CarePlan.class, carePlanId);
        String qualifiedEoc =
                idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeOfCareId);
        carePlanAPI.activateCarePlan(qualifiedId, qualifiedEoc, context);
        return "redirect:/episodes/" + episodeOfCareId + "/care-plans/" + carePlanId;
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable String episodeOfCareId,
            @PathVariable("id") String carePlanId,
            @RequestParam("target") String target,
            EHealthContext context) {
        String qualifiedId = idFactory.createId(FhirServer.CARE_PLAN, CarePlan.class, carePlanId);
        String qualifiedEoc =
                idFactory.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeOfCareId);
        CarePlan.CarePlanStatus status = CarePlan.CarePlanStatus.fromCode(target);
        if (status == null) {
            throw new IllegalArgumentException("Unsupported CarePlan status: " + target);
        }
        carePlanAPI.changeCarePlanStatus(qualifiedId, qualifiedEoc, status, context);
        return "redirect:/episodes/" + episodeOfCareId + "/care-plans/" + carePlanId;
    }
}
