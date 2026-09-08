package dk.sundhed.ehealth.referenceclients.clinician.fhir;

import org.hl7.fhir.r4.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Builds a FHIR transaction {@link Bundle} that flips a {@link CarePlan} and all its referenced
 * activities from {@code draft} to {@code active} in a single atomic call.
 *
 * <p>For each activity resource the matching status enum is applied:
 * <ul>
 *   <li>{@link ServiceRequest} → {@link ServiceRequest.ServiceRequestStatus#ACTIVE}</li>
 *   <li>{@link Task} → {@link Task.TaskStatus#READY} (the FHIR R4 "active" equivalent for Task)</li>
 *   <li>{@link Appointment} → {@link Appointment.AppointmentStatus#BOOKED}</li>
 * </ul>
 * The CarePlan is given a {@link Period} start of "now" if it does not already have one.
 *
 * <p>This util produces the bundle ready for
 * {@code client.transaction().withBundle(bundle).execute()}.
 */
public final class CarePlanActivationUtil {

    private CarePlanActivationUtil() {
    }

    /**
     * Builds a transaction Bundle that activates the given care plan and activities.
     *
     * @param carePlan                          the draft care plan to activate
     * @param activities                        the activities (Task / Appointment / ServiceRequest)
     *                                          to activate alongside
     * @param activityDefinitionsByCanonicalUrl the {@link ActivityDefinition}s each
     *                                          {@link ServiceRequest} instantiates, keyed by
     *                                          versionless canonical URL - supplies a recurring
     *                                          schedule when {@code $apply} left the ServiceRequest's
     *                                          occurrence empty (see {@link #resolveOccurrence})
     * @return a {@link Bundle.BundleType#TRANSACTION} bundle ready to {@code execute()}
     */
    public static Bundle buildActivationBundle(
            CarePlan carePlan,
            List<? extends Resource> activities,
            Map<String, ActivityDefinition> activityDefinitionsByCanonicalUrl) {
        Date now = new Date();

        carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        if (!carePlan.hasPeriod() || carePlan.getPeriod().getStart() == null) {
            carePlan.setPeriod(new Period().setStart(now));
        }

        List<Resource> activatedActivities = new ArrayList<>();
        for (Resource activity : activities) {
            activateActivity(activity, now, activityDefinitionsByCanonicalUrl);
            activatedActivities.add(activity);
        }

        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry(putEntry(carePlan));
        for (Resource activity : activatedActivities) {
            bundle.addEntry(putEntry(activity));
        }
        return bundle;
    }

    private static void activateActivity(
            Resource activity, Date now, Map<String, ActivityDefinition> activityDefinitionsByCanonicalUrl) {
        switch (activity) {
            case ServiceRequest serviceRequest -> {
                serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
                if (!serviceRequest.hasOccurrence()) {
                    serviceRequest.setOccurrence(
                            resolveOccurrence(serviceRequest, activityDefinitionsByCanonicalUrl, now));
                }
            }
            case Task task -> task.setStatus(Task.TaskStatus.READY);
            case Appointment appointment -> appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
            default -> {
                // Unknown activity type: bundle it back as-is. The server will reject it loudly
                // if the type is unexpected, which is the right failure mode for a reference client.
            }
        }
    }

    /**
     * A recurring {@link Timing} copied from the ServiceRequest's ActivityDefinition when it has
     * one, so a genuinely repeating activity (e.g. "daily") doesn't collapse to a single one-shot
     * occurrence just because {@code $apply} left it unset. Falls back to a one-shot {@link Period}
     * starting {@code now} - the only option left once there's no ActivityDefinition to ask, or it
     * doesn't specify a timing itself.
     */
    private static Type resolveOccurrence(
            ServiceRequest serviceRequest,
            Map<String, ActivityDefinition> activityDefinitionsByCanonicalUrl,
            Date now) {
        ActivityDefinition activityDefinition =
                activityDefinitionOf(serviceRequest, activityDefinitionsByCanonicalUrl);
        if (activityDefinition != null && activityDefinition.hasTimingTiming()) {
            return activityDefinition.getTimingTiming().copy();
        }
        return new Period().setStart(now);
    }

    private static ActivityDefinition activityDefinitionOf(
            ServiceRequest serviceRequest, Map<String, ActivityDefinition> activityDefinitionsByCanonicalUrl) {
        if (serviceRequest.getInstantiatesCanonical().isEmpty()) {
            return null;
        }
        String canonical = serviceRequest.getInstantiatesCanonical().getFirst().getValue();
        if (canonical == null) {
            return null;
        }
        String key = new IdType(canonical).toVersionless().getValue();
        return activityDefinitionsByCanonicalUrl.get(key);
    }

    private static Bundle.BundleEntryComponent putEntry(Resource resource) {
        String fullUrl = resource.getIdElement().toVersionless().getValueAsString();
        String requestUrl = resource.getIdElement().toUnqualifiedVersionless().getValueAsString();
        return new Bundle.BundleEntryComponent()
                .setFullUrl(fullUrl)
                .setResource(resource)
                .setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl(requestUrl));
    }
}
