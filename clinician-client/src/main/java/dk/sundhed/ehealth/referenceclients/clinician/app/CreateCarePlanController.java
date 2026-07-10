package dk.sundhed.ehealth.referenceclients.clinician.app;

import dk.sundhed.ehealth.referenceclients.clinician.api.CarePlanAPI;
import dk.sundhed.ehealth.referenceclients.clinician.api.PlanAPI;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirServer;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.BaseUrlResolver;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.security.EHealthContext;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Create-care-plan flow.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /episodes/{id}/care-plans/new}: picker listing published PlanDefinitions</li>
 *   <li>{@code POST /episodes/{id}/care-plans}: invokes {@code $apply} and redirects to the new
 *       plan's detail page</li>
 * </ul>
 *
 * <p>The picker page is reached from the episode detail view (Track A) once that template lands;
 * the URL pattern matches the spec so the integration is purely additive.
 */
@Controller
@RequestMapping("/episodes/{episodeOfCareId}/care-plans")
public class CreateCarePlanController {

    private final PlanAPI planAPI;
    private final CarePlanAPI carePlanAPI;
    private final BaseUrlResolver baseUrlResolver;

    public CreateCarePlanController(
            PlanAPI planAPI, CarePlanAPI carePlanAPI, BaseUrlResolver baseUrlResolver) {
        this.planAPI = planAPI;
        this.carePlanAPI = carePlanAPI;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/new")
    public String showForm(
            @PathVariable String episodeOfCareId,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "withActivities", defaultValue = "false") boolean withActivities,
            EHealthContext context,
            Model model) {
        String qualifiedEocId =
                baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeOfCareId);
        EHealthContext episodeContext = context.withEpisodeOfCare(qualifiedEocId);

        PlanAPI.SearchResult result =
                planAPI.findPublishedPlanDefinitions(episodeContext, search, withActivities);
        List<PlanDefinitionOptionView> options =
                CarePlanMapper.toOptions(result.planDefinitions(), result.activityDefinitions());

        model.addAttribute("episodeOfCareId", episodeOfCareId);
        model.addAttribute("planDefinitions", options);
        model.addAttribute("search", search);
        model.addAttribute("withActivities", withActivities);
        return "create-careplan";
    }

    @PostMapping
    public String create(
            @PathVariable String episodeOfCareId,
            @RequestParam("planDefinitionId") String planDefinitionId,
            EHealthContext context) {
        String qualifiedEocId =
                baseUrlResolver.createId(FhirServer.CARE_PLAN, EpisodeOfCare.class, episodeOfCareId);
        String qualifiedPlanDefinitionId =
                baseUrlResolver.createId(FhirServer.PLAN, PlanDefinition.class, planDefinitionId);
        EHealthContext episodeContext = context.withEpisodeOfCare(qualifiedEocId);

        CarePlan created = carePlanAPI.applyPlanDefinition(
                qualifiedPlanDefinitionId, qualifiedEocId, episodeContext);
        String newCarePlanId = created.getIdElement().getIdPart();

        return "redirect:/episodes/" + episodeOfCareId + "/care-plans/" + newCarePlanId;
    }
}
