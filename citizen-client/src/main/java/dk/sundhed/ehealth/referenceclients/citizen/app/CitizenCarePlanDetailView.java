package dk.sundhed.ehealth.referenceclients.citizen.app;

import dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir.FhirExtensions;
import org.hl7.fhir.r4.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Citizen-facing projection of a single {@link CarePlan} and its activities, built from the search
 * bundle returned by {@code CitizenCarePlanAPI.fetchCarePlanWithActivities}. All FHIR-shape logic
 * lives here so the Thymeleaf template stays declarative.
 *
 * @param carePlanId        bare CarePlan id
 * @param title             plan title, or the id when untitled
 * @param status            plan status code (e.g. {@code active})
 * @param description       plan description; null when absent
 * @param start             plan period start; null when absent
 * @param end               plan period end; null when absent
 * @param episodeRef        fully-qualified EpisodeOfCare URL from the plan's workflow-episodeOfCare
 *                          extension; null when absent. Used to scope the measurement lookup.
 * @param serviceRequestIds bare ids of this plan's ServiceRequest activities; used to filter the
 *                          episode-wide measurement list down to measurements based on this plan
 * @param activities        the plan's activities (Task / Appointment / ServiceRequest), time-sorted
 */
public record CitizenCarePlanDetailView(
        String carePlanId,
        String title,
        String status,
        String description,
        LocalDate start,
        LocalDate end,
        String episodeRef,
        List<String> serviceRequestIds,
        List<ActivityDetailView> activities) {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /**
     * One activity within the plan.
     *
     * @param type   FHIR resource type (Task / Appointment / ServiceRequest)
     * @param status activity status code; null when absent
     * @param label  human-readable label; null when none could be derived
     * @param when   scheduled time; null when the activity carries no occurrence
     */
    public record ActivityDetailView(
            String type, String status, String label, LocalDateTime when) {
    }

    /**
     * Builds the view from the search bundle, or returns null when no CarePlan is present.
     */
    public static CitizenCarePlanDetailView from(Bundle bundle) {
        CarePlan carePlan = null;
        List<ActivityDetailView> activities = new ArrayList<>();
        List<String> serviceRequestIds = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof CarePlan matchedCarePlan && carePlan == null) {
                carePlan = matchedCarePlan;
            } else if (resource instanceof Task task) {
                activities.add(fromTask(task));
            } else if (resource instanceof Appointment appointment) {
                activities.add(fromAppointment(appointment));
            } else if (resource instanceof ServiceRequest serviceRequest) {
                activities.add(fromServiceRequest(serviceRequest));
                serviceRequestIds.add(serviceRequest.getIdElement().getIdPart());
            }
        }
        if (carePlan == null) {
            return null;
        }
        activities.sort(Comparator.comparing(
                ActivityDetailView::when, Comparator.nullsLast(Comparator.naturalOrder())));

        Period period = carePlan.hasPeriod() ? carePlan.getPeriod() : null;
        return new CitizenCarePlanDetailView(
                carePlan.getIdElement().getIdPart(),
                carePlan.hasTitle() ? carePlan.getTitle() : "Care plan " + carePlan.getIdElement().getIdPart(),
                carePlan.hasStatus() ? carePlan.getStatus().toCode() : null,
                carePlan.hasDescription() ? carePlan.getDescription() : null,
                toLocalDate(period == null ? null : period.getStart()),
                toLocalDate(period == null ? null : period.getEnd()),
                FhirExtensions.referenceValue(carePlan, FhirExtensions.EPISODE_OF_CARE),
                List.copyOf(serviceRequestIds),
                List.copyOf(activities));
    }

    private static ActivityDetailView fromTask(Task task) {
        return new ActivityDetailView(
                "Task",
                task.hasStatus() ? task.getStatus().toCode() : null,
                task.hasDescription() ? task.getDescription() : codeLabel(task.getCode()),
                null);
    }

    private static ActivityDetailView fromAppointment(Appointment appointment) {
        String label = appointment.hasServiceType()
                ? codeLabel(appointment.getServiceTypeFirstRep())
                : null;
        if (label == null && appointment.hasDescription()) {
            label = appointment.getDescription();
        }
        LocalDateTime when = appointment.hasStart()
                ? LocalDateTime.ofInstant(appointment.getStart().toInstant(), ZONE)
                : null;
        return new ActivityDetailView(
                "Appointment",
                appointment.hasStatus() ? appointment.getStatus().toCode() : null,
                label,
                when);
    }

    private static ActivityDetailView fromServiceRequest(ServiceRequest serviceRequest) {
        LocalDateTime when = null;
        if (serviceRequest.hasOccurrenceDateTimeType()) {
            when = LocalDateTime.ofInstant(serviceRequest.getOccurrenceDateTimeType().getValue().toInstant(), ZONE);
        } else if (serviceRequest.hasOccurrencePeriod() && serviceRequest.getOccurrencePeriod().hasStart()) {
            when = LocalDateTime.ofInstant(serviceRequest.getOccurrencePeriod().getStart().toInstant(), ZONE);
        }
        return new ActivityDetailView(
                "ServiceRequest",
                serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null,
                codeLabel(serviceRequest.getCode()),
                when);
    }

    private static String codeLabel(CodeableConcept code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        if (code.hasText()) {
            return code.getText();
        }
        for (Coding coding : code.getCoding()) {
            if (coding.hasDisplay()) {
                return coding.getDisplay();
            }
        }
        return code.hasCoding() ? code.getCodingFirstRep().getCode() : null;
    }

    private static LocalDate toLocalDate(java.util.Date date) {
        return date == null ? null : date.toInstant().atZone(ZONE).toLocalDate();
    }
}
