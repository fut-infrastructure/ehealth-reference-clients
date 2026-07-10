package dk.sundhed.ehealth.referenceclients.clinician.app;

import org.hl7.fhir.r4.model.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
     *
     * <p>The caller passes the CarePlan plus its activities already partitioned by resource type
     * (Task / Appointment / ServiceRequest); we keep that partition rather than merging into one
     * list because the detail template renders each type in its own table. Beyond copying fields,
     * this derives two UI affordances from the status: {@code canActivate} (the dedicated Activate
     * button is only meaningful while the plan is a {@code draft}) and {@code allowedTransitions}
     * (the status dropdown options, see {@link #allowedTransitions}). The EpisodeOfCare back-link
     * is not a plain field either; it is pulled from an extension by {@link #extractEpisodeOfCareId}.
     */
    public static CarePlanDetailView toDetailView(
            CarePlan carePlan,
            List<Task> tasks,
            List<Appointment> appointments,
            List<ServiceRequest> serviceRequests) {
        String carePlanId = carePlan.getIdElement().getIdPart();
        String status = carePlan.hasStatus() ? carePlan.getStatus().toCode() : null;
        boolean canActivate = carePlan.getStatus() == CarePlan.CarePlanStatus.DRAFT;
        List<String> allowedTransitions = allowedTransitions(carePlan.getStatus());

        String title = carePlan.hasTitle() ? carePlan.getTitle() : null;
        String description = carePlan.hasDescription() ? carePlan.getDescription() : null;

        Period period = carePlan.hasPeriod() ? carePlan.getPeriod() : null;
        LocalDate start = toLocalDate(period == null ? null : period.getStart());
        LocalDate end = toLocalDate(period == null ? null : period.getEnd());

        String episodeOfCareId = extractEpisodeOfCareId(carePlan);

        // Each activity type maps to its own ActivityView list; the template shows them separately.
        List<CarePlanDetailView.ActivityView> taskViews =
                tasks.stream().map(CarePlanMapper::toTaskView).toList();
        List<CarePlanDetailView.ActivityView> appointmentViews =
                appointments.stream().map(CarePlanMapper::toAppointmentView).toList();
        List<CarePlanDetailView.ActivityView> serviceRequestViews =
                serviceRequests.stream().map(CarePlanMapper::toServiceRequestView).toList();

        return new CarePlanDetailView(
                carePlanId,
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
    public static List<PlanDefinitionOptionView> toOptions(
            List<PlanDefinition> planDefinitions, Map<String, ActivityDefinition> activities) {
        return planDefinitions.stream().map(planDefinition -> toOption(planDefinition, activities)).toList();
    }

    /**
     * Builds the care-plan summary rows shown on the episode detail page.
     */
    public static List<CarePlanSummaryView> toSummaries(List<CarePlan> carePlans) {
        return carePlans.stream().map(CarePlanMapper::toSummary).toList();
    }

    private static CarePlanSummaryView toSummary(CarePlan carePlan) {
        String carePlanId = carePlan.getIdElement().getIdPart();
        String status = carePlan.hasStatus() ? carePlan.getStatus().toCode() : null;
        String title = carePlan.hasTitle() ? carePlan.getTitle() : carePlanId;
        return new CarePlanSummaryView(carePlanId, status, title);
    }

    /**
     * Maps one {@link PlanDefinition} to a picker row. The label falls back {@code title -> name ->
     * bare id} so the row always shows something clickable. {@code actionSummaries} previews what
     * applying the plan would create, one line per action (see {@link #summariseAction}); the
     * shared {@code activities} map lets those summaries resolve referenced ActivityDefinitions
     * without a per-action server round-trip.
     */
    private static PlanDefinitionOptionView toOption(
            PlanDefinition planDefinition, Map<String, ActivityDefinition> activities) {
        String planDefinitionId = planDefinition.getIdElement().getIdPart();
        String label;
        if (planDefinition.hasTitle()) {
            label = planDefinition.getTitle();
        } else if (planDefinition.hasName()) {
            label = planDefinition.getName();
        } else {
            label = planDefinitionId;
        }
        String version = planDefinition.hasVersion() ? planDefinition.getVersion() : null;
        LocalDate lastUpdated = toLocalDate(planDefinition.getMeta().getLastUpdated());
        List<String> actionSummaries = planDefinition.getAction().stream()
                .map(action -> summariseAction(action, activities))
                .toList();
        return new PlanDefinitionOptionView(planDefinitionId, label, version, lastUpdated, actionSummaries);
    }

    /**
     * Produces a short human-readable label for one PlanDefinition action, shown in the picker's
     * activity preview. What an action exposes as a label depends on how the plan was authored, so
     * we walk a fallback ladder from most to least specific and stop at the first hit:
     *
     * <ol>
     *   <li>the action's own {@code title}, if set;</li>
     *   <li>otherwise the {@link ActivityDefinition} the action instantiates, resolved from the
     *       pre-indexed {@code activities} map: its {@code title}, else a {@code "topic (code)"}
     *       label derived from its topic display and measurement code;</li>
     *   <li>otherwise the bare canonical URL, so a referenced-but-unresolved definition still
     *       renders instead of a blank row;</li>
     *   <li>finally the action's own inline {@code code}, or the literal {@code "action"}.</li>
     * </ol>
     */
    private static String summariseAction(
            PlanDefinition.PlanDefinitionActionComponent action,
            Map<String, ActivityDefinition> activities) {
        // 1. An explicit action title is authoritative.
        if (action.hasTitle()) {
            return action.getTitle();
        }
        // 2. Otherwise resolve the referenced ActivityDefinition. The canonical may carry a
        //    "|version" suffix, but the map is keyed by the versionless URL, so strip it first.
        if (action.hasDefinition()) {
            String canonical = action.getDefinitionCanonicalType().getValue();
            if (canonical != null) {
                String key = new IdType(canonical).toVersionless().getValue();
                ActivityDefinition activityDefinition = activities.get(key);
                if (activityDefinition != null) {
                    if (activityDefinition.hasTitle()) return activityDefinition.getTitle();
                    // No title: build a label from the first non-blank topic display/code...
                    String topic = activityDefinition.getTopic().stream()
                            .flatMap(codeableConcept -> codeableConcept.getCoding().stream())
                            .map(coding -> coding.hasDisplay() ? coding.getDisplay() : coding.getCode())
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst().orElse(null);
                    // ...and the measurement code, combining them as "topic (code)" when both exist.
                    String code = activityDefinition.hasCode() ? codeLabel(activityDefinition.getCode()) : null;
                    if (topic != null && code != null) return topic + " (" + code + ")";
                    if (topic != null) return topic;
                    if (code != null) return code;
                }
                // Definition referenced but not in the map (not _included, or a dangling ref):
                // show the canonical URL rather than nothing.
                return new IdType(canonical).toUnqualifiedVersionless().getValue();
            }
        }
        // 3. No definition reference at all: fall back to the action's own code, else a placeholder.
        if (action.hasCode() && !action.getCode().isEmpty()) {
            return codeLabel(action.getCode().getFirst());
        }
        return "action";
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

    private static CarePlanDetailView.ActivityView toServiceRequestView(ServiceRequest serviceRequest) {
        return new CarePlanDetailView.ActivityView(
                serviceRequest.getIdElement().getIdPart(),
                "ServiceRequest",
                serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null,
                codeLabel(serviceRequest.hasCode() ? serviceRequest.getCode() : null));
    }

    private static String codeLabel(CodeableConcept code) {
        if (code == null) {
            return null;
        }
        if (code.hasText()) {
            return code.getText();
        }
        if (code.hasCoding()) {
            Coding coding = code.getCodingFirstRep();
            if (coding.hasDisplay()) {
                return coding.getDisplay();
            }
            if (coding.hasCode()) {
                return coding.getCode();
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
