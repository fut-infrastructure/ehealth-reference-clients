package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.util.List;
import org.hl7.fhir.r4.model.CarePlan;

/**
 * Compact projection of a {@link CarePlan} for the clickable list on the citizen's {@code /me}
 * page.
 *
 * @param id     bare CarePlan id (links to {@code /care-plans/{id}})
 * @param title  plan title, or the id when untitled
 * @param status plan status code (e.g. {@code active}); null when absent
 */
public record CitizenCarePlanSummaryView(String id, String title, String status) {

    public static CitizenCarePlanSummaryView from(CarePlan carePlan) {
        String id = carePlan.getIdElement().getIdPart();
        return new CitizenCarePlanSummaryView(
                id,
                carePlan.hasTitle() ? carePlan.getTitle() : id,
                carePlan.hasStatus() ? carePlan.getStatus().toCode() : null);
    }

    public static List<CitizenCarePlanSummaryView> from(List<CarePlan> carePlans) {
        return carePlans.stream().map(CitizenCarePlanSummaryView::from).toList();
    }
}
