package dk.sundhed.ehealth.referenceclients.clinician.app;

import org.hl7.fhir.r4.model.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Maps FHIR {@code CarePlan} (+ activities + PlanDefinition) resources to the view records
 * rendered by the care-plan templates.
 *
 * <p>All derivation logic lives here (status labels, "can activate" affordances, allowed
 * transitions), keeping the {@code api/} layer raw.
 */
public final class CarePlanMapper {

    private CarePlanMapper() {
    }

    /**
     * Builds a {@link CarePlanDetailView} from the FHIR resources gathered for the detail page.
     */
    public static CarePlanDetailView toDetailView(
            CarePlan carePlan,
            List<Task> tasks,
            List<Appointment> appointments,
            List<ServiceRequest> serviceRequests) {
        String id = carePlan.getIdElement().getIdPart();
        String status = carePlan.hasStatus() ? carePlan.getStatus().toCode() : null;
        boolean canActivate = carePlan.getStatus() == CarePlan.CarePlanStatus.DRAFT;
        List<String> allowedTransitions = allowedTransitions(carePlan.getStatus());

        String title = carePlan.hasTitle() ? carePlan.getTitle() : null;
        String description = carePlan.hasDescription() ? carePlan.getDescription() : null;

        Period period = carePlan.hasPeriod() ? carePlan.getPeriod() : null;
        LocalDate start = toLocalDate(period == null ? null : period.getStart());
        LocalDate end = toLocalDate(period == null ? null : period.getEnd());

        String episodeOfCareId = extractEpisodeOfCareId(carePlan);

        List<CarePlanDetailView.ActivityView> taskViews =
                tasks.stream().map(CarePlanMapper::toTaskView).toList();
        List<CarePlanDetailView.ActivityView> appointmentViews =
                appointments.stream().map(CarePlanMapper::toAppointmentView).toList();
        List<CarePlanDetailView.ActivityView> serviceRequestViews =
                serviceRequests.stream().map(CarePlanMapper::toServiceRequestView).toList();

        return new CarePlanDetailView(
                id,
                status,
                canActivate,
                allowedTransitions,
                title,
                description,
                start,
                end,
                episodeOfCareId,
                taskViews,
                appointmentViews,
                serviceRequestViews);
    }

    /**
     * Builds the picker options for {@code GET /episodes/{id}/care-plans/new}.
     */
    public static List<PlanDefinitionOptionView> toOptions(List<PlanDefinition> planDefinitions) {
        return planDefinitions.stream().map(CarePlanMapper::toOption).toList();
    }

    /**
     * Builds the care-plan summary rows shown on the episode detail page.
     */
    public static List<CarePlanSummaryView> toSummaries(List<CarePlan> carePlans) {
        return carePlans.stream().map(CarePlanMapper::toSummary).toList();
    }

    private static CarePlanSummaryView toSummary(CarePlan carePlan) {
        String id = carePlan.getIdElement().getIdPart();
        String status = carePlan.hasStatus() ? carePlan.getStatus().toCode() : null;
        String title = carePlan.hasTitle() ? carePlan.getTitle() : id;
        return new CarePlanSummaryView(id, status, title);
    }

    private static PlanDefinitionOptionView toOption(PlanDefinition pd) {
        String id = pd.getIdElement().getIdPart();
        String label;
        if (pd.hasTitle()) {
            label = pd.getTitle();
        } else if (pd.hasName()) {
            label = pd.getName();
        } else {
            label = id;
        }
        String version = pd.hasVersion() ? pd.getVersion() : null;
        return new PlanDefinitionOptionView(id, label, version);
    }

    /**
     * Allowed status transitions per the spec: from {@code active} the user can move to
     * {@code on-hold}, {@code completed}, {@code revoked}, or {@code entered-in-error}; from
     * {@code on-hold} they can go back to {@code active} or move to the same terminal states.
     * Draft has no dropdown (it uses the dedicated Activate button); terminal states are dead-ends.
     */
    private static List<String> allowedTransitions(CarePlan.CarePlanStatus current) {
        if (current == null) {
            return List.of();
        }
        return switch (current) {
            case ACTIVE -> List.of("on-hold", "completed", "revoked", "entered-in-error");
            case ONHOLD -> List.of("active", "completed", "revoked", "entered-in-error");
            default -> List.of();
        };
    }

    private static CarePlanDetailView.ActivityView toTaskView(Task task) {
        return new CarePlanDetailView.ActivityView(
                task.getIdElement().getIdPart(),
                "Task",
                task.hasStatus() ? task.getStatus().toCode() : null,
                codeLabel(task.hasCode() ? task.getCode() : null));
    }

    private static CarePlanDetailView.ActivityView toAppointmentView(Appointment appointment) {
        String label = null;
        if (appointment.hasServiceType() && !appointment.getServiceType().isEmpty()) {
            label = codeLabel(appointment.getServiceTypeFirstRep());
        }
        if (label == null && appointment.hasDescription()) {
            label = appointment.getDescription();
        }
        return new CarePlanDetailView.ActivityView(
                appointment.getIdElement().getIdPart(),
                "Appointment",
                appointment.hasStatus() ? appointment.getStatus().toCode() : null,
                label);
    }

    private static CarePlanDetailView.ActivityView toServiceRequestView(ServiceRequest sr) {
        return new CarePlanDetailView.ActivityView(
                sr.getIdElement().getIdPart(),
                "ServiceRequest",
                sr.hasStatus() ? sr.getStatus().toCode() : null,
                codeLabel(sr.hasCode() ? sr.getCode() : null));
    }

    private static String codeLabel(CodeableConcept code) {
        if (code == null) {
            return null;
        }
        if (code.hasText()) {
            return code.getText();
        }
        if (code.hasCoding()) {
            Coding c = code.getCodingFirstRep();
            if (c.hasDisplay()) {
                return c.getDisplay();
            }
            if (c.hasCode()) {
                return c.getCode();
            }
        }
        return null;
    }

    /**
     * Standard FHIR workflow extension URL the FUT IG uses on {@code ehealth-careplan} to point at
     * the owning EpisodeOfCare (see {@code input/fsh/ehealth-careplan.fsh}: {@code extension
     * contains http://hl7.org/fhir/StructureDefinition/workflow-episodeOfCare named episodeOfCare
     * 1..1}). The extension is mandatory on conforming CarePlans, so this is the primary lookup.
     */
    private static final String WORKFLOW_EPISODE_OF_CARE_URL =
            "http://hl7.org/fhir/StructureDefinition/workflow-episodeOfCare";

    private static String extractEpisodeOfCareId(CarePlan carePlan) {
        // FUT IG: ehealth-careplan carries the EpisodeOfCare reference in the standard
        // workflow-episodeOfCare extension (1..1, mandatory). Check it first.
        Extension ext = carePlan.getExtensionByUrl(WORKFLOW_EPISODE_OF_CARE_URL);
        if (ext != null && ext.hasValue() && ext.getValue() instanceof Reference ref
                && ref.hasReference() && ref.getReference().contains("EpisodeOfCare/")) {
            return new IdType(ref.getReference()).getIdPart();
        }
        // Defensive fallbacks for non-conforming payloads (e.g. test fixtures or older variants).
        if (carePlan.hasEncounter() && carePlan.getEncounter().hasReference()) {
            String encounterRef = carePlan.getEncounter().getReference();
            if (encounterRef.contains("EpisodeOfCare/")) {
                return new IdType(encounterRef).getIdPart();
            }
        }
        for (Reference supportingRef : carePlan.getSupportingInfo()) {
            if (supportingRef.hasReference()
                    && supportingRef.getReference().contains("EpisodeOfCare/")) {
                return new IdType(supportingRef.getReference()).getIdPart();
            }
        }
        return null;
    }

    private static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
