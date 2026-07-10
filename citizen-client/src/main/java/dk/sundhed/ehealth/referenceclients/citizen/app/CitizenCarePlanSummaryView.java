package dk.sundhed.ehealth.referenceclients.citizen.app;

import org.hl7.fhir.r4.model.CarePlan;

import java.util.List;

/**
 * Compact projection of a {@link CarePlan} for the clickable list on the citizen's {@code /me}
 * page.
 *
 * @param carePlanId bare CarePlan id (links to {@code /care-plans/{id}})
 * @param title      plan title, or the id when untitled
 * @param status     plan status code (e.g. {@code active}); null when absent
 */
public record CitizenCarePlanSummaryView(String carePlanId, String title, String status) {

    public static CitizenCarePlanSummaryView from(CarePlan carePlan) {
        String carePlanId = carePlan.getIdElement().getIdPart();
        return new CitizenCarePlanSummaryView(
                carePlanId,
                carePlan.hasTitle() ? carePlan.getTitle() : "Care plan " + carePlanId,
                carePlan.hasStatus() ? carePlan.getStatus().toCode() : null);
    }

    public static List<CitizenCarePlanSummaryView> from(List<CarePlan> carePlans) {
        return carePlans.stream().map(CitizenCarePlanSummaryView::from).toList();
    }
}
