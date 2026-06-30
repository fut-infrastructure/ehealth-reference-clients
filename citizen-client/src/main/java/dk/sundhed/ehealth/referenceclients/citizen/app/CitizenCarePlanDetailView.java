package dk.sundhed.ehealth.referenceclients.citizen.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;

/**
 * Citizen-facing projection of a single {@link CarePlan} and its activities, built from the search
 * bundle returned by {@code CitizenCarePlanAPI.fetchCarePlanWithActivities}. All FHIR-shape logic
 * lives here so the Thymeleaf template stays declarative.
 *
 * @param id          bare CarePlan id
 * @param title       plan title, or the id when untitled
 * @param status      plan status code (e.g. {@code active})
 * @param description plan description; null when absent
 * @param start       plan period start; null when absent
 * @param end         plan period end; null when absent
 * @param activities  the plan's activities (Task / Appointment / ServiceRequest), time-sorted
 */
public record CitizenCarePlanDetailView(
        String id,
        String title,
        String status,
        String description,
        LocalDate start,
        LocalDate end,
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
            String type, String status, String label, LocalDateTime when) {}

    /** Builds the view from the search bundle, or returns null when no CarePlan is present. */
    public static CitizenCarePlanDetailView from(Bundle bundle) {
        CarePlan carePlan = null;
        List<ActivityDetailView> activities = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof CarePlan cp && carePlan == null) {
                carePlan = cp;
            } else if (resource instanceof Task task) {
                activities.add(fromTask(task));
            } else if (resource instanceof Appointment appointment) {
                activities.add(fromAppointment(appointment));
            } else if (resource instanceof ServiceRequest serviceRequest) {
                activities.add(fromServiceRequest(serviceRequest));
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
                carePlan.hasTitle() ? carePlan.getTitle() : carePlan.getIdElement().getIdPart(),
                carePlan.hasStatus() ? carePlan.getStatus().toCode() : null,
                carePlan.hasDescription() ? carePlan.getDescription() : null,
                toLocalDate(period == null ? null : period.getStart()),
                toLocalDate(period == null ? null : period.getEnd()),
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

    private static ActivityDetailView fromServiceRequest(ServiceRequest sr) {
        LocalDateTime when = null;
        if (sr.hasOccurrenceDateTimeType()) {
            when = LocalDateTime.ofInstant(sr.getOccurrenceDateTimeType().getValue().toInstant(), ZONE);
        } else if (sr.hasOccurrencePeriod() && sr.getOccurrencePeriod().hasStart()) {
            when = LocalDateTime.ofInstant(sr.getOccurrencePeriod().getStart().toInstant(), ZONE);
        }
        return new ActivityDetailView(
                "ServiceRequest",
                sr.hasStatus() ? sr.getStatus().toCode() : null,
                codeLabel(sr.getCode()),
                when);
    }

    private static String codeLabel(CodeableConcept code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        if (code.hasText()) {
            return code.getText();
        }
        for (Coding c : code.getCoding()) {
            if (c.hasDisplay()) {
                return c.getDisplay();
            }
        }
        return code.hasCoding() ? code.getCodingFirstRep().getCode() : null;
    }

    private static LocalDate toLocalDate(java.util.Date date) {
        return date == null ? null : date.toInstant().atZone(ZONE).toLocalDate();
    }
}
